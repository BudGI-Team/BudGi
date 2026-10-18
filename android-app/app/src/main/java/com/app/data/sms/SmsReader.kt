package com.app.data.sms

import android.content.ContentResolver
import android.provider.Telephony
import javax.inject.Inject

data class RawSms(
    val body: String,
    val sender: String,
    val timestamp: Long
)

class SmsReader @Inject constructor(
    private val contentResolver: ContentResolver
) {
    /**
     * Read SMS inbox with optional timestamp filter.
     * Filters out personal phone number senders at read-time for efficiency.
     *
     * @param limit Maximum number of messages to read per batch
     * @param sinceTimestamp Only read messages newer than this epoch millisecond timestamp.
     *                      Pass 0 to read all messages (initial scan).
     */
    fun readInbox(limit: Int = 5000, sinceTimestamp: Long = 0): List<RawSms> {
        val result = mutableListOf<RawSms>()
        val projection = arrayOf(
            Telephony.Sms.BODY,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.DATE
        )

        // Filter by date if we have a previous scan timestamp
        val selection = if (sinceTimestamp > 0) "${Telephony.Sms.DATE} > ?" else null
        val selectionArgs = if (sinceTimestamp > 0) arrayOf(sinceTimestamp.toString()) else null

        val cursor = contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${Telephony.Sms.DATE} DESC"
        ) ?: return emptyList()

        cursor.use {
            val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
            val senderIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)
            var count = 0
            while (it.moveToNext() && count < limit) {
                val body = it.getString(bodyIdx).orEmpty()
                val sender = it.getString(senderIdx).orEmpty()

                // Skip empty messages
                if (body.isBlank() || sender.isBlank()) {
                    count++
                    continue
                }

                // Skip personal phone number senders (10+ digit numbers without alpha prefix)
                // Bank SMS senders have alphanumeric IDs like "VM-HDFCBK", "VK-SBIINB"
                if (isPersonalSender(sender)) {
                    count++
                    continue
                }

                result += RawSms(
                    body = body,
                    sender = sender,
                    timestamp = it.getLong(dateIdx)
                )
                count++
            }
        }
        return result
    }

    /**
     * Detect if a sender is a personal phone number (not a bank/service).
     * Bank senders: "VM-HDFCBK", "VK-SBIINB", "AD-ICICIB", "HDFCBK"
     * Personal senders: "+919876543210", "9876543210"
     */
    private fun isPersonalSender(sender: String): Boolean {
        val cleaned = sender.replace("+", "").replace("-", "").replace(" ", "")
        // If it's purely numeric and >= 10 digits, it's a phone number
        return cleaned.matches(Regex("^\\d{10,}$"))
    }
}
