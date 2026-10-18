package com.app.presentation.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.presentation.ui.theme.CategoryColors
import com.app.presentation.ui.theme.BudgiRadius
import com.app.presentation.viewmodel.ProfileViewModel
import kotlinx.coroutines.delay

private val AVATAR_PRESETS = listOf(
    "zen_saver" to "Zen Saver",
    "calculated_brain" to "Wealth Architect",
    "wealthy_hype" to "Hype Investor",
    "sovereign_capital" to "Tactical Pilot",
    "stressed_spender" to "Rebalancing"
)

private val CURRENCY_PRESETS = listOf(
    "INR" to "₹ INR",
    "USD" to "$ USD",
    "EUR" to "€ EUR",
    "GBP" to "£ GBP",
    "AED" to "د.إ AED"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val user = uiState.user
    val haptic = LocalHapticFeedback.current

    // Form states initialized from user
    var name by remember(user?.name) { mutableStateOf(user?.displayName ?: "") }
    var username by remember(user?.username) { mutableStateOf(user?.username ?: "") }
    var selectedAvatar by remember(user?.avatar) { mutableStateOf(user?.avatar?.ifBlank { "zen_saver" } ?: "zen_saver") }
    var selectedCurrency by remember(user?.currency) { mutableStateOf(user?.currency ?: "INR") }
    var monthlyIncome by remember(user?.monthlyIncome) { 
        mutableStateOf(if ((user?.monthlyIncome ?: 0.0) > 0) user?.monthlyIncome?.toLong().toString() else "") 
    }
    val initialDob = remember(user?.dateOfBirth) { formatDobYMD(user?.dateOfBirth) }
    var dateOfBirth by remember(initialDob) { mutableStateOf(initialDob) }

    val email = user?.email?.ifBlank { "Not set" } ?: "Not set"
    val phone = user?.phoneNumber?.ifBlank { "Not linked" } ?: "Not linked"
    val streakCount = user?.streak ?: 1

    // Debounced username availability validation
    var usernameAvailable by remember { mutableStateOf<Boolean?>(null) }
    var usernameError by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val mimeType = context.contentResolver.getType(it) ?: "image/jpeg"
            val inputStream = context.contentResolver.openInputStream(it)
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            if (bytes != null) {
                val ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
                val fileName = "avatar_${System.currentTimeMillis()}.$ext"
                viewModel.uploadProfilePicture(bytes, fileName, mimeType)
            }
        }
    }

    LaunchedEffect(username) {
        val clean = username.trim().removePrefix("@").lowercase()
        val originalClean = user?.username?.trim()?.removePrefix("@")?.lowercase()
        
        if (clean == originalClean) {
            usernameAvailable = true
            usernameError = null
            return@LaunchedEffect
        }
        
        if (clean.length < 3) {
            usernameAvailable = null
            usernameError = if (clean.isNotEmpty()) "Min 3 characters" else null
            return@LaunchedEffect
        }

        delay(400) // 400ms debounce
        viewModel.checkUsername(clean) { available, err ->
            usernameAvailable = available
            usernameError = err
        }
    }

    // Has user changed anything?
    val hasChanges = remember(name, username, selectedAvatar, selectedCurrency, monthlyIncome, dateOfBirth, user, initialDob) {
        val cleanUser = user?.username?.trim()?.removePrefix("@")?.lowercase() ?: ""
        val cleanInput = username.trim().removePrefix("@").lowercase()
        val currentIncome = user?.monthlyIncome ?: 0.0
        val inputIncome = monthlyIncome.toDoubleOrNull() ?: 0.0

        name != (user?.displayName ?: "") ||
        cleanInput != cleanUser ||
        selectedAvatar != (user?.avatar ?: "zen_saver") ||
        selectedCurrency != (user?.currency ?: "INR") ||
        inputIncome != currentIncome ||
        formatDobYMD(dateOfBirth) != initialDob
    }

    val isDark = true

    WalletoBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Account & Identity",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    actions = {
                        // Streak Badge Pill
                        Box(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CategoryColors.Housing.copy(alpha = 0.15f))
                                .border(0.5.dp, CategoryColors.Housing.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "🔥 ${streakCount}d",
                                color = CategoryColors.Housing,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            bottomBar = {
                Surface(
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    BudgiPrimaryPillButton(
                        text = "Save Changes",
                        onClick = {
                            val cleanUser = username.trim().removePrefix("@").lowercase()
                            val incomeDouble = monthlyIncome.toDoubleOrNull()
                            viewModel.updateFullProfile(
                                name = name.trim(),
                                username = if (cleanUser.isNotBlank()) cleanUser else null,
                                dateOfBirth = if (dateOfBirth.isNotBlank()) formatDobYMD(dateOfBirth) else null,
                                avatar = selectedAvatar,
                                currency = selectedCurrency,
                                monthlyIncome = incomeDouble
                            )
                        },
                        isLoading = uiState.isUpdating,
                        enabled = hasChanges && (usernameError == null) && !uiState.isUpdating
                    )
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // ── 1. AVATAR SELECTION HERO ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier.size(114.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(108.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                            Color.Transparent
                                        )
                                    )
                                )
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    photoLauncher.launch("image/*")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val customPhoto = user?.profileImage
                            if (!customPhoto.isNullOrBlank()) {
                                AsyncImage(
                                    model = customPhoto,
                                    contentDescription = "Profile Picture",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                MoodAvatar(
                                    mood = selectedAvatar,
                                    modifier = Modifier.size(92.dp)
                                )
                            }
                        }

                        // Circular camera upload badge at bottom right
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 4.dp, bottom = 4.dp)
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    photoLauncher.launch("image/*")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoCamera,
                                contentDescription = "Upload Profile Photo",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Photo action buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                photoLauncher.launch("image/*")
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(BudgiRadius.Pill),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoCamera,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Upload Photo",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (!user?.profileImage.isNullOrBlank()) {
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.removeProfilePicture()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                shape = RoundedCornerShape(BudgiRadius.Pill),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Remove",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Text(
                        text = "OR CHOOSE AVATAR PRESET",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.2.sp
                    )

                    // Horizontal snap avatar choices
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        items(AVATAR_PRESETS) { (key, label) ->
                            val isSelected = selectedAvatar == key
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    )
                                    .border(
                                        if (isSelected) 1.5.dp else 0.5.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedAvatar = key
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(52.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    MoodAvatar(mood = key, modifier = Modifier.size(46.dp))
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // ── 2. PERSONAL IDENTITY SECTION ──
                Text(
                    "PERSONAL INFORMATION",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .border(
                            0.5.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Full Name Field
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Full Name",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        MinimalistTextField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = "First and Last Name"
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                    // Username Field with live validation feedback
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Unique Username",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (usernameAvailable == true && username.isNotBlank()) {
                                Text(
                                    "✓ Available",
                                    color = CategoryColors.Groceries,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else if (usernameError != null) {
                                Text(
                                    usernameError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        MinimalistTextField(
                            value = username,
                            onValueChange = { 
                                username = it.removePrefix("@").replace(" ", "").lowercase()
                            },
                            placeholder = "@handle"
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                    // Date of Birth Field
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Date of Birth (YYYY-MM-DD)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        MinimalistTextField(
                            value = dateOfBirth,
                            onValueChange = { input ->
                                val filtered = input.filter { it.isDigit() || it == '-' }.take(10)
                                dateOfBirth = filtered
                            },
                            placeholder = "2000-06-15"
                        )
                    }
                }

                // ── 3. FINANCIAL SETTINGS SECTION ──
                Text(
                    "FINANCIAL PREFERENCES",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .border(
                            0.5.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Currency Chips
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Primary Currency",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CURRENCY_PRESETS.forEach { (code, label) ->
                                val isSelected = selectedCurrency == code
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                        .border(
                                            0.5.dp,
                                            if (isSelected) Color.Transparent
                                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            selectedCurrency = code
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                    // Monthly Income / Target
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Monthly Budget / Income Target",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        MinimalistTextField(
                            value = monthlyIncome,
                            onValueChange = { 
                                if (it.all { char -> char.isDigit() }) monthlyIncome = it 
                            },
                            placeholder = "e.g. 50000"
                        )
                    }
                }

                // ── 4. CREDENTIALS & ACCOUNT SECURITY ──
                Text(
                    "ACCOUNT CREDENTIALS",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .border(
                            0.5.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AccountFieldItem("Email Address", email, Icons.Default.Email)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    AccountFieldItem("Linked Phone", phone, Icons.Default.Phone)
                }

                // ── 5. FEEDBACK MESSAGES ──
                if (uiState.message != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CategoryColors.Groceries.copy(alpha = 0.15f))
                            .border(1.dp, CategoryColors.Groceries.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            "✓ ${uiState.message}",
                            color = CategoryColors.Groceries,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (uiState.error != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            "⚠ ${uiState.error}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun AccountFieldItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun formatDobYMD(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val trimmed = raw.trim()
    val datePart = if (trimmed.contains("T")) trimmed.substringBefore("T")
                   else if (trimmed.contains(" ")) trimmed.substringBefore(" ")
                   else trimmed
    val parts = datePart.split("-")
    if (parts.size == 3) {
        val y = parts[0].padStart(4, '0')
        val m = parts[1].padStart(2, '0')
        val d = parts[2].padStart(2, '0')
        return "$y-$m-$d"
    }
    return if (datePart.length > 10) datePart.take(10) else datePart
}
