package com.saikumar.expensetracker.data.domain

import com.saikumar.expensetracker.data.db.TransactionDao
import com.saikumar.expensetracker.data.entity.Transaction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class DuplicateDetectorTest {

    private lateinit var transactionDao: TransactionDao
    private lateinit var duplicateDetector: DuplicateDetector

    @Before
    fun setup() {
        transactionDao = mock()
        duplicateDetector = DuplicateDetector(transactionDao)
    }

    @Test
    fun `check should return EXACT_HASH when hash exists`() = runBlocking {
        // Arrange
        whenever(transactionDao.existsBySmsHash("hash123")).thenReturn(true)

        // Act
        val result = duplicateDetector.check(
            smsHash = "hash123",
            referenceNo = "ref123",
            amountPaisa = 50000,
            timestamp = System.currentTimeMillis()
        )

        // Assert
        assertTrue(result.isDuplicate)
        assertEquals(DuplicateDetector.Tier.EXACT_HASH, result.tier)
        assertEquals(0.999, result.confidence, 0.0001)
    }

    @Test
    fun `check should return REFERENCE_MATCH when ref and amount match`() = runBlocking {
        // Arrange
        whenever(transactionDao.existsBySmsHash(any())).thenReturn(false)
        whenever(transactionDao.existsByReferenceAndAmount("ref123", 50000)).thenReturn(true)

        // Act
        val result = duplicateDetector.check(
            smsHash = "hashNew",
            referenceNo = "ref123",
            amountPaisa = 50000,
            timestamp = System.currentTimeMillis()
        )

        // Assert
        assertTrue(result.isDuplicate)
        assertEquals(DuplicateDetector.Tier.REFERENCE_MATCH, result.tier)
        assertEquals(0.95, result.confidence, 0.0001)
    }

    @Test
    fun `check should return FUZZY_MATCH when strong context match found`() = runBlocking {
        // Arrange
        val timestamp = 1609459200000L // arbitrary time
        whenever(transactionDao.existsBySmsHash(any())).thenReturn(false)
        whenever(transactionDao.existsByReferenceAndAmount(any(), any())).thenReturn(false)

        val candidate = Transaction(
            id = 100L,
            amountPaisa = 50000,
            timestamp = timestamp - 60000, // 1 min earlier
            merchantName = "UBER EATS",
            smsHash = "oldHash",
            fullSmsBody = "body",
            categoryId = 1
        )
        whenever(transactionDao.findPotentialDuplicates(eq(50000), any(), any()))
            .thenReturn(listOf(candidate))

        // Act
        val result = duplicateDetector.check(
            smsHash = "hashNew",
            referenceNo = null,
            amountPaisa = 50000,
            timestamp = timestamp,
            merchantName = "Uber Eats" // Case difference, should match
        )

        // Assert
        assertTrue("Should detect fuzzy duplicate", result.isDuplicate)
        assertEquals(DuplicateDetector.Tier.FUZZY_MATCH, result.tier)
        // Base 0.4 + Merchant 0.3 = 0.7. Limit is >= 0.7
        assertTrue(result.confidence >= 0.7)
    }

    @Test
    fun `check should return not duplicate when no matches found`() = runBlocking {
        // Arrange
        whenever(transactionDao.existsBySmsHash(any())).thenReturn(false)
        whenever(transactionDao.existsByReferenceAndAmount(any(), any())).thenReturn(false)
        whenever(transactionDao.findPotentialDuplicates(any(), any(), any())).thenReturn(emptyList())

        // Act
        val result = duplicateDetector.check(
            smsHash = "uniqueHash",
            referenceNo = "uniqueRef",
            amountPaisa = 12345,
            timestamp = System.currentTimeMillis()
        )

        // Assert
        assertFalse(result.isDuplicate)
        assertEquals(0.0, result.confidence, 0.0)
    }
}
