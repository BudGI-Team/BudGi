package com.app.presentation.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import com.app.R
import com.app.presentation.viewmodel.AuthViewModel
import com.app.presentation.ui.theme.*

import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.BorderStroke

@Composable
fun AuthBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val baseBg = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBg)
            .drawBehind {
                // Bottom-left cyan ambient light (exact Onboarding atmospheric signature)
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
                // Bottom-right warm amber ambient light (exact Onboarding atmospheric signature)
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
            .navigationBarsPadding(),
        content = content
    )
}

@Composable
fun MinimalistTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onPasswordToggle: () -> Unit = {},
    keyboardType: KeyboardType = KeyboardType.Text,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                fontSize = 15.sp
            )
        },
        leadingIcon = leadingIcon,
        trailingIcon = {
            if (isPassword) {
                IconButton(onClick = onPasswordToggle) {
                    Icon(
                        if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Toggle Password",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = if (isDark) Color(0xFF131722).copy(alpha = 0.75f) else Color.White,
            unfocusedContainerColor = if (isDark) Color(0xFF131722).copy(alpha = 0.50f) else Color.White.copy(alpha = 0.85f),
            focusedBorderColor = if (isDark) Color.White.copy(alpha = 0.30f) else Color(0xFF0F172A).copy(alpha = 0.35f),
            unfocusedBorderColor = if (isDark) DarkColors.GlassBorderStrong else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f),
            focusedTextColor = MaterialTheme.colorScheme.onBackground,
            unfocusedTextColor = MaterialTheme.colorScheme.onBackground
        ),
        singleLine = true
    )
}

