package com.chakir.plexhubtv.core.model

data class XtreamAccount(
    val id: String,
    val label: String,
    val baseUrl: String,
    val port: Int,
    val username: String,
    val status: XtreamAccountStatus,
    val expirationDate: Long?,
    val maxConnections: Int,
    val allowedFormats: List<String>,
    val serverUrl: String?,
    val httpsPort: Int?,
)

enum class XtreamAccountStatus {
    Active,
    Expired,
    Banned,
    Disabled,
    Unknown,
    ;

    companion object {
        fun fromApiStatus(status: String?): XtreamAccountStatus =
            when (status?.lowercase()) {
                "active" -> Active
                "expired" -> Expired
                "banned" -> Banned
                "disabled" -> Disabled
                else -> Unknown
            }
    }
}
