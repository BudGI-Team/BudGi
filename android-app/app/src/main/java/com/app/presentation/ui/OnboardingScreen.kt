package com.app.presentation.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.app.R
import com.app.presentation.ui.theme.*
import com.app.presentation.viewmodel.AuthViewModel

/**
 * Ultra-Premium Progressive Single-Question Onboarding Flow.
 * Faithfully matches the high-converting single-concept UX:
 * 1. Entry Gateway ("Walk with BudGi")
 * 2. Email Address with quick domain auto-complete chips (@gmail.com, @outlook.com)
 * 3. Security Password Setup with live strength calibration
 * 4. First Name with massive hero typography & live blinking cursor
 * 5. Last Name with massive hero typography & live blinking cursor
 * 6. Choose a Username (@handle) with real-time backend uniqueness check
 * 7. Date of Birth with smooth wheel selector
 * 8. Profile Avatar carousel with glowing halo selector
 * 9. Currency & Monthly Savings Target with dynamic ring gauge
 * 10. The Grand Finale: Day 1 Streak Achievement Award + Minting BudGi Treasury Passport
 * 11. On-Device Bank SMS Tracking Activation
 */

private val AVATAR_OPTIONS = listOf(
    "zen_saver" to "Zen Saver",
    "calculated_brain" to "Wealth Architect",
    "wealthy_hype" to "Hype Investor",
    "sovereign_capital" to "Tactical Pilot",
    "stressed_spender" to "Rebalancing"
)

private val CURRENCY_OPTIONS = listOf(
    "INR" to "₹ INR",
    "USD" to "$ USD",
    "EUR" to "€ EUR",
    "GBP" to "£ GBP",
    "AED" to "د.إ AED"
)