@Composable
fun MinimalistButton(
    text: String,
    onClick: () -> Unit,
    isDark: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    isLoading: Boolean = false,
    enabled: Boolean = true
) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (darkTheme) Color.White else Color(0xFF0F172A),
            contentColor = if (darkTheme) Color.Black else Color.White,
            disabledContainerColor = if (darkTheme) Color.White.copy(alpha = 0.25f) else Color(0xFF0F172A).copy(alpha = 0.20f),
            disabledContentColor = if (darkTheme) Color.White.copy(alpha = 0.40f) else Color(0xFF0F172A).copy(alpha = 0.40f)
        ),
        shape = RoundedCornerShape(BudgiRadius.Pill),
        elevation = ButtonDefaults.buttonElevation(0.dp),
        enabled = enabled && !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = if (darkTheme) Color.Black else Color.White,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(22.dp)
            )
        } else {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = text,
                color = if (enabled) (if (darkTheme) Color.Black else Color.White) else (if (darkTheme) Color.White.copy(alpha = 0.40f) else Color(0xFF0F172A).copy(alpha = 0.40f)),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun SocialCircularButton(iconResId: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(DarkColors.BgCard.copy(alpha = 0.6f))
            .border(1.dp, DarkColors.GlassBorderStrong, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = iconResId),
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun LoginScreen(
    onNavigateToSignUp: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    var phone by rememberSaveable { mutableStateOf("") }
    var otp by rememberSaveable { mutableStateOf("") }
    var countdown by remember { mutableIntStateOf(60) }
    var canResend by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            onLoginSuccess()
            viewModel.resetState()
        }
    }

    LaunchedEffect(uiState.isOtpSent) {
        if (uiState.isOtpSent) {
            countdown = 60
            canResend = false
            while (countdown > 0) {
                kotlinx.coroutines.delay(1000)
                countdown--
            }
            canResend = true
        }
    }

    AuthBackground {
        if (uiState.requiresPhoneVerification) {
            PhoneVerificationSheet(
                viewModel = viewModel,
                onDismiss = { viewModel.resetState() }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // BudGi Logo
            Image(
                painter = painterResource(id = R.drawable.budgi_logo),
                contentDescription = "BudGi Logo",
                modifier = Modifier.size(52.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                "Welcome Back",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                if (!uiState.isOtpSent) "Sign in with Google or your verified phone number" else "Enter the verification code sent to your phone",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            if (!uiState.isOtpSent) {
                // Google Sign In Button
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
                                    viewModel.handleGoogleSignIn(credential.idToken)
                                }
                            } catch (e: Exception) {
                                viewModel.setAuthError("Google Sign-In failed: ${e.localizedMessage ?: "Operation cancelled"}")
                            }
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

                Spacer(modifier = Modifier.height(24.dp))

                // Divider
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = if (isDark) DarkColors.GlassBorderStrong else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                    Text(
                        " or continue with phone ",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = if (isDark) DarkColors.GlassBorderStrong else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Phone input
                MinimalistTextField(
                    value = phone,
                    onValueChange = { if (it.length <= 10 && it.all { c -> c.isDigit() }) phone = it },
                    placeholder = "10-digit mobile number",
                    keyboardType = KeyboardType.Phone,
                    leadingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 14.dp, end = 6.dp)
                        ) {
                            Text("+91", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
                            Spacer(Modifier.width(8.dp))
                            Box(modifier = Modifier.width(1.dp).height(20.dp).background(if (isDark) DarkColors.GlassBorderStrong else MaterialTheme.colorScheme.outlineVariant))
                        }
                    }
                )

                if (uiState.error != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Send OTP Button
                MinimalistButton(
                    text = "Continue with Phone",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.phoneLoginRequest(phone)
                    },
                    isLoading = uiState.isLoading,
                    enabled = phone.length == 10 && !uiState.isLoading
                )
            } else {
                // OTP Step
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isDark) Color(0xFF131722).copy(alpha = 0.6f) else Color(0xFFF1F5F9))
                        .border(1.dp, if (isDark) DarkColors.GlassBorderStrong else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            "Verification code sent to",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "+91 $phone",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                "Change",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier.clickable {
                                    viewModel.resetOtpState()
                                    otp = ""
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                MinimalistTextField(
                    value = otp,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) otp = it },
                    placeholder = "Enter 6-digit OTP",
                    keyboardType = KeyboardType.NumberPassword
                )

                if (uiState.error != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Resend timer
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canResend) {
                        Text(
                            "Resend Code",
                            color = MaterialTheme.colorScheme.onBackground,
                            textDecoration = TextDecoration.Underline,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable {
                                viewModel.phoneLoginRequest(phone)
                            }
                        )
                    } else {
                        Text(
                            "Resend code in ${countdown}s",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                MinimalistButton(
                    text = "Verify & Access Account",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.phoneLoginVerify(phone, otp)
                    },
                    isLoading = uiState.isLoading,
                    enabled = otp.length == 6 && !uiState.isLoading
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.padding(top = 24.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Don't have an account? ",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
                Text(
                    "Sign Up",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable {
                        viewModel.resetOtpState()
                        onNavigateToSignUp()
                    }
                )
            }
        }
    }
}

@Composable
fun SignUpScreen(
    onNavigateToLogin: () -> Unit,
    onSignUpSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var otp by rememberSaveable { mutableStateOf("") }
    var countdown by remember { mutableIntStateOf(60) }
    var canResend by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            onSignUpSuccess()
            viewModel.resetState()
        }
    }

    LaunchedEffect(uiState.isOtpSent) {
        if (uiState.isOtpSent) {
            countdown = 60
            canResend = false
            while (countdown > 0) {
                kotlinx.coroutines.delay(1000)
                countdown--
            }
            canResend = true
        }
    }

    AuthBackground {
        if (uiState.requiresPhoneVerification) {
            PhoneVerificationSheet(
                viewModel = viewModel,
                onDismiss = { viewModel.resetState() }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Logo centered
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = R.drawable.budgi_logo),
                    contentDescription = "Logo",
                    modifier = Modifier.size(52.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                "Create Account",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                if (!uiState.isOtpSent) "Join BudGi with Google or verified mobile number" else "Enter the verification code to complete setup",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            if (!uiState.isOtpSent) {
                // Google Sign In Button
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
                                if (credential is GoogleIdTokenCredential) viewModel.handleGoogleSignIn(credential.idToken)
                            } catch (e: Exception) {
                                viewModel.setAuthError("Google Sign-In failed: ${e.localizedMessage ?: "Operation cancelled"}")
                            }
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
                    Image(painter = painterResource(id = R.drawable.ic_google), contentDescription = "Google", modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Sign up with Google", color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = if (isDark) DarkColors.GlassBorderStrong else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                    Text(
                        " or register with phone ",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = if (isDark) DarkColors.GlassBorderStrong else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                MinimalistTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Full Name"
                )

                Spacer(modifier = Modifier.height(14.dp))

                MinimalistTextField(
                    value = phone,
                    onValueChange = { if (it.length <= 10 && it.all { c -> c.isDigit() }) phone = it },
                    placeholder = "10-digit mobile number",
                    keyboardType = KeyboardType.Phone,
                    leadingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 14.dp, end = 6.dp)
                        ) {
                            Text("+91", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
                            Spacer(Modifier.width(8.dp))
                            Box(modifier = Modifier.width(1.dp).height(20.dp).background(if (isDark) DarkColors.GlassBorderStrong else MaterialTheme.colorScheme.outlineVariant))
                        }
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                MinimalistTextField(
                    value = username,
                    onValueChange = { username = it.filter { c -> c.isLetterOrDigit() || c == '_' } },
                    placeholder = "Username (optional)"
                )

                if (uiState.error != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                MinimalistButton(
                    text = "Continue with Phone",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.phoneRegisterRequest(phone, name, username.ifBlank { null })
                    },
                    isLoading = uiState.isLoading,
                    enabled = name.trim().length >= 2 && phone.length == 10 && !uiState.isLoading
                )
            } else {
                // OTP Step
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isDark) Color(0xFF131722).copy(alpha = 0.6f) else Color(0xFFF1F5F9))
                        .border(1.dp, if (isDark) DarkColors.GlassBorderStrong else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            "Verification code sent to",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "+91 $phone",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                "Change",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier.clickable {
                                    viewModel.resetOtpState()
                                    otp = ""
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                MinimalistTextField(
                    value = otp,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) otp = it },
                    placeholder = "Enter 6-digit OTP",
                    keyboardType = KeyboardType.NumberPassword
                )

                if (uiState.error != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canResend) {
                        Text(
                            "Resend Code",
                            color = MaterialTheme.colorScheme.onBackground,
                            textDecoration = TextDecoration.Underline,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable {
                                viewModel.phoneRegisterRequest(phone, name, username.ifBlank { null })
                            }
                        )
                    } else {
                        Text(
                            "Resend code in ${countdown}s",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                MinimalistButton(
                    text = "Complete Registration",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.phoneRegisterVerify(phone, otp, name, username.ifBlank { null })
                    },
                    isLoading = uiState.isLoading,
                    enabled = otp.length == 6 && !uiState.isLoading
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.padding(top = 24.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Already have an account? ",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
                Text(
                    "Log In",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable {
                        viewModel.resetOtpState()
                        onNavigateToLogin()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneVerificationSheet(
    viewModel: AuthViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var phone by rememberSaveable { mutableStateOf("") }
    var otp by rememberSaveable { mutableStateOf("") }
    var countdown by remember { mutableIntStateOf(60) }
    var canResend by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    LaunchedEffect(uiState.isOtpSent) {
        if (uiState.isOtpSent) {
            countdown = 60
            canResend = false
            while (countdown > 0) {
                kotlinx.coroutines.delay(1000)
                countdown--
            }
            canResend = true
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) Color(0xFF0F121C) else Color.White,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                if (!uiState.isOtpSent) "Phone Verification" else "Enter Verification Code",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                if (!uiState.isOtpSent)
                    "Link your mobile number to secure your BudGi account."
                else
                    "Enter the 6-digit code sent to +91 $phone",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (uiState.error != null) {
                Text(
                    uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (!uiState.isOtpSent) {
                MinimalistTextField(
                    value = phone,
                    onValueChange = { if (it.length <= 10 && it.all { char -> char.isDigit() }) phone = it },
                    placeholder = "10-digit Mobile Number",
                    keyboardType = KeyboardType.Phone
                )

                Spacer(modifier = Modifier.height(16.dp))

                MinimalistButton(
                    text = "Send 6-Digit OTP",
                    onClick = {
                        val clean = phone.filter { it.isDigit() }
                        viewModel.sendPhoneOtp("+91$clean")
                    },
                    isLoading = uiState.isLoading,
                    enabled = phone.length == 10 && !uiState.isLoading
                )
            } else {
                MinimalistTextField(
                    value = otp,
                    onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) otp = it },
                    placeholder = "••••••",
                    keyboardType = KeyboardType.NumberPassword
                )

                Spacer(modifier = Modifier.height(16.dp))

                MinimalistButton(
                    text = "Verify & Access Account",
                    onClick = {
                        val clean = phone.filter { it.isDigit() }
                        viewModel.verifyPhoneOtp("+91$clean", otp)
                    },
                    isLoading = uiState.isLoading,
                    enabled = otp.length == 6 && !uiState.isLoading
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (canResend) {
                    Text(
                        "Resend Code",
                        color = MaterialTheme.colorScheme.onBackground,
                        textDecoration = TextDecoration.Underline,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            val clean = phone.filter { it.isDigit() }
                            viewModel.sendPhoneOtp("+91$clean")
                        }
                    )
                } else {
                    Text(
                        "Resend code in ${countdown}s",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

