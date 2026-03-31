# Impact Navigation : Refonte "Cinema Gold"

Cette analyse détaille les implications techniques de la transition vers le thème "Cinema Gold", avec un focus particulier sur la mécanique de navigation D-pad et la gestion du focus dans Jetpack Compose pour Android TV.

## 1. Remplacement du `HomeHeader` par `SpotlightGrid`

### Comportement Actuel
Dans `NetflixHomeContent` :
- `HomeHeader` occupe les 40% supérieurs de l'écran. Il est **passif** (non-focusable).
- Le focus initial est forcé sur la première rangée de contenu (`NetflixContentRow`) via un `FocusRequester` (`firstRowFocusRequester`).
- Lorsqu'une carte de contenu prend le focus, un callback `onFocusChanged(it)` est émis vers le parent.
- Ce parent met à jour le `focusedItem`, qui redessine le `HomeHeader` et déclenche le changement de backdrop global.

### Nouveau Comportement (Spotlight Grid)
- Le `SpotlightGrid` devient une zone **active** et **focusable**.
- **Focus Flow** :
  1. À l'ouverture de l'application, le `FocusRequester` initial pointe désormais sur la **grande carte principale** du `SpotlightGrid`.
  2. La navigation droite (D-pad Right) depuis la grande carte emmène le focus sur les deux cartes secondaires empilées.
  3. La navigation bas (D-pad Down) transfère le focus vers le premier `NetflixContentRow` ("Reprendre" ou "Ma Liste").
  4. La navigation haut depuis les rangées de contenu doit cibler la carte du `SpotlightGrid` la plus proche horizontalement.
- **Mise à jour d'état** : L'état `focusedItem` (qui maintient l'élément surligné) ne sera plus utilisé pour alimenter un header passif en haut de page, mais potentiellement pour afficher des infos contextuelles furtives, ou sera complètement supprimé au profit de backdrops internes aux éléments.

## 2. Suppression du Backdrop Global (`MainScreen`)

### Comportement Actuel
Le composant englobant (`MainScreen` ou le Scaffold principal) gère probablement une `AsyncImage` en plein écran avec des gradients superposés (`BackdropColors`), en écoutant le `focusedItem` remonté par les écrans enfants.

### Nouveau Comportement
- Le backdrop global doit être supprimé ou désactivé sur l'écran d'accueil (`HomeScreen` / `DiscoverScreen`).
- Le fond de l'application devient un unicolore noir profond (`#06060a`).
- **Raison** : Chaque carte du `SpotlightGrid` contient déjà sa propre image de fond et ses propres informations. Avoir une image de fond globale créerait une surcharge visuelle et un conflit de lisibilité.
- **Impact Fichiers** : `MainScreen.kt`, `HomeScreen.kt` (le comportement du backdrop devra être internalisé dans les écrans de détail, ce qui semble être le cas d'après les spécifications "Backdrop réduit en haut").

## 3. TopBar et Comportement de Scroll

### Comportement Actuel
La `TopBar` est dessinée par-dessus le contenu et devient progressivement opaque (ou disparaît/rétrécit) lorsque l'utilisateur scrolle vers le bas (détecté via le changement de `focusedRowIndex` ou un `listState`).

### Nouveau Comportement
- Ce comportement doit être **préservé**.
- Puisque le `SpotlightGrid` se trouve au-dessus ou en tant que premier élément de la `LazyColumn`, le calcul du scroll-state reste identique.
- Une attention particulière doit être portée au z-index : la TopBar doit rester bien au-dessus du SpotlightGrid, même quand les cartes de la grille gèrent un scale de focus (`1.06f`).

## 4. Composants partagés et Focus Restoration

### Mécanisme de Focus Restoration
Actuellement, Compose for TV s'appuie beaucoup sur `remember { FocusRequester() }` et la sauvegarde de clés pour redonner le focus à la bonne ligne au retour d'un écran.
- Le `SpotlightGrid` devra exposer des `FocusRequester` pour ses propres éléments, de sorte que si un utilisateur clique sur une carte Spotlight > consulte les détails > revient, le focus soit rendu à cette même carte.
- **Fichiers impactés** : `NetflixHomeContent.kt`, un nouveau fichier `SpotlightGridRow.kt`, `core/ui/FocusUtils` (si existant).

## Liste des Fichiers Clés Modifiés ou Créés

1. **`core/designsystem/Theme.kt`** & **`Color.kt`**
   - Mise à jour stricte de la palette : `#06060a` (fond), `#c9952e` (or), `#e8e2d6` (texte principal).
   - Adaptation du Typography (poids 700, 600, 500).

2. **`core/ui/SpotlightGrid.kt`** (CRÉATION)
   - Contient la disposition asymétrique (1 grande + 2 petites), la gestion du focus (scale, bordure dorée `3px`), et la navigation interne.

3. **`feature/home/NetflixHomeScreen.kt`** / **`NetflixHomeContent.kt`**
   - Remplacement de l'invocation `HomeHeader` par le nouveau `SpotlightGrid`.
   - Ajustement de l'indexation de la `LazyColumn` et de la cible du `firstRowFocusRequester`.

4. **`core/ui/MainScreen.kt`** ou équivalent
   - Désactivation du backdrop dynamique global lors de la navigation sur le Home/Hub.

5. **`core/ui/NetflixMediaCard.kt`**
   - Implémentation du style 16:9 ("Reprendre") avec overlay de temps.
   - Ajout des filigranes "Netflix Top 10" pour les cartes de classement.

6. **`feature/detail/NetflixDetailScreen.kt`**
   - Réduction de la hauteur du backdrop.
   - Intégration du composant overlap (affiche superposée à la jointure backdrop/contenu).
   - Ajout du système d'étoiles dorées et tags en pills.

7. **`feature/iptv/LiveTvScreen.kt`**
   - Re-styling des catégories et grille des chaînes (badges rouges "EN DIRECT", overlay de programme).
