package com.app

import android.os.Bundle
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import com.app.data.local.ThemeManager
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.app.domain.repository.AuthRepository
import com.app.presentation.ui.FinanceAppRoot
import com.app.presentation.ui.BudGiLogo
import com.app.presentation.ui.bouncyClick
import com.app.presentation.ui.theme.AppTheme
import androidx.compose.material3.MaterialTheme
import com.app.data.local.TokenManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.luminance
import android.util.Log

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var tokenManager: TokenManager

    @Inject
    lateinit var themeManager: ThemeManager

    @Inject
    lateinit var biometricSecurityManager: com.app.security.BiometricSecurityManager

    @Inject
    lateinit var appUpdateManager: com.app.data.updater.AppUpdateManager

    private var isAuthenticated = mutableStateOf(false)
    private var showPrompt = mutableStateOf(false)
    @Volatile
    private var isPromptActive = false
    private var currentBiometricPrompt: BiometricPrompt? = null

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initial state
        isAuthenticated.value = !tokenManager.isBiometricEnabled
        showPrompt.value = tokenManager.isBiometricEnabled

        if (tokenManager.isBiometricEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val themePreference by themeManager.isDarkMode.collectAsState()
            val isDarkMode = themePreference ?: androidx.compose.foundation.isSystemInDarkTheme()

            AppTheme(darkTheme = isDarkMode) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val updateScope = rememberCoroutineScope()
                    val updateState by appUpdateManager.updateState.collectAsState()

                    // Active App Content: Preserved across backgrounding to avoid navigation state drops
                    if (isAuthenticated.value || !tokenManager.isBiometricEnabled) {
                        var smsPermissionGranted by remember { mutableStateOf(false) }
                        val permissionLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.RequestMultiplePermissions()
                        ) { result ->
                            smsPermissionGranted = result[android.Manifest.permission.READ_SMS] == true &&
                                                   result[android.Manifest.permission.RECEIVE_SMS] == true
                        }
                        LaunchedEffect(Unit) {
                            val readGranted = ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                android.Manifest.permission.READ_SMS
                            ) == PackageManager.PERMISSION_GRANTED
                            val receiveGranted = ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                android.Manifest.permission.RECEIVE_SMS
                            ) == PackageManager.PERMISSION_GRANTED
                            smsPermissionGranted = readGranted && receiveGranted
                        }
                        FinanceAppRoot(
                            windowSizeClass = windowSizeClass,
                            smsPermissionGranted = smsPermissionGranted,
                            onRequestPermission = {
                                val permissionsToRequest = mutableListOf(
                                    android.Manifest.permission.READ_SMS,
                                    android.Manifest.permission.RECEIVE_SMS
                                )
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                                permissionLauncher.launch(permissionsToRequest.toTypedArray())
                            },
                            authRepository = authRepository,
                            onCheckForUpdates = {
                                updateScope.launch {
                                    appUpdateManager.checkForUpdates(isUserInitiated = true)
                                }
                            }
                        )
                    }

                    // Secure Biometric Lock Overlay (Opaque & Non-pass-through)
                    if (showPrompt.value && !isAuthenticated.value) {
                        LockedScreen(
                            onUnlockClick = { triggerBiometric(force = true) },
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { triggerBiometric(force = true) }
                        )
                    }

                    // Background In-App Update Checker
                    LaunchedEffect(Unit) {
                        delay(2500)
                        appUpdateManager.checkForUpdates(isUserInitiated = false)
                    }

                    com.app.presentation.ui.AppUpdateDialog(
                        updateState = updateState,
                        onUpdateClick = { apkUrl ->
                            updateScope.launch {
                                appUpdateManager.downloadAndInstall(apkUrl)
                            }
                        },
                        onDismiss = { appUpdateManager.dismiss() }
                    )
                }
            }
        }
    }

    @Synchronized
    private fun triggerBiometric(force: Boolean = false) {
        if (!tokenManager.isBiometricEnabled || isAuthenticated.value) return
        if (isPromptActive && !force) return
        if (isFinishing || isDestroyed) return

        try {
            currentBiometricPrompt?.cancelAuthentication()
        } catch (_: Exception) {}
        currentBiometricPrompt = null

        isPromptActive = true
        showBiometricPrompt(
            onSuccess = {
                isAuthenticated.value = true
                showPrompt.value = false
                isPromptActive = false
                currentBiometricPrompt = null
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            },
            onError = { _, _ ->
                isPromptActive = false
                currentBiometricPrompt = null
            }
        )
    }

    override fun onResume() {
        super.onResume()
        if (::appUpdateManager.isInitialized) {
            appUpdateManager.checkPendingInstall()
        }
        if (tokenManager.isBiometricEnabled && !isAuthenticated.value) {
            showPrompt.value = true
            window.decorView.postDelayed({
                if (!isFinishing && !isDestroyed && tokenManager.isBiometricEnabled && !isAuthenticated.value && !isPromptActive) {
                    triggerBiometric()
                }
            }, 180)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Whenever the app gains focus from recent tasks, automatically trigger biometrics
        if (hasFocus && tokenManager.isBiometricEnabled && !isAuthenticated.value && !isPromptActive) {
            window.decorView.postDelayed({
                if (!isFinishing && !isDestroyed && tokenManager.isBiometricEnabled && !isAuthenticated.value && !isPromptActive) {
                    triggerBiometric()
                }
            }, 150)
        }
    }

    override fun onPause() {
        super.onPause()
        if (tokenManager.isBiometricEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            // Immediately lock when leaving or swiping to recents overview
            isAuthenticated.value = false
            showPrompt.value = true
        }
        // Cancel active prompt on pause to avoid window transition deadlocks
        try {
            currentBiometricPrompt?.cancelAuthentication()
        } catch (_: Exception) {}
        currentBiometricPrompt = null
        isPromptActive = false
    }

    override fun onStop() {
        super.onStop()
        if (tokenManager.isBiometricEnabled) {
            isAuthenticated.value = false
            showPrompt.value = true
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        try {
            currentBiometricPrompt?.cancelAuthentication()
        } catch (_: Exception) {}
        currentBiometricPrompt = null
        isPromptActive = false
    }

    override fun onDestroy() {
        try {
            currentBiometricPrompt?.cancelAuthentication()
        } catch (_: Exception) {}
        currentBiometricPrompt = null
        isPromptActive = false
        super.onDestroy()
    }

    private fun showBiometricPrompt(onSuccess: () -> Unit, onError: (Int, String) -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val cryptoObject = try {
            biometricSecurityManager.createCryptoObject()
        } catch (e: Exception) {
            Log.w("MainActivity", "Hardware crypto object creation failed, falling back to credentials", e)
            null
        }

        val prompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    isPromptActive = false
                    currentBiometricPrompt = null
                    onError(errorCode, errString.toString())
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isPromptActive = false
                    currentBiometricPrompt = null
                    val isHardwareVerified = if (result.cryptoObject != null) {
                        try {
                            biometricSecurityManager.verifyAuthentication(result.cryptoObject)
                        } catch (e: Exception) {
                            true
                        }
                    } else {
                        true
                    }
                    if (isHardwareVerified) {
                        onSuccess()
                    } else {
                        onError(-1, "Hardware cryptographic verification failed")
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Still listening for next fingerprint scan attempt
                }
            }
        )
        currentBiometricPrompt = prompt

        try {
            if (cryptoObject != null) {
                try {
                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                        .setTitle("BudGi Security")
                        .setSubtitle("Unlock BudGi with Hardware-Backed Biometrics")
                        .setNegativeButtonText("Use PIN / Cancel")
                        .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
                        .build()

                    prompt.authenticate(promptInfo, cryptoObject)
                } catch (e: Exception) {
                    Log.w("MainActivity", "Crypto authenticate failed, falling back to standard prompt", e)
                    val fallbackPromptInfo = BiometricPrompt.PromptInfo.Builder()
                        .setTitle("BudGi Security")
                        .setSubtitle("Unlock BudGi with fingerprint or device credentials")
                        .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                        .build()
                    prompt.authenticate(fallbackPromptInfo)
                }
            } else {
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("BudGi Security")
                    .setSubtitle("Unlock BudGi with your fingerprint or device credentials")
                    .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build()

                prompt.authenticate(promptInfo)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start biometric prompt, trying device credential fallback", e)
            try {
                val fallbackPromptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("BudGi Security")
                    .setSubtitle("Unlock BudGi using your credential")
                    .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build()
                prompt.authenticate(fallbackPromptInfo)
            } catch (inner: Exception) {
                Log.e("MainActivity", "Complete biometric authentication failure", inner)
                isPromptActive = false
                currentBiometricPrompt = null
                onError(-1, inner.message ?: "Authentication error")
            }
        }
    }
}

