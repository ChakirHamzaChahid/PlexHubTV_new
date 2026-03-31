# Mockups Fonctionnels — Thème "Cinema Gold"

Ce document décrit la structure visuelle et interactive de chaque écran de l'application Android TV PlexHubTV pour la refonte "Cinema Gold". Aucune limitation technique n'est retenue, le but est d'atteindre le design validé de manière *pixel-perfect*.

---

## 🎨 Design System Global
- **Fond de l'application** : Noir profond (`#06060a`) - aucun backdrop global.
- **Surface Cards** : Noir légèrement grisé `rgba(232,226,214,0.02-0.06)`.
- **Textes** : Primaire `e8e2d6` (body 500), Titres (system-ui, 700). Labels (600).
- **Couleur d'accent (Or)** : `#c9952e`. Utilisée pour les focus, les textes d'accroche et les boutons principaux.
- **État "Focus" TV** :
  - Scale up in-out : `1.03` à `1.06`.
  - Bordure : `3px` solid `#c9952e`.
  - `outline-offset` equivalent : `2px` (espace entre l'image et la bordure).
  - Élévation (ombre subtile en mode focus).
- **Titres de Section** : Précédés d'un marqueur vertical doré `3px x 14px`.

---

## 📺 1. Écran d'Accueil (Home / Hub)

**Structure Haut-en-bas :**
1. **TopBar** (transparente au top, devient fond opaque au scroll).
2. **Spotlight Grid** : Composant remplaçant le Hero Billboard.
   - **Configuration** : Grille asymétrique (1 grande card à gauche occupant 2 rangées, 2 petites cards empilées à droite).
   - **Focusable** : Oui. Les utilisateurs navigent entre ces 3 cartes.
   - **Contenu Card** : Backdrop image + Titre (Bottom-Left) + Meta (Année, Durée, Badge 4K).
3. **NetflixContentRow "Reprendre"** :
   - Cards format paysage (16:9).
   - Overlay : Badge de temps restant ("32min restantes") en coin inférieur droit.
   - Barre de progression horizontale, accent doré bas du poster.
4. **NetflixContentRow "Top 10" (Suggestion / Trending)** :
   - Cards format portrait (2:3).
   - Filigrane géant numéroté caché en partie derrière le poster (façon Netflix Top 10) aux couleurs de l'accent.
5. **Autres Hubs / Rows classiques** :
   - Posters (2:3).
   - Coin supérieur droit : Petit triangle SVG doré indiquant un contenu "Non Vu".

---

## 📚 2. Écran Bibliothèque (Films / Séries)

**Structure :**
- **En-tête** : Titre aligné à gauche ("Films") avec marqueur vertical doré `3px`. Compteur items sur la même ligne (ex: "847 films" gris clair).
- **Bandeau Filtres (Pills)** : Une `LazyRow` sous le titre contenant des boutons ovales ("Pills") focusables pour : `Serveur`, `Genre`, `Trier par`.
- **Grille de Posters** (Responsive) :
  - Chaque poster intègre :
    - En haut à gauche : Badge Score Doré (fond noir/transparent, écriture `#c9952e`).
    - En haut à droite : Badge "4K" ou "HDR" fond sombre.
    - Coin supérieur droit : Triangle doré "Non Vu" (si applicable).
    - Bas du poster à droite : "2023" (Année).
- **Alphabet Sidebar** (Si mode de tri alphabétique) : Lettrines empilées à droite de l'écran, scrollable focusable, couleur or au focus.

---

## 🎬 3. Détail d'un Film

**Structure :**
1. **Header Réduit (Backdrop Banner)** :
   - L'image de backdrop prend uniquement le ~1/3 supérieur de l'écran et fond en dégradé noir (`#06060a`) vers le bas.
2. **Zone de Contenu Principale (Overlap)** :
   - **Poster** : Surélevé (z-index supérieur) pour chevaucher le dégradé du backdrop.
   - Sur le poster : Score doré + Badges Qualité en overlay propre.
   - **Zone Texte (à droite du poster)** :
     - Titre grand format (ExtraBold 700).
     - Row Meta : Année • Durée • Rating (Étoiles dorées `★ ★ ★ ★ ☆`) • Codec (HEVC) • Serveur.
     - Genres : Pills horizontales de tags.
   - **Boutons d'Action (Focusables)** :
     - **Lancer (Play)** : Fond `#c9952e`, Texte `#06060a` (Noir).
     - **Autres (Bande Annonce, Favoris, Vu)** : Fond transparent, Bordure fine `rgba(232,226,214,0.08)`, Texte clair. Bordure dorée au focus.
3. **Synopsis & Équipe** :
   - Texte clair (Weight 500). Bouton "Plus..." focusable si texte trop long.
   - Initiales ou vignettes du Casting dans une row focusable (cercles).
4. **Tabs & Recommandations** :
   - Tabs style Pills (Similaires, Collections, Bande Annoce).
   - `LazyRow` pour les posters associés.

---

## 📺 4. Détail d'une Série

**Différences par rapport au Film :**
- Pas de grand backdrop en header pour privilégier la liste des épisodes. Layout splité :
  - **Gauche (1/3)** : Grand poster de la saison sélectionnée + Titre de la série + Synopis court.
  - **Droite (2/3)** : Contenu détaillé (Onglets + Liste).
- **Tabs de Saison** : Menu horizontal de Pills focusables (Saison 1, Saison 2).
- **Liste des Épisodes (LazyColumn)** :
  - Chaque ligne montre : Thumbnail 16:9 à gauche, Numéro & Titre de l'épisode, Résumé court (2 lignes), Durée.
  - **Progression** : Barre fine `#c9952e` sous le thumbnail.
  - **Indicateur Vu** : Petit point doré ("dot") au lieu de checkmark.
  - **Focus** : La background du conteneur d'épisode entier devient légèrement ocre transparent + bordure globale.

---

## 📡 5. Écran IPTV (Live TV)

**Structure :**
- **Vue par catégories** (Si source backend) : Liste de gauche classique mais revampée (texte or au focus, marker actif).
- **Grille des Chaînes (Landscape Cards)** :
  - Grandes cartes 16:9 au lieu de carrées.
  - **Encart "EN DIRECT"** : Badge rouge pétant avec un point rouge qui clignote en CSS/animation Compose.
  - Contenu Overlay sur la card logo de chaîne : Le programme actuellement en cours + Timeline de progression (vert/orange/or).
- **Filtres Haut de Page** : Pays / Genres via conception Pills.

---

## ⚙️ 6. Choix et Configuration des Sources Médias

**Structure (Écran Plein) :**
- Titre + Bouton de rafraîchissement global.
- **Cartes Source (Grid Layout)** :
  - Hautement stylisées.
  - Icône large identitaire (Le 'P' de Plex en or, 'X' de Xtream en bleu, Jellyfin en violet).
  - Statut de connexion : Pastille lumineuse (vert=OK, gris=Offline, rouge=Erreur).
  - Statistiques dans la card (ex: "1 234 Films • 45 Séries").
- **Cartes d'Action Secondaire** :
  - **"Ajouter une source"** : Container border-dashed `rgba(232,226,214,0.3)`. Au focus, le dashed devient Or `#c9952e` et plein.

---

## 🔀 7. Popup : Choix de la Source de Lecture

*S'ouvre quand le média existe sur plusieurs serveurs ou qualités.*
- **Affichage** : Overlay noir semi-transparent blur (`Box` avec fond Alpha) par-dessus l'interface détail.
- **Modale Centrale** : Window aux bords très arrondis (16dp). Fond propre noir `#06060a`, Stroke extérieure très fine grise/dorée.
- **En-tête de Popup** : Miniature du poster à gauche, Titre à droite, Année, Badge.
- **Liste des Options (Verticale)** :
  - Chaque option est focusable. Fond transparent par défaut, transition vers sombre-doré au focus.
  - **Contenu d'une option** :
    - Icône serveur (ex: Plex).
    - Ligne 1 : Nom complet de la source ("Plex - Home Server").
    - Ligne 2 : Mode ("Lecture directe", "Transcodage x264").
    - Ligne 3 (Bas) : Badges (4K ou 1080p, HEVC, Taille `4 GB`).
    - **Indicateur Qualité Réseau** : Icônes signal (3 barres vertes=Local, Or=Internet distant).
    - **Badge Spécial** : Un étiquetage textuel "⭐ RECOMMANDE" accroché en haut à droite du conteneur de la meilleure proposition (déterminée par bitrate ou proximité).
- **Actions Bas** : Boutons "Annuler" (texte clair) et "Lancer la lecture" (plein or). Se rafraichit dynamiquement quand on navigue dans les options.
