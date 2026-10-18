package com.app.data.sms

import android.util.Log
import com.app.data.remote.api.SmsApiService
import com.app.data.remote.api.SmsBatchRequest
import com.app.data.remote.api.SmsMessageDto
import com.app.data.remote.api.ScamRequest
import com.app.data.remote.api.ScamResponse
import com.app.domain.model.Transaction
import com.app.domain.model.SplitDetail
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlmSmsFallbackService @Inject constructor(
    private val smsApiService: SmsApiService
) {
    suspend fun parseWithFallback(messages: List<SmsMessageDto>): List<Transaction> {
        if (messages.isEmpty()) return emptyList()
        
        try {
            val request = SmsBatchRequest(messages = messages)
            val response = smsApiService.parseLLM(request)
            if (response.isSuccessful && response.body()?.data != null) {
                val data = response.body()!!.data!!
                return data.transactions.map { dto ->
                    Transaction(
                        id = dto.id ?: "",
                        amount = dto.amount,
                        type = dto.type,
                        category = dto.category,
                        description = dto.description,
                        merchant = dto.merchant,
                        date = dto.date,
                        source = "sms_llm",
                        isRecurring = dto.isRecurring,
                        splitDetails = dto.splitDetails?.map { SplitDetail(it.personName, it.amount, it.settled) } ?: emptyList(),
                        upiId = dto.upiId,
                        referenceNumber = dto.referenceNumber,
                        accountLast4 = dto.accountLast4,
                        availableBalance = dto.availableBalance,
                        rawSms = dto.rawSms,
                        smsBodyDate = dto.smsBodyDate,
                        sender = dto.sender ?: ""
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("LlmSmsFallback", "LLM fallback failed: ${e.message}")
        }
        return emptyList()
    }

    suspend fun verifySmishing(text: String): ScamResponse? {
        try {
            val request = ScamRequest(text = text)
            val response = smsApiService.verifyScam(request)
            if (response.isSuccessful && response.body()?.data != null) {
                return response.body()!!.data
            }
        } catch (e: Exception) {
            Log.e("LlmSmsFallback", "Smishing verification failed: ${e.message}")
        }
        return null
    }
}