private val EMAIL_DOMAINS = listOf("@gmail.com", "@outlook.com", "@icloud.com", "@yahoo.com")

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onNavigateToLogin: () -> Unit = onComplete,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState(initial = null)

    // ── Onboarding State ────────────────────────────────────────────────────────
    var currentStep by rememberSaveable { mutableIntStateOf(0) }
    val totalSteps = 10 // Steps 1..10

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var usernameAvailable by remember { mutableStateOf<Boolean?>(null) }
    var usernameError by remember { mutableStateOf<String?>(null) }

    var birthDay by rememberSaveable { mutableIntStateOf(15) }
    var birthMonth by rememberSaveable { mutableIntStateOf(6) } // June
    var birthYear by rememberSaveable { mutableIntStateOf(2000) }

    var selectedAvatar by rememberSaveable { mutableStateOf("zen_saver") }
    var selectedCurrency by rememberSaveable { mutableStateOf("INR") }
    var monthlyTarget by rememberSaveable { mutableDoubleStateOf(25000.0) }

    // If user is authenticated, prefill all existing data so they can review/customize
    LaunchedEffect(currentUser) {
        currentUser?.let { user ->
            if (email.isBlank() && user.email.isNotBlank()) {
                email = user.email
            }
            if (firstName.isBlank() && user.name.isNotBlank()) {
                val parts = user.name.trim().split(" ", limit = 2)
                firstName = parts.getOrNull(0) ?: ""
                lastName = parts.getOrNull(1) ?: ""
            }
            if (username.isBlank() && !user.username.isNullOrBlank()) {
                username = "@${user.username}"
            }
            if (!user.dateOfBirth.isNullOrBlank()) {
                val clean = user.dateOfBirth.substringBefore("T").trim()
                val parts = clean.split("-")
                if (parts.size >= 3) {
                    parts[0].toIntOrNull()?.let { if (it in 1920..2025) birthYear = it }
                    parts[1].toIntOrNull()?.let { if (it in 1..12) birthMonth = it }
                    parts[2].toIntOrNull()?.let { if (it in 1..31) birthDay = it }
                }
            }
            if (user.avatar.isNotBlank()) {
                selectedAvatar = user.avatar
            } else if (user.mood.isNotBlank() && user.mood != "neutral") {
                selectedAvatar = user.mood
            }
            if (user.currency.isNotBlank()) {
                selectedCurrency = user.currency
            }
            if (user.monthlyIncome > 0) {
                monthlyTarget = user.monthlyIncome
            }
        }
    }

    // Blinking cursor state
    val infiniteTransition = rememberInfiniteTransition(label = "cursor_anim")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor"
    )

    // Permission launcher for SMS
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Mark onboarding complete & enter app
        onComplete()
    }

    // Auto-advance on successful auth creation
    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            viewModel.resetState()
        }
    }

    val currentOwnUsername = currentUser?.username?.removePrefix("@")?.lowercase()?.trim()

    // Live debounce username validation (non-blocking)
    LaunchedEffect(username, currentOwnUsername) {
        val clean = username.trim().removePrefix("@").lowercase().filter { it.isLetterOrDigit() || it == '_' }
        if (clean.length < 3) {
            usernameAvailable = null
            usernameError = if (clean.isNotEmpty()) "Min 3 characters" else null
            return@LaunchedEffect
        }
        // If returning user enters their own previous username, approve immediately
        if (!currentOwnUsername.isNullOrBlank() && clean == currentOwnUsername) {
            usernameAvailable = true
            usernameError = null
            return@LaunchedEffect
        }
        delay(300)
        viewModel.checkUsername(clean) { available, err ->
            if (!currentOwnUsername.isNullOrBlank() && clean == currentOwnUsername) {
                usernameAvailable = true
                usernameError = null
            } else {
                usernameAvailable = available
                usernameError = if (available == false && err?.contains("taken", ignoreCase = true) == true) err else null
            }
        }
    }

    BackHandler(enabled = currentStep > 0) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        currentStep--
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val baseBg = MaterialTheme.colorScheme.background

    // Atmospheric background with ambient corner glow (adaptive dark/light)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(baseBg)
            .drawBehind {
                // Bottom-left cyan ambient light
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF0284C7).copy(alpha = if (isDark) 0.20f else 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(0f, size.height),
                        radius = size.width * 0.95f
                    ),
                    radius = size.width * 0.95f,
                    center = Offset(0f, size.height)
                )
                // Bottom-right warm amber ambient light
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFEA580C).copy(alpha = if (isDark) 0.16f else 0.06f),
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
            .navigationBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Top Header: Back Button & Segmented Progress Bar ──────────────────
            if (currentStep > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (currentStep > 0) {
                                currentStep--
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // Segmented Progress Bar (1..9) — clickable to jump to any step
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 1..9) {
                            val isCompleted = i <= currentStep
                            val isCurrent = i == currentStep
                            val canJump = currentUser == null || i >= 3
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(26.dp)
                                    .clickable(
                                        enabled = canJump,
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        currentStep = i
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(if (isCurrent) 4.5.dp else 3.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(
                                            if (isCurrent) Color(0xFF38BDF8)
                                            else if (isCompleted) (if (isDark) Color.White else Color(0xFF0F172A))
                                            else (if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.12f))
                                        )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "Skip",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onComplete()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // ── Active Screen Content ───────────────────────────────────────────
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> -width } + fadeOut()
                        )
                    } else {
                        (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> width } + fadeOut()
                        )
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                label = "onboarding_step"
            ) { step ->
                when (step) {
                    0 -> StepWelcomeGateway(
                        isAuthenticated = currentUser != null,
                        onContinue = {
                            if (email.isNotBlank()) {
                                currentStep = 3
                            } else {
                                currentStep = 1
                            }
                        },
                        onContinueWithEmail = { currentStep = 1 },
                        onGoogleSignIn = { idToken ->
                            viewModel.handleGoogleSignIn(idToken)
                            // Advance to name setup
                            currentStep = 3
                        },
                        onNavigateToLogin = onNavigateToLogin
                    )
                    1 -> StepEmail(
                        email = email,
                        onEmailChange = { email = it },
                        onDomainSelected = { domain ->
                            val prefix = if (email.contains("@")) email.substringBefore("@") else email
                            email = "$prefix$domain"
                        },
                        onNext = { currentStep = 2 }
                    )
                    2 -> StepPassword(
                        password = password,
                        onPasswordChange = { password = it },
                        passwordVisible = passwordVisible,
                        onTogglePassword = { passwordVisible = !passwordVisible },
                        onNext = { currentStep = 3 }
                    )
                    3 -> StepFirstName(
                        firstName = firstName,
                        onFirstNameChange = { firstName = it },
                        cursorAlpha = cursorAlpha,
                        onNext = {
                            if (lastName.isBlank()) {
                                currentStep = 4
                            } else {
                                currentStep = 4
                            }
                        }
                    )
                    4 -> StepLastName(
                        lastName = lastName,
                        onLastNameChange = { lastName = it },
                        cursorAlpha = cursorAlpha,
                        onNext = {
                            if (username.isBlank()) {
                                val generated = "${firstName.trim()}${lastName.trim()}".lowercase().filter { it.isLetterOrDigit() }
                                username = if (generated.isNotEmpty()) "@$generated" else "@budgi_user"
                            }
                            currentStep = 5
                        }
                    )
                    5 -> StepUsername(
                        username = username,
                        onUsernameChange = { username = it },
                        available = usernameAvailable,
                        error = usernameError,
                        cursorAlpha = cursorAlpha,
                        currentOwnUsername = currentOwnUsername,
                        onNext = { currentStep = 6 }
                    )
                    6 -> StepDateOfBirth(
                        day = birthDay,
                        month = birthMonth,
                        year = birthYear,
                        onDayChange = { birthDay = it },
                        onMonthChange = { birthMonth = it },
                        onYearChange = { birthYear = it },
                        onNext = { currentStep = 7 }
                    )
                    7 -> StepProfileAvatar(
                        userName = firstName.ifBlank { "You" },
                        selectedAvatar = selectedAvatar,
                        onSelectAvatar = { selectedAvatar = it },
                        onNext = { currentStep = 8 }
                    )
                    8 -> StepCurrencyAndTarget(
                        selectedCurrency = selectedCurrency,
                        onCurrencySelect = { selectedCurrency = it },
                        monthlyTarget = monthlyTarget,
                        onTargetChange = { monthlyTarget = it },
                        onNext = { currentStep = 9 }
                    )
                    9 -> {
                        val monthsList = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                        val formattedDob = "${birthDay.toString().padStart(2, '0')} ${monthsList[(birthMonth - 1).coerceIn(0, 11)]} $birthYear"
                        StepStreakPassportAward(
                            firstName = firstName,
                            lastName = lastName,
                            username = username,
                            avatar = selectedAvatar,
                            currency = selectedCurrency,
                            dateOfBirth = formattedDob,
                            monthlyTarget = monthlyTarget,
                            isLoading = uiState.isLoading,
                            isReturningUser = currentUser != null,
                            onJumpToStep = { step -> currentStep = step },
                            onEditDetails = {
                                currentStep = if (currentUser != null && email.isNotBlank()) 3 else 1
                            },
                            onNext = {
                                val fullName = "${firstName.trim()} ${lastName.trim()}".trim()
                                val dobString = "$birthYear-${birthMonth.toString().padStart(2, '0')}-${birthDay.toString().padStart(2, '0')}"
                                if (currentUser != null) {
                                    viewModel.updateProfile(
                                        name = fullName.ifBlank { null },
                                        username = username,
                                        dateOfBirth = dobString,
                                        avatar = selectedAvatar,
                                        currency = selectedCurrency,
                                        monthlyIncome = monthlyTarget,
                                        onboarded = true
                                    ) {
                                        currentStep = 10
                                    }
                                } else {
                                    viewModel.signUpWithProfile(
                                        name = fullName,
                                        email = email,
                                        password = password,
                                        username = username,
                                        dateOfBirth = dobString,
                                        avatar = selectedAvatar,
                                        currency = selectedCurrency
                                    ) {
                                        currentStep = 10
                                    }
                                }
                            }
                        )
                    }
                    10 -> StepSmsPermissionActivation(
                        onAllow = {
                            val perms = mutableListOf<String>()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                perms.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            perms.add(Manifest.permission.RECEIVE_SMS)
                            perms.add(Manifest.permission.READ_SMS)
                            permissionLauncher.launch(perms.toTypedArray())
                        },
                        onSkip = {
                            onComplete()
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 0: Welcome Gateway ("Walk With BudGi")
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepWelcomeGateway(
    isAuthenticated: Boolean = false,
    onContinue: () -> Unit = {},
    onContinueWithEmail: () -> Unit,
    onGoogleSignIn: (String) -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(0.7f))

        // BudGi Logo Emblem with subtle glow
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(Color(0xFF3B82F6).copy(alpha = 0.2f), CircleShape)
            )
            Image(
                painter = painterResource(id = R.drawable.budgi_logo),
                contentDescription = "BudGi Logo",
                modifier = Modifier.size(72.dp)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

        // Hero Typography
        Text(
            text = "Walk With",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        )
        Text(
            text = "BudGi",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontFamily = com.app.presentation.ui.theme.BillabongFont,
                fontSize = 46.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.5.sp,
                color = Color(0xFF38BDF8)
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Sovereign personal treasury & real-time telemetry",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.weight(1f))

        if (isAuthenticated) {
            // Authenticated user: Walk with BudGi primary action
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(BudgiRadius.Pill),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) Color.White else Color(0xFF0F172A),
                    contentColor = if (isDark) Color.Black else Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Text(
                    "Walk with BudGi",
                    color = if (isDark) Color.Black else Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = if (isDark) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(36.dp))
        } else {
            // 1. Continue with Google (Pill button matching AuthScreens)
            OutlinedButton(
                onClick = {
                    val credentialManager = CredentialManager.create(context)
                    val googleIdOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(context.getString(R.string.google_server_client_id))
                        .build()
                    val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
                    coroutineScope.launch {
                        try {
                            val result = credentialManager.getCredential(context, request)
                            val credential = result.credential
                            if (credential is GoogleIdTokenCredential) {
                                onGoogleSignIn(credential.idToken)
                            }
                        } catch (_: Exception) { }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(BudgiRadius.Pill),
                border = BorderStroke(
                    1.dp,
                    if (isDark) DarkColors.GlassBorderStrong else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.60f)
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isDark) Color(0xFF131722).copy(alpha = 0.50f) else Color.White
                )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_google),
                    contentDescription = "Google",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Continue with Google",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Continue with Email
            Button(
                onClick = onContinueWithEmail,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(BudgiRadius.Pill),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) Color.White else Color(0xFF0F172A),
                    contentColor = if (isDark) Color.Black else Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MailOutline,
                    contentDescription = "Email",
                    tint = if (isDark) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Continue with Email",
                    color = if (isDark) Color.Black else Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Footer log-in link
            Row(
                modifier = Modifier.padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Already have an account? ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                Text(
                    "Log In",
                    color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { onNavigateToLogin() }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 1: Enter Your Email with Quick Domain Chips
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepEmail(
    email: String,
    onEmailChange: (String) -> Unit,
    onDomainSelected: (String) -> Unit,
    onNext: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isValid = email.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Enter Your Email:",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Clean adaptive glass input box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isDark) Color(0xFF151C2A) else Color(0xFFF1F5F9))
                .border(
                    1.dp,
                    if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.10f),
                    RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.MailOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                BasicTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(Color(0xFF38BDF8)),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { if (isValid) onNext() }),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (email.isEmpty()) {
                            Text("john.doe@example.com", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 16.sp)
                        }
                        innerTextField()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Domain auto-complete pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            EMAIL_DOMAINS.take(2).forEach { domain ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(BudgiRadius.Pill))
                        .background(if (isDark) Color(0xFF1E273A) else Color(0xFFE2E8F0))
                        .border(
                            1.dp,
                            if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f),
                            RoundedCornerShape(BudgiRadius.Pill)
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDomainSelected(domain)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        domain,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Keyboard-anchored Continue Button
        ContinuePillButton(
            text = "Continue >",
            enabled = isValid,
            onClick = onNext
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 2: Create Password
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepPassword(
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePassword: () -> Unit,
    onNext: () -> Unit
) {
    val isValid = password.length >= 8
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Create Password:",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Minimum 8 characters with letters and numbers",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(28.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isDark) Color(0xFF151C2A) else Color(0xFFF1F5F9))
                .border(
                    1.dp,
                    if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.10f),
                    RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                BasicTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 16.sp
                    ),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    cursorBrush = SolidColor(Color(0xFF38BDF8)),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (isValid) onNext() }),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (password.isEmpty()) {
                            Text("Enter password", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 16.sp)
                        }
                        innerTextField()
                    }
                )
                IconButton(onClick = onTogglePassword, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Toggle password",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        ContinuePillButton(
            text = "Continue >",
            enabled = isValid,
            onClick = onNext
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 3: First Name (Massive Hero Typography + Live Blinking Cursor)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepFirstName(
    firstName: String,
    onFirstNameChange: (String) -> Unit,
    cursorAlpha: Float,
    onNext: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(56.dp))

        Text(
            text = "Your First Name:",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Hero Typography Display
        Box(contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = firstName.ifBlank { "" },
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 42.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
                // Blinking vertical cursor (Sky accent)
                Box(
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .width(3.dp)
                        .height(44.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = cursorAlpha))
                )
            }

            // Hidden textfield for keyboard capture
            BasicTextField(
                value = firstName,
                onValueChange = onFirstNameChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                textStyle = TextStyle(color = Color.Transparent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { if (firstName.isNotBlank()) onNext() }),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        ContinuePillButton(
            text = "Continue >",
            enabled = firstName.isNotBlank(),
            onClick = onNext
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 4: Last Name (Massive Hero Typography + Live Blinking Cursor)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepLastName(
    lastName: String,
    onLastNameChange: (String) -> Unit,
    cursorAlpha: Float,
    onNext: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(56.dp))

        Text(
            text = "Your Last Name:",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Hero Typography Display
        Box(contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = lastName.ifBlank { "" },
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 42.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
                Box(
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .width(3.dp)
                        .height(44.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = cursorAlpha))
                )
            }

            BasicTextField(
                value = lastName,
                onValueChange = onLastNameChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                textStyle = TextStyle(color = Color.Transparent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { if (lastName.isNotBlank()) onNext() }),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        ContinuePillButton(
            text = "Continue >",
            enabled = lastName.isNotBlank(),
            onClick = onNext
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 5: Choose a Username with Live Uniqueness Check
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepUsername(
    username: String,
    onUsernameChange: (String) -> Unit,
    available: Boolean?,
    error: String?,
    cursorAlpha: Float,
    currentOwnUsername: String? = null,
    onNext: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val clean = username.trim().removePrefix("@").lowercase().filter { it.isLetterOrDigit() || it == '_' }
    val isOwnUsername = !currentOwnUsername.isNullOrBlank() && clean == currentOwnUsername
    val canProceed = clean.length >= 3 && (isOwnUsername || (error == null || !error.contains("taken", ignoreCase = true)))

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(56.dp))

        Text(
            text = "Choose a Username",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Clean, responsive @ handle input
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "@",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(modifier = Modifier.width(4.dp))
            BasicTextField(
                value = clean,
                onValueChange = { input ->
                    val sanitized = input.removePrefix("@").filter { it.isLetterOrDigit() || it == '_' }.take(20).lowercase()
                    onUsernameChange("@$sanitized")
                },
                textStyle = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { if (canProceed) onNext() }),
                singleLine = true,
                modifier = Modifier
                    .width(IntrinsicSize.Min)
                    .defaultMinSize(minWidth = 100.dp)
                    .focusRequester(focusRequester)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Live availability badge
        if (isOwnUsername) {
            Text("✓ Your current username", color = Color(0xFF34D399), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        } else if (available == true) {
            Text("✓ Available", color = Color(0xFF34D399), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        } else if (error != null) {
            Text(error, color = Color(0xFFF87171), fontSize = 14.sp)
        } else if (clean.length >= 3) {
            Text("✓ Valid handle format", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.weight(1f))

        ContinuePillButton(
            text = "Continue >",
            enabled = canProceed,
            onClick = {
                onUsernameChange("@$clean")
                onNext()
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 6: Date of Birth with Interactive Wheel Picker
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepDateOfBirth(
    day: Int,
    month: Int,
    year: Int,
    onDayChange: (Int) -> Unit,
    onMonthChange: (Int) -> Unit,
    onYearChange: (Int) -> Unit,
    onNext: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = LocalHapticFeedback.current
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    val maxDays = when (month) {
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    val currentYear = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) }
    val age = (currentYear - year).coerceAtLeast(0)

    val openDatePicker = {
        val picker = android.app.DatePickerDialog(
            context,
            { _, selectedYear, selectedMonth, selectedDay ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onYearChange(selectedYear)
                onMonthChange(selectedMonth + 1)
                onDayChange(selectedDay)
            },
            year,
            (month - 1).coerceIn(0, 11),
            day.coerceIn(1, 31)
        )
        val maxCal = java.util.Calendar.getInstance()
        maxCal.add(java.util.Calendar.YEAR, -5)
        picker.datePicker.maxDate = maxCal.timeInMillis
        val minCal = java.util.Calendar.getInstance()
        minCal.set(1930, 0, 1)
        picker.datePicker.minDate = minCal.timeInMillis
        picker.show()
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "Your Date of Birth",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Used to calibrate your financial life-stage and retirement projections.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Age & Date Formatted Live Badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF38BDF8).copy(alpha = 0.12f))
                .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "${day.toString().padStart(2, '0')} ${months[(month - 1).coerceIn(0, 11)]} $year • $age years old",
                color = Color(0xFF38BDF8),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(26.dp))

        // 3-Column Interactive Stepper
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f))
                .border(
                    1.dp,
                    if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f),
                    RoundedCornerShape(24.dp)
                )
                .padding(vertical = 18.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Day selector
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "DAY",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(8.dp))
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDayChange(if (day < maxDays) day + 1 else 1)
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Increase day",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF38BDF8).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDayChange(if (day < maxDays) day + 1 else 1)
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = day.toString().padStart(2, '0'),
                        color = if (isDark) Color.White else Color(0xFF0F172A),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(Modifier.height(10.dp))
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDayChange(if (day > 1) day - 1 else maxDays)
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Decrease day",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // Month selector
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "MONTH",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(8.dp))
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onMonthChange(if (month < 12) month + 1 else 1)
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Increase month",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF38BDF8).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onMonthChange(if (month < 12) month + 1 else 1)
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = months[(month - 1).coerceIn(0, 11)],
                        color = if (isDark) Color.White else Color(0xFF0F172A),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(Modifier.height(10.dp))
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onMonthChange(if (month > 1) month - 1 else 12)
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Decrease month",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // Year selector
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "YEAR",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(8.dp))
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onYearChange((year + 1).coerceAtMost(currentYear - 5))
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Increase year",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF38BDF8).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onYearChange(if (year < currentYear - 5) year + 1 else 1970)
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$year",
                        color = if (isDark) Color.White else Color(0xFF0F172A),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(Modifier.height(10.dp))
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onYearChange((year - 1).coerceAtLeast(1930))
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Decrease year",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Open Native Calendar Picker Button
        OutlinedButton(
            onClick = openDatePicker,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(BudgiRadius.Pill),
            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color(0xFF38BDF8).copy(alpha = 0.08f),
                contentColor = Color(0xFF38BDF8)
            )
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Pick From Calendar View",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        ContinuePillButton(text = "Continue >", enabled = true, onClick = onNext)

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 7: Profile Avatar Carousel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepProfileAvatar(
    userName: String,
    selectedAvatar: String,
    onSelectAvatar: (String) -> Unit,
    onNext: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Choose your profile picture",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Center highlighted avatar snap carousel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AVATAR_OPTIONS.forEach { (avatarKey, label) ->
                val isSelected = avatarKey == selectedAvatar
                val size = if (isSelected) 110.dp else 70.dp

                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(size)
                        .clip(CircleShape)
                        .background(if (isSelected) (if (isDark) Color(0xFF1E273A) else Color(0xFFE2E8F0)) else (if (isDark) Color(0xFF121722) else Color(0xFFF1F5F9)))
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) Color(0xFF38BDF8) else (if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.10f)),
                            shape = CircleShape
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSelectAvatar(avatarKey)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    MoodAvatar(mood = avatarKey, modifier = Modifier.size(size * 0.85f))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Display user's name beneath the chosen avatar (matching design!)
        Text(
            text = userName,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Personalize your financial identity across your spaces.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(1f))

        ContinuePillButton(text = "Next >", enabled = true, onClick = onNext)

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 8: Currency & Monthly Target (Circular Ring Gauge from 3rd batch)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepCurrencyAndTarget(
    selectedCurrency: String,
    onCurrencySelect: (String) -> Unit,
    monthlyTarget: Double,
    onTargetChange: (Double) -> Unit,
    onNext: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "Set Monthly Target",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Currency selection pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CURRENCY_OPTIONS.forEach { (code, label) ->
                val isSelected = code == selectedCurrency
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(BudgiRadius.Pill))
                        .background(if (isSelected) (if (isDark) Color.White else Color(0xFF0F172A)) else (if (isDark) Color(0xFF1E273A) else Color(0xFFE2E8F0)))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onCurrencySelect(code)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) (if (isDark) Color.Black else Color.White) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        // Ring Gauge (100% BudGi Cyan / Sky palette)
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 14.dp.toPx()
                // Track
                drawArc(
                    color = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                )
                // Progress (70% calibration)
                drawArc(
                    brush = Brush.sweepGradient(listOf(Color(0xFF38BDF8), Color(0xFF0284C7), Color(0xFF38BDF8))),
                    startAngle = -90f,
                    sweepAngle = 260f,
                    useCenter = false,
                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${if (selectedCurrency == "INR") "₹" else "$"}${monthlyTarget.toInt()}",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
                Text("Monthly Target", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Quick adjust chips
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(15000.0, 25000.0, 50000.0, 100000.0).forEach { amount ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(BudgiRadius.Pill))
                        .background(if (isDark) Color(0xFF1E273A) else Color(0xFFE2E8F0))
                        .clickable { onTargetChange(amount) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        "${amount.toInt() / 1000}k",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        ContinuePillButton(text = "Next >", enabled = true, onClick = onNext)

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 9: Day 1 Streak & Treasury Passport Minting
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepStreakPassportAward(
    firstName: String,
    lastName: String,
    username: String,
    avatar: String,
    currency: String,
    dateOfBirth: String,
    monthlyTarget: Double,
    isLoading: Boolean = false,
    isReturningUser: Boolean = false,
    onJumpToStep: (Int) -> Unit,
    onEditDetails: () -> Unit,
    onNext: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Your first day of streak!",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Golden Laurel Wreath + Streak "1"
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(105.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Laurel leaves arc
                drawArc(
                    brush = Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFD97706))),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Text(
                text = "1",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFF59E0B)
                )
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // BudGi Treasury Passport Titanium Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A), Color(0xFF020617))
                    )
                )
                .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("BUDGI TREASURY PASSPORT", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("SERIES 2026", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))) {
                        MoodAvatar(mood = avatar, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "$firstName $lastName".ifBlank { "BudGi Explorer" },
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = username.ifBlank { "@explorer" },
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "DOB: $dateOfBirth",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "TARGET: ${if (currency == "INR") "₹" else "$"}${monthlyTarget.toInt()}/mo",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("CURRENCY: $currency", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                    Text("#BG-2026-8492", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Quick edit buttons row so user can change ANY detail
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                Triple("👤 Name", 3, 1f),
                Triple("@ Handle", 5, 1f),
                Triple("🎂 DOB", 6, 1f),
                Triple("🎭 Avatar", 7, 1f),
                Triple("🎯 Target", 8, 1f)
            ).forEach { (label, step, weight) ->
                Box(
                    modifier = Modifier
                        .weight(weight)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.05f))
                        .border(
                            1.dp,
                            if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.10f),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onJumpToStep(step) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        ContinuePillButton(
            text = if (isReturningUser) "Save All Changes >" else "Save & Initialize Ledger >",
            enabled = true,
            isLoading = isLoading,
            onClick = onNext
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 10: On-Device Bank SMS Tracking Activation
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepSmsPermissionActivation(
    onAllow: () -> Unit,
    onSkip: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Frosted System Permission Dialog Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(if (isDark) Color(0xFF151D2A).copy(alpha = 0.95f) else MaterialTheme.colorScheme.surface)
                .border(
                    1.dp,
                    if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.10f),
                    RoundedCornerShape(24.dp)
                )
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Running / Sync emblem
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF22C55E)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Allow \"BudGi\" to detect Bank SMS for automated tracking?",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "BudGi uses on-device SMS parsing to track expenses automatically. 100% private, zero cloud exposure.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Primary Allow Action
                Button(
                    onClick = onAllow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(BudgiRadius.Pill),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDark) Color.White else Color(0xFF0F172A),
                        contentColor = if (isDark) Color.Black else Color.White
                    )
                ) {
                    Text("Allow Smart Tracking", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                TextButton(onClick = onSkip) {
                    Text(
                        "Continue with Manual Entry",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared Continue Pill Button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ContinuePillButton(
    text: String,
    enabled: Boolean,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Button(
        onClick = {
            if (!isLoading) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
        },
        enabled = enabled && !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(BudgiRadius.Pill),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isDark) Color.White else Color(0xFF0F172A),
            contentColor = if (isDark) Color.Black else Color.White,
            disabledContainerColor = if (isDark) Color.White.copy(alpha = 0.25f) else Color(0xFF0F172A).copy(alpha = 0.20f),
            disabledContentColor = if (isDark) Color.White.copy(alpha = 0.40f) else Color(0xFF0F172A).copy(alpha = 0.40f)
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = if (isDark) Color.Black else Color.White,
                strokeWidth = 2.5.dp
            )
        } else {
            Text(
                text = text,
                color = if (enabled) (if (isDark) Color.Black else Color.White) else (if (isDark) Color.White.copy(alpha = 0.4f) else Color(0xFF0F172A).copy(alpha = 0.4f)),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
