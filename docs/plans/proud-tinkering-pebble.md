# Plan: Generateur de bibliotheque Plex virtuelle depuis Xtream VOD

## Contexte

PlexHub Backend synchronise deja les catalogues Xtream vers une base SQLite (table `Media`) avec enrichissement TMDB. Cette feature ajoute une **couche de generation de fichiers** qui transforme les donnees synchronisees en arborescence disque compatible Plex, sans jamais stocker de video -- uniquement des `.strm` (URL brute), `.nfo` (metadonnees XML) et images (poster/fanart).

**Approche cle** : exploiter la pipeline de sync existante (`sync_worker` + `enrichment_worker`) comme source de donnees. Le generateur lit la table `Media` enrichie et construit l'arborescence Plex. Cela evite de dupliquer les appels API Xtream et beneficie des metadonnees TMDB deja presentes.

---

## Architecture des modules

```
app/
  plex_generator/                    # Nouveau package
    __init__.py
    models.py                        # Pydantic: PlexMovie, PlexEpisode, PlexSeries, SyncReport
    source.py                        # ABC MediaSource + DatabaseSource (lit Media table)
    naming.py                        # Nommage Plex + sanitization caracteres
    storage.py                       # ABC LibraryStorage + LocalStorage (ecriture disque)
    mapping.py                       # Mapping JSON: source_id -> chemin local + URL
    nfo_builder.py                   # Construction XML NFO (movie.nfo, tvshow.nfo)
    generator.py                     # Orchestrateur principal: sync incrementale
  cli.py                             # CLI Typer: commande `generate`
  api/plex.py                        # Endpoint API: POST /api/plex/generate
tests/
  __init__.py
  test_naming.py                     # Tests nommage + sanitization
  test_mapping.py                    # Tests mapping CRUD + idempotence
  test_generator.py                  # Tests generateur avec MockSource
```

---

## Modele de donnees

### `app/plex_generator/models.py`

```python
class PlexMovie(BaseModel):
    source_id: str           # rating_key existant (ex: "vod_12345.mp4")
    title: str               # Titre nettoye
    year: int | None
    stream_url: str          # URL finale de lecture
    poster_url: str | None   # URL image poster (TMDB ou Xtream)
    fanart_url: str | None   # URL image fanart/backdrop
    genres: str | None       # Genres comma-separated
    summary: str | None
    imdb_id: str | None
    tmdb_id: int | None
    content_rating: str | None

class PlexEpisode(BaseModel):
    source_id: str           # rating_key (ex: "ep_67890.mkv")
    series_title: str
    season_num: int
    episode_num: int
    title: str | None        # Titre de l'episode
    stream_url: str
    summary: str | None

class PlexSeries(BaseModel):
    source_id: str           # rating_key serie (ex: "series_6581")
    title: str
    year: int | None
    poster_url: str | None
    fanart_url: str | None
    genres: str | None
    summary: str | None
    imdb_id: str | None
    tmdb_id: int | None
    episodes: list[PlexEpisode]

class SyncReport(BaseModel):
    created: int = 0
    updated: int = 0
    deleted: int = 0
    unchanged: int = 0
    errors: list[str] = []
    duration_seconds: float = 0.0
```

---

## Strategie de nommage

### `app/plex_generator/naming.py`

**Regles :**
- Reutilise `parse_title_and_year()` du module existant `app/utils/string_normalizer.py`
- Sanitization: remplace `\ / : * ? " < > |` par espace, strip points/espaces en fin (Windows)
- Films : `Films/<Titre> (<Annee>)/<Titre> (<Annee>).strm`
- Series : `Series/<Titre>/Season <NN>/<Titre> S<NN>E<NN>.strm`
- Si annee absente pour un film : `Films/<Titre>/<Titre>.strm`
- Zero-pad saisons et episodes sur 2 chiffres minimum

**Fonctions :**
```python
def sanitize_for_filesystem(name: str) -> str
def movie_path(title: str, year: int | None) -> str        # retourne chemin relatif du .strm
def series_episode_path(series_title: str, season: int, episode: int) -> str
def movie_nfo_path(title: str, year: int | None) -> str    # Films/<T>/movie.nfo
def movie_poster_path(title: str, year: int | None) -> str # Films/<T>/poster.jpg
def series_nfo_path(series_title: str) -> str               # Series/<T>/tvshow.nfo
def series_poster_path(series_title: str) -> str            # Series/<T>/poster.jpg
```

