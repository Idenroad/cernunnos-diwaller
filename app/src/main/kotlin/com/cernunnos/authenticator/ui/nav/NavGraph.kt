package com.cernunnos.authenticator.ui.nav

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cernunnos.authenticator.R
import com.cernunnos.authenticator.ui.screens.AddScreen
import com.cernunnos.authenticator.ui.screens.AddDocumentScreen
import com.cernunnos.authenticator.ui.screens.DetailScreen
import com.cernunnos.authenticator.ui.screens.DocumentDetailScreen
import com.cernunnos.authenticator.ui.screens.DocumentsScreen
import com.cernunnos.authenticator.ui.screens.ExportScreen
import com.cernunnos.authenticator.ui.screens.ImportScreen
import com.cernunnos.authenticator.ui.screens.ListScreen
import com.cernunnos.authenticator.ui.screens.OnboardingScreen
import com.cernunnos.authenticator.ui.screens.P2PScreen
import com.cernunnos.authenticator.ui.screens.SendDocumentScreen
import com.cernunnos.authenticator.ui.screens.SettingsScreen
import com.cernunnos.authenticator.ui.screens.DecryptCernScreen
import com.cernunnos.authenticator.ui.screens.SplashScreen
import com.cernunnos.authenticator.ui.viewmodel.AppViewModel
import com.cernunnos.authenticator.data.storage.AppPreferences
import kotlinx.coroutines.launch

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val LIST = "list"
    const val ADD = "add"
    const val DETAIL = "detail/{entryId}"
    const val EXPORT = "export"
    const val IMPORT = "import"
    const val SETTINGS = "settings"
    const val P2P = "p2p"
    const val DOCUMENTS = "documents"
    const val ADD_DOCUMENT = "add_document"
    const val DOCUMENT_DETAIL = "document_detail/{documentId}"
    const val SEND_DOCUMENT = "send_document"
    const val DECRYPT_CERN = "decrypt_cern/{fileUri}"

    fun detail(entryId: String) = "detail/$entryId"
    fun documentDetail(documentId: String) = "document_detail/$documentId"
    fun decryptCern(fileUri: String) = "decrypt_cern/$fileUri"
}

