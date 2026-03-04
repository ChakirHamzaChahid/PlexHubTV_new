# Notes de version PlexHubTV

## 🎉 Dernières améliorations

### 📚 Amélioration Multi-Sources
**Enfin résolu !** Le système multi-sources fonctionne maintenant parfaitement :

- ✅ **Tous vos épisodes sont maintenant visibles** : Si vous avez une série sur Plex (6 épisodes) et Xtream (4 épisodes), vous verrez bien les 6 épisodes ! Les 4 premiers en multi-source, les 2 derniers en Plex uniquement
- ✅ **Plex toujours en priorité** : Lorsque vous avez le même contenu sur plusieurs serveurs, c'est toujours la source Plex qui s'affiche en premier (meilleure qualité des métadonnées)
- ✅ **Agrégation stable** : Le tri par "Date d'ajout" fonctionne maintenant correctement avec le multi-sources (le message "Disponible sur" s'affiche bien)
- ✅ **Sous-titres et langues** : Les épisodes Plex affichent maintenant correctement tous les sous-titres et pistes audio disponibles

### 📺 Support IPTV (Backend & Xtream)
**Nouveau !** PlexHubTV supporte maintenant les sources IPTV :

- 🆕 **Backend PlexHub** : Connectez votre backend PlexHub pour accéder à vos contenus IPTV
- 🆕 **Comptes Xtream** : Ajoutez vos comptes Xtream Codes directement dans l'application
- 🎬 **Lecture intégrée** : Regardez vos contenus IPTV avec le même player que vos médias Plex
- 📂 **Gestion des catégories** : Filtrez et organisez vos catégories IPTV selon vos préférences
- 🔄 **Synchronisation automatique** : Vos contenus IPTV se synchronisent automatiquement avec votre bibliothèque

### 🎨 Expérience Utilisateur Améliorée

**Continue Watching / À suivre** :
- 🎯 **Tri intelligent** : Les contenus en cours sont triés par date de visionnage récente
- ❌ **Suppression facile** : Appui long sur un élément pour le retirer de "À suivre"
- ⏱️ **Temps restant affiché** : Voyez combien de temps il reste sur toutes les cartes de contenu
- ✓ **Badge "Vu"** : Indicateur visuel clair pour les épisodes et films déjà regardés

**Navigation & Interface** :
- ✨ **Squelettes de chargement** : Animation shimmer élégante pendant le chargement
- 🎯 **Focus amélioré** : Navigation au clavier/télécommande plus fluide sur tous les écrans
- 📱 **États vides améliorés** : Messages plus clairs quand une section est vide
- 🔔 **Toast de reprise** : Notification discrète quand vous reprenez une vidéo en cours

### ⚡ Performances

**Plus rapide que jamais** :
- 🚀 **Recherche ultra-rapide** : Migration vers FTS4 (Full-Text Search) - résultats instantanés
- 📊 **Historique optimisé** : Chargement 10x plus rapide de votre historique de visionnage
- 🏠 **Écran d'accueil** : Réduction drastique des appels réseau redondants
- 💾 **Mémoire optimisée** : Meilleure gestion de la mémoire sur toute l'application

### 🔧 Corrections de Bugs

**Stabilité & Fiabilité** :
- ✓ Correction des plantages lors de l'initialisation du player
- ✓ Résolution des problèmes de focus sur les écrans Favoris/Historique/Téléchargements
- ✓ Correction du toast de reprise qui réapparaissait lors du changement de qualité
- ✓ Validation des URIs de sous-titres pour éviter les erreurs de lecture
- ✓ Correction des échecs de synchronisation transitoires
- ✓ Meilleure gestion des erreurs réseau

### 🏗️ Refactorisation Technique
*(Pour les curieux)*

- Architecture modulaire améliorée avec séparation claire des responsabilités
- Migration vers kotlinx-serialization pour de meilleures performances
- Suppression des dépendances obsolètes (Resource<T> wrapper)
- Code plus maintenable et testable
- Meilleure injection de dépendances

---

## 📝 Notes Techniques

**Commits inclus** : 102 commits depuis `20dd355`

**Modules affectés** :
- `:app` - Application principale
- `:data` - Couche de données et repositories
- `:domain` - Logique métier et use cases
- `:core:*` - Modules partagés (model, network, ui, etc.)
- `:feature:*` - Écrans et fonctionnalités

**Migration de base de données** : v29 (ajout de `displayRating`, `historyGroupKey`, `id_bridge`)

---

## 🙏 Remerciements

Merci d'utiliser PlexHubTV ! N'hésitez pas à signaler tout problème ou suggestion sur GitHub.

**Version** : Development Build
**Date** : Mars 2026
**Plateforme** : Android TV
