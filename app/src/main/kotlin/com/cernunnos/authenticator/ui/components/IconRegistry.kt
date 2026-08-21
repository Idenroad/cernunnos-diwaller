package com.cernunnos.authenticator.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Registry of Material icons available for custom entry icons.
 * Maps a stable string key to an [ImageVector] so that icon choices
 * survive serialization/deserialization.
 */
object IconRegistry {

    /** All available icons keyed by a stable string identifier. */
    val icons: List<Pair<String, ImageVector>> = listOf(
        "Security" to Icons.Default.Security,
        "Shield" to Icons.Default.Shield,
        "Lock" to Icons.Default.Lock,
        "VpnKey" to Icons.Default.VpnKey,
        "Email" to Icons.Default.Email,
        "Phone" to Icons.Default.Phone,
        "Person" to Icons.Default.Person,
        "People" to Icons.Default.People,
        "AccountCircle" to Icons.Default.AccountCircle,
        "ShoppingBag" to Icons.Default.ShoppingBag,
        "ShoppingCart" to Icons.Default.ShoppingCart,
        "Store" to Icons.Default.Store,
        "CreditCard" to Icons.Default.CreditCard,
        "Payments" to Icons.Default.Payments,
        "Business" to Icons.Default.Business,
        "Work" to Icons.Default.Work,
        "Domain" to Icons.Default.Domain,
        "Cloud" to Icons.Default.Cloud,
        "Language" to Icons.Default.Language,
        "Public" to Icons.Default.Public,
        "Home" to Icons.Default.Home,
        "School" to Icons.Default.School,
        "HealthAndSafety" to Icons.Default.HealthAndSafety,
        "MusicNote" to Icons.Default.MusicNote,
        "PhotoCamera" to Icons.Default.PhotoCamera,
        "SportsEsports" to Icons.Default.SportsEsports,
        "Gamepad" to Icons.Default.Gamepad,
        "Flight" to Icons.Default.Flight,
        "Train" to Icons.Default.Train,
        "LocalShipping" to Icons.Default.LocalShipping,
        "SupportAgent" to Icons.Default.SupportAgent,
        "Build" to Icons.Default.Build,
        "Api" to Icons.Default.Api,
        "Send" to Icons.Default.Send,
        "VideoCall" to Icons.Default.VideoCall,
    )

    private val iconMap: Map<String, ImageVector> = icons.associate { it.first to it.second }

    /** Returns the [ImageVector] for the given icon name, or null if unknown. */
    fun getIcon(name: String): ImageVector? = iconMap[name]
}
