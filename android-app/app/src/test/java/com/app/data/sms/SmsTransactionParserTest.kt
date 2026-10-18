package com.app.data.sms

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SmsTransactionParserTest {

    private lateinit var classifier: TransactionCategoryClassifier
    private lateinit var parser: SmsTransactionParser

    @Before
    fun setUp() {
        classifier = TransactionCategoryClassifier()
        parser = SmsTransactionParser(classifier)
        parser.clearProcessedHashes()
    }

    @Test
    fun testHdfcUpiDebit() {
        val sms = "Sent Rs. 450.00 from HDFC Bank A/C **1234 to swiggy@icici on 02-10-2026. Ref 523456789012. Avl bal Rs. 14,250.00"
        val tx = parser.parse(sms, "HDFCBK", System.currentTimeMillis())

        assertNotNull(tx)
        assertEquals(450.0, tx!!.amount, 0.01)
        assertEquals("debit", tx.type)
        assertEquals("Swiggy", tx.merchant)
        assertEquals("Food & Dining", tx.category)
        assertEquals("523456789012", tx.referenceNumber)
    }

    @Test
    fun testSbiSalaryCredit() {
        val sms = "Dear Customer, your A/C XXXXX123456 has a salary credited with INR 75,000.00 on 01-10-2026. Info: NEFT-TECHCORP-REF89123. Avl Bal: INR 1,20,500.00."
        val tx = parser.parse(sms, "SBINB", System.currentTimeMillis())

        assertNotNull(tx)
        assertEquals(75000.0, tx!!.amount, 0.01)
        assertEquals("credit", tx.type)
        assertEquals("Income", tx.category)
    }

    @Test
    fun testBankOfBarodaDrFrom() {
        val sms = "Your A/c xx7933 is debited by Rs.1200.00 on 28-09-2026. Trx of Rs.1200.00 towards ZEPTO. Avl Bal: Rs.5400.00. CRA/102938475610"
        val tx = parser.parse(sms, "BOBTXN", System.currentTimeMillis())

        assertNotNull(tx)
        assertEquals(1200.0, tx!!.amount, 0.01)
        assertEquals("debit", tx.type)
        assertEquals("Zepto", tx.merchant)
        assertEquals("Groceries", tx.category)
    }

    @Test
    fun testSubscriptionDetection() {
        val sms = "Auto-debit of INR 649.00 from A/C *5678 for Netflix subscription on 25-09-2026 successful. Ref 987654321012"
        val tx = parser.parse(sms, "ICICIB", System.currentTimeMillis())

        assertNotNull(tx)
        assertEquals(649.0, tx!!.amount, 0.01)
        assertEquals("debit", tx.type)
        assertEquals("Netflix", tx.merchant)
        assertEquals("Subscriptions", tx.category)
    }

    @Test
    fun testVpaExtraction() {
        val sms = "Paid Rs. 350.00 to VPA country.delight@okaxis on 29-09-2026. UPI Ref 112233445566. Avl bal Rs. 8,900"
        val tx = parser.parse(sms, "AXISBK", System.currentTimeMillis())

        assertNotNull(tx)
        assertEquals(350.0, tx!!.amount, 0.01)
        assertEquals("debit", tx.type)
        assertEquals("Country Delight", tx.merchant)
        assertEquals("Groceries", tx.category)
    }

    @Test
    fun testDuplicateDetection() {
        val sms = "Rs. 200.00 debited from a/c *1234 to Uber on 02-10-2026. Ref 998877665544"
        val timestamp = System.currentTimeMillis()
        val first = parser.parse(sms, "HDFCBK", timestamp)
        val second = parser.parse(sms, "HDFCBK", timestamp + 5000) // 5 seconds later

        assertNotNull(first)
        assertNull("Identical transaction should be filtered as duplicate", second)
    }
}