---

## Strategie de synchronisation

### `app/plex_generator/generator.py`

**Algorithme idempotent en 5 etapes :**

1. **Charger le mapping existant** (`mapping.json` dans le repertoire de sortie)
2. **Interroger la source** (`MediaSource.get_movies()` + `MediaSource.get_series()`)
3. **Diff pour chaque item :**
   - `source_id` absent du mapping -> **CREATE** : generer .strm + .nfo + images, ajouter au mapping
   - `source_id` present, `stream_url` change -> **UPDATE** : reecrire le .strm, mettre a jour le mapping
   - `source_id` present, chemin attendu different (titre renomme) -> **MOVE** : supprimer ancien, creer nouveau
   - `source_id` present, rien change -> **SKIP**
4. **Items dans le mapping mais absents de la source** -> **DELETE** : supprimer fichiers + dossier vide, retirer du mapping
5. **Sauvegarder le mapping** + retourner `SyncReport`

**Nettoyage des dossiers vides** apres suppression (remonte l'arbo jusqu'a la racine Films/Series).

---

## Source de donnees

### `app/plex_generator/source.py`

```python
class MediaSource(ABC):
    @abstractmethod
    async def get_movies(self) -> list[PlexMovie]: ...
    @abstractmethod
    async def get_series(self) -> list[PlexSeries]: ...

class DatabaseSource(MediaSource):
    """Lit la table Media + construit les URLs via XtreamService."""
    def __init__(self, account_id: str): ...
```

**`DatabaseSource.get_movies()`** :
- Query : `SELECT * FROM media WHERE server_id='xtream_{account_id}' AND type='movie' AND is_in_allowed_categories=1`
- Pour chaque row, construire `stream_url` via `xtream_service.build_movie_url()`
- Utiliser `resolved_thumb_url` / `resolved_art_url` pour poster/fanart (deja enrichies par TMDB)

**`DatabaseSource.get_series()`** :
- Query shows : `type='show'`
- Query episodes : `type='episode'` groupes par `grandparent_rating_key` + `parent_index`
- Construire `stream_url` via `xtream_service.build_episode_url()`
- Assembler en `PlexSeries` avec liste d'episodes

---

## Stockage

### `app/plex_generator/storage.py`

```python
class LibraryStorage(ABC):
    @abstractmethod
    def write_strm(self, rel_path: str, url: str) -> None: ...
    @abstractmethod
    def write_file(self, rel_path: str, content: str | bytes) -> None: ...
    @abstractmethod
    def download_image(self, rel_path: str, image_url: str) -> bool: ...
    @abstractmethod
    def delete_file(self, rel_path: str) -> None: ...
    @abstractmethod
    def read_strm(self, rel_path: str) -> str | None: ...
    @abstractmethod
    def cleanup_empty_dirs(self, rel_path: str) -> None: ...

class LocalStorage(LibraryStorage):
    def __init__(self, base_dir: Path): ...
```

- `write_strm` : ecrit l'URL brute + newline dans le fichier, `mkdir -p` le parent
- `download_image` : utilise `httpx` synchrone (images legeres), ignore les erreurs (non bloquant)
- `cleanup_empty_dirs` : remonte depuis le fichier supprime jusqu'a `Films/` ou `Series/`

---

## Mapping

### `app/plex_generator/mapping.py`

Fichier JSON `{PLEX_LIBRARY_DIR}/.plex_mapping.json` :

```json
{
  "vod_12345.mp4": {
    "path": "Films/Dune (2021)/Dune (2021).strm",
    "stream_url": "http://xtream.example.com/movie/user/pass/12345.mp4",
    "updated_at": "2026-03-20T14:30:00"
  }
}
```

**Classe `MappingStore`** :
- `load()` / `save()` : lecture/ecriture JSON atomique (ecrire dans `.tmp` puis rename)
- `get(source_id)` / `set(source_id, entry)` / `remove(source_id)`
- `all_source_ids()` : set de tous les IDs mappes

---

## NFO Builder

### `app/plex_generator/nfo_builder.py`

- `build_movie_nfo(movie: PlexMovie) -> str` : XML Kodi-style
- `build_tvshow_nfo(series: PlexSeries) -> str` : XML Kodi-style

