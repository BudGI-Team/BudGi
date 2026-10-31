package com.app.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(onBack: () -> Unit) {
    WalletoBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            CenterAlignedTopAppBar(
                title = { Text("Devices", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                Text("CURRENT DEVICE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val deviceModel = android.os.Build.MODEL
                    val osVersion = android.os.Build.VERSION.RELEASE

                    Icon(Icons.Default.Smartphone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(deviceModel, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
                        Text("Active Now • Android $osVersion", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("This Device", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text("PARSING & CATEGORIZATION ENGINES", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    EngineFeatureRow(
                        title = "Bank Template Engine",
                        subtitle = "15+ Indian Banks (HDFC, SBI, ICICI, Axis, Kotak, BOB, PNB)",
                        status = "Active",
                        badgeColor = Color(0xFF10B981)
                    )
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    EngineFeatureRow(
                        title = "UPI Merchant Intelligence",
                        subtitle = "300+ Verified Merchant Brands & QR Resolvers",
                        status = "Active",
                        badgeColor = Color(0xFF10B981)
                    )
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    EngineFeatureRow(
                        title = "Multi-Signal Categorizer",
                        subtitle = "Weighted keywords, time-of-day & amount brackets",
                        status = "Active",
                        badgeColor = Color(0xFF10B981)
                    )
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    EngineFeatureRow(
                        title = "Cross-App Payment Fusion",
                        subtitle = "GPay, Paytm, PhonePe, CRED auto-deduplication",
                        status = "Active",
                        badgeColor = Color(0xFF10B981)
                    )
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    EngineFeatureRow(
                        title = "100% On-Device Privacy",
                        subtitle = "Zero bank SMS or payment data leaves your device",
                        status = "Protected",
                        badgeColor = Color(0xFF3B82F6)
                    )
                }
            }
        }
    }
}

@Composable
private fun EngineFeatureRow(
    title: String,
    subtitle: String,
    status: String,
    badgeColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(
            color = badgeColor.copy(alpha = 0.12f),
            shape = androidx.compose.foundation.shape.CircleShape
        ) {
            Text(
                text = status.uppercase(),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = badgeColor
            )
        }
    }
}
