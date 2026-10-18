package com.app.utils

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

object DateUtils {

    fun parseIsoDateToMillis(dateStr: String): Long {
        if (dateStr.isBlank()) return System.currentTimeMillis()
        return try {
            Instant.parse(dateStr).toEpochMilli()
        } catch (e: Exception) {
            try {
                OffsetDateTime.parse(dateStr).toInstant().toEpochMilli()
            } catch (e2: Exception) {
                try {
                    LocalDateTime.parse(dateStr).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                } catch (e3: Exception) {
                    try {
                        LocalDate.parse(dateStr).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    } catch (e4: Exception) {
                        try {
                            // Extract YYYY-MM-DD prefix if present
                            if (dateStr.length >= 10 && dateStr[4] == '-' && dateStr[7] == '-') {
                                LocalDate.parse(dateStr.substring(0, 10)).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            } else {
                                System.currentTimeMillis()
                            }
                        } catch (e5: Exception) {
                            System.currentTimeMillis()
                        }
                    }
                }
            }
        }
    }

    fun parseIsoDateToLocalDate(dateStr: String): LocalDate {
        if (dateStr.isBlank()) return LocalDate.now()
        return try {
            LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: Exception) {
            try {
                Instant.parse(dateStr).atZone(ZoneId.systemDefault()).toLocalDate()
            } catch (e2: Exception) {
                try {
                    OffsetDateTime.parse(dateStr).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
                } catch (e3: Exception) {
                    try {
                        LocalDateTime.parse(dateStr).toLocalDate()
                    } catch (e4: Exception) {
                        if (dateStr.length >= 10 && dateStr[4] == '-' && dateStr[7] == '-') {
                            try {
                                LocalDate.parse(dateStr.substring(0, 10))
                            } catch (e5: Exception) {
                                LocalDate.now()
                            }
                        } else {
                            LocalDate.now()
                        }
                    }
                }
            }
        }
    }

    /**
     * Format an ISO date string or timestamp into a premium visual text like:
     * "Today", "Yesterday", "18 Jul", or "Jul 18, 2026".
     */
    fun formatDisplayDate(dateStr: String, timestamp: Long = 0): String {
        val targetLocalDate = if (dateStr.isNotBlank()) {
            parseIsoDateToLocalDate(dateStr)
        } else if (timestamp > 0) {
            Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        } else {
            LocalDate.now()
        }

        val today = LocalDate.now()
        val yesterday = today.minusDays(1)

        return when (targetLocalDate) {
            today -> "Today"
            yesterday -> "Yesterday"
            else -> {
                val currentYear = today.year
                val formatPattern = if (targetLocalDate.year == currentYear) {
                    "dd MMM"
                } else {
                    "dd MMM yyyy"
                }
                val date = Date.from(targetLocalDate.atStartOfDay(ZoneId.systemDefault()).toInstant())
                SimpleDateFormat(formatPattern, Locale.US).format(date)
            }
        }
    }

    fun formatDisplayTime(dateStr: String, timestamp: Long = 0): String {
        return try {
            val zone = ZoneId.systemDefault()

            // 1. If a valid positive timestamp is provided and not UTC midnight (epoch modulo day != 0)
            if (timestamp > 0) {
                val isMidnightUtc = (timestamp % 86400000L) == 0L
                if (!isMidnightUtc) {
                    val zdt = Instant.ofEpochMilli(timestamp).atZone(zone)
                    return DateTimeFormatter.ofPattern("hh:mm a", Locale.US).format(zdt)
                }
            }

            // 2. Parse from ISO string if it contains time ('T') and is not UTC midnight
            if (dateStr.isNotBlank() && dateStr.contains('T')) {
                val millis = parseIsoDateToMillis(dateStr)
                if (millis > 0) {
                    val isMidnightUtc = (millis % 86400000L) == 0L
                    if (!isMidnightUtc) {
                        val zdt = Instant.ofEpochMilli(millis).atZone(zone)
                        return DateTimeFormatter.ofPattern("hh:mm a", Locale.US).format(zdt)
                    }
                }
            }

            "—"
        } catch (e: Exception) {
            if (timestamp > 0) {
                try {
                    val isMidnightUtc = (timestamp % 86400000L) == 0L
                    if (!isMidnightUtc) {
                        SimpleDateFormat("hh:mm a", Locale.US).format(Date(timestamp))
                    } else {
                        "—"
                    }
                } catch (_: Exception) {
                    "—"
                }
            } else {
                "—"
            }
        }
    }
}
