package com.cernunnos.authenticator.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.cernunnos.authenticator.R
import com.cernunnos.authenticator.data.storage.AppPreferences
import com.cernunnos.authenticator.totp.TotpGenerator

/**
 * Service that provides live TOTP codes to the CodesWidget.
 * Reads entries from the repository (only if vault is unlocked).
 */
class CodesWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return CodesWidgetFactory(applicationContext)
    }

    private class CodesWidgetFactory(private val context: Context) : RemoteViewsFactory {

        private val packageName = context.packageName

        private data class WidgetEntry(
            val id: String,
            val issuer: String,
            val label: String,
            val code: String,
            val remaining: Int,
        )

        private var entries: List<WidgetEntry> = emptyList()

        override fun onCreate() {}

        override fun onDataSetChanged() {
            try {
                val prefs = AppPreferences(context)

                // Security: do NOT decrypt secrets when the vault is locked.
                // The widget service runs in the same process as the app, so
                // decrypted secrets would be accessible in memory even when
                // the vault is locked. Instead, show a "vault locked" placeholder.
                val vaultLocked = prefs.vaultLockedTs > prefs.lastAppOpenTs
                if (vaultLocked) {
                    entries = emptyList()
                    return
                }

                // The widget cannot unlock the main vault (it has no passphrase).
                // Instead, it reads the latest local backup file and decrypts it
                // with the auto-backup passphrase (stored in EncryptedSharedPreferences).
                // If no backup or passphrase is available, the widget shows nothing.
                val backupPass = prefs.autoBackupPassphrase
                if (backupPass == null || backupPass.length < 8) {
                    entries = emptyList()
                    return
                }

                val backupDir = java.io.File(context.filesDir, "backups")
                if (!backupDir.exists()) {
                    entries = emptyList()
                    return
                }
                val latestBackup = backupDir.listFiles { f ->
                    f.name.startsWith("cernunnos_backup_") && f.name.endsWith(".txt")
                }?.maxByOrNull { it.lastModified() }

                if (latestBackup == null || latestBackup.length() < 16) {
                    entries = emptyList()
                    return
                }

                val allEntries = try {
                    com.cernunnos.authenticator.util.ExportImport.import(
                        latestBackup.readText(),
                        backupPass.toCharArray(),
                    )
                } catch (e: Exception) {
                    android.util.Log.w("CodesWidget", "Failed to decrypt backup for widget: ${e.message}")
                    entries = emptyList()
                    return
                }

                // Validate widget category still exists, fallback to uncategorized
                val filtered = when (prefs.widgetMode) {
                    "favorites" -> allEntries.filter { it.favorite }
                    "category" -> {
                        val catId = prefs.widgetCategory
                        val validCategories = prefs.categories.map { it.id }.toSet()
                        if (catId == null || catId !in validCategories) {
                            allEntries.filter { it.categoryId == null }
                        } else {
                            allEntries.filter { it.categoryId == catId }
                        }
                    }
                    else -> allEntries
                }

                val now = System.currentTimeMillis() / 1000
                entries = filtered.take(prefs.widgetMaxEntries).mapNotNull { entry ->
                    try {
                        val code = if (entry.type == "hotp") {
                            TotpGenerator.generateHotp(entry.secret, entry.counter, entry.digits, entry.algorithm)
                        } else {
                            TotpGenerator.generate(
                                entry.secret, now, entry.period, entry.digits, entry.algorithm
                            )
                        }
                        WidgetEntry(
                            id = entry.id,
                            issuer = entry.issuer.ifEmpty { entry.label },
                            label = entry.label,
                            code = if (entry.digits == 8) {
                                "${code.substring(0, 4)} ${code.substring(4)}"
                            } else {
                                "${code.substring(0, 3)} ${code.substring(3)}"
                            },
                            remaining = TotpGenerator.remainingSeconds(entry.period, now),
                        )
                    } catch (e: Exception) {
                        android.util.Log.w("CodesWidget", "Failed to generate code for ${entry.issuer}: ${e.message}")
                        null
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("CodesWidget", "onDataSetChanged failed", e)
                entries = emptyList()
            }
        }

        override fun onDestroy() {
            entries = emptyList()
        }

        override fun getCount(): Int = entries.size

        override fun getViewAt(position: Int): RemoteViews {
            val entry = entries[position]
            val views = RemoteViews(context.packageName, R.layout.widget_codes_item)

            // Mask codes when the device is locked (keyguard active) so secrets
            // are not exposed on the lockscreen.
            // Also mask if the user has enabled "require unlock" for the widget
            // and the app hasn't been opened in the last 60 seconds.
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
            val prefs = com.cernunnos.authenticator.data.storage.AppPreferences(context)
            val requireUnlock = prefs.widgetRequireUnlock
            val recentlyActive = System.currentTimeMillis() - prefs.lastAppOpenTs < 60_000L
            // Mask if vault was locked after the last app open (vault is not unlocked)
            val vaultLocked = prefs.vaultLockedTs > prefs.lastAppOpenTs
            val masked = keyguardManager?.isKeyguardLocked == true || vaultLocked || (requireUnlock && !recentlyActive)

            views.setTextViewText(R.id.widget_item_issuer, entry.issuer)
            views.setTextViewText(R.id.widget_item_code, if (masked) "••••••" else entry.code)
            views.setTextViewText(R.id.widget_item_timer, if (masked) "" else "${entry.remaining}s")

            // Color timer red in final seconds
            val timerColor = if (entry.remaining <= 5) {
                0xFFFF4757.toInt()
            } else {
                0xFF8888AA.toInt()
            }
            views.setTextColor(R.id.widget_item_timer, timerColor)

            // Fill-in intent for item click
            val fillInIntent = Intent().apply {
                putExtra("entry_id", entry.id)
            }
            views.setOnClickFillInIntent(R.id.widget_item_root, fillInIntent)

            return views
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(position: Int): Long = position.toLong()

        override fun hasStableIds(): Boolean = false
    }
}
