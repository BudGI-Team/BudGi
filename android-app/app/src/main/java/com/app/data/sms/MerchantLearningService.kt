package com.app.data.sms

import com.app.data.local.dao.MerchantCategoryMappingDao
import com.app.data.local.entity.MerchantCategoryMappingEntity
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of deciding a category with learning loop metadata.
 */
data class CategorizationDecision(
    val category: String,
    val confidence: Double,
    val source: String,             // "UserLearned", "UpiRegistry", "Rule", "AmountHeuristic"
    val needsReview: Boolean,       // true = show to user for confirmation
    val suggestedCategory: String,  // Best guess
    val alternateCategory: String? = null,
    val alias: String? = null
)

/**
 * Quick category suggestion chip for UI.
 */
data class CategorySuggestion(
    val category: String,
    val confidence: Double,
    val reason: String
)

/**
 * Merchant Learning Service (Phase 4)
 *
 * Implements a closed-loop active learning system:
 * 1. Checks user's personal learned merchant mappings first (100% user preference).
 * 2. Consults [UpiMerchantRegistry] for recognized brand VPAs and QR patterns.
 * 3. Falls back to multi-signal scoring via [TransactionCategoryClassifier].
 * 4. Learns automatically when the user edits or confirms a category in the UI.
 * 5. Provides smart 1-tap suggestion chips for the frontend transaction cards.
 */
@Singleton
class MerchantLearningService @Inject constructor(
    private val merchantMappingDao: MerchantCategoryMappingDao,
    private val categoryClassifier: TransactionCategoryClassifier,
    private val upiRegistry: UpiMerchantRegistry
) {

    /**
     * Determines the optimal category for a transaction and whether user confirmation is required.
     */
    suspend fun decideCategory(
        merchant: String?,
        upiId: String?,
        normalizedMerchant: String,
        amount: Double,
        type: String,
        message: String,
        hourOfDay: Int = -1,
        dayOfWeek: Int = -1
    ): CategorizationDecision {
        // Priority 1: User's personal learned history
        if (normalizedMerchant.isNotBlank()) {
            val userMapping = merchantMappingDao.findByNormalizedMerchant(normalizedMerchant)
            if (userMapping != null) {
                // If user has confirmed this merchant before, trust it completely!
                val confidence = if (userMapping.frequency >= 3) 0.99 else 0.90
                return CategorizationDecision(
                    category = userMapping.category,
                    confidence = confidence,
                    source = "UserLearned",
                    needsReview = false,
                    suggestedCategory = userMapping.category,
                    alias = userMapping.alias
                )
            }
        }

        // Priority 2: UPI Merchant Registry
        val registryMatch = upiRegistry.lookup(upiId ?: merchant)
        if (registryMatch != null) {
            when (registryMatch.merchantType) {
                MerchantType.KNOWN_BRAND -> {
                    return CategorizationDecision(
                        category = registryMatch.category,
                        confidence = registryMatch.confidence,
                        source = "UpiRegistry",
                        needsReview = false,
                        suggestedCategory = registryMatch.category
                    )
                }
                MerchantType.PERSON -> {
                    return CategorizationDecision(
                        category = "Transfer",
                        confidence = 0.92,
                        source = "UpiRegistry",
                        needsReview = false,
                        suggestedCategory = "Transfer"
                    )
                }
                MerchantType.LOCAL_VENDOR_QR -> {
                    // QR vendor with low confidence: suggest Groceries/Food, but flag for user review
                    return CategorizationDecision(
                        category = registryMatch.category,
                        confidence = registryMatch.confidence,
                        source = "UpiRegistry",
                        needsReview = true,
                        suggestedCategory = registryMatch.category,
                        alternateCategory = "Food & Dining"
                    )
                }
                else -> {
                    // Proceed to classifier for higher resolution
                }
            }
        }

        // Priority 3: Multi-signal heuristic classifier
        val classResult = categoryClassifier.classifyWithConfidence(
            message = message,
            upiId = upiId,
            merchant = merchant,
            amount = amount,
            hourOfDay = hourOfDay,
            dayOfWeek = dayOfWeek
        )

        val needsReview = classResult.confidence < 0.75 ||
                classResult.category == "Others" ||
                classResult.category == "Other"

        return CategorizationDecision(
            category = classResult.category,
            confidence = classResult.confidence,
            source = classResult.source,
            needsReview = needsReview,
            suggestedCategory = classResult.category,
            alternateCategory = classResult.alternateCategory
        )
    }

    /**
     * Stores user feedback permanently into Room DB.
     * All future transactions from this merchant will now be auto-categorized.
     */
    suspend fun learnFromUser(
        merchantKey: String,
        normalizedMerchant: String,
        category: String,
        alias: String? = null
    ) {
        if (normalizedMerchant.isBlank()) return

        val existing = merchantMappingDao.findByNormalizedMerchant(normalizedMerchant)
        val nowIso = Instant.now().toString()

        val mapping = MerchantCategoryMappingEntity(
            id = existing?.id ?: 0,
            merchantKey = merchantKey,
            normalizedMerchant = normalizedMerchant,
            category = category,
            alias = alias ?: existing?.alias,
            confidence = 1.0,
            frequency = (existing?.frequency ?: 0) + 1,
            lastUsedTimestamp = System.currentTimeMillis(),
            learnedFromUser = true,
            source = "UserLearned",
            syncStatus = "pending_update",
            createdAt = existing?.createdAt ?: nowIso,
            updatedAt = nowIso
        )

        merchantMappingDao.upsert(mapping)
    }

    /**
     * Produces top 3 category suggestions for UI quick-confirm buttons.
     */
    fun getSuggestedCategories(
        merchant: String?,
        upiId: String?,
        amount: Double
    ): List<CategorySuggestion> {
        val suggestions = mutableListOf<CategorySuggestion>()

        val registry = upiRegistry.lookup(upiId ?: merchant)
        if (registry != null) {
            suggestions.add(
                CategorySuggestion(
                    category = registry.category,
                    confidence = registry.confidence,
                    reason = "Recognized merchant ${registry.merchantName}"
                )
            )
        }

        val classified = categoryClassifier.classifyWithConfidence(
            message = merchant ?: "",
            upiId = upiId,
            merchant = merchant,
            amount = amount
        )

        if (suggestions.none { it.category == classified.category }) {
            suggestions.add(
                CategorySuggestion(
                    category = classified.category,
                    confidence = classified.confidence,
                    reason = "Matched spending pattern"
                )
            )
        }

        classified.alternateCategory?.let { alt ->
            if (suggestions.none { it.category == alt }) {
                suggestions.add(
                    CategorySuggestion(
                        category = alt,
                        confidence = classified.alternateConfidence,
                        reason = "Common alternative"
                    )
                )
            }
        }

        // Fill up to 3 defaults if needed
        val fallbacks = listOf("Food & Dining", "Groceries", "Shopping")
        for (fb in fallbacks) {
            if (suggestions.size >= 3) break
            if (suggestions.none { it.category == fb }) {
                suggestions.add(
                    CategorySuggestion(
                        category = fb,
                        confidence = 0.50,
                        reason = "Popular category"
                    )
                )
            }
        }

        return suggestions.take(3)
    }
}

