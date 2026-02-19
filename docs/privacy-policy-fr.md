# Politique de Confidentialité - PlexHubTV

Dernière mise à jour : 19 février 2026

PlexHubTV est un client tiers pour Plex Media Server, conçu pour Android TV. Cette application est développée par un développeur indépendant et **n'est pas affiliée à Plex Inc.**

## Données que nous collectons

### 1. Données d'authentification Plex
- Jeton d'authentification Plex
- Identifiant de compte Plex (via l'API Plex)
- Identifiants des serveurs Plex (ID serveur, IP ou hostname)

**Finalité** :
Permettre à l'application de se connecter à votre compte Plex et d'accéder à vos bibliothèques média.

### 2. Données de lecture locales
- Médias que vous lisez (films, séries, épisodes)
- Progression de lecture (position, durée)
- Favoris, watchlist, historique

**Stockage** :
Toutes ces données sont stockées **localement sur votre appareil** dans une base de données chiffrée (Room + EncryptedSharedPreferences).

**Finalité** :
Fournir des fonctionnalités comme "Reprendre la lecture", l'historique, certaines recommandations et les téléchargements hors ligne.

### 3. Données de crash et de performance
Si vous activez le reporting de crash (Crashlytics) :

- Modèle de l'appareil, version d'Android
- Version et type de build de l'app
- Traces de pile et détails d'erreur
- Données d'usage anonymisées (écrans, événements)

**Prestataire** :
Google Firebase (Crashlytics, Analytics, Performance).

**Finalité** :
Détecter les crashs et améliorer la stabilité et les performances de l'application.

### 4. Requêtes réseau

PlexHubTV communique avec :
- **Plex Inc.** (plex.tv, serveurs Plex que vous configurez)
- **The Movie Database (TMDb)** pour les métadonnées et notes
- **OMDb API** pour les notes IMDb

Ces services ont leurs propres politiques de confidentialité :
- Plex : https://www.plex.tv/about/privacy-legal/
- TMDb : https://www.themoviedb.org/privacy-policy
- OMDb : http://www.omdbapi.com/legal.htm

## Stockage et durée de conservation

- Toutes les données sensibles (jeton Plex, clés API) sont stockées localement avec le chiffrement Android (EncryptedSharedPreferences).
- Aucune donnée personnelle n'est envoyée à un serveur appartenant à PlexHubTV.
  **Nous n'exploitons aucun serveur backend.**
- Vous pouvez supprimer toutes les données locales en désinstallant l'app ou via "Effacer les données" sur votre appareil Android TV.

## Données des enfants

PlexHubTV ne s'adresse pas spécifiquement aux enfants de moins de 13 ans.
Les restrictions de contenu dépendent de la configuration de votre serveur Plex et de vos profils Plex.

## Vos droits

PlexHubTV n'hébergeant pas vos données en ligne, vos droits concernent principalement :

- **Les données sur l'appareil** : vous pouvez les supprimer via les paramètres Android TV.
- **Les données de votre compte Plex** : gérées par Plex Inc. via https://plex.tv

Vous pouvez révoquer l'accès de PlexHubTV à votre compte Plex en supprimant l'app des appareils autorisés dans Plex.

## Contact

Pour toute question concernant cette politique de confidentialité, vous pouvez contacter le développeur à :

**Email** : chakir.elarram@gmail.com
