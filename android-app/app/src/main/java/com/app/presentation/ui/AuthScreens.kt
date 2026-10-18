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
    keyboardType: KeyboardType = KeyboardType.Text
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
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            onLoginSuccess()
            viewModel.resetState()
        }
    }

    val isValidEmail = email.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val isValidPassword = password.isNotEmpty()

    AuthBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(56.dp))

            // BudGi Logo
            Image(
                painter = painterResource(id = R.drawable.budgi_logo),
                contentDescription = "BudGi Logo",
                modifier = Modifier.size(52.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Welcome Back",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Secure access to your financial dashboard",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Email input
            MinimalistTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = "Email address",
                keyboardType = KeyboardType.Email
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password input
            MinimalistTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = "Password",
                isPassword = true,
                passwordVisible = passwordVisible,
                onPasswordToggle = { passwordVisible = !passwordVisible }
            )

            // Forgot password
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    "Forgot Password?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { onNavigateToForgotPassword() }
                )
            }

            if (uiState.error != null) {
                Text(
                    uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
            }

            // Primary Log In Button
            MinimalistButton(
                text = "Log In",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.login(email.trim().lowercase(), password)
                },
                isLoading = uiState.isLoading,
                enabled = isValidEmail && isValidPassword && !uiState.isLoading
            )

            Spacer(modifier = Modifier.height(28.dp))

            val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

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
                    " Or continue with ",
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

            // Google Sign In Button (54dp Pill matching Onboarding)
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
                        } catch (e: Exception) { }
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
                    modifier = Modifier.clickable { onNavigateToSignUp() }
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
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var agreeTerms by rememberSaveable { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            onSignUpSuccess()
            viewModel.resetState()
        }
    }

    val isValidEmail = email.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val isValidPassword = password.length >= 8 && password == confirmPassword

    AuthBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            
            // Logo centered
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = R.drawable.budgi_logo),
                    contentDescription = "Logo",
                    modifier = Modifier.size(48.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                "Create Your Account",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Start managing personal and collaborative wealth.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(28.dp))
            
            MinimalistTextField(value = name, onValueChange = { name = it }, placeholder = "Full Name")
            Spacer(modifier = Modifier.height(16.dp))
            MinimalistTextField(value = email, onValueChange = { email = it }, placeholder = "Email address", keyboardType = KeyboardType.Email)
            Spacer(modifier = Modifier.height(16.dp))
            MinimalistTextField(value = password, onValueChange = { password = it }, placeholder = "Password (min 8 chars)", isPassword = true, passwordVisible = passwordVisible, onPasswordToggle = { passwordVisible = !passwordVisible })
            Spacer(modifier = Modifier.height(16.dp))
            MinimalistTextField(value = confirmPassword, onValueChange = { confirmPassword = it }, placeholder = "Confirm Password", isPassword = true, passwordVisible = confirmPasswordVisible, onPasswordToggle = { confirmPasswordVisible = !confirmPasswordVisible })
            
            val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

            // Terms & Conditions checkbox
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 14.dp)
            ) {
                Checkbox(
                    checked = agreeTerms,
                    onCheckedChange = { agreeTerms = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = if (isDark) Color.White else Color(0xFF0F172A),
                        checkmarkColor = if (isDark) Color.Black else Color.White,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Text(
                    "I agree to the Terms of Service and Privacy Policy",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
            
            if (uiState.error != null) {
                Text(uiState.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
            }
            
            // Primary create account button
            MinimalistButton(
                text = "Create Account",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.signUp(name.trim(), email.trim().lowercase(), password)
                },
                isLoading = uiState.isLoading,
                enabled = isValidEmail && isValidPassword && name.isNotBlank() && agreeTerms && !uiState.isLoading
            )
            
            Spacer(modifier = Modifier.height(28.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = if (isDark) DarkColors.GlassBorderStrong else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
                Text(
                    " Or continue with ",
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

            // Google Sign In Button (54dp Pill matching Onboarding)
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
                        } catch (e: Exception) { }
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
                Text("Continue with Google", color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 32.dp)) {
                Text("Already have an account? ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                Text("Log In", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.clickable { onNavigateToLogin() })
            }
        }
    }
}
