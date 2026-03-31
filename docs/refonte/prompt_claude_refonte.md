# Prompt de Refonte Complet pour Claude Code

Copiez-collez ce prompt dans Claude Code (ou tout autre agent AI) pour initier l'implémentation complète de la refonte "Cinema Gold".

---

**PROMPT :**

Tu vas réaliser la refonte graphique TOTALE de l'application Android TV `PlexHubTV` en Jetpack Compose, en appliquant un nouveau design system "Cinema Gold" validé. 

**CONTEXTE & RÈGLES GLOBALES** :
- **Aucune fonctionnalité existante ne doit être perdue** (le code gère le multi-source, Plex, Jellyfin, Xtream, le lecteur ExoPlayer, la navigation au clavier/D-Pad, etc.).
- Ne retire aucune logique métier, ViewModel ou gestion d'état existante. Ne modifie que la couche UI.
- Tu travailles sur un projet Android TV (il faut donc gérer scrupuleusement le `FocusRequester`, l'état `isFocused`, le scale on focus, et les comportements D-pad).

**DESIGN SYSTEM "CINEMA GOLD"** :
- **Palette stricte** :
  - Fond de l'app : Noir profond `#06060a`.
  - Accents et bordures focus : Doré `#c9952e`.
  - Texte principal : `#e8e2d6` (poids 500 pour body, 600 labels, 700 titres).
  - Surfaces secondaires (cards) : `rgba(232,226,214, 0.04)`.
- **Focus TV (Générique)** : Tout élément focusable doit implémenter :
  - Un scale up fluide (de `1.0f` à `1.04f` ou `1.06f`).
  - Une bordure dorée de `3dp` ajoutée autour de l'élément (sans décaler le layout interne).
  - Un "glow" ou shadow s'accordant au thème.
- **Marqueurs de section** : Tout titre de section/row doit être précédé d'un rectangle vertical doré de `3dp` de large par `14dp` de haut.

**ÉCRANS & COMPOSANTS À MODIFIER :**

1. **Suppression du Backdrop Global & Mise à jour de `MainScreen`**
   - L'application actuelle affiche dynamiquement l'image de fond du `focusedItem` en plein écran via un header passif (`HomeHeader`) et un hero billboard.
   - **Tâche** : Supprime le `HomeHeader` passif. Modifie `MainScreen` (ou le composant racine) pour retirer l'image de fond globale. Le fond de l'application doit rester statiquement `#06060a`.

2. **Création du `SpotlightGrid` (Nouvel Accueil)**
   - Crée un composant `SpotlightGrid.kt` qui remplace le billboard. 
   - Layout : Une Row contenant 1 grande carte à gauche (occupant 2/3 de l'espace verticalement) et une Column à droite contenant 2 petites cartes empilées.
   - Les 3 cartes sont *focusables*. Le focus initial à l'ouverture de l'app doit être donné à la grande carte de gauche.
   - Chaque carte affiche son propre backdrop (image asynchrone), avec le titre en bas à gauche et des métadonnées (Année, Durée).

3. **Mise à jour de `NetflixHomeContent` / `NetflixHomeScreen`**
   - Remplace l'appel à `HomeHeader` (qui occupait les 40% supérieurs) par ton nouveau `SpotlightGrid`.
   - Modifie la mécanique de focus : le `firstRowFocusRequester` qui ciblait la première ContentRow doit maintenant cibler la carte principale du `SpotlightGrid`.
   - Assure-toi que la `LazyColumn` en dessous permet une navigation fluide D-Pad (down) depuis le `SpotlightGrid`.

4. **Style des ContentRows ("Reprendre", "Ma Liste", etc.)**
   - Modifie `NetflixMediaCard` pour gérer les spécificités :
     - Pour la row "Continue Watching" : Format paysage 16:9 avec une barre de progression fine et dorée en bas. Ajoute un overlay sombre (badge) textuel en bas à droite indiquant le temps restant (ex: "32 m").
     - Pour les autres rows : Format portrait 2:3.
     - Implémente le badge "Score" carré doré en haut à gauche et le badge "4K/HDR" en haut à droite.
     - Optionnel : pour la rangée des recommandations/trending, affiche un chiffre géant coupé derrière le poster (style "Top 10").

5. **Refonte de l'Écran Détail (`NetflixDetailScreen`)**
   - L'écran Détail ne doit plus avoir un backdrop plein écran.
   - Le backdrop doit être rogné pour n'occuper que le tiers supérieur (un bandeau) avec un dégradé fondu vers le bas (`#06060a`).
   - Le `Poster` (affiche) vient "mordre" sur ce backdrop (changement de z-index et offset négatif).
   - Zone de métadonnées : Utilise des étoiles visuelles pour le rating (icônes dorées). Affiche les tags/genres sous forme de "Pills" (boutons arrondis avec bordure subtile).
   - Les boutons d'action (Lancer, Trailer, etc.) : Le bouton Lancer est rempli d'or (`#c9952e`) avec texte noir. Les autres ont un fond transparent et des bordures claires.

6. **Refonte de l'Écran Détail de Saison (Séries)**
   - Plus de backdrop supérieur. Affiche le poster de la série à gauche (fixe).
   - Au milieu/droite : Crée des Tabs pour les saisons en forme de "Pills" focusables.
   - Ligne d'épisode : Affichage en `LazyColumn`. Chaque item a un aperçu 16:9, numéro, titre. Le statut "Vu" est représenté par un point doré en haut à droite, pas par une coche. Au focus, le conteneur entier prend un fond très légèrement doré/transparent avec la bordure gold.

7. **Écran IPTV (`LiveTvScreen`)**
   - Modifie la vue de la grille des chaînes pour avoir des cartes 16:9 plus larges.
   - Ajoute un composant `BadgeLive` rouge (avec un dot animé clignotant) incrusté sur le coin de l'image de la chaîne ou du programme en cours.

8. **Popup Choix de Source (SourceSelectionDialog)**
   - S'ouvre sur un fond noir transparent.
   - Transforme la boîte centrale avec des coins ronds (16dp).
   - La liste des serveurs: fond transparent par défaut. Focus = transition vers `rgba(201, 149, 46, 0.15)` avec bordure dorée.
   - Ajoute les badges textuels sur 3 lignes par item : [Icône] Nom complet, [Mode] (Direct/Transcode), et les tags techniques.
   - Ajoute un sticker textuel "RECOMMANDE" sur la première source (la meilleure).

Je te demande de modifier tous les fichiers impactés, un par un, en veillant scrupuleusement à ne casser aucune fonctionnalité existante (lecture, état, gestion des sources réseau). Implémente le CSS-in-JS (Compose Modifiers) pour obtenir un rendu "Premium".
Commence par l'implémentation du `SpotlightGrid.kt` et la suppression du `HomeHeader` passif de l'accueil.
