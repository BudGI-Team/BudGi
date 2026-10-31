package com.app.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.data.local.dao.DetectedBankSummary
import com.app.presentation.viewmodel.BanksViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BanksScreen(
    onBack: () -> Unit,
    viewModel: BanksViewModel = hiltViewModel()
) {
    val detectedBanks by viewModel.detectedBanks.collectAsStateWithLifecycle()
    val totalLiquidity by viewModel.totalLiquidity.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    WalletoBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            CenterAlignedTopAppBar(
                title = { Text("Connected Accounts", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshAccounts() },
                        enabled = !uiState.isSyncing
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Sync Accounts",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                // ── Hero Liquidity Matrix Card ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF0F172A),
                                    Color(0xFF1E293B)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF10B981).copy(alpha = 0.5f),
                                    Color(0xFF06B6D4).copy(alpha = 0.2f)
                                )
                            ),
                            RoundedCornerShape(24.dp)
                        )
                        .padding(22.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "TOTAL LIQUID BALANCE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    letterSpacing = 1.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color(0xFF10B981)
                            )
                            Surface(
                                color = Color(0xFF10B981).copy(alpha = 0.15f),
                                shape = CircleShape
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        "LIVE SMS PASSBOOK",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color(0xFF10B981)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "₹${"%,.2f".format(totalLiquidity)}",
                            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp),
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Aggregated in real-time from bank debit/credit balance alerts",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(26.dp))

                Text(
                    text = "AUTO-DISCOVERED BANK ACCOUNTS",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.sp,
                        letterSpacing = 0.8.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                if (detectedBanks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                RoundedCornerShape(20.dp)
                            )
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(20.dp)
                            )
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "No Bank Accounts Discovered Yet",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Sync your transaction SMS to automatically populate your bank passbook",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        detectedBanks.forEach { bank ->
                            BankAccountCard(bank)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // ── Privacy & Security Guarantee Banner ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                            RoundedCornerShape(16.dp)
                        )
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Security,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "100% on-device passbook. BudGi reconstructs balances directly from bank SMS alerts with zero login credentials required.",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun BankAccountCard(bank: DetectedBankSummary) {
    val bankName = resolveBankDisplayName(bank.sender)
    val lastUpdated = try {
        Instant.ofEpochMilli(bank.latestTimestamp)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd MMM, hh:mm a"))
    } catch (_: Exception) { "Recent" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                RoundedCornerShape(18.dp)
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                RoundedCornerShape(18.dp)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AccountBalance,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bankName,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "A/c •••• ${bank.accountLast4}  •  ${bank.transactionCount} transactions",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Updated $lastUpdated",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        if (bank.latestBalance != null && bank.latestBalance >= 0.0) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "₹${"%,.0f".format(bank.latestBalance)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10B981)
                )
                Text(
                    text = "Available",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun resolveBankDisplayName(sender: String): String {
    val upper = sender.uppercase()
    return when {
        upper.contains("HDFC") -> "HDFC Bank"
        upper.contains("SBI") -> "State Bank of India"
        upper.contains("ICICI") -> "ICICI Bank"
        upper.contains("AXIS") -> "Axis Bank"
        upper.contains("KOTAK") -> "Kotak Mahindra Bank"
        upper.contains("BOB") || upper.contains("BARODA") -> "Bank of Baroda"
        upper.contains("PUNB") || upper.contains("PNB") -> "Punjab National Bank"
        upper.contains("CAN") || upper.contains("CANARA") -> "Canara Bank"
        upper.contains("UNION") || upper.contains("UBI") -> "Union Bank of India"
        upper.contains("INDUS") || upper.contains("IND") -> "IndusInd Bank"
        upper.contains("YES") -> "Yes Bank"
        upper.contains("FED") || upper.contains("FEDERAL") -> "Federal Bank"
        upper.contains("IDBI") -> "IDBI Bank"
        upper.contains("JUPITER") || upper.contains("JUP") -> "Jupiter Money"
        upper.contains("FI") || upper.contains("FIBANK") -> "Fi Money"
        upper.contains("SLICE") -> "Slice"
        upper.contains("PAYTM") -> "Paytm Payments Bank"
        upper.contains("AIRTEL") -> "Airtel Payments Bank"
        else -> if (sender.isNotBlank()) "$sender Bank" else "Savings Account"
    }
}
