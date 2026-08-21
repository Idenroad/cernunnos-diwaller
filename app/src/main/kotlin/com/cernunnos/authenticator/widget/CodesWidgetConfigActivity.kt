package com.cernunnos.authenticator.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.cernunnos.authenticator.R
import com.cernunnos.authenticator.data.storage.AppPreferences
import com.cernunnos.authenticator.ui.theme.CernunnosTheme
import com.cernunnos.authenticator.ui.theme.cernunnosChipColors

/**
 * Configuration activity for the CodesWidget.
 * Lets the user choose which entries to display: favorites, a category, or all.
 */
class CodesWidgetConfigActivity : FragmentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Prevent screenshots and screen recording
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
        )
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val prefs = AppPreferences(this)

        setContent {
            CernunnosTheme(darkTheme = true) {
                ConfigScreen(
                    categories = prefs.categories,
                    currentMode = prefs.widgetMode,
                    currentCategory = prefs.widgetCategory,
                    onSelect = { mode, category ->
                        prefs.widgetMode = mode
                        prefs.widgetCategory = category
                        prefs.widgetCodesEnabled = true

                        // Trigger widget update
                        val mgr = AppWidgetManager.getInstance(this)
                        val updateIntent = Intent().apply {
                            action = CodesWidget.ACTION_REFRESH
                            component = android.content.ComponentName(
                                packageName,
                                CodesWidget::class.java.name,
                            )
                        }
                        sendBroadcast(updateIntent)

                        // Return result
                        val resultValue = Intent().putExtra(
                            AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId
                        )
                        setResult(RESULT_OK, resultValue)
                        finish()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigScreen(
    categories: List<com.cernunnos.authenticator.data.model.Category>,
    currentMode: String,
    currentCategory: String?,
    onSelect: (String, String?) -> Unit,
) {
    var mode by remember { mutableStateOf(currentMode) }
    var selectedCategory by remember { mutableStateOf(currentCategory) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.widget_config_title)) })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Text(
                stringResource(R.string.widget_config_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            // Mode selection
            Text(stringResource(R.string.widget_config_mode), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "favorites" to R.string.widget_mode_favorites,
                    "category" to R.string.widget_mode_category,
                    "all" to R.string.widget_mode_all,
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = mode == key,
                        onClick = {
                            mode = key
                            if (key != "category") selectedCategory = null
                        },
                        label = { Text(stringResource(label)) },
                        colors = cernunnosChipColors(),
                    )
                }
            }

            // Category selection
            if (mode == "category") {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.widget_config_category), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // "Uncategorized" option
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text(stringResource(R.string.uncategorized)) },
                        colors = cernunnosChipColors(),
                    )
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat.id,
                            onClick = { selectedCategory = cat.id },
                            label = { Text(cat.name) },
                            colors = cernunnosChipColors(),
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { onSelect(mode, selectedCategory) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.widget_config_done)) }
        }
    }
}
