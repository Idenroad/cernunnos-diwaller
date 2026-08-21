package com.cernunnos.authenticator.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cernunnos.authenticator.ui.components.ServiceIcon
import androidx.fragment.app.FragmentActivity
import com.cernunnos.authenticator.R
import com.cernunnos.authenticator.data.crypto.BiometricVault
import com.cernunnos.authenticator.data.model.TotpEntry
import com.cernunnos.authenticator.totp.TotpGenerator
import com.cernunnos.authenticator.ui.components.TotpCodeCompact
import com.cernunnos.authenticator.ui.theme.cernunnosChipColors
import com.cernunnos.authenticator.ui.viewmodel.AppViewModel
import com.cernunnos.authenticator.ui.viewmodel.VaultState
import com.cernunnos.authenticator.util.BiometricAuthHelper
import kotlinx.coroutines.launch

/**
 * Generate a TOTP/HOTP/Steam/Yandex/mOTP code for an entry.
 * Centralized so all copy/display callbacks use the same logic.
 */
fun generateCodeForEntry(entry: TotpEntry, tick: Long): String {
    return try {
        when (entry.type) {
            "hotp" -> TotpGenerator.generateHotp(entry.secret, entry.counter, entry.digits, entry.algorithm)
            "steam" -> TotpGenerator.generateSteam(entry.secret, tick)
            "yandex" -> TotpGenerator.generateYandex(entry.secret, tick, entry.period)
            "motp" -> TotpGenerator.generateMotp(
                secretHex = entry.secret.joinToString("") { "%02x".format(it) },
                pin = entry.pin ?: "",
                time = tick,
            )
            else -> TotpGenerator.generate(entry.secret, tick, entry.period, entry.digits, entry.algorithm)
        }
    } catch (e: Exception) {
        android.util.Log.w("Cernunnos", "Code generation failed for ${entry.issuer}: ${e.message}")
        "------"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    vm: AppViewModel,
    onAdd: () -> Unit,
    onEntryClick: (String) -> Unit,
    onSettings: () -> Unit,
    onLock: () -> Unit,
    onDocuments: () -> Unit = {},
    onSendDocument: () -> Unit = {},
) {
    val state by vm.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardScope = androidx.compose.runtime.rememberCoroutineScope()
    val prefs = remember { com.cernunnos.authenticator.data.storage.AppPreferences(context) }
    val tapToReveal = remember { prefs.tapToReveal }
    var favoriteDialogEntryId by remember { mutableStateOf<String?>(null) }
    val favoriteDialogEntry = favoriteDialogEntryId?.let { id -> state.entries.find { it.id == id } }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // ── Multi-select state ──
    var multiSelectMode by remember { mutableStateOf(false) }
    val selectedIds = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    // Pending encrypted export awaiting the clipboard-security confirmation.
    var pendingExportData by remember { mutableStateOf<String?>(null) }
    var showClipboardWarning by remember { mutableStateOf(false) }

    // ── Advanced filter state ──
    var showFilterSheet by remember { mutableStateOf(false) }
    var filterAlgorithm by remember { mutableStateOf<String?>(null) } // null = All
    var filterDigits by remember { mutableStateOf<Int?>(null) }
    var filterPeriod by remember { mutableStateOf<Int?>(null) }
    var filterType by remember { mutableStateOf<String?>(null) }
    val hasActiveFilters = filterAlgorithm != null || filterDigits != null || filterPeriod != null || filterType != null

    if (state.vaultState == VaultState.LOCKED) {
        UnlockScreen(vm = vm)
        return
    }

    val sortMode = remember { prefs.sortMode }
    val viewMode = remember { prefs.viewMode }

    // Sort entries (manual mode uses manualOrder)
    val manualOrder = remember { prefs.manualOrder }
    val sortedEntries = remember(state.entries, sortMode, manualOrder) {
        when (sortMode) {
            "issuer" -> state.entries.sortedBy { it.issuer.lowercase() }
            "date" -> state.entries.sortedByDescending { it.id }
            "favorites" -> state.entries.sortedByDescending { it.favorite }
            "manual" -> {
                val orderIndex = manualOrder.withIndex().associate { it.value to it.index }
                val ordered = state.entries.sortedBy { orderIndex[it.id] ?: Int.MAX_VALUE }
                // Preserve manualOrder relative ordering, then unlisted entries at end (by issuer)
                val inOrder = ordered.filter { it.id in orderIndex }
                val notInOrder = ordered.filter { it.id !in orderIndex }.sortedBy { it.issuer.lowercase().ifEmpty { it.label.lowercase() } }
                inOrder + notInOrder
            }
            else -> state.entries.sortedBy { it.issuer.lowercase().ifEmpty { it.label.lowercase() } }
        }
    }

    Scaffold(
        topBar = {
            if (multiSelectMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.multiselect_selected, selectedIds.size)) },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    navigationIcon = {
                        IconButton(onClick = {
                            multiSelectMode = false
                            selectedIds.clear()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.multiselect_done))
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            // Select all currently visible entries
                            sortedEntries.forEach { selectedIds[it.id] = true }
                        }) {
                            Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.multiselect_select_all))
                        }
                        IconButton(
                            onClick = { showExportDialog = true },
                            enabled = selectedIds.isNotEmpty(),
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = stringResource(R.string.multiselect_export))
                        }
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            enabled = selectedIds.isNotEmpty(),
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.multiselect_delete))
                        }
                        IconButton(
                            onClick = { showCategoryPicker = true },
                            enabled = selectedIds.isNotEmpty(),
                        ) {
                            Icon(Icons.Default.Category, contentDescription = stringResource(R.string.multiselect_category))
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Image(
                            painter = painterResource(R.drawable.cernunnos_logo),
                            contentDescription = stringResource(R.string.cd_cernunnos),
                            modifier = Modifier.width(140.dp),
                        )
                    },
                    actions = {
                        IconButton(onClick = onDocuments) {
                            Icon(Icons.Default.Description, contentDescription = stringResource(R.string.cd_documents))
                        }
                        IconButton(onClick = onSendDocument) {
                            Icon(Icons.Default.Email, contentDescription = stringResource(R.string.cd_send_secure))
                        }
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.list_settings))
                        }
                        IconButton(onClick = { multiSelectMode = true }) {
                            Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.multiselect_title))
                        }
                        IconButton(onClick = onLock) {
                            Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.list_lock))
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!multiSelectMode) {
                FloatingActionButton(onClick = onAdd, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add))
                }
            }
        },
    ) { padding ->
        // Filter state: null = all, "cat_xxx" = specific category, "none" = uncategorized
        var selectedFilter by remember { mutableStateOf<String?>(null) }
        var favoritesOnly by remember { mutableStateOf(false) }
        var searchQuery by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Backup error warning banner
            state.backupError?.let { backupErr ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            backupErr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { vm.clearBackupError() },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_dismiss),
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
            // Search bar + filter button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.list_search), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { showFilterSheet = true }) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = stringResource(R.string.list_filter_advanced),
                        tint = if (hasActiveFilters) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Top toggle: All / Favorites / Categories
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.listMode == "all" && !favoritesOnly,
                    onClick = { vm.setListMode("all"); favoritesOnly = false },
                    label = { Text(stringResource(R.string.list_filter_all)) },
                    colors = cernunnosChipColors(),
                )
                FilterChip(
                    selected = favoritesOnly,
                    onClick = { favoritesOnly = !favoritesOnly },
                    label = {
                        Text(stringResource(R.string.list_filter_favorites))
                    },
                    leadingIcon = {
                        Icon(
                            if (favoritesOnly) Icons.Filled.Star else Icons.Outlined.Star,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    colors = cernunnosChipColors(),
                )
                FilterChip(
                    selected = state.listMode == "categories",
                    onClick = { vm.setListMode("categories"); favoritesOnly = false },
                    label = { Text(stringResource(R.string.list_filter_categories)) },
                    colors = cernunnosChipColors(),
                )
            }

            // Category filter (only in categories mode)
            if (state.listMode == "categories" && state.entries.isNotEmpty()) {
                // Threshold: if total chips (All + Uncategorized + categories with entries) > 5,
                // use a dropdown to avoid horizontal scrolling and visual clutter.
                val categoriesWithEntries = state.categories.filter { cat -> state.entries.any { it.categoryId == cat.id } }
                val totalChips = 2 + categoriesWithEntries.size // All + Uncategorized + categories
                if (totalChips <= 5) {
                    // Chips row — compact, 1-tap
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = selectedFilter == null,
                                onClick = { selectedFilter = null },
                                label = { Text(stringResource(R.string.list_filter_all)) },
                                colors = cernunnosChipColors(),
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedFilter == "none",
                                onClick = { selectedFilter = "none" },
                                label = { Text(stringResource(R.string.list_filter_uncategorized)) },
                                colors = cernunnosChipColors(),
                            )
                        }
                        items(categoriesWithEntries, key = { it.id }) { cat ->
                            FilterChip(
                                selected = selectedFilter == cat.id,
                                onClick = { selectedFilter = cat.id },
                                label = { Text(cat.name) },
                                colors = cernunnosChipColors(),
                            )
                        }
                    }
                } else {
                    // Dropdown — saves vertical space when many categories
                    var dropdownExpanded by remember { mutableStateOf(false) }
                    val selectedLabel = when (selectedFilter) {
                        null -> stringResource(R.string.list_filter_all)
                        "none" -> stringResource(R.string.list_filter_uncategorized)
                        else -> categoriesWithEntries.find { it.id == selectedFilter }?.name
                            ?: stringResource(R.string.list_filter_all)
                    }
                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        OutlinedTextField(
                            value = selectedLabel,
                            onValueChange = { },
                            readOnly = true,
                            label = { Text(stringResource(R.string.list_filter_categories)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                        )
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.list_filter_all)) },
                                onClick = { selectedFilter = null; dropdownExpanded = false },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.list_filter_uncategorized)) },
                                onClick = { selectedFilter = "none"; dropdownExpanded = false },
                            )
                            categoriesWithEntries.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = { selectedFilter = cat.id; dropdownExpanded = false },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (state.entries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp),
                    ) {
                        Icon(
                            Icons.Outlined.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(96.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(
                            stringResource(R.string.list_empty),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.list_empty_desc),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = onAdd,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.add_account))
                        }
                    }
                }
            } else {
                // Apply favorites filter on top of mode filter
                // Memoized: only re-filter when inputs change, not on every tick
                val modeFiltered = remember(sortedEntries, state.listMode, selectedFilter) {
                    when (state.listMode) {
                        "categories" -> when {
                            selectedFilter == null -> sortedEntries
                            selectedFilter == "none" -> sortedEntries.filter { it.categoryId == null }
                            else -> sortedEntries.filter { it.categoryId == selectedFilter }
                        }
                        else -> sortedEntries
                    }
                }
                val favFiltered = remember(modeFiltered, favoritesOnly) {
                    if (favoritesOnly) modeFiltered.filter { it.favorite } else modeFiltered
                }
                val searchFiltered = remember(favFiltered, searchQuery) {
                    if (searchQuery.isNotBlank()) {
                        favFiltered.filter {
                            it.issuer.contains(searchQuery, ignoreCase = true) ||
                            it.label.contains(searchQuery, ignoreCase = true)
                        }
                    } else favFiltered
                }
                // Apply advanced filters
                val filtered = remember(searchFiltered, filterAlgorithm, filterDigits, filterPeriod, filterType) {
                    searchFiltered.filter { entry ->
                        (filterAlgorithm == null || entry.algorithm.equals(filterAlgorithm, ignoreCase = true)) &&
                        (filterDigits == null || entry.digits == filterDigits) &&
                        (filterPeriod == null || entry.period == filterPeriod) &&
                        (filterType == null || entry.type.equals(filterType, ignoreCase = true))
                    }
                }

                val pullToRefreshState = rememberPullToRefreshState()
                val cloudEnabled = prefs.cloudBackupEnabled
                PullToRefreshBox(
                    isRefreshing = false,
                    onRefresh = {
                        if (cloudEnabled) {
                            vm.syncFromCloud()
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "Cloud sync is not enabled",
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        }
                    },
                    state = pullToRefreshState,
                ) {
                    if (state.listMode == "categories" && selectedFilter == null && !multiSelectMode && viewMode != "tiles") {
                        // Grouped by category (preserved, no multi-select/tiles to keep it simple)
                        CategoryGroupedList(
                            entries = filtered,
                            categories = state.categories,
                            tick = state.tick,
                            onEntryClick = onEntryClick,
                            onLongPress = { id -> favoriteDialogEntryId = id },
                            onCopy = { entry ->
                                val code = generateCodeForEntry(entry, state.tick)
                                copyToClipboardWithClear(context, code, scope = clipboardScope)
                            },
                            onSwipeDelete = { entry ->
                                vm.removeEntry(entry.id)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = context.getString(R.string.entry_deleted),
                                        actionLabel = context.getString(R.string.undo_delete),
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        vm.restoreEntry(entry)
                                    }
                                }
                            },
                            onIncrementHotp = { entry -> vm.incrementHotp(entry.id) },
                            tapToReveal = tapToReveal,
                        )
                    } else if (viewMode == "tiles" && !multiSelectMode) {
                        // Tiles view: 2-column grid
                        TilesGrid(
                            entries = filtered,
                            tick = state.tick,
                            onEntryClick = onEntryClick,
                            onLongPress = { id -> favoriteDialogEntryId = id },
                            onCopy = { entry ->
                                val code = generateCodeForEntry(entry, state.tick)
                                copyToClipboardWithClear(context, code, scope = clipboardScope)
                            },
                            onIncrementHotp = { entry -> vm.incrementHotp(entry.id) },
                            tapToReveal = tapToReveal,
                        )
                    } else {
                        // Flat list (list / compact modes, or multi-select mode)
                        FlatEntryList(
                            entries = filtered,
                            tick = state.tick,
                            viewMode = if (multiSelectMode) "list" else viewMode,
                            sortMode = sortMode,
                            multiSelectMode = multiSelectMode,
                            selectedIds = selectedIds,
                            onEntryClick = onEntryClick,
                            onLongPress = { id -> favoriteDialogEntryId = id },
                            onCopy = { entry ->
                                val code = generateCodeForEntry(entry, state.tick)
                                copyToClipboardWithClear(context, code, scope = clipboardScope)
                            },
                            onSwipeDelete = { entry ->
                                vm.removeEntry(entry.id)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = context.getString(R.string.entry_deleted),
                                        actionLabel = context.getString(R.string.undo_delete),
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        vm.restoreEntry(entry)
                                    }
                                }
                            },
                            onReorder = { newOrder -> vm.reorderEntries(newOrder) },
                            onIncrementHotp = { entry -> vm.incrementHotp(entry.id) },
                            tapToReveal = tapToReveal,
                        )
                    }
                }
            }
        }

        state.message?.let { msg ->
            LaunchedEffect(msg) { vm.clearMessage() }
        }
    }

    // Favorite confirmation dialog
    favoriteDialogEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { favoriteDialogEntryId = null },
            title = { Text(stringResource(R.string.favorite_title)) },
            text = {
                Text(
                    if (entry.favorite)
                        stringResource(R.string.favorite_remove_msg, entry.issuer.ifEmpty { entry.label })
                    else
                        stringResource(R.string.favorite_add_msg, entry.issuer.ifEmpty { entry.label })
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.toggleFavorite(entry.id)
                    favoriteDialogEntryId = null
                }) {
                    Text(
                        if (entry.favorite) stringResource(R.string.favorite_remove)
                        else stringResource(R.string.favorite_add),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { favoriteDialogEntryId = null }) {
                    Text(stringResource(R.string.detail_cancel))
                }
            },
        )
    }

    // Advanced filter bottom sheet
    if (showFilterSheet) {
        AdvancedFilterSheet(
            filterAlgorithm = filterAlgorithm,
            filterDigits = filterDigits,
            filterPeriod = filterPeriod,
            filterType = filterType,
            onAlgorithmChange = { filterAlgorithm = it },
            onDigitsChange = { filterDigits = it },
            onPeriodChange = { filterPeriod = it },
            onTypeChange = { filterType = it },
            onReset = {
                filterAlgorithm = null
                filterDigits = null
                filterPeriod = null
                filterType = null
            },
            onDismiss = { showFilterSheet = false },
        )
    }

    // Multi-select delete confirmation
    if (showDeleteConfirm) {
        val count = selectedIds.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.multiselect_delete_title)) },
            text = { Text(stringResource(R.string.multiselect_delete_msg, count)) },
            confirmButton = {
                TextButton(onClick = {
                    // Save deleted entries for undo before removal
                    val deletedEntries = selectedIds.keys.mapNotNull { id ->
                        state.entries.find { it.id == id }
                    }
                    vm.removeEntries(selectedIds.keys.toList())
                    selectedIds.clear()
                    multiSelectMode = false
                    showDeleteConfirm = false
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "$count ${context.getString(R.string.entries_deleted)}",
                            actionLabel = context.getString(R.string.undo_delete),
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            deletedEntries.forEach { vm.restoreEntry(it) }
                        }
                    }
                }) { Text(stringResource(R.string.multiselect_delete), color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.detail_cancel))
                }
            },
        )
    }

    // Multi-select export dialog
    if (showExportDialog) {
        MultiSelectExportDialog(
            selectedCount = selectedIds.size,
            onExport = { passphrase ->
                val data = vm.exportSelectedEntries(selectedIds.keys.toSet(), passphrase)
                if (data != null) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Cernunnos Export", data))
                    Toast.makeText(context, context.getString(R.string.export_copied_clipboard), Toast.LENGTH_LONG).show()
                    selectedIds.clear()
                    multiSelectMode = false
                }
                showExportDialog = false
            },
            onDismiss = { showExportDialog = false },
        )
    }

    // Multi-select category picker
    if (showCategoryPicker) {
        MultiSelectCategoryDialog(
            categories = state.categories,
            onCategorySelected = { categoryId ->
                selectedIds.keys.forEach { id -> vm.assignCategory(id, categoryId) }
                selectedIds.clear()
                multiSelectMode = false
                showCategoryPicker = false
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.category_updated),
                        duration = SnackbarDuration.Short,
                    )
                }
            },
            onDismiss = { showCategoryPicker = false },
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TotpEntryRow(entry: TotpEntry, tick: Long, onClick: () -> Unit, onLongPress: () -> Unit = {}, onIncrementHotp: () -> Unit = {}, onCopy: () -> Unit = {}, tapToReveal: Boolean = false) {
    // Use tick as time source so Compose recomposes every second (for TOTP)
    val code = generateCodeForEntry(entry, tick)
    val remaining = if (entry.type == "hotp") 0 else TotpGenerator.remainingSeconds(entry.period, tick)

    // Tap-to-reveal state
    var revealed by remember(entry.id) { mutableStateOf(false) }
    // Track the code value to auto-hide when TOTP code changes
    var lastCode by remember(entry.id) { mutableStateOf(code) }
    if (tapToReveal && code != lastCode) {
        lastCode = code
        revealed = false
    }
    // Auto-hide after 10 seconds
    if (tapToReveal && revealed) {
        LaunchedEffect(entry.id, revealed) {
            kotlinx.coroutines.delay(10_000L)
            revealed = false
        }
    }
    val isMasked = tapToReveal && !revealed

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onLongPress() },
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Service icon
            ServiceIcon(
                name = entry.issuer.ifEmpty { entry.label },
                size = 36.dp,
                textSize = 14.sp,
                iconName = entry.iconName,
                customIconUri = entry.customIconUri,
            )
            Spacer(Modifier.width(10.dp))
            // Left: issuer + label + favorite star
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.issuer.ifEmpty { entry.label }.ifEmpty { "—" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (entry.favorite) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                if (entry.label.isNotEmpty() && entry.issuer.isNotEmpty()) {
                    Text(
                        entry.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            // Quick copy button (between text and code)
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.cd_copy_code),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(4.dp))
            // Right: compact code + countdown ring (TOTP) or refresh button (HOTP)
            if (entry.type == "hotp") {
                TotpCodeCompact(
                    code = code,
                    remainingSeconds = entry.counter.toInt(),
                    period = entry.period,
                    modifier = Modifier.width(110.dp).then(
                        if (tapToReveal) Modifier.clickable { revealed = !revealed } else Modifier
                    ),
                    isHotp = true,
                    masked = isMasked,
                )
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = onIncrementHotp,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Next HOTP code",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                TotpCodeCompact(
                    code = code,
                    remainingSeconds = remaining,
                    period = entry.period,
                    modifier = Modifier.width(150.dp).then(
                        if (tapToReveal) Modifier.clickable { revealed = !revealed } else Modifier
                    ),
                    isHotp = false,
                    masked = isMasked,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryGroupedList(
    entries: List<TotpEntry>,
    categories: List<com.cernunnos.authenticator.data.model.Category>,
    tick: Long,
    onEntryClick: (String) -> Unit,
    onLongPress: (String) -> Unit = {},
    onCopy: (TotpEntry) -> Unit = {},
    onSwipeDelete: (TotpEntry) -> Unit = {},
    onIncrementHotp: (TotpEntry) -> Unit = {},
    tapToReveal: Boolean = false,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Uncategorized entries first
        val uncategorized = entries.filter { it.categoryId == null }
        if (uncategorized.isNotEmpty()) {
            item(key = "header_none") {
                CategoryHeader(stringResource(R.string.list_filter_uncategorized))
            }
            items(uncategorized, key = { it.id }) { entry ->
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) {
                            onSwipeDelete(entry)
                            false
                        } else {
                            false
                        }
                    },
                )
                SwipeToDismissBox(
                    state = dismissState,
                    backgroundContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    },
                    enableDismissFromStartToEnd = false,
                ) {
                    TotpEntryRow(
                        entry = entry,
                        tick = tick,
                        onClick = { onEntryClick(entry.id) },
                        onLongPress = { onLongPress(entry.id) },
                        onIncrementHotp = { onIncrementHotp(entry) },
                        onCopy = { onCopy(entry) },
                        tapToReveal = tapToReveal,
                    )
                }
            }
        }

        // Then each category
        categories.forEach { cat ->
            val catEntries = entries.filter { it.categoryId == cat.id }
            if (catEntries.isNotEmpty()) {
                item(key = "header_${cat.id}") {
                    CategoryHeader(cat.name)
                }
                items(catEntries, key = { it.id }) { entry ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                onSwipeDelete(entry)
                                false
                            } else {
                                false
                            }
                        },
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        },
                        enableDismissFromStartToEnd = false,
                    ) {
                        TotpEntryRow(
                            entry = entry,
                            tick = tick,
                            onClick = { onEntryClick(entry.id) },
                            onLongPress = { onLongPress(entry.id) },
                            onIncrementHotp = { onIncrementHotp(entry) },
                            onCopy = { onCopy(entry) },
                            tapToReveal = tapToReveal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(name: String) {
    Text(
        text = name,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnlockScreen(vm: AppViewModel) {
    val state by vm.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? androidx.fragment.app.FragmentActivity
    var passphrase by remember { mutableStateOf("") }

    // Auto-trigger biometric prompt if device credential mode
    LaunchedEffect(state.vaultMode) {
        if (state.vaultMode == BiometricVault.VaultMode.DEVICE_CREDENTIAL && activity != null) {
            try {
                val cipher = vm.prepareDeviceCredentialUnlock()
                if (cipher != null) {
                    BiometricAuthHelper.authenticate(
                        activity = activity,
                        title = "Cernunnos Diwaller",
                        subtitle = context.getString(R.string.unlock_desc),
                        cipher = cipher,
                        onSuccess = { authCipher -> vm.completeDeviceCredentialUnlock(authCipher) },
                        onError = { },
                    )
                }
            } catch (e: Exception) {
                // Fall through to passphrase UI
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.unlock_title)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (state.vaultMode == BiometricVault.VaultMode.DEVICE_CREDENTIAL) {
                // Device credential mode: show button to trigger biometric
                Text(
                    stringResource(R.string.unlock_desc),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (activity != null) {
                            val cipher = vm.prepareDeviceCredentialUnlock()
                            if (cipher != null) {
                                BiometricAuthHelper.authenticate(
                                    activity = activity,
                                    title = "Cernunnos Diwaller",
                                    subtitle = context.getString(R.string.unlock_desc),
                                    cipher = cipher,
                                    onSuccess = { authCipher -> vm.completeDeviceCredentialUnlock(authCipher) },
                                    onError = { },
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = stringResource(R.string.unlock_button))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.unlock_button))
                }
                state.error?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                }
            } else {
                // Passphrase mode
                Text(
                    stringResource(R.string.unlock_desc),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.unlock_passphrase)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                }
                TextButton(
                    onClick = { vm.unlock(passphrase) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.unlock_button))
                }
            }
        }
    }
}

// ── Tiles view: 2-column grid ──

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TilesGrid(
    entries: List<TotpEntry>,
    tick: Long,
    onEntryClick: (String) -> Unit,
    onLongPress: (String) -> Unit = {},
    onCopy: (TotpEntry) -> Unit = {},
    onIncrementHotp: (TotpEntry) -> Unit = {},
    tapToReveal: Boolean = false,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        gridItems(entries, key = { it.id }) { entry ->
            val code = generateCodeForEntry(entry, tick)
            // Tap-to-reveal state
            var revealed by remember(entry.id) { mutableStateOf(false) }
            var lastCode by remember(entry.id) { mutableStateOf(code) }
            if (tapToReveal && code != lastCode) {
                lastCode = code
                revealed = false
            }
            if (tapToReveal && revealed) {
                LaunchedEffect(entry.id, revealed) {
                    kotlinx.coroutines.delay(10_000L)
                    revealed = false
                }
            }
            val isMasked = tapToReveal && !revealed
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onEntryClick(entry.id) },
                        onLongClick = { onLongPress(entry.id) },
                    ),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ServiceIcon(
                        name = entry.issuer.ifEmpty { entry.label },
                        size = 48.dp,
                        textSize = 18.sp,
                        iconName = entry.iconName,
                        customIconUri = entry.customIconUri,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        entry.issuer.ifEmpty { entry.label }.ifEmpty { "—" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(4.dp))
                    TotpCodeCompact(
                        code = code,
                        remainingSeconds = if (entry.type == "hotp") entry.counter.toInt() else TotpGenerator.remainingSeconds(entry.period, tick),
                        period = entry.period,
                        modifier = Modifier.fillMaxWidth().then(
                            if (tapToReveal) Modifier.clickable { revealed = !revealed } else Modifier
                        ),
                        isHotp = entry.type == "hotp",
                        masked = isMasked,
                    )
                    Spacer(Modifier.height(4.dp))
                    if (entry.type == "hotp") {
                        IconButton(onClick = { onIncrementHotp(entry) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Next HOTP code", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        IconButton(onClick = { onCopy(entry) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── Flat list (list / compact modes, with multi-select + reorder support) ──

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FlatEntryList(
    entries: List<TotpEntry>,
    tick: Long,
    viewMode: String,
    sortMode: String,
    multiSelectMode: Boolean,
    selectedIds: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Boolean>,
    onEntryClick: (String) -> Unit,
    onLongPress: (String) -> Unit = {},
    onCopy: (TotpEntry) -> Unit = {},
    onSwipeDelete: (TotpEntry) -> Unit = {},
    onReorder: (List<String>) -> Unit = {},
    onIncrementHotp: (TotpEntry) -> Unit = {},
    tapToReveal: Boolean = false,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(if (viewMode == "compact") 2.dp else 4.dp),
    ) {
        items(entries, key = { it.id }) { entry ->
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value == SwipeToDismissBoxValue.EndToStart && !multiSelectMode) {
                        onSwipeDelete(entry)
                        false
                    } else {
                        false
                    }
                },
            )
            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                },
                enableDismissFromStartToEnd = false,
                enableDismissFromEndToStart = !multiSelectMode,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (multiSelectMode) {
                        Checkbox(
                            checked = selectedIds[entry.id] == true,
                            onCheckedChange = { checked ->
                                if (checked) selectedIds[entry.id] = true else selectedIds.remove(entry.id)
                            },
                        )
                    }
                    if (sortMode == "manual" && !multiSelectMode) {
                        Column {
                            IconButton(onClick = {
                                val idx = entries.indexOf(entry)
                                if (idx > 0) {
                                    val newOrder = entries.toMutableList()
                                    val moved = newOrder.removeAt(idx)
                                    newOrder.add(idx - 1, moved)
                                    onReorder(newOrder.map { it.id })
                                }
                            }) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.cd_move_up), modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = {
                                val idx = entries.indexOf(entry)
                                if (idx >= 0 && idx < entries.size - 1) {
                                    val newOrder = entries.toMutableList()
                                    val moved = newOrder.removeAt(idx)
                                    newOrder.add(idx + 1, moved)
                                    onReorder(newOrder.map { it.id })
                                }
                            }) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.cd_move_down), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    TotpEntryRow(
                        entry = entry,
                        tick = tick,
                        onClick = { onEntryClick(entry.id) },
                        onLongPress = { onLongPress(entry.id) },
                        onIncrementHotp = { onIncrementHotp(entry) },
                        onCopy = { onCopy(entry) },
                        tapToReveal = tapToReveal,
                    )
                }
            }
        }
    }
}

