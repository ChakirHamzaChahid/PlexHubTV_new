package com.chakir.plexhubtv.core.model

/**
 * Un "Hub" représente une section horizontale ou carrousel sur l'écran d'accueil.
 *
 * Exemples : "On Deck" (En cours), "Recently Added Movies", "More from this director".
 *
 * @property key Clé API permettant d'accéder au contenu complet de ce Hub.
 * @property title Titre affiché à l'utilisateur (ex: "Récemment ajoutés").
 * @property type Type de contenu (movie, show, mixed).
 * @property hubIdentifier Identifiant stable pour la logique métier (ex: "recentlyAdded").
 * @property items Liste des médias contenus dans ce hub (généralement limitée aux 10-20 premiers).
 * @property serverId ID du serveur source (si le hub est spécifique à un serveur).
 */
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Hub(
    val key: String,
    val title: String,
    val type: String,
    val hubIdentifier: String? = null,
    val items: List<MediaItem>,
    val serverId: String? = null,
) : Parcelable
