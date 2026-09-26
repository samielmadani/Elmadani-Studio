package com.samielmadani.elmadanistudio.data

data class WebsiteEntry(
    val name: String,
    val url: String,
    val description: String? = null
)

object WebsiteCatalog {
    val entries = listOf(
        WebsiteEntry("Rami Baha Visuals", "https://ramibahavisuals.com"),
        WebsiteEntry("Sami Elmadani", "https://samielmadani.com"),
        WebsiteEntry("Sami Ali Elmadani", "https://samialielmadani.pages.dev"),
        WebsiteEntry("Nigerian Community Canterbury", "https://nigeriancanterbury.org"),
        WebsiteEntry("Bariz Shah", "https://barizshah.com"),
        WebsiteEntry("Maha Elmadani", "https://mahaelmadani.com"),
        WebsiteEntry("CloudLedger", "https://cloudledger.co.nz")
    )
}
