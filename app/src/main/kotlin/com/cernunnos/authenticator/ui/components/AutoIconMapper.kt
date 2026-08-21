package com.cernunnos.authenticator.ui.components

/**
 * Automatic issuer-to-icon mapping.
 *
 * When a TOTP entry is created or imported, the issuer name is matched against
 * this map to automatically assign a Material icon from [IconRegistry].
 * This provides a visual icon without requiring the user to pick one manually.
 *
 * Matching is case-insensitive and uses substring matching (e.g. "Microsoft"
 * matches "microsoft", "Microsoft 365", "Office 365 Microsoft", etc.).
 *
 * For issuers not in this map, [ServiceIcon] falls back to initials with a
 * brand-colored background (see [BrandColors]).
 */
object AutoIconMapper {

    /**
     * Map of normalized issuer substring → IconRegistry key.
     * Order matters: more specific keys should come first (e.g. "outlook" before "office").
     */
    private val mappings = listOf(
        // Tech giants
        "google" to "Language",
        "gmail" to "Email",
        "microsoft" to "Business",
        "outlook" to "Email",
        "office 365" to "Business",
        "office365" to "Business",
        "apple" to "Phone",
        "amazon" to "ShoppingBag",
        "aws" to "Cloud",

        // Cloud / DevOps
        "github" to "Api",
        "gitlab" to "Api",
        "docker" to "Build",
        "cloudflare" to "Cloud",
        "digitalocean" to "Cloud",
        "linode" to "Cloud",
        "vultr" to "Cloud",
        "heroku" to "Cloud",
        "vercel" to "Cloud",
        "netlify" to "Cloud",
        "azure" to "Cloud",
        "oracle" to "Business",
        "ibm" to "Business",

        // Communication
        "discord" to "SportsEsports",
        "slack" to "Work",
        "telegram" to "Send", // mapped below
        "signal" to "Phone",
        "skype" to "Phone",
        "zoom" to "VideoCall", // mapped below
        "teams" to "Work",
        "twitch" to "SportsEsports",
        "whatsapp" to "Phone",

        // Social
        "reddit" to "People",
        "pinterest" to "PhotoCamera",
        "linkedin" to "Work",
        "tiktok" to "MusicNote",
        "snapchat" to "PhotoCamera",
        "tumblr" to "PhotoCamera",
        "mastodon" to "People",
        "bluesky" to "Language",
        "facebook" to "People",
        "instagram" to "PhotoCamera",
        "twitter" to "People",
        "x.com" to "People",

        // Streaming
        "netflix" to "SportsEsports",
        "spotify" to "MusicNote",
        "youtube" to "SportsEsports",
        "disney" to "SportsEsports",
        "hulu" to "SportsEsports",
        "soundcloud" to "MusicNote",
        "deezer" to "MusicNote",
        "tidal" to "MusicNote",

        // Gaming
        "steam" to "SportsEsports",
        "epic games" to "SportsEsports",
        "epic" to "SportsEsports",
        "battle.net" to "SportsEsports",
        "battlenet" to "SportsEsports",
        "playstation" to "SportsEsports",
        "xbox" to "SportsEsports",
        "nintendo" to "SportsEsports",
        "riot" to "SportsEsports",
        "origin" to "SportsEsports",
        "gog" to "SportsEsports",

        // Finance
        "paypal" to "Payments",
        "stripe" to "CreditCard",
        "coinbase" to "Payments",
        "binance" to "Payments",
        "kraken" to "Payments",
        "revolut" to "CreditCard",
        "wise" to "CreditCard",
        "robinhood" to "Payments",
        "cash app" to "Payments",
        "venmo" to "Payments",
        "chase" to "CreditCard",
        "amex" to "CreditCard",
        "visa" to "CreditCard",
        "mastercard" to "CreditCard",

        // Productivity
        "notion" to "Work",
        "airtable" to "Work",
        "trello" to "Work",
        "asana" to "Work",
        "dropbox" to "Cloud",
        "box" to "Cloud",
        "evernote" to "School",
        "atlassian" to "Work",
        "jira" to "Work",
        "confluence" to "Work",
        "figma" to "Build",
        "miro" to "Work",
        "linear" to "Work",

        // Security / Privacy
        "proton" to "Shield",
        "protonmail" to "Email",
        "protonvpn" to "Shield",
        "bitwarden" to "Lock",
        "1password" to "Lock",
        "lastpass" to "Lock",
        "nordvpn" to "Shield",
        "expressvpn" to "Shield",
        "surfshark" to "Shield",
        "mozilla" to "Shield",
        "firefox" to "Shield",
        "duckduckgo" to "Shield",
        "okta" to "Security",
        "auth0" to "Security",
        "duo" to "Security",
        "vault" to "Lock",

        // E-commerce
        "ebay" to "ShoppingCart",
        "shopify" to "Store",
        "etsy" to "Store",
        "alibaba" to "ShoppingCart",
        "mercadolibre" to "ShoppingCart",
        "uber" to "LocalShipping",
        "lyft" to "LocalShipping",
        "airbnb" to "Home",
        "booking" to "Home",
        "doordash" to "LocalShipping",
        "instacart" to "ShoppingCart",

        // Email
        "yahoo" to "Email",
        "zoho" to "Email",
        "fastmail" to "Email",

        // Dev tools
        "npm" to "Build",
        "jetbrains" to "Build",
        "postman" to "Api",
        "twilio" to "Phone",
        "sendgrid" to "Email",
        "mailgun" to "Email",

        // Other
        "openai" to "Build",
        "anthropic" to "Build",
        "perplexity" to "Language",
        "patreon" to "People",
        "kickstarter" to "People",
        "medium" to "School",
        "substack" to "Email",
        "onlyfans" to "PhotoCamera",
        "tinder" to "People",
    )

    /**
     * Additional icon keys that need to be added to IconRegistry.
     * These are referenced by [mappings] but not yet in IconRegistry.icons.
     */
    private val extraIconKeys = setOf("Send", "VideoCall")

    /**
     * Get the automatic icon name for a given issuer.
     * Returns null if no mapping is found.
     */
    fun getIconName(issuer: String): String? {
        val normalized = issuer.lowercase().trim()
        for ((key, iconName) in mappings) {
            if (normalized.contains(key)) {
                // Only return if the icon exists in IconRegistry
                if (IconRegistry.getIcon(iconName) != null) return iconName
            }
        }
        return null
    }

    /**
     * Check if an icon key needs to be added to IconRegistry.
     */
    fun needsExtraIcon(key: String): Boolean = key in extraIconKeys
}
