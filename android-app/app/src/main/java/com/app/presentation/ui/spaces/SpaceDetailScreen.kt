package com.app.presentation.ui.spaces

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.presentation.ui.WalletoBackground
import com.app.presentation.ui.theme.BudgiRadius
import com.app.presentation.ui.theme.DarkColors
import com.app.presentation.ui.theme.Dimens
import com.app.presentation.viewmodel.SpacesViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.math.abs
import kotlin.math.roundToInt

data class SpaceMember(
    val id: String,
    val name: String,
    val role: String = "MEMBER",
    val email: String = ""
)

data class SpaceExpenseItem(
    val id: String,
    val description: String,
    val amount: Double,
    val paidById: String,
    val paidByName: String,
    val splitWithMemberIds: List<String>,
    val category: String,
    val date: String,
    val timestamp: Long,
    val isSettlement: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceDetailScreen(
    spaceId: String,
    windowSizeClass: WindowSizeClass,
    onBack: () -> Unit,
    onInvite: () -> Unit,
    viewModel: SpacesViewModel = hiltViewModel()
) {
    val space by viewModel.getSpaceById(spaceId).collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    var selectedTab by remember { mutableIntStateOf(0) }

    // Dialog state
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showSettleUpDialog by remember { mutableStateOf(false) }
    var deleteExpenseTarget by remember { mutableStateOf<SpaceExpenseItem?>(null) }
    var showAddGoalDialog by remember { mutableStateOf(false) }
    var contributeGoalTarget by remember { mutableStateOf<Pair<String, Double>?>(null) }
    var deleteGoalTarget by remember { mutableStateOf<String?>(null) }
    var showDeleteSpaceConfirm by remember { mutableStateOf(false) }
    var showCodeCopiedToast by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(showCodeCopiedToast) {
        if (showCodeCopiedToast) {
            snackbarHostState.showSnackbar("Invite code copied to clipboard!")
            showCodeCopiedToast = false
        }
    }

    val onShareCode: (String, String) -> Unit = { code, spaceName ->
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(
                Intent.EXTRA_TEXT,
                "Join my shared space \"$spaceName\" on BudGi! Use invite code: $code"
            )
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share Invite Code")
        context.startActivity(shareIntent)
    }

    WalletoBackground {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = space?.name ?: "Space Details",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    actions = {
                        space?.let { currentSpace ->
                            IconButton(onClick = {
                                if (currentSpace.inviteCode.isNotBlank()) {
                                    onShareCode(currentSpace.inviteCode, currentSpace.name)
                                } else {
                                    onInvite()
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share Invite Link",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        IconButton(onClick = { showDeleteSpaceConfirm = true }) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete Space",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                if (space != null) {
                    when (selectedTab) {
                        0 -> {
                            FloatingActionButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showAddExpenseDialog = true
                                },
                                modifier = Modifier.padding(bottom = Dimens.bottomDockHeight + 12.dp),
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                shape = RoundedCornerShape(BudgiRadius.Pill),
                                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Split Expense", modifier = Modifier.size(20.dp))
                                    Text("Split Expense", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        1 -> {
                            FloatingActionButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showAddGoalDialog = true
                                },
                                modifier = Modifier.padding(bottom = Dimens.bottomDockHeight + 12.dp),
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                shape = RoundedCornerShape(BudgiRadius.Pill),
                                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add Goal", modifier = Modifier.size(20.dp))
                                    Text("Add Goal", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        else -> Unit
                    }
                }
            },
            containerColor = Color.Transparent
        ) { padding ->
            if (space == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                val currentSpace = space!!
                val gson = remember { Gson() }
                val mapType = remember { object : TypeToken<List<Map<String, Any>>>() {}.type }

                // Parse Members
                val parsedMembers: List<SpaceMember> = remember(currentSpace.membersJson) {
                    val list = mutableListOf<SpaceMember>()
                    try {
                        val rawList = gson.fromJson<List<Map<String, Any>>>(currentSpace.membersJson, mapType) ?: emptyList()
                        rawList.forEach { m ->
                            val role = m["role"] as? String ?: "MEMBER"
                            val userMap = m["userId"] as? Map<*, *>
                            val id = (userMap?.get("id") as? String)
                                ?: (userMap?.get("_id") as? String)
                                ?: (m["id"] as? String)
                                ?: (m["_id"] as? String)
                                ?: ""
                            val fName = userMap?.get("firstName") as? String ?: ""
                            val lName = userMap?.get("lastName") as? String ?: ""
                            val name = when {
                                fName.isNotBlank() || lName.isNotBlank() -> "$fName $lName".trim()
                                userMap?.get("name") is String -> userMap["name"] as String
                                m["name"] is String -> m["name"] as String
                                else -> "Member"
                            }
                            val email = (userMap?.get("email") as? String) ?: (m["email"] as? String) ?: ""
                            if (id.isNotBlank() || name.isNotBlank()) {
                                list.add(SpaceMember(id = id.ifBlank { name }, name = name, role = role, email = email))
                            }
                        }
                    } catch (_: Exception) {}
                    list
                }

                val effectiveMembers = remember(parsedMembers, currentUser) {
                    if (parsedMembers.isEmpty()) {
                        listOf(
                            SpaceMember(
                                id = currentUser?.uid ?: "user_me",
                                name = currentUser?.name?.ifBlank { "You" } ?: "You",
                                role = "OWNER",
                                email = currentUser?.email ?: ""
                            )
                        )
                    } else {
                        parsedMembers
                    }
                }

                // Identify current user's member ID in this space
                val currentMember = remember(effectiveMembers, currentUser) {
                    effectiveMembers.find { m ->
                        (currentUser?.uid != null && m.id == currentUser?.uid) ||
                        (currentUser?.email != null && m.email.isNotBlank() && m.email.equals(currentUser?.email, ignoreCase = true)) ||
                        (currentUser?.name != null && m.name.isNotBlank() && m.name.equals(currentUser?.name, ignoreCase = true))
                    } ?: effectiveMembers.first()
                }

                // Parse Expenses
                val expenses: List<SpaceExpenseItem> = remember(currentSpace.expensesJson) {
                    try {
                        val rawList = gson.fromJson<List<Map<String, Any>>>(currentSpace.expensesJson, mapType) ?: emptyList()
                        rawList.mapNotNull { exp ->
                            val id = exp["id"] as? String ?: return@mapNotNull null
                            val desc = exp["description"] as? String ?: "Expense"
                            val amt = (exp["amount"] as? Number)?.toDouble() ?: 0.0
                            val pId = exp["paidById"] as? String ?: ""
                            val pName = exp["paidByName"] as? String ?: "Someone"
                            val splitList = (exp["splitWithMemberIds"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                            val cat = exp["category"] as? String ?: "General"
                            val dt = exp["date"] as? String ?: ""
                            val ts = (exp["timestamp"] as? Number)?.toLong() ?: 0L
                            val isSettle = exp["isSettlement"] as? Boolean ?: false
                            SpaceExpenseItem(id, desc, amt, pId, pName, splitList, cat, dt, ts, isSettle)
                        }
                    } catch (_: Exception) {
                        emptyList()
                    }
                }

                // Parse Goals
                val goals: List<Map<String, Any>> = remember(currentSpace.goalsJson) {
                    try { gson.fromJson(currentSpace.goalsJson, mapType) ?: emptyList() } catch (_: Exception) { emptyList() }
                }

                val totalSaved = remember(goals) {
                    goals.sumOf { (it["currentAmount"] as? Number)?.toDouble() ?: 0.0 }
                }
                val totalTarget = remember(goals) {
                    goals.sumOf { (it["targetAmount"] as? Number)?.toDouble() ?: 0.0 }
                }
                val overallProgress = if (totalTarget > 0) (totalSaved / totalTarget).toFloat().coerceIn(0f, 1f) else 0f

                // Financial calculations for user
                val totalGroupSpent = remember(expenses) {
                    expenses.filterNot { it.isSettlement }.sumOf { it.amount }
                }
                val myNetBalance = remember(expenses, currentMember, effectiveMembers) {
                    calculateMemberBalance(currentMember.id, expenses, effectiveMembers)
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = Dimens.bottomDockHeight + 84.dp)
                ) {
                    // Segmented Tabs Header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(BudgiRadius.Pill))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                    RoundedCornerShape(BudgiRadius.Pill)
                                )
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val tabs = listOf("Splits & Ledger", "Shared Goals", "Members & Code")
                            tabs.forEachIndexed { index, title ->
                                val isSelected = selectedTab == index
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(BudgiRadius.Pill))
                                        .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            selectedTab = index
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // TAB CONTENT
                    when (selectedTab) {
                        0 -> {
                            // ── TAB 0: SPLITS & LEDGER ──
                            // Net Balance Summary Card
                            item {
                                val isOwed = myNetBalance > 0.01
                                val owes = myNetBalance < -0.01
                                val statusColor = when {
                                    isOwed -> DarkColors.Green
                                    owes -> DarkColors.Red
                                    else -> Color(0xFF38BDF8)
                                }
                                val statusTitle = when {
                                    isOwed -> "YOU ARE OWED"
                                    owes -> "YOU OWE"
                                    else -> "ALL SETTLED UP"
                                }
                                val statusSubtitle = when {
                                    isOwed -> "Group members owe you in this space"
                                    owes -> "You have pending splits to pay back"
                                    else -> "Everyone is squared away ✨"
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(Dimens.cardCornerRadius))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(
                                            1.dp,
                                            statusColor.copy(alpha = 0.35f),
                                            RoundedCornerShape(Dimens.cardCornerRadius)
                                        )
                                        .padding(20.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .clip(CircleShape)
                                                            .background(statusColor)
                                                    )
                                                    Text(
                                                        text = statusTitle,
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 11.sp,
                                                            letterSpacing = 1.2.sp,
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        color = statusColor
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = when {
                                                        isOwed -> "+₹${"%,.0f".format(myNetBalance)}"
                                                        owes -> "-₹${"%,.0f".format(abs(myNetBalance))}"
                                                        else -> "₹0.00"
                                                    },
                                                    style = MaterialTheme.typography.headlineMedium,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = statusSubtitle,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            // Settle Up Icon Button / Pill
                                            OutlinedButton(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    showSettleUpDialog = true
                                                },
                                                shape = RoundedCornerShape(BudgiRadius.Pill),
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                                ),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.primary
                                                ),
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Bolt,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Settle Up", fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        // Total group spend metrics bar
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "Total Space Spend",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                text = "₹${"%,.0f".format(totalGroupSpent)}",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            // Expense Header
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                        Text(
                                            text = "LEDGER & EXPENSES (${expenses.size})",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 11.sp,
                                                letterSpacing = 1.2.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    TextButton(
                                        onClick = { showAddExpenseDialog = true },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Split Expense", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Expenses List / Empty State
                            if (expenses.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(Dimens.cardCornerRadius))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .border(
                                                1.dp,
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                                RoundedCornerShape(Dimens.cardCornerRadius)
                                            )
                                            .padding(28.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(54.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                            Text(
                                                text = "No Shared Expenses Yet",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Split dinner, travel, rent, or groceries effortlessly with real debt tracking.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Button(
                                                onClick = { showAddExpenseDialog = true },
                                                shape = RoundedCornerShape(BudgiRadius.Pill),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isDark) Color.White else Color(0xFF0F172A),
                                                    contentColor = if (isDark) Color.Black else Color.White
                                                ),
                                                elevation = ButtonDefaults.buttonElevation(0.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Add,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = if (isDark) Color.Black else Color.White
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    "Split First Expense",
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isDark) Color.Black else Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                items(expenses) { exp ->
                                    val isPayer = exp.paidById == currentMember.id
                                    val splitCount = if (exp.splitWithMemberIds.isNotEmpty()) exp.splitWithMemberIds.size else effectiveMembers.size
                                    val share = if (splitCount > 0) exp.amount / splitCount else 0.0
                                    val isIncluded = exp.splitWithMemberIds.isEmpty() || exp.splitWithMemberIds.contains(currentMember.id)

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(Dimens.cardCornerRadius))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .border(
                                                1.dp,
                                                if (exp.isSettlement) DarkColors.Green.copy(alpha = 0.35f)
                                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                                RoundedCornerShape(Dimens.cardCornerRadius)
                                            )
                                            .padding(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                // Category Icon
                                                Box(
                                                    modifier = Modifier
                                                        .size(42.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(
                                                            if (exp.isSettlement) DarkColors.Green.copy(alpha = 0.12f)
                                                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = getCategoryIcon(exp.category),
                                                        contentDescription = null,
                                                        tint = if (exp.isSettlement) DarkColors.Green else MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = exp.description,
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        if (exp.isSettlement) {
                                                            Surface(
                                                                color = DarkColors.Green.copy(alpha = 0.15f),
                                                                shape = RoundedCornerShape(6.dp)
                                                            ) {
                                                                Text(
                                                                    text = "SETTLED",
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                                                    color = DarkColors.Green
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = "Paid by ${if (isPayer) "You" else exp.paidByName} • ${exp.date}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }

                                            // Right Amount & Debt column
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(
                                                        text = "₹${"%,.0f".format(exp.amount)}",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    when {
                                                        exp.isSettlement -> {
                                                            Text(
                                                                text = if (isPayer) "You paid" else "Settlement",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = DarkColors.Green
                                                            )
                                                        }
                                                        isPayer -> {
                                                            val lent = if (isIncluded) (exp.amount - share) else exp.amount
                                                            Text(
                                                                text = if (lent > 0) "+₹${"%,.0f".format(lent)} (lent)" else "You paid for all",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = DarkColors.Green
                                                            )
                                                        }
                                                        isIncluded -> {
                                                            Text(
                                                                text = "-₹${"%,.0f".format(share)} (you owe)",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = DarkColors.Red
                                                            )
                                                        }
                                                        else -> {
                                                            Text(
                                                                text = "Not involved",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }

                                                IconButton(
                                                    onClick = { deleteExpenseTarget = exp },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Delete Expense",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            // ── TAB 1: SHARED GOALS ──
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(Dimens.cardCornerRadius))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                            RoundedCornerShape(Dimens.cardCornerRadius)
                                        )
                                        .padding(20.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "TOTAL SHARED SAVINGS",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 11.sp,
                                                        letterSpacing = 1.2.sp,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "₹${"%,.0f".format(totalSaved)}",
                                                    style = MaterialTheme.typography.headlineMedium,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }

                                            Button(
                                                onClick = { showAddGoalDialog = true },
                                                shape = RoundedCornerShape(BudgiRadius.Pill),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isDark) Color.White else Color(0xFF0F172A),
                                                    contentColor = if (isDark) Color.Black else Color.White
                                                ),
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                                elevation = ButtonDefaults.buttonElevation(0.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Add,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = if (isDark) Color.Black else Color.White
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    "New Goal",
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isDark) Color.Black else Color.White
                                                )
                                            }
                                        }

                                        if (totalTarget > 0) {
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = "Target: ₹${"%,.0f".format(totalTarget)}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        text = "${(overallProgress * 100).roundToInt()}% achieved",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                                LinearProgressIndicator(
                                                    progress = { overallProgress },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(8.dp)
                                                        .clip(RoundedCornerShape(4.dp)),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Shared Goals Header
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                        Text(
                                            text = "SHARED GOALS (${goals.size})",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 11.sp,
                                                letterSpacing = 1.2.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            if (goals.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(Dimens.cardCornerRadius))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .border(
                                                1.dp,
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                                RoundedCornerShape(Dimens.cardCornerRadius)
                                            )
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Savings,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(36.dp)
                                            )
                                            Text(
                                                text = "No Shared Goals Yet",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Set a goal for vacation, rent, or a joint savings milestone.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Button(
                                                onClick = { showAddGoalDialog = true },
                                                shape = RoundedCornerShape(BudgiRadius.Pill),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isDark) Color.White else Color(0xFF0F172A),
                                                    contentColor = if (isDark) Color.Black else Color.White
                                                ),
                                                elevation = ButtonDefaults.buttonElevation(0.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Add,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = if (isDark) Color.Black else Color.White
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    "Add Goal",
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isDark) Color.Black else Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                items(goals) { goal ->
                                    val title = goal["title"] as? String ?: "Goal"
                                    val current = (goal["currentAmount"] as? Number)?.toDouble() ?: 0.0
                                    val target = (goal["targetAmount"] as? Number)?.toDouble() ?: 0.0
                                    val isPending = goal["isPending"] as? Boolean ?: false
                                    val progress = if (target > 0) (current / target).toFloat().coerceIn(0f, 1f) else 0f
                                    val isCompleted = current >= target && target > 0

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(Dimens.cardCornerRadius))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .border(
                                                1.dp,
                                                if (isCompleted) DarkColors.Green.copy(alpha = 0.4f)
                                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                                RoundedCornerShape(Dimens.cardCornerRadius)
                                            )
                                            .padding(18.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        text = title,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (isPending) {
                                                        Surface(
                                                            color = MaterialTheme.colorScheme.errorContainer,
                                                            shape = RoundedCornerShape(6.dp)
                                                        ) {
                                                            Text(
                                                                text = "SYNCING",
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                                                color = MaterialTheme.colorScheme.onErrorContainer
                                                            )
                                                        }
                                                    }
                                                    if (isCompleted) {
                                                        Surface(
                                                            color = DarkColors.Green.copy(alpha = 0.15f),
                                                            shape = RoundedCornerShape(6.dp)
                                                        ) {
                                                            Text(
                                                                text = "REACHED",
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                                                color = DarkColors.Green
                                                            )
                                                        }
                                                    }
                                                }

                                                IconButton(
                                                    onClick = { deleteGoalTarget = title },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.DeleteOutline,
                                                        contentDescription = "Delete Goal",
                                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }

                                            LinearProgressIndicator(
                                                progress = { progress },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(8.dp)
                                                    .clip(RoundedCornerShape(4.dp)),
                                                color = if (isCompleted) DarkColors.Green else MaterialTheme.colorScheme.primary,
                                                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "₹${"%,.0f".format(current)} / ₹${"%,.0f".format(target)} (${(progress * 100).roundToInt()}%)",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )

                                                Button(
                                                    onClick = { contributeGoalTarget = title to target },
                                                    shape = RoundedCornerShape(BudgiRadius.Pill),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (isDark) Color.White else Color(0xFF0F172A),
                                                        contentColor = if (isDark) Color.Black else Color.White
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(32.dp),
                                                    elevation = ButtonDefaults.buttonElevation(0.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Add,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = if (isDark) Color.Black else Color.White
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        "Deposit",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isDark) Color.Black else Color.White
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        2 -> {
                            // ── TAB 2: MEMBERS & CODE ──
                            // Invite Code Card
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(Dimens.cardCornerRadius))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                            RoundedCornerShape(Dimens.cardCornerRadius)
                                        )
                                        .padding(20.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                        Text(
                                            text = "SPACE INVITE CODE",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 11.sp,
                                                letterSpacing = 1.2.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = currentSpace.inviteCode.ifBlank { "BUDGI-SPACE" },
                                            style = MaterialTheme.typography.headlineMedium.copy(
                                                letterSpacing = 3.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Share this code with friends, roommates, or partners to collaborate on expenses.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    clipboardManager.setText(AnnotatedString(currentSpace.inviteCode))
                                                    showCodeCopiedToast = true
                                                },
                                                shape = RoundedCornerShape(BudgiRadius.Pill),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isDark) Color.White else Color(0xFF0F172A),
                                                    contentColor = if (isDark) Color.Black else Color.White
                                                ),
                                                modifier = Modifier.weight(1f),
                                                elevation = ButtonDefaults.buttonElevation(0.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.ContentCopy,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = if (isDark) Color.Black else Color.White
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    "Copy Code",
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isDark) Color.Black else Color.White
                                                )
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    onShareCode(currentSpace.inviteCode, currentSpace.name)
                                                },
                                                shape = RoundedCornerShape(BudgiRadius.Pill),
                                                modifier = Modifier.weight(1f),
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                                )
                                            ) {
                                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Share Link", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            // Members List Header
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    Text(
                                        text = "MEMBERS & BALANCES (${effectiveMembers.size})",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            letterSpacing = 1.2.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Member Cards
                            items(effectiveMembers) { member ->
                                val memberBal = calculateMemberBalance(member.id, expenses, effectiveMembers)
                                val isMe = member.id == currentMember.id

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(Dimens.cardCornerRadius))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                            RoundedCornerShape(Dimens.cardCornerRadius)
                                        )
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = member.name.take(1).uppercase(),
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    fontSize = 15.sp
                                                )
                                            }

                                            Column {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = if (isMe) "${member.name} (You)" else member.name,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Surface(
                                                        color = if (member.role == "OWNER") MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                        else MaterialTheme.colorScheme.surfaceVariant,
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = if (member.role == "OWNER") "OWNER" else "MEMBER",
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                                            color = if (member.role == "OWNER") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                                if (member.email.isNotBlank()) {
                                                    Text(
                                                        text = member.email,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        // Member net balance in this group
                                        Column(horizontalAlignment = Alignment.End) {
                                            when {
                                                memberBal > 0.01 -> {
                                                    Text(
                                                        text = "+₹${"%,.0f".format(memberBal)}",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = DarkColors.Green
                                                    )
                                                    Text(
                                                        text = "gets back",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                        color = DarkColors.Green
                                                    )
                                                }
                                                memberBal < -0.01 -> {
                                                    Text(
                                                        text = "-₹${"%,.0f".format(abs(memberBal))}",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = DarkColors.Red
                                                    )
                                                    Text(
                                                        text = "owes group",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                        color = DarkColors.Red
                                                    )
                                                }
                                                else -> {
                                                    Text(
                                                        text = "Settled",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── DIALOGS ──

                // 1. Add Expense Dialog
                if (showAddExpenseDialog) {
                    var description by remember { mutableStateOf("") }
                    var amountText by remember { mutableStateOf("") }
                    var selectedCategory by remember { mutableStateOf("Food") }
                    var paidByMemberId by remember { mutableStateOf(currentMember.id) }
                    var selectedSplitMemberIds by remember {
                        mutableStateOf(effectiveMembers.map { it.id }.toSet())
                    }

                    val categories = listOf("Food", "Groceries", "Travel", "Rent", "Entertainment", "General")
                    val amountNum = amountText.toDoubleOrNull() ?: 0.0
                    val splitMembersCount = selectedSplitMemberIds.size
                    val perPersonAmt = if (splitMembersCount > 0 && amountNum > 0.0) amountNum / splitMembersCount else 0.0

                    AlertDialog(
                        onDismissRequest = { showAddExpenseDialog = false },
                        title = {
                            Text(
                                text = "Split New Expense",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        text = {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = description,
                                    onValueChange = { description = it },
                                    label = { Text("What was this for?") },
                                    placeholder = { Text("e.g. Dinner, Uber, Groceries") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = amountText,
                                    onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                    label = { Text("Total Amount (₹)") },
                                    placeholder = { Text("1200") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Category Chips
                                Text(
                                    text = "Category",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(categories) { cat ->
                                        val isSelected = selectedCategory == cat
                                        Surface(
                                            shape = RoundedCornerShape(BudgiRadius.Pill),
                                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                            ),
                                            modifier = Modifier.clickable { selectedCategory = cat }
                                        ) {
                                            Text(
                                                text = cat,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }

                                // Paid By Picker
                                Text(
                                    text = "Paid By",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(effectiveMembers) { m ->
                                        val isSelected = paidByMemberId == m.id
                                        Surface(
                                            shape = RoundedCornerShape(BudgiRadius.Pill),
                                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                            ),
                                            modifier = Modifier.clickable { paidByMemberId = m.id }
                                        ) {
                                            Text(
                                                text = if (m.id == currentMember.id) "You" else m.name,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }

                                // Split Members Multi-select
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Split Between (${selectedSplitMemberIds.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TextButton(
                                        onClick = {
                                            selectedSplitMemberIds = if (selectedSplitMemberIds.size == effectiveMembers.size) {
                                                setOf(currentMember.id)
                                            } else {
                                                effectiveMembers.map { it.id }.toSet()
                                            }
                                        },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(
                                            text = if (selectedSplitMemberIds.size == effectiveMembers.size) "Clear" else "Select All",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    effectiveMembers.forEach { m ->
                                        val isChecked = selectedSplitMemberIds.contains(m.id)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedSplitMemberIds = if (isChecked) {
                                                        if (selectedSplitMemberIds.size > 1) selectedSplitMemberIds - m.id else selectedSplitMemberIds
                                                    } else {
                                                        selectedSplitMemberIds + m.id
                                                    }
                                                }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { checked ->
                                                    selectedSplitMemberIds = if (checked) {
                                                        selectedSplitMemberIds + m.id
                                                    } else {
                                                        if (selectedSplitMemberIds.size > 1) selectedSplitMemberIds - m.id else selectedSplitMemberIds
                                                    }
                                                },
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Text(
                                                text = if (m.id == currentMember.id) "${m.name} (You)" else m.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }

                                // Calculation preview
                                if (amountNum > 0.0 && splitMembersCount > 0) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "₹${"%,.0f".format(perPersonAmt)} / person",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "$splitMembersCount people",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            val canSave = description.isNotBlank() && amountNum > 0.0 && selectedSplitMemberIds.isNotEmpty()
                            Button(
                                onClick = {
                                    if (canSave) {
                                        val payer = effectiveMembers.find { it.id == paidByMemberId } ?: currentMember
                                        viewModel.addExpense(
                                            spaceId = spaceId,
                                            description = description.trim(),
                                            amount = amountNum,
                                            paidById = payer.id,
                                            paidByName = payer.name,
                                            splitWithMemberIds = selectedSplitMemberIds.toList(),
                                            category = selectedCategory
                                        )
                                        showAddExpenseDialog = false
                                    }
                                },
                                enabled = canSave,
                                shape = RoundedCornerShape(BudgiRadius.Pill),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDark) Color.White else Color(0xFF0F172A),
                                    contentColor = if (isDark) Color.Black else Color.White
                                )
                            ) {
                                Text("Add Expense", fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showAddExpenseDialog = false }) {
                                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        shape = RoundedCornerShape(20.dp)
                    )
                }

                // 2. Settle Up Dialog
                if (showSettleUpDialog) {
                    var fromMemberId by remember { mutableStateOf(currentMember.id) }
                    var toMemberId by remember {
                        mutableStateOf(effectiveMembers.firstOrNull { it.id != currentMember.id }?.id ?: currentMember.id)
                    }
                    var settleAmountText by remember {
                        val initAmt = if (myNetBalance < -0.01) abs(myNetBalance).toString() else ""
                        mutableStateOf(initAmt)
                    }

                    val settleAmountNum = settleAmountText.toDoubleOrNull() ?: 0.0

                    AlertDialog(
                        onDismissRequest = { showSettleUpDialog = false },
                        title = {
                            Text(
                                text = "⚡ Record Settlement",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        text = {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Record a direct payment between space members to clear debts.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Text(
                                    text = "Who Paid?",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(effectiveMembers) { m ->
                                        val isSelected = fromMemberId == m.id
                                        Surface(
                                            shape = RoundedCornerShape(BudgiRadius.Pill),
                                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                            ),
                                            modifier = Modifier.clickable { fromMemberId = m.id }
                                        ) {
                                            Text(
                                                text = if (m.id == currentMember.id) "You" else m.name,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "To Whom?",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(effectiveMembers.filter { it.id != fromMemberId }) { m ->
                                        val isSelected = toMemberId == m.id
                                        Surface(
                                            shape = RoundedCornerShape(BudgiRadius.Pill),
                                            color = if (isSelected) DarkColors.Green.copy(alpha = 0.2f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isSelected) DarkColors.Green else Color.Transparent
                                            ),
                                            modifier = Modifier.clickable { toMemberId = m.id }
                                        ) {
                                            Text(
                                                text = if (m.id == currentMember.id) "You" else m.name,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) DarkColors.Green else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = settleAmountText,
                                    onValueChange = { settleAmountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                    label = { Text("Settlement Amount (₹)") },
                                    placeholder = { Text("500") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            val canSettle = settleAmountNum > 0.0 && fromMemberId != toMemberId
                            Button(
                                onClick = {
                                    if (canSettle) {
                                        val fromMem = effectiveMembers.find { it.id == fromMemberId } ?: currentMember
                                        val toMem = effectiveMembers.find { it.id == toMemberId } ?: effectiveMembers.first()
                                        viewModel.settleUp(
                                            spaceId = spaceId,
                                            fromMemberId = fromMem.id,
                                            fromMemberName = fromMem.name,
                                            toMemberId = toMem.id,
                                            toMemberName = toMem.name,
                                            amount = settleAmountNum
                                        )
                                        showSettleUpDialog = false
                                    }
                                },
                                enabled = canSettle,
                                shape = RoundedCornerShape(BudgiRadius.Pill),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDark) Color.White else Color(0xFF0F172A),
                                    contentColor = if (isDark) Color.Black else Color.White
                                )
                            ) {
                                Text("Record Settlement", fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showSettleUpDialog = false }) {
                                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        shape = RoundedCornerShape(20.dp)
                    )
                }

                // 3. Delete Expense Dialog
                deleteExpenseTarget?.let { targetExp ->
                    AlertDialog(
                        onDismissRequest = { deleteExpenseTarget = null },
                        title = {
                            Text(
                                text = "Delete Entry?",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        text = {
                            Text(
                                text = "Remove \"${targetExp.description}\" (₹${"%,.0f".format(targetExp.amount)}) from this space? Balances will be recalculated immediately.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.deleteExpense(spaceId, targetExp.id)
                                    deleteExpenseTarget = null
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                shape = RoundedCornerShape(Dimens.buttonCornerRadius)
                            ) {
                                Text("Delete", fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { deleteExpenseTarget = null }) {
                                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        shape = RoundedCornerShape(20.dp)
                    )
                }

                // 4. Add Goal Dialog
                if (showAddGoalDialog) {
                    var goalTitle by remember { mutableStateOf("") }
                    var goalTarget by remember { mutableStateOf("") }
                    val goalCategory by remember { mutableStateOf("Savings") }

                    AlertDialog(
                        onDismissRequest = { showAddGoalDialog = false },
                        title = {
                            Text(
                                text = "Add Shared Goal",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = goalTitle,
                                    onValueChange = { goalTitle = it },
                                    label = { Text("Goal Title") },
                                    placeholder = { Text("e.g. Vacation Fund, Groceries") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = goalTarget,
                                    onValueChange = { goalTarget = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                    label = { Text("Target Amount (₹)") },
                                    placeholder = { Text("50000") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            val canAdd = goalTitle.isNotBlank() && (goalTarget.toDoubleOrNull() ?: 0.0) > 0.0
                            Button(
                                onClick = {
                                    val amount = goalTarget.toDoubleOrNull() ?: 0.0
                                    if (goalTitle.isNotBlank() && amount > 0.0) {
                                        viewModel.addGoal(spaceId, goalTitle.trim(), amount, goalCategory)
                                        showAddGoalDialog = false
                                    }
                                },
                                enabled = canAdd,
                                shape = RoundedCornerShape(BudgiRadius.Pill),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDark) Color.White else Color(0xFF0F172A),
                                    contentColor = if (isDark) Color.Black else Color.White
                                )
                            ) {
                                Text("Add Goal", fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showAddGoalDialog = false }) {
                                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        shape = RoundedCornerShape(20.dp)
                    )
                }

                // 5. Contribute / Deposit Dialog
                contributeGoalTarget?.let { (goalTitle, _) ->
                    var depositAmount by remember { mutableStateOf("") }

                    AlertDialog(
                        onDismissRequest = { contributeGoalTarget = null },
                        title = {
                            Text(
                                text = "Deposit to $goalTitle",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Enter the contribution amount towards this shared goal.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = depositAmount,
                                    onValueChange = { depositAmount = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                    label = { Text("Contribution (₹)") },
                                    placeholder = { Text("2000") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            val canDeposit = (depositAmount.toDoubleOrNull() ?: 0.0) > 0.0
                            Button(
                                onClick = {
                                    val amt = depositAmount.toDoubleOrNull() ?: 0.0
                                    if (amt > 0.0) {
                                        viewModel.contributeToGoal(spaceId, goalTitle, amt)
                                        contributeGoalTarget = null
                                    }
                                },
                                enabled = canDeposit,
                                shape = RoundedCornerShape(BudgiRadius.Pill),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDark) Color.White else Color(0xFF0F172A),
                                    contentColor = if (isDark) Color.Black else Color.White
                                )
                            ) {
                                Text("Deposit", fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { contributeGoalTarget = null }) {
                                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        shape = RoundedCornerShape(20.dp)
                    )
                }

                // 6. Delete Goal Confirmation Dialog
                deleteGoalTarget?.let { goalTitle ->
                    AlertDialog(
                        onDismissRequest = { deleteGoalTarget = null },
                        title = {
                            Text(
                                text = "Delete Goal?",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        text = {
                            Text(
                                text = "Are you sure you want to remove \"$goalTitle\" from this space? This cannot be undone.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.deleteGoal(spaceId, goalTitle)
                                    deleteGoalTarget = null
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                shape = RoundedCornerShape(Dimens.buttonCornerRadius)
                            ) {
                                Text("Delete", fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { deleteGoalTarget = null }) {
                                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        shape = RoundedCornerShape(20.dp)
                    )
                }

                // 7. Delete Space Confirmation Dialog
                if (showDeleteSpaceConfirm) {
                    AlertDialog(
                        onDismissRequest = { showDeleteSpaceConfirm = false },
                        title = {
                            Text(
                                text = "Delete Shared Space?",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        text = {
                            Text(
                                text = "Are you sure you want to delete this shared space? All splits, goals, and history will be permanently deleted.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showDeleteSpaceConfirm = false
                                    viewModel.deleteSpace(spaceId) {
                                        onBack()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                shape = RoundedCornerShape(Dimens.buttonCornerRadius)
                            ) {
                                Text("Delete Space", fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteSpaceConfirm = false }) {
                                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }
        }
    }
}

// ── HELPER FUNCTIONS ──

fun calculateMemberBalance(
    memberId: String,
    expenses: List<SpaceExpenseItem>,
    effectiveMembers: List<SpaceMember>
): Double {
    var balance = 0.0
    expenses.forEach { exp ->
        val splitCount = if (exp.splitWithMemberIds.isNotEmpty()) exp.splitWithMemberIds.size else effectiveMembers.size
        val share = if (splitCount > 0) exp.amount / splitCount else 0.0
        val isIncluded = exp.splitWithMemberIds.isEmpty() || exp.splitWithMemberIds.contains(memberId)
        val isPayer = exp.paidById == memberId

        if (exp.isSettlement) {
            if (isPayer) {
                balance += exp.amount
            } else if (exp.splitWithMemberIds.contains(memberId)) {
                balance -= exp.amount
            }
        } else {
            if (isPayer) {
                if (isIncluded) {
                    balance += (exp.amount - share)
                } else {
                    balance += exp.amount
                }
            } else if (isIncluded) {
                balance -= share
            }
        }
    }
    return balance
}

fun getCategoryIcon(category: String): ImageVector {
    return when (category.lowercase()) {
        "food", "dining" -> Icons.Default.Restaurant
        "groceries", "grocery" -> Icons.Default.ShoppingCart
        "travel", "transit", "fuel" -> Icons.Default.DirectionsCar
        "rent", "home", "utilities" -> Icons.Default.Home
        "entertainment", "movie" -> Icons.Default.ConfirmationNumber
        "settlement" -> Icons.Default.CheckCircle
        else -> Icons.AutoMirrored.Filled.ReceiptLong
    }
}