```xml
<!-- movie.nfo -->
<movie>
  <title>Dune</title>
  <year>2021</year>
  <plot>...</plot>
  <genre>Science Fiction</genre>
  <uniqueid type="imdb">tt1160419</uniqueid>
  <uniqueid type="tmdb">438631</uniqueid>
</movie>
```

---

## CLI

### `app/cli.py`

```bash
# Generer la bibliotheque pour un compte
python -m app.cli generate --account-id <ID> --output ./Media

# Dry-run (affiche le rapport sans ecrire)
python -m app.cli generate --account-id <ID> --output ./Media --dry-run

# Sans NFO ni images (strm seulement)
python -m app.cli generate --account-id <ID> --output ./Media --strm-only

# Generer pour tous les comptes
python -m app.cli generate --all --output ./Media
```

Utilise **Typer** (ajoute `typer` aux requirements).

---

## API

### `app/api/plex.py`

```
POST /api/plex/generate
  Body: { "accountId": "...", "outputDir": "...", "strmOnly": false, "dryRun": false }
  Response: SyncReport (JSON)
```

Integre dans `app/main.py` comme nouveau router.

---

## Fichiers modifies

| Fichier | Modification |
|---|---|
| `app/config.py` | Ajouter `PLEX_LIBRARY_DIR` (obligatoire, pas de defaut) |
| `app/main.py` | Inclure `api/plex.py` router |
| `requirements.txt` | Ajouter `typer>=0.9.0` |
| `.env.example` | Ajouter `PLEX_LIBRARY_DIR=` (sans defaut, a renseigner) |
| `docker-compose.yml` | Ajouter variable + volume pour PLEX_LIBRARY_DIR |

---

## Fichiers crees (11 fichiers)

| Fichier | Description |
|---|---|
| `app/plex_generator/__init__.py` | Package init |
| `app/plex_generator/models.py` | Modeles Pydantic |
| `app/plex_generator/source.py` | ABC + DatabaseSource |
| `app/plex_generator/naming.py` | Nommage Plex + sanitization |
| `app/plex_generator/storage.py` | ABC + LocalStorage |
| `app/plex_generator/mapping.py` | MappingStore JSON |
| `app/plex_generator/nfo_builder.py` | XML NFO builder |
| `app/plex_generator/generator.py` | Orchestrateur sync |
| `app/cli.py` | CLI Typer |
| `app/api/plex.py` | Endpoint API |
| `tests/test_plex_generator.py` | Tests unitaires |

---

## Points d'attention Plex + .strm

1. **Un .strm = une seule ligne** contenant l'URL brute, pas de BOM, encodage UTF-8
2. **Nommage = source de verite** pour le matching Plex. Le titre du dossier film doit etre `Titre (Annee)` exactement
3. **Plex ignore les .nfo par defaut** -- ils sont utiles avec l'agent "Local Media Assets" ou des plugins tiers (XBMCnfoMoviesImporter). On les genere quand meme en bonus
4. **Caracteres interdits** sur Windows : `\ / : * ? " < > |` -- nettoyes systematiquement
5. **Pas de trailing dots** sur les noms de dossier (Windows les ignore silencieusement)
6. **Episodes : format strict** `S01E01` (zero-padded) pour un matching fiable
7. **Les URLs Xtream contiennent les credentials** -- le fichier .strm est donc sensible. On le signale dans les logs mais c'est inherent au protocole Xtream

---

## Ordre d'implementation

1. `naming.py` + `tests/test_plex_generator.py::test_naming_*`
2. `models.py`
3. `mapping.py` + tests mapping
4. `storage.py`
5. `nfo_builder.py`
6. `source.py` (DatabaseSource)
7. `generator.py` + tests generator (avec MockSource)
8. `config.py` (ajout PLEX_LIBRARY_DIR)
9. `cli.py`
10. `app/api/plex.py` + `main.py` (router)
11. `.env.example`, `requirements.txt`, `docker-compose.yml`

---

## Verification

1. **Tests unitaires** : `pytest tests/test_plex_generator.py -v`
2. **Test CLI dry-run** : `python -m app.cli generate --account-id <ID> --output ./test_media --dry-run`
3. **Test CLI reel** : `python -m app.cli generate --account-id <ID> --output ./test_media` puis verifier l'arborescence generee
4. **Idempotence** : relancer la meme commande, verifier que le rapport indique 0 created, 0 deleted
5. **Test API** : `POST /api/plex/generate` avec un body valide
6. **Verification Plex** : pointer une bibliotheque Plex vers le dossier genere et lancer un scan