@Composable
fun CernunnosNavHost() {
    val navController = rememberNavController()
    val vm: AppViewModel = viewModel()
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val state by vm.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Check for pending .cern file to decrypt (set by MainActivity intent handler)
    // The pending URI is stored in the ViewModel and survives process death.
    val cernTrigger = vm.cernFileTrigger
    LaunchedEffect(cernTrigger) {
        vm.consumePendingCernUri()?.let { uri ->
            // Navigate to decrypt screen — URL-encode the URI so it survives nav args
            val encoded = android.net.Uri.encode(uri)
            navController.navigate(Routes.decryptCern(encoded))
        }
    }
    val isDark = when (state.themeMode) {
        "light" -> false
        "dark" -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    com.cernunnos.authenticator.ui.theme.CernunnosTheme(
        darkTheme = isDark,
        dynamicColor = state.dynamicColorEnabled,
    ) {

    // Determine start route: splash if animation enabled, otherwise skip to onboarding/list
    // Mark first launch done + disable splash BEFORE building NavHost so that if
    // the OS kills the activity in the background, the splash doesn't replay on restore.
    val startRoute = if (prefs.splashAnimationEnabled) {
        if (!prefs.firstLaunchDone) {
            prefs.firstLaunchDone = true
        }
        prefs.splashAnimationEnabled = false
        Routes.SPLASH
    } else {
        when (vm.uiState.value.vaultState) {
            null, com.cernunnos.authenticator.ui.viewmodel.VaultState.UNINITIALIZED -> Routes.ONBOARDING
            else -> Routes.LIST
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    NavHost(
        navController = navController,
        startDestination = startRoute,
        // Default transitions: slide in from right + fade for forward navigation,
        // slide out to right + fade for back navigation.
        enterTransition = {
            slideInHorizontally(initialOffsetX = { it }) + fadeIn()
        },
        exitTransition = {
            slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
        },
        popEnterTransition = {
            slideInHorizontally(initialOffsetX = { -it }) + fadeIn()
        },
        popExitTransition = {
            slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        },
    ) {
        // SPLASH → LIST/ONBOARDING: fade only (no slide)
        composable(
            Routes.SPLASH,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() },
        ) {
            SplashScreen(
                onFinish = {
                    val nextRoute = when (vm.uiState.value.vaultState) {
                        null, com.cernunnos.authenticator.ui.viewmodel.VaultState.UNINITIALIZED -> Routes.ONBOARDING
                        else -> Routes.LIST
                    }
                    navController.navigate(nextRoute) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                vm = vm,
                onDone = {
                    navController.navigate(Routes.LIST) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.LIST) {
            // Safety check: if the vault is not initialized, redirect to onboarding.
            // This should never happen via normal navigation, but guards against
            // race conditions or state restoration bugs.
            val currentVaultState = vm.uiState.value.vaultState
            if (currentVaultState == null || currentVaultState == com.cernunnos.authenticator.ui.viewmodel.VaultState.UNINITIALIZED) {
                LaunchedEffect(Unit) {
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(Routes.LIST) { inclusive = true }
                    }
                }
            } else {
                ListScreen(
                    vm = vm,
                    onAdd = { navController.navigate(Routes.ADD) },
                    onEntryClick = { id -> navController.navigate(Routes.detail(id)) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                    onLock = {
                        vm.lock()
                        navController.navigate(Routes.LIST) {
                            popUpTo(Routes.LIST) { inclusive = true }
                        }
                    },
                    onDocuments = { navController.navigate(Routes.DOCUMENTS) },
                    onSendDocument = { navController.navigate(Routes.SEND_DOCUMENT) },
                )
            }
        }

        composable(Routes.ADD) {
            AddScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
            )
        }

        composable(
            Routes.DETAIL,
            arguments = listOf(navArgument("entryId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getString("entryId") ?: return@composable
            DetailScreen(
                vm = vm,
                entryId = entryId,
                onBack = { navController.popBackStack() },
                onDeleteWithUndo = { entry ->
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
            )
        }

        composable(Routes.EXPORT) {
            ExportScreen(vm = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.IMPORT) {
            ImportScreen(vm = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
                onExport = { navController.navigate(Routes.EXPORT) },
                onImport = { navController.navigate(Routes.IMPORT) },
                onP2P = { navController.navigate(Routes.P2P) },
                onDecryptCern = { uri ->
                    val encoded = android.net.Uri.encode(uri)
                    navController.navigate(Routes.decryptCern(encoded))
                },
            )
        }

        composable(Routes.P2P) {
            P2PScreen(vm = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.SEND_DOCUMENT) {
            SendDocumentScreen(onBack = { navController.popBackStack() })
        }

        composable(
            Routes.DECRYPT_CERN,
            arguments = listOf(navArgument("fileUri") { type = NavType.StringType }),
        ) { backStackEntry ->
            val fileUri = backStackEntry.arguments?.getString("fileUri") ?: ""
            DecryptCernScreen(
                fileUri = fileUri,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.DOCUMENTS) {
            // Share the same DocumentViewModel across all document routes
            // so the vault stays unlocked when navigating Documents → Add → Detail.
            // We scope the VM to the Activity (context) rather than the navController
            // so it persists across all document screens.
            val docVm: com.cernunnos.authenticator.ui.viewmodel.DocumentViewModel = viewModel(
                viewModelStoreOwner = context as androidx.lifecycle.ViewModelStoreOwner,
            )
            DocumentsScreen(
                vm = docVm,
                onAdd = { navController.navigate(Routes.ADD_DOCUMENT) },
                onDocumentClick = { id -> navController.navigate(Routes.documentDetail(id)) },
                onBack = { navController.popBackStack() },
                onDecryptCern = { uri ->
                    val encoded = android.net.Uri.encode(uri)
                    navController.navigate(Routes.decryptCern(encoded))
                },
            )
        }

        composable(Routes.ADD_DOCUMENT) {
            val docVm: com.cernunnos.authenticator.ui.viewmodel.DocumentViewModel = viewModel(
                viewModelStoreOwner = context as androidx.lifecycle.ViewModelStoreOwner,
            )
            AddDocumentScreen(
                vm = docVm,
                onDone = { navController.popBackStack() },
            )
        }

        composable(
            Routes.DOCUMENT_DETAIL,
            arguments = listOf(navArgument("documentId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getString("documentId") ?: return@composable
            val docVm: com.cernunnos.authenticator.ui.viewmodel.DocumentViewModel = viewModel(
                viewModelStoreOwner = context as androidx.lifecycle.ViewModelStoreOwner,
            )
            DocumentDetailScreen(
                vm = docVm,
                documentId = docId,
                onBack = { navController.popBackStack() },
                onDeleted = {
                    navController.popBackStack(Routes.DOCUMENTS, inclusive = false)
                },
            )
        }
    }
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter),
    )
    } // end Box
    } // end CernunnosTheme
}
