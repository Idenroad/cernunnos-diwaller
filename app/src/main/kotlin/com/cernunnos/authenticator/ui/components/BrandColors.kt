package com.cernunnos.authenticator.ui.components

/**
 * Curated brand database for popular 2FA services.
 * Maps issuer names (lowercase, normalized) to exact brand colors.
 * Used by ServiceIcon to render icons with authentic brand colors.
 *
 * This covers the ~80 most popular 2FA services. Unknown services
 * fall back to hash-based color generation.
 */
object BrandColors {

    // (normalized issuer substring -> ARGB color)
    private val brands = mapOf(
        // Tech giants
        "google" to 0xFF4285F4.toInt(),
        "microsoft" to 0xFF0078D4.toInt(),
        "apple" to 0xFF000000.toInt(),
        "amazon" to 0xFFFF9900.toInt(),
        "meta" to 0xFF0866FF.toInt(),
        "facebook" to 0xFF1877F2.toInt(),
        "instagram" to 0xFFE1306C.toInt(),
        "whatsapp" to 0xFF25D366.toInt(),
        "twitter" to 0xFF1DA1F2.toInt(),
        "x.com" to 0xFF000000.toInt(),
        "x " to 0xFF000000.toInt(),

        // Cloud / DevOps
        "github" to 0xFF181717.toInt(),
        "gitlab" to 0xFFFC6D26.toInt(),
        "docker" to 0xFF2496ED.toInt(),
        "aws" to 0xFFFF9900.toInt(),
        "cloudflare" to 0xFFF38020.toInt(),
        "digitalocean" to 0xFF0080FF.toInt(),
        "linode" to 0xFF00A95C.toInt(),
        "vultr" to 0xFF007BFC.toInt(),
        "heroku" to 0xFF430098.toInt(),
        "vercel" to 0xFF000000.toInt(),
        "netlify" to 0xFF00C7B7.toInt(),
        "azure" to 0xFF0078D4.toInt(),
        "oracle" to 0xFFC74634.toInt(),
        "ibm" to 0xFF0F62FE.toInt(),

        // Communication
        "discord" to 0xFF5865F2.toInt(),
        "slack" to 0xFF4A154B.toInt(),
        "telegram" to 0xFF0088CC.toInt(),
        "signal" to 0xFF3A76F0.toInt(),
        "skype" to 0xFF00AFF0.toInt(),
        "zoom" to 0xFF2D8CFF.toInt(),
        "teams" to 0xFF6264A7.toInt(),
        "twitch" to 0xFF9146FF.toInt(),

        // Social
        "reddit" to 0xFFFF4500.toInt(),
        "pinterest" to 0xFFE60023.toInt(),
        "linkedin" to 0xFF0E76A8.toInt(),
        "tiktok" to 0xFF000000.toInt(),
        "snapchat" to 0xFFFFFC00.toInt(),
        "tumblr" to 0xFF36465D.toInt(),
        "mastodon" to 0xFF6364FF.toInt(),
        "bluesky" to 0xFF0085FF.toInt(),

        // Streaming
        "netflix" to 0xFFE50914.toInt(),
        "spotify" to 0xFF1DB954.toInt(),
        "youtube" to 0xFFFF0000.toInt(),
        "twitch" to 0xFF9146FF.toInt(),
        "disney" to 0xFF0E1A4B.toInt(),
        "hulu" to 0xFF1CE783.toInt(),
        "prime video" to 0xFF00A8E1.toInt(),
        "soundcloud" to 0xFFFF5500.toInt(),
        "deezer" to 0xFFFEAA2D.toInt(),
        "tidal" to 0xFF000000.toInt(),

        // Gaming
        "steam" to 0xFF1B2838.toInt(),
        "epic games" to 0xFF2A2A2A.toInt(),
        "battle.net" to 0xFF00AEFF.toInt(),
        "playstation" to 0xFF003791.toInt(),
        "xbox" to 0xFF107C10.toInt(),
        "nintendo" to 0xFFE60012.toInt(),
        "riot" to 0xFFD32936.toInt(),
        "origin" to 0xFF000000.toInt(),
        "gog" to 0xFF5C2D91.toInt(),

        // Finance
        "paypal" to 0xFF003087.toInt(),
        "stripe" to 0xFF635BFF.toInt(),
        "coinbase" to 0xFF0052FF.toInt(),
        "binance" to 0xFFF0B90B.toInt(),
        "kraken" to 0xFF5841D8.toInt(),
        "revolut" to 0xFF0075EB.toInt(),
        "wise" to 0xFF163300.toInt(),
        "robinhood" to 0xFF00C805.toInt(),
        "cash app" to 0xFF00D632.toInt(),
        "venmo" to 0xFF3D95CE.toInt(),
        "chase" to 0xFF117ACA.toInt(),
        "amex" to 0xFF006FCF.toInt(),
        "visa" to 0xFF1A1F71.toInt(),
        "mastercard" to 0xFFEB001B.toInt(),

        // Productivity
        "notion" to 0xFF000000.toInt(),
        "airtable" to 0xFFFCB400.toInt(),
        "trello" to 0xFF0079BF.toInt(),
        "asana" to 0xFFF06A6A.toInt(),
        "dropbox" to 0xFF0061FF.toInt(),
        "box" to 0xFF0061D5.toInt(),
        "evernote" to 0xFF00A82D.toInt(),
        "atlassian" to 0xFF0052CC.toInt(),
        "jira" to 0xFF0052CC.toInt(),
        "confluence" to 0xFF172B4D.toInt(),
        "figma" to 0xFFF24E1E.toInt(),
        "miro" to 0xFFFFD02F.toInt(),
        "linear" to 0xFF5E6AD2.toInt(),

        // Security / Privacy
        "proton" to 0xFF6D4AFF.toInt(),
        "protonmail" to 0xFF6D4AFF.toInt(),
        "protonvpn" to 0xFF6D4AFF.toInt(),
        "bitwarden" to 0xFF175DDC.toInt(),
        "1password" to 0xFF0572EC.toInt(),
        "lastpass" to 0xFFD32D27.toInt(),
        "nordvpn" to 0xFF4687FF.toInt(),
        "expressvpn" to 0xFFDA3940.toInt(),
        "surfshark" to 0xFF1EBFBF.toInt(),
        "mozilla" to 0xFF000000.toInt(),
        "firefox" to 0xFFFF7139.toInt(),
        "duckduckgo" to 0xFFDE5833.toInt(),
        "cloudflare" to 0xFFF38020.toInt(),

        // E-commerce
        "ebay" to 0xFFE53238.toInt(),
        "shopify" to 0xFF95BF47.toInt(),
        "etsy" to 0xFFF1641E.toInt(),
        "alibaba" to 0xFFFF6A00.toInt(),
        "mercadolibre" to 0xFFFFE600.toInt(),
        "uber" to 0xFF000000.toInt(),
        "lyft" to 0xFFFF00BF.toInt(),
        "airbnb" to 0xFFFF5A5F.toInt(),
        "booking" to 0xFF003580.toInt(),

        // Email
        "gmail" to 0xFFEA4335.toInt(),
        "outlook" to 0xFF0078D4.toInt(),
        "yahoo" to 0xFF6001D2.toInt(),
        "zoho" to 0xFFC8202F.toInt(),
        "fastmail" to 0xFF2962FF.toInt(),

        // Dev tools
        "npm" to 0xFFCB3837.toInt(),
        "jetbrains" to 0xFF000000.toInt(),
        "docker" to 0xFF2496ED.toInt(),
        "postman" to 0xFFFF6C37.toInt(),
        "twilio" to 0xFFF22F46.toInt(),
        "sendgrid" to 0xFF1A82E2.toInt(),
        "mailgun" to 0xFFC02126.toInt(),
        "vault" to 0xFF000000.toInt(),
        "okta" to 0xFF007DC1.toInt(),
        "auth0" to 0xFFEB5424.toInt(),
        "duo" to 0xFF6BBE45.toInt(),

        // Other popular
        "openai" to 0xFF10A37F.toInt(),
        "anthropic" to 0xFFD97757.toInt(),
        "perplexity" to 0xFF20808D.toInt(),
        "patreon" to 0xFFFF424D.toInt(),
        "kickstarter" to 0xFF05CE78.toInt(),
        "medium" to 0xFF000000.toInt(),
        "substack" to 0xFFFF6719.toInt(),
        "onlyfans" to 0xFF00AEEF.toInt(),
        "tinder" to 0xFFFF6B6B.toInt(),
        "doordash" to 0xFFFF3008.toInt(),
        "instacart" to 0xFF43C9B0.toInt(),
    )

    /**
     * Get the brand color for a given issuer name.
     * Returns null if the brand is not in the database.
     */
    fun getColor(issuer: String): Int? {
        val normalized = issuer.lowercase().trim()
        // Try exact match first
        brands[normalized]?.let { return it }
        // Try "contains" match (e.g. "Google LLC" contains "google")
        for ((key, color) in brands) {
            if (normalized.contains(key)) return color
        }
        return null
    }
}