@Composable
fun LockedScreen(
    onUnlockClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        onUnlockClick()
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                // Bottom-left cyan ambient light (signature unified atmosphere)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF0284C7).copy(alpha = if (isDark) 0.18f else 0.07f),
                            Color.Transparent
                        ),
                        center = Offset(0f, size.height),
                        radius = size.width * 0.95f
                    ),
                    radius = size.width * 0.95f,
                    center = Offset(0f, size.height)
                )
                // Bottom-right warm amber ambient light (signature unified atmosphere)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFEA580C).copy(alpha = if (isDark) 0.14f else 0.05f),
                            Color.Transparent
                        ),
                        center = Offset(size.width, size.height),
                        radius = size.width * 0.95f
                    ),
                    radius = size.width * 0.95f,
                    center = Offset(size.width, size.height)
                )
            }
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            // Minimal BudGi Logo
            BudGiLogo(iconSize = 64.dp, showText = false)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "BudGi Locked",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Clean Minimal Fingerprint Target (WhatsApp / Google Pay style)
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                    .bouncyClick(scaleDown = 0.90f) {
                        onUnlockClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = "Unlock BudGi",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Touch the fingerprint sensor",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(36.dp))

            TextButton(
                onClick = onUnlockClick,
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = "Unlock with Device PIN",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
