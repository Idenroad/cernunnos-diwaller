package com.cernunnos.authenticator.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.cernunnos.authenticator.R
import com.cernunnos.authenticator.ui.theme.CernunnosBorder
import com.cernunnos.authenticator.ui.theme.CernunnosPrimary
import com.cernunnos.authenticator.ui.theme.CernunnosPrimaryLight
import com.cernunnos.authenticator.ui.theme.CernunnosTextMuted
import com.cernunnos.authenticator.ui.viewmodel.AppViewModel
import com.cernunnos.authenticator.ui.viewmodel.VaultState
import com.cernunnos.authenticator.util.BiometricAuthHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(vm: AppViewModel, onDone: () -> Unit) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    // null = walkthrough, "choice" = method choice, "passphrase" = passphrase form, "device" = device
    var selectedMethod by remember { mutableStateOf<String?>(null) }
    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }
    var deviceAvailable by remember { mutableStateOf(BiometricAuthHelper.isDeviceCredentialAvailable(context)) }

    LaunchedEffect(state.vaultState) {
        if (state.vaultState == VaultState.UNLOCKED) onDone()
    }

    val bgGradient = Brush.radialGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f),
            MaterialTheme.colorScheme.background,
        ),
        center = androidx.compose.ui.geometry.Offset(0.5f, 0.3f),
        radius = 1000f,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, CernunnosBorder, RoundedCornerShape(20.dp))
                .padding(horizontal = 40.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Cernunnos Diwaller horizontal logo
            Image(
                painter = painterResource(R.drawable.cernunnos_logo),
                contentDescription = "Cernunnos Diwaller",
                modifier = Modifier.fillMaxWidth(0.8f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.splash_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = CernunnosTextMuted,
            )

            Spacer(Modifier.height(32.dp))

            if (selectedMethod == null) {
                // ── Feature highlights walkthrough ──
                FeatureHighlightsWalkthrough(
                    onSkip = { selectedMethod = "choice" },
                    onComplete = { selectedMethod = "choice" },
                )
            } else if (selectedMethod == "choice") {
                // ── Method choice ──
                Text(
                    stringResource(R.string.onboarding_choose_method),
                    style = MaterialTheme.typography.titleMedium,
                    color = CernunnosPrimaryLight,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.onboarding_choose_method_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = CernunnosTextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Spacer(Modifier.height(24.dp))

                // Passphrase option
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, CernunnosBorder, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = CernunnosPrimary)
                            Spacer(Modifier.size(12.dp))
                            Text(
                                stringResource(R.string.onboarding_passphrase_method),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.onboarding_passphrase_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = CernunnosTextMuted,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { selectedMethod = "passphrase" },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = CernunnosPrimary),
                        ) { Text(stringResource(R.string.onboarding_use_passphrase)) }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Device credential option
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, CernunnosBorder, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = CernunnosPrimary)
                            Spacer(Modifier.size(12.dp))
                            Text(
                                stringResource(R.string.onboarding_device_method),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.onboarding_device_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = CernunnosTextMuted,
                        )
                        Spacer(Modifier.height(12.dp))
                        if (deviceAvailable) {
                            Button(
                                onClick = {
                                    if (activity != null) {
                                        // Authenticate first, then use cipher
                                        val cipher = vm.prepareDeviceCredentialInit()
                                        if (cipher != null) {
                                            BiometricAuthHelper.authenticate(
                                                activity = activity,
                                                title = "Cernunnos Diwaller",
                                                subtitle = context.getString(R.string.onboarding_auth_desc),
                                                cipher = cipher,
                                                onSuccess = { authCipher ->
                                                    vm.completeDeviceCredentialInit(authCipher)
                                                },
                                                onError = { err ->
                                                    vm.clearError()
                                                },
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = CernunnosPrimary),
                            ) { Text(stringResource(R.string.onboarding_use_device)) }
                        } else {
                            Text(
                                stringResource(R.string.onboarding_no_device_credential),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            } else if (selectedMethod == "passphrase") {
                // ── Passphrase form ──
                Text(
                    stringResource(R.string.onboarding_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = CernunnosPrimaryLight,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.onboarding_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = CernunnosTextMuted,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )

                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it; mismatch = false },
                    label = { Text(stringResource(R.string.onboarding_passphrase)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it; mismatch = false },
                    label = { Text(stringResource(R.string.onboarding_confirm)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = mismatch,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))
                if (mismatch) {
                    Text(stringResource(R.string.onboarding_mismatch), color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                }

                Button(
                    onClick = {
                        if (passphrase != confirm) {
                            mismatch = true
                        } else {
                            vm.initializeVault(passphrase)
                        }
                    },
                    enabled = passphrase.length >= 8 && passphrase == confirm,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CernunnosPrimary),
                ) { Text(stringResource(R.string.onboarding_create), fontWeight = FontWeight.Medium) }

                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.TextButton(onClick = { selectedMethod = "choice" }) {
                    Text("←", color = CernunnosTextMuted)
                }
            }
        }
    }
}

/**
 * 3-step feature highlights walkthrough displayed before the method choice.
 * Uses a HorizontalPager for swipe navigation between steps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeatureHighlightsWalkthrough(
    onSkip: () -> Unit,
    onComplete: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })

    data class Step(val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: String, val text: String)
    val steps = listOf(
        Step(
            icon = Icons.Default.Lock,
            title = stringResource(R.string.onboarding_feature_secure_vault),
            text = stringResource(R.string.onboarding_feature_secure_vault_desc),
        ),
        Step(
            icon = Icons.Default.CloudUpload,
            title = stringResource(R.string.onboarding_feature_cloud_sync),
            text = stringResource(R.string.onboarding_feature_cloud_sync_desc),
        ),
        Step(
            icon = Icons.Default.Security,
            title = stringResource(R.string.onboarding_feature_privacy),
            text = stringResource(R.string.onboarding_feature_privacy_desc),
        ),
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Large icon illustration (96dp)
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(CernunnosPrimary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        steps[page].icon,
                        contentDescription = null,
                        tint = CernunnosPrimary,
                        modifier = Modifier.size(48.dp),
                    )
                }

                Spacer(Modifier.height(24.dp))
                Text(
                    steps[page].title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = CernunnosPrimaryLight,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    steps[page].text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CernunnosTextMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Progress dots indicator
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            repeat(3) { index ->
                val color =
                    if (pagerState.currentPage == index) CernunnosPrimary
                    else CernunnosBorder
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (pagerState.currentPage == index) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Buttons: Skip (left) and Next/Get Started (right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onSkip) {
                Text(stringResource(R.string.onboarding_skip))
            }
            Button(
                onClick = {
                    if (pagerState.currentPage < 2) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        onComplete()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CernunnosPrimary),
            ) {
                Text(
                    if (pagerState.currentPage < 2)
                        stringResource(R.string.onboarding_next)
                    else
                        stringResource(R.string.onboarding_get_started),
                )
            }
        }
    }
}
