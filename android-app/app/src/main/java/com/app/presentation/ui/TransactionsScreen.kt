package com.app.presentation.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.domain.model.Transaction
import com.app.presentation.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextOverflow
import com.app.utils.DateUtils

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TransactionsScreen(
    windowSizeClass: WindowSizeClass,
    transactions: List<Transaction>,
    isLoading: Boolean,
    onClearActivity: () -> Unit,
    onAddTransaction: (Double, String, String, String, String, List<com.app.domain.model.SplitDetail>) -> Unit,
    onEditCategory: (transactionId: String, newCategory: String, applyToPast: Boolean, applyToFuture: Boolean) -> Unit = { _, _, _, _ -> },
    onScanReceipt: (java.io.File, (com.app.data.remote.dto.ScannedReceiptDto) -> Unit, (String) -> Unit) -> Unit = { _, _, _ -> },
    onImportCsv: (java.io.File, (com.app.data.remote.dto.CsvImportResultDto) -> Unit, (String) -> Unit) -> Unit = { _, _, _ -> }
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("All") }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showActionSheet by rememberSaveable { mutableStateOf(false) }
    var isProcessingFile by remember { mutableStateOf(false) }
    var processingMessage by remember { mutableStateOf("") }
    var scannedReceiptPrefill by remember { mutableStateOf<com.app.data.remote.dto.ScannedReceiptDto?>(null) }
    var dateFilter by remember { mutableStateOf(com.app.presentation.viewmodel.DateFilter.ALL_TIME) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var selectedDate by remember { mutableStateOf<java.time.LocalDate?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            val file = uriToFile(context, uri, "receipt_${System.currentTimeMillis()}.jpg")
            if (file != null) {
                showActionSheet = false
                isProcessingFile = true
                processingMessage = "Scanning receipt with OCR..."
                onScanReceipt(
                    file,
                    { dto ->
                        isProcessingFile = false
                        scannedReceiptPrefill = dto
                        showAddDialog = true
                    },
                    { errorMsg ->
                        isProcessingFile = false
                        android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    val csvLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            val file = uriToFile(context, uri, "statement_${System.currentTimeMillis()}.csv")
            if (file != null) {
                showActionSheet = false
                isProcessingFile = true
                processingMessage = "Importing bank statement CSV..."
                onImportCsv(
                    file,
                    { result ->
                        isProcessingFile = false
                        val msg = "Imported ${result.imported} transactions (${result.duplicates} skipped)"
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                    },
                    { errorMsg ->
                        isProcessingFile = false
                        android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    val listState = rememberLazyListState()

    // Scroll-aware top section (Dynamic Cash Flow / Spent Card + Daily Rail)
    var isSpentAndDayRailVisible by remember { mutableStateOf(true) }
    var previousFirstVisibleItemIndex by remember { mutableStateOf(0) }
    var previousFirstVisibleItemScrollOffset by remember { mutableStateOf(0) }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        val currentIndex = listState.firstVisibleItemIndex
        val currentOffset = listState.firstVisibleItemScrollOffset
        if (currentIndex == 0 && currentOffset <= 20) {
            isSpentAndDayRailVisible = true
        } else if (currentIndex > previousFirstVisibleItemIndex) {
            isSpentAndDayRailVisible = false
        } else if (currentIndex < previousFirstVisibleItemIndex) {
            isSpentAndDayRailVisible = true
        } else {
            val delta = currentOffset - previousFirstVisibleItemScrollOffset
            if (delta > 15) {
                isSpentAndDayRailVisible = false
            } else if (delta < -15) {
                isSpentAndDayRailVisible = true
            }
        }
        previousFirstVisibleItemIndex = currentIndex
        previousFirstVisibleItemScrollOffset = currentOffset
    }

    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            exportTransactionsToCsv(context, it, transactions)
        }
    }

    val isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
    val horizontalPadding = if (isExpanded) 32.dp else 20.dp

    // Categories with "All" first
    val categories = remember(transactions) {
        listOf("All") + transactions.map { it.category }.distinct()
    }

    // Filter transactions
    val filteredTransactions = remember(transactions, searchQuery, selectedCategory, dateFilter, selectedDate) {
        val now = java.time.LocalDate.now()
        val (startDate, endDate) = when (dateFilter) {
            com.app.presentation.viewmodel.DateFilter.THIS_WEEK -> {
                val start = now.minusDays(now.dayOfWeek.value.toLong() % 7)
                start to start.plusDays(6)
            }
            com.app.presentation.viewmodel.DateFilter.THIS_MONTH -> now.withDayOfMonth(1) to now.withDayOfMonth(now.lengthOfMonth())
            com.app.presentation.viewmodel.DateFilter.LAST_MONTH -> {
                val lastMonth = now.minusMonths(1)
                lastMonth.withDayOfMonth(1) to lastMonth.withDayOfMonth(lastMonth.lengthOfMonth())
            }
            com.app.presentation.viewmodel.DateFilter.LAST_3_MONTHS -> now.minusMonths(3).withDayOfMonth(1) to now.withDayOfMonth(now.lengthOfMonth())
            com.app.presentation.viewmodel.DateFilter.LAST_6_MONTHS -> now.minusMonths(6).withDayOfMonth(1) to now.withDayOfMonth(now.lengthOfMonth())
            com.app.presentation.viewmodel.DateFilter.ALL_TIME -> java.time.LocalDate.MIN to java.time.LocalDate.MAX
        }

        transactions.filter { tx ->
            val matchCategory = selectedCategory == "All" || tx.category.equals(selectedCategory, ignoreCase = true)
            val matchSearch = searchQuery.isBlank() ||
                    tx.merchant?.contains(searchQuery, ignoreCase = true) == true ||
                    tx.sender.contains(searchQuery, ignoreCase = true) ||
                    tx.category.contains(searchQuery, ignoreCase = true)

            val txDate = DateUtils.parseIsoDateToLocalDate(tx.date)
            val matchDate = if (selectedDate != null) {
                txDate == selectedDate
            } else {
                !txDate.isBefore(startDate) && !txDate.isAfter(endDate)
            }

            matchCategory && matchSearch && matchDate
        }
    }

    // Dynamic Cash Flow Stats for filtered range
    val totalSpent = remember(filteredTransactions) {
        filteredTransactions.filter { it.type == "expense" || it.type == "debit" }.sumOf { it.amount }
    }
    val totalIncome = remember(filteredTransactions) {
        filteredTransactions.filter { it.type == "income" || it.type == "credit" }.sumOf { it.amount }
    }
    val netCashFlow = totalIncome - totalSpent

    // ── Day Rail (Train) Precomputations ──
    val dailyStatsMap = remember(transactions) {
        val map = mutableMapOf<java.time.LocalDate, Triple<Double, Double, Int>>()
        transactions.forEach { tx ->
            val date = DateUtils.parseIsoDateToLocalDate(tx.date)
            val current = map[date] ?: Triple(0.0, 0.0, 0)
            val isExpense = tx.type == "expense" || tx.type == "debit"
            val isIncome = tx.type == "income" || tx.type == "credit"
            val newSpend = current.first + if (isExpense) tx.amount else 0.0
            val newIncome = current.second + if (isIncome) tx.amount else 0.0
            val newCount = current.third + 1
            map[date] = Triple(newSpend, newIncome, newCount)
        }
        map
    }

    val today = remember { java.time.LocalDate.now() }
    val todayIndex = 90
    // Cyclic rolling window: 90 days past, Today in center, 30 days future
    val recentDays = remember { (-90L..30L).map { java.time.LocalDate.now().plusDays(it) } }
    val dayRailState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (recentDays.isNotEmpty()) {
            dayRailState.scrollToItem((todayIndex - 2).coerceAtLeast(0))
        }
    }

    LaunchedEffect(selectedDate) {
        selectedDate?.let { sel ->
            val idx = recentDays.indexOf(sel)
            if (idx >= 0) {
                dayRailState.animateScrollToItem((idx - 2).coerceAtLeast(0))
            }
        }
    }

    // Grouping by natural friendly date headers
    val groupedTransactions = remember(filteredTransactions) {
        val today = java.time.LocalDate.now()
        val dtf = java.time.format.DateTimeFormatter.ofPattern("d MMM")
        val fullDtf = java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM")
        val yearDtf = java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy")

        filteredTransactions.groupBy { tx ->
            val localDate = DateUtils.parseIsoDateToLocalDate(tx.date)
            when {
                localDate == today -> "Today, ${localDate.format(dtf)}"
                localDate == today.minusDays(1) -> "Yesterday, ${localDate.format(dtf)}"
                localDate.year == today.year -> localDate.format(fullDtf)
                else -> localDate.format(yearDtf)
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = (selectedDate ?: java.time.LocalDate.now())
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    }
                    showDatePicker = false
                }) {
                    Text("Select", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text(
                    text = "Clear All Activity?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "This will permanently clear your local transaction logs. Are you sure?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmDialog = false
                        onClearActivity()
                    },
                    shape = RoundedCornerShape(Dimens.buttonCornerRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Clear All", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirmDialog = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    showActionSheet = true
                },
                modifier = Modifier.padding(bottom = Dimens.bottomDockHeight + 12.dp),
                shape = RoundedCornerShape(Dimens.buttonCornerRadius),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("Add", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .statusBarsPadding()
                .padding(top = 10.dp)
        ) {
            // ── Clean Header Row ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Activity",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${filteredTransactions.size} transactions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Export CSV Button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            exportLauncher.launch("budgi_transactions_${java.time.LocalDate.now()}.csv")
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FileDownload,
                            contentDescription = "Export to CSV",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Clear Activity Button
                    if (transactions.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearConfirmDialog = true },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Clear All Activity",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isSpentAndDayRailVisible,
                enter = expandVertically(
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.82f, stiffness = 420f)
                ) + fadeIn(animationSpec = androidx.compose.animation.core.tween(160)),
                exit = shrinkVertically(
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.82f, stiffness = 420f)
                ) + fadeOut(animationSpec = androidx.compose.animation.core.tween(120))
            ) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))

                    // ── Dynamic Cash Flow Quick Summary Card ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        0.8.dp,
                        if (isDark) DarkColors.Border else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(vertical = 14.dp, horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Total Spent
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "↓ Spent",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = CategoryColors.Food
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "₹${"%,.0f".format(totalSpent)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(30.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    )

                    // Total Income
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "↑ Income",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = CategoryColors.Groceries
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "₹${"%,.0f".format(totalIncome)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(30.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    )

                    // Net Flow
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Net Flow",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val netColor = if (netCashFlow >= 0) DarkColors.Green else MaterialTheme.colorScheme.error
                        val netSign = if (netCashFlow >= 0) "+" else "-"
                        Text(
                            text = "$netSign₹${"%,.0f".format(kotlin.math.abs(netCashFlow))}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = netColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── Day Rail (Cyclic Carousel with Top-Popping Today) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(106.dp)
                    .padding(top = 4.dp, bottom = 2.dp)
            ) {
                LazyRow(
                    state = dayRailState,
                    flingBehavior = rememberSnapFlingBehavior(lazyListState = dayRailState),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(start = horizontalPadding, end = horizontalPadding, top = 14.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    items(recentDays, key = { it.toString() }) { date ->
                        val isToday = date == today
                        val isSelected = selectedDate == date
                        val stats = dailyStatsMap[date]
                        val hasTx = stats != null && stats.third > 0
                        val spend = stats?.first ?: 0.0
                        val income = stats?.second ?: 0.0

                        // Fixed uniform width 54.dp prevents jitter and shifting during cyclic scrolling
                        // Today is the hero top-popping card with elevated height and offset
                        val cardHeight = when {
                            isToday -> 80.dp
                            isSelected -> 74.dp
                            else -> 66.dp
                        }
                        val cardOffsetY = when {
                            isToday -> (-8).dp
                            isSelected -> (-4).dp
                            else -> 0.dp
                        }
                        val cardElevation = when {
                            isToday -> 10.dp
                            isSelected -> 8.dp
                            else -> 0.dp
                        }

                        Box(
                            modifier = Modifier
                                .width(54.dp)
                                .height(cardHeight)
                                .offset(y = cardOffsetY)
                                .shadow(
                                    elevation = cardElevation,
                                    shape = RoundedCornerShape(18.dp),
                                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = if (isToday) 0.55f else 0.35f),
                                    ambientColor = Color.Black.copy(alpha = 0.3f)
                                )
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        isToday -> MaterialTheme.colorScheme.surface
                                        else -> MaterialTheme.colorScheme.surface
                                    }
                                )
                                .border(
                                    width = if (isToday || isSelected) 1.8.dp else 1.dp,
                                    color = when {
                                        isToday -> MaterialTheme.colorScheme.primary
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                    },
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .clickable {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    selectedDate = if (isSelected) null else date
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 5.dp)
                            ) {
                                // Top Micro-Badge: Today gets prominent "TODAY" pill badge; Selected gets "ACTIVE"; Regular gets Weekday "EEE"
                                if (isToday) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(BudgiRadius.Pill))
                                            .background(if (isSelected) Color.White else MaterialTheme.colorScheme.primary)
                                            .padding(horizontal = 6.dp, vertical = 1.5.dp)
                                    ) {
                                        Text(
                                            text = "TODAY",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.5.sp),
                                            fontWeight = FontWeight.Black,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                } else if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(BudgiRadius.Pill))
                                            .background(Color.White)
                                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                                    ) {
                                        Text(
                                            text = "ACTIVE",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.5.sp),
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF111827)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                } else {
                                    Text(
                                        text = date.format(java.time.format.DateTimeFormatter.ofPattern("EEE")).uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                }

                                Text(
                                    text = "${date.dayOfMonth}",
                                    style = if (isToday) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = when {
                                        isSelected -> Color.White
                                        isToday -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )

                                Spacer(modifier = Modifier.height(3.dp))

                                // Micro Activity Status Dots using Dashboard tokens
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (spend > 0) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) Color.White else MaterialTheme.colorScheme.error)
                                        )
                                    }
                                    if (income > 0) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) Color.White else DarkColors.Green)
                                        )
                                    }
                                    if (!hasTx) {
                                        Box(
                                            modifier = Modifier
                                                .size(4.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSelected) Color.White.copy(alpha = 0.35f)
                                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                                )
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

            // Daily Summary Strip when date is selected
            AnimatedVisibility(
                visible = selectedDate != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                selectedDate?.let { selDate ->
                    val stats = dailyStatsMap[selDate]
                    val selSpend = stats?.first ?: 0.0
                    val selIncome = stats?.second ?: 0.0
                    val selCount = stats?.third ?: 0
                    val dtfHeader = java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM")

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPadding, vertical = 6.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                RoundedCornerShape(14.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = selDate.format(dtfHeader),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (selSpend > 0) {
                                            Text(
                                                text = "-₹${"%,.0f".format(selSpend)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                        if (selIncome > 0) {
                                            Text(
                                                text = "+₹${"%,.0f".format(selIncome)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = DarkColors.Green
                                            )
                                        }
                                        Text(
                                            text = "$selCount txn${if (selCount != 1) "s" else ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    selectedDate = null
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear day filter",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── Minimalist Search Pill ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        0.8.dp,
                        if (isDark) DarkColors.Border else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        RoundedCornerShape(22.dp)
                    )
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search merchant, note, or category...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── Unified Dynamic Filter Row ──
            val hasActiveFilter = searchQuery.isNotBlank() ||
                    selectedCategory != "All" ||
                    dateFilter != com.app.presentation.viewmodel.DateFilter.ALL_TIME ||
                    selectedDate != null

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = horizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // If filters are active, show "Reset" chip first
                if (hasActiveFilter) {
                    item {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                                .clickable {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    searchQuery = ""
                                    selectedCategory = "All"
                                    dateFilter = com.app.presentation.viewmodel.DateFilter.ALL_TIME
                                    selectedDate = null
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Reset",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                // Date Filter Chips
                val dateFilters = listOf(
                    com.app.presentation.viewmodel.DateFilter.ALL_TIME to "All Time",
                    com.app.presentation.viewmodel.DateFilter.THIS_MONTH to "This Month",
                    com.app.presentation.viewmodel.DateFilter.THIS_WEEK to "This Week"
                )

                items(dateFilters) { (filter, label) ->
                    val isSelected = (dateFilter == filter && selectedDate == null)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surface
                            )
                            .border(
                                1.dp,
                                if (isSelected) Color.Transparent
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                dateFilter = filter
                                selectedDate = null
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Date Picker Chip
                item {
                    val isPicked = selectedDate != null
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isPicked) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surface
                            )
                            .border(
                                1.dp,
                                if (isPicked) Color.Transparent
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                if (isPicked) {
                                    selectedDate = null
                                } else {
                                    showDatePicker = true
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (selectedDate != null) {
                                    "📅 ${selectedDate!!.format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))}"
                                } else {
                                    "📅 Pick Date"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isPicked) FontWeight.Bold else FontWeight.Medium,
                                color = if (isPicked) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            if (isPicked) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear picked date",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }

                // Category Chips (BudGi Sleek Minimal Filter Pills)
                items(categories) { cat ->
                    val isSelected = selectedCategory.equals(cat, ignoreCase = true)
                    val emoji = if (cat == "All") "✦" else getCategoryEmoji(cat)
                    val catColor = if (cat == "All") MaterialTheme.colorScheme.primary else getCategoryColor(cat)

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (isSelected) catColor.copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            )
                            .border(
                                1.dp,
                                if (isSelected) catColor.copy(alpha = 0.55f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                CircleShape
                            )
                            .clickable {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                selectedCategory = cat
                            }
                            .padding(horizontal = 13.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "$emoji $cat",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) catColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── Grouped Activity Feed ──
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isLoading && filteredTransactions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = horizontalPadding),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        SkeletonList(count = 8)
                    }
                } else if (filteredTransactions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = horizontalPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "💳",
                                fontSize = 42.sp
                            )
                            Text(
                                text = if (searchQuery.isNotEmpty()) "No results for \"$searchQuery\""
                                else "No transactions found",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Try adjusting your dates or category filters.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (hasActiveFilter) {
                                val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                                Button(
                                    onClick = {
                                        searchQuery = ""
                                        selectedCategory = "All"
                                        dateFilter = com.app.presentation.viewmodel.DateFilter.ALL_TIME
                                        selectedDate = null
                                    },
                                    shape = RoundedCornerShape(BudgiRadius.Pill),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isDark) Color.White else Color(0xFF0F172A),
                                        contentColor = if (isDark) Color.Black else Color.White
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(0.dp),
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    Text("Reset Filters", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = horizontalPadding,
                            end = horizontalPadding,
                            top = 4.dp,
                            bottom = Dimens.bottomDockHeight + 64.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        groupedTransactions.forEach { (dateHeader, items) ->
                            val daySpent = items.filter { it.type == "expense" || it.type == "debit" }.sumOf { it.amount }

                            item(key = "header_$dateHeader") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 14.dp, bottom = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = dateHeader.uppercase(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (daySpent > 0) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surface)
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "Spent ₹${"%,.0f".format(daySpent)}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            items(items, key = { it.id }) { transaction ->
                                TransactionItem(
                                    merchant = transaction.merchant ?: transaction.sender,
                                    category = transaction.category,
                                    amount = "%,.0f".format(transaction.amount),
                                    date = DateUtils.formatDisplayTime(transaction.date, transaction.timestamp),
                                    type = transaction.type,
                                    splitDetails = transaction.splitDetails,
                                    categorySource = transaction.categorySource,
                                    isRecurring = transaction.isRecurring,
                                    isAnomaly = transaction.isAnomaly,
                                    onClick = { editingTransaction = transaction }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Transaction Dialog (Prefilled if coming from OCR)
    if (showAddDialog) {
        val initialAmount = scannedReceiptPrefill?.amount?.let { if (it > 0) "%,.0f".format(it).replace(",", "") else "" } ?: ""
        val initialMerchant = scannedReceiptPrefill?.merchant ?: ""
        val initialCategory = scannedReceiptPrefill?.category ?: "Food & Dining"
        val initialDate = scannedReceiptPrefill?.date ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        AddTransactionDialog(
            availableCategories = categories.filter { it != "All" },
            initialAmount = initialAmount,
            initialMerchant = initialMerchant,
            initialCategory = initialCategory,
            initialDate = initialDate,
            onDismiss = {
                showAddDialog = false
                scannedReceiptPrefill = null
            },
            onConfirm = { amount, type, category, merchant, date, splitDetails ->
                onAddTransaction(amount, type, category, merchant, date, splitDetails)
                showAddDialog = false
                scannedReceiptPrefill = null
            }
        )
    }

    // Action Bottom Sheet for Manual / OCR / CSV
    if (showActionSheet) {
        TransactionActionBottomSheet(
            onDismiss = { showActionSheet = false },
            onManualClick = {
                showActionSheet = false
                scannedReceiptPrefill = null
                showAddDialog = true
            },
            onScanReceiptClick = {
                galleryLauncher.launch("image/*")
            },
            onImportCsvClick = {
                csvLauncher.launch("*/*")
            }
        )
    }

    // Background OCR / CSV Processing Overlay Dialog
    if (isProcessingFile) {
        androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (isDark) Color(0xFF13161C) else MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    0.8.dp,
                    if (isDark) Color(0xFF282D3C) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                        strokeWidth = 2.5.dp
                    )
                    Text(
                        text = processingMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    // Category Edit Subscreen
    editingTransaction?.let { tx ->
        CategoryEditDialog(
            transaction = tx,
            availableCategories = categories,
            onDismiss = { editingTransaction = null },
            onConfirm = { newCategory, applyToPast, applyToFuture ->
                onEditCategory(tx.id, newCategory, applyToPast, applyToFuture)
                editingTransaction = null
            }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    availableCategories: List<String> = emptyList(),
    initialAmount: String = "",
    initialType: String = "expense",
    initialCategory: String = "Food & Dining",
    initialMerchant: String = "",
    initialDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
    onDismiss: () -> Unit,
    onConfirm: (Double, String, String, String, String, List<com.app.domain.model.SplitDetail>) -> Unit
) {
    var amount by rememberSaveable { mutableStateOf(initialAmount) }
    var type by rememberSaveable { mutableStateOf(initialType) } // "expense" or "income"
    var category by rememberSaveable { mutableStateOf(initialCategory) }
    var merchant by rememberSaveable { mutableStateOf(initialMerchant) }
    var paymentMethod by rememberSaveable { mutableStateOf("UPI") }
    var selectedDateStr by rememberSaveable { 
        mutableStateOf(initialDate) 
    }
    var splitDetails by rememberSaveable { mutableStateOf(listOf<com.app.domain.model.SplitDetail>()) }
    var showSplits by rememberSaveable { mutableStateOf(false) }

    var isCustomCategoryMode by rememberSaveable { mutableStateOf(false) }
    var customCategoryText by rememberSaveable { mutableStateOf("") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var methodExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val defaultCategories = listOf(
        "Food & Dining", "Transport", "Shopping", "Entertainment",
        "Health & Fitness", "Bills & Utilities", "Education", "Travel", "Groceries", "Others"
    )
    val categories = remember(availableCategories) {
        (availableCategories + defaultCategories).filter { it.isNotBlank() && it != "All" }.distinct()
    }
    val paymentMethods = listOf("UPI", "Credit Card", "Debit Card", "Net Banking", "Cash")

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.of("UTC"))
                            .toLocalDate()
                        selectedDateStr = date.toString()
                    }
                    showDatePicker = false
                }) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Subtle ambient glows
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .offset(x = (-60).dp, y = (-40).dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 80.dp, y = 60.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
                                Color.Transparent
                            )
                        )
                    )
            )

            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(scrollState)
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // Top Bar with Back Arrow and Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onDismiss()
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = if (type == "expense") "Add Expense" else "Add Income",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Segmented Toggle: [Expense] [Income]
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                        .padding(4.dp)
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Expense Tab
                        val isExpense = type == "expense"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isExpense) {
                                        Brush.horizontalGradient(
                                            colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                                        )
                                    } else SolidColor(Color.Transparent)
                                )
                                .clickable {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    type = "expense"
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Expense",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isExpense) FontWeight.Bold else FontWeight.Medium,
                                color = if (isExpense) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Income Tab
                        val isIncome = type == "income"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isIncome) {
                                        Brush.horizontalGradient(
                                            colors = listOf(CategoryColors.Groceries, CategoryColors.Groceries.copy(alpha = 0.85f))
                                        )
                                    } else SolidColor(Color.Transparent)
                                )
                                .clickable {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    type = "income"
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Income",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isIncome) FontWeight.Bold else FontWeight.Medium,
                                color = if (isIncome) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Section: Amount
                Text(
                    text = "Amount",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "₹ ",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        androidx.compose.foundation.text.BasicTextField(
                            value = amount,
                            onValueChange = { input ->
                                amount = input.filter { it.isDigit() || it == '.' }
                            },
                            textStyle = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { innerTextField ->
                                if (amount.isEmpty()) {
                                    Text(
                                        text = "850",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Section: Category
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(
                        onClick = {
                            isCustomCategoryMode = !isCustomCategoryMode
                            if (!isCustomCategoryMode && customCategoryText.isNotBlank()) {
                                category = customCategoryText.trim()
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isCustomCategoryMode) "Pick from list" else "+ Custom",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (isCustomCategoryMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = customCategoryText,
                            onValueChange = {
                                customCategoryText = it
                                category = it.ifBlank { "Others" }
                            },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (customCategoryText.isEmpty()) {
                                    Text(
                                        text = "Enter custom category (e.g. Pet Care, Gym)...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                                inner()
                            }
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable { categoryExpanded = !categoryExpanded }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val catIcon = when {
                                    category.contains("Transport", true) -> Icons.Default.DirectionsCar
                                    category.contains("Food", true) -> Icons.Default.Restaurant
                                    category.contains("Shopping", true) -> Icons.Default.ShoppingBag
                                    category.contains("Entertainment", true) -> Icons.Outlined.SportsEsports
                                    category.contains("Health", true) -> Icons.Default.Favorite
                                    else -> Icons.Default.Category
                                }
                                Icon(
                                    imageVector = catIcon,
                                    contentDescription = category,
                                    tint = getCategoryColor(category),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = category,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Dropdown",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat, fontWeight = FontWeight.Medium) },
                                    onClick = {
                                        category = cat
                                        categoryExpanded = false
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "+ Add Custom Category...",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    isCustomCategoryMode = true
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Section: Date
                Text(
                    text = "Date",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            RoundedCornerShape(20.dp)
                        )
                        .clickable { showDatePicker = true }
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = "Date",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = selectedDateStr,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Select Date",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Section: Payment Method
                Text(
                    text = "Payment Method",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { methodExpanded = !methodExpanded }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = "Method",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = paymentMethod,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Dropdown",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = methodExpanded,
                        onDismissRequest = { methodExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        paymentMethods.forEach { meth ->
                            DropdownMenuItem(
                                text = { Text(meth, fontWeight = FontWeight.Medium) },
                                onClick = {
                                    paymentMethod = meth
                                    methodExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Section: Note (Optional)
                Text(
                    text = "Note (Optional)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = merchant,
                        onValueChange = { merchant = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { innerTextField ->
                            if (merchant.isEmpty()) {
                                Text(
                                    text = "Add a note or merchant...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                            innerTextField()
                        }
                    )
                }

                // Optional: Split with friends expander
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSplits = !showSplits }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GroupAdd,
                            contentDescription = "Split",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Split with friends",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Icon(
                        imageVector = if (showSplits) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle split",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (showSplits) {
                    Spacer(modifier = Modifier.height(8.dp))
                    IconButton(
                        onClick = { splitDetails = splitDetails + com.app.domain.model.SplitDetail("", 0.0) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = "Add Person", tint = MaterialTheme.colorScheme.primary)
                    }
                    splitDetails.forEachIndexed { index, split ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = split.personName,
                                onValueChange = { name ->
                                    splitDetails = splitDetails.toMutableList().also { it[index] = it[index].copy(personName = name) }
                                },
                                placeholder = { Text("Name") },
                                modifier = Modifier.weight(1.5f),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp)
                            )
                            OutlinedTextField(
                                value = if (split.amount == 0.0) "" else split.amount.toString(),
                                onValueChange = { amt ->
                                    splitDetails = splitDetails.toMutableList().also { it[index] = it[index].copy(amount = amt.toDoubleOrNull() ?: 0.0) }
                                },
                                placeholder = { Text("₹ Amt") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp)
                            )
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        splitDetails = splitDetails.toMutableList().also { it[index] = it[index].copy(settled = !it[index].settled) }
                                    },
                                color = if (split.settled) CategoryColors.Groceries.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = if (split.settled) "✓ Paid" else "Unpaid",
                                    color = if (split.settled) CategoryColors.Groceries else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                                )
                            }
                            IconButton(onClick = { splitDetails = splitDetails.toMutableList().also { it.removeAt(index) } }) {
                                Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Bottom CTA Button: Add Transaction / Save Expense
                val parsedAmount = amount.toDoubleOrNull() ?: 0.0
                val canSubmit = parsedAmount > 0.0

                BudgiPrimaryPillButton(
                    text = if (type == "expense") "Save Expense" else "Add Income",
                    enabled = canSubmit,
                    onClick = {
                            val finalCategory = if (isCustomCategoryMode && customCategoryText.isNotBlank()) customCategoryText.trim() else category
                            val finalMerchant = merchant.ifBlank { finalCategory }
                            onConfirm(
                                parsedAmount,
                                type,
                                finalCategory,
                                finalMerchant,
                                selectedDateStr,
                                splitDetails.filter { it.personName.isNotBlank() && it.amount > 0 }
                            )
                        }
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

private fun exportTransactionsToCsv(context: android.content.Context, uri: android.net.Uri, transactions: List<Transaction>) {
    try {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            val writer = java.io.OutputStreamWriter(outputStream)
            writer.append("Date,Merchant,Category,Amount,Type,Reference\n")
            transactions.forEach { tx ->
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(DateUtils.parseIsoDateToMillis(tx.date)))
                val merchant = (tx.merchant ?: tx.sender).replace(",", " ")
                val category = tx.category.replace(",", " ")
                writer.append("$dateStr,$merchant,$category,${tx.amount},${tx.type},${tx.referenceNumber ?: ""}\n")
            }
            writer.flush()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun uriToFile(context: android.content.Context, uri: android.net.Uri, fileName: String): java.io.File? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val tempFile = java.io.File(context.cacheDir, fileName)
        tempFile.outputStream().use { outputStream ->
            inputStream.copyTo(outputStream)
        }
        tempFile
    } catch (_: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionActionBottomSheet(
    onDismiss: () -> Unit,
    onManualClick: () -> Unit,
    onScanReceiptClick: () -> Unit,
    onImportCsvClick: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) Color(0xFF13161C) else MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isDark) Color(0xFF282D3C) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Add Transaction",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Select your preferred input method",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 1. Manual Entry Option
                TransactionActionOptionItem(
                    icon = Icons.Outlined.Edit,
                    title = "Manual Entry",
                    subtitle = "Log an expense or income with custom details",
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onManualClick()
                    }
                )

                // 2. Scan Receipt Option
                TransactionActionOptionItem(
                    icon = Icons.Outlined.Receipt,
                    title = "Scan Receipt",
                    subtitle = "AI OCR parses total, merchant & category from photo",
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onScanReceiptClick()
                    }
                )

                // 3. Import CSV Statement Option
                TransactionActionOptionItem(
                    icon = Icons.Default.FileUpload,
                    title = "Import Bank Statement",
                    subtitle = "Bulk import transaction history from a CSV file",
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onImportCsvClick()
                    }
                )
            }
        }
    }
}

@Composable
private fun TransactionActionOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isDark) Color(0xFF181C26) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(
            0.8.dp,
            if (isDark) Color(0xFF282D3C) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) Color(0xFF222632) else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = if (isDark) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