// ── Advanced filter bottom sheet ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedFilterSheet(
    filterAlgorithm: String?,
    filterDigits: Int?,
    filterPeriod: Int?,
    filterType: String?,
    onAlgorithmChange: (String?) -> Unit,
    onDigitsChange: (Int?) -> Unit,
    onPeriodChange: (Int?) -> Unit,
    onTypeChange: (String?) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            Text(stringResource(R.string.advanced_filters), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))

            Text(stringResource(R.string.filter_algorithm), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to "All", "SHA1" to "SHA1", "SHA256" to "SHA256", "SHA512" to "SHA512").forEach { (value, label) ->
                    FilterChip(
                        selected = filterAlgorithm == value,
                        onClick = { onAlgorithmChange(if (value == filterAlgorithm) null else value) },
                        label = { Text(if (value == null) stringResource(R.string.list_filter_all) else label) },
                        colors = cernunnosChipColors(),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            Text(stringResource(R.string.filter_digits), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to "All", 6 to "6", 8 to "8").forEach { (value, label) ->
                    FilterChip(
                        selected = filterDigits == value,
                        onClick = { onDigitsChange(if (value == filterDigits) null else value) },
                        label = { Text(if (value == null) stringResource(R.string.list_filter_all) else label) },
                        colors = cernunnosChipColors(),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            Text(stringResource(R.string.filter_period), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to "All", 15 to "15s", 30 to "30s", 60 to "60s").forEach { (value, label) ->
                    FilterChip(
                        selected = filterPeriod == value,
                        onClick = { onPeriodChange(if (value == filterPeriod) null else value) },
                        label = { Text(if (value == null) stringResource(R.string.list_filter_all) else label) },
                        colors = cernunnosChipColors(),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            Text(stringResource(R.string.filter_type), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to "All", "totp" to "TOTP", "hotp" to "HOTP").forEach { (value, label) ->
                    FilterChip(
                        selected = filterType == value,
                        onClick = { onTypeChange(if (value == filterType) null else value) },
                        label = { Text(if (value == null) stringResource(R.string.list_filter_all) else label) },
                        colors = cernunnosChipColors(),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onReset) { Text(stringResource(R.string.reset)) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
            }
        }
    }
}

// ── Multi-select export dialog ──

@Composable
private fun MultiSelectExportDialog(
    selectedCount: Int,
    onExport: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_entries, selectedCount)) },
        text = {
            Column {
                Text(stringResource(R.string.export_passphrase_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.multiselect_export_pass)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onExport(passphrase) },
                enabled = passphrase.length >= 8,
            ) { Text(stringResource(R.string.multiselect_export_button), color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.detail_cancel)) }
        },
    )
}

// ── Multi-select category dialog ──

@Composable
private fun MultiSelectCategoryDialog(
    categories: List<com.cernunnos.authenticator.data.model.Category>,
    onCategorySelected: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_category)) },
        text = {
            Column {
                categories.forEach { cat ->
                    TextButton(
                        onClick = { onCategorySelected(cat.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(cat.name) }
                }
                TextButton(
                    onClick = { onCategorySelected(null) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.remove_from_category)) }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.detail_cancel)) }
        },
    )
}
