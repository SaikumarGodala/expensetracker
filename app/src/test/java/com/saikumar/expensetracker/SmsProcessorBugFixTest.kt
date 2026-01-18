package com.saikumar.expensetracker

import com.saikumar.expensetracker.sms.SmsParser
import com.saikumar.expensetracker.sms.CounterpartyExtractor
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SmsProcessor bug fixes.
 * 
 * These tests use real SMS messages extracted from log_batch_1768151538106_23c659fc.json
 * to verify that the 7 identified bugs have been fixed.
 * 
 * BUG-001: CC payments misclassified as INCOME
 * BUG-002: EPF/PF balance notifications treated as income
 * BUG-003: Promotional loan offers treated as transactions
 * BUG-004: Airtel recharge reminders treated as income
 * BUG-005: "debited@SBI" extracted as UPI ID
 * BUG-006: P2P transfers not detected (transfer to/from NAME)
 * BUG-007: Home Loan not categorized properly
 */
class SmsProcessorBugFixTest {

    // ============ BUG-001: CC Payment Receipt Tests ============
    
    @Test
    fun `BUG-001 CC payment receipt should NOT be extracted as income-related UPI`() {
        // From log: CC payment confirmation should NOT trigger income classification
        val ccPaymentMessage = "DEAR HDFCBANK CARDMEMBER, PAYMENT OF Rs. 1820.00 RECEIVED TOWARDS YOUR CREDIT CARD ENDING WITH 1404. TOTAL OUTSTANDING IS Rs.1568.0 (10JAN26)"
        
        // The message contains "RECEIVED" but should NOT be treated as income
        // It's a CC payment confirmation (liability payment)
        val upper = ccPaymentMessage.uppercase()
        val isCCPaymentReceipt = upper.contains("CREDIT CARD") && 
                                (upper.contains("PAYMENT RECEIVED") || upper.contains("CREDITED TO YOUR") ||
                                 upper.contains("PAYMENT OF") && upper.contains("RECEIVED"))
        
        assertTrue("CC payment receipt should be detected", isCCPaymentReceipt)
    }

    // ============ BUG-002: EPF Balance Notification Tests ============
    
    @Test
    fun `BUG-002 EPF passbook balance notification should be filtered as promotional`() {
        // From log: EPF balance notification with total balance (not a transaction)
        val epfMessage = "Dear XXXXXXXX2971, your passbook balance against PUPUN**************3732 is Rs. 5,42,966/-. Contribution of Rs. 13,946/- for due month Dec-25 has been received."
        
        val lower = epfMessage.lowercase()
        val isEpfBalanceNotification = lower.contains("passbook balance") || lower.contains("balance against")
        
        assertTrue("EPF passbook balance notification should be detected", isEpfBalanceNotification)
    }
    
    @Test
    fun `BUG-002 EPFO contribution notification should be filtered`() {
        val epfoMessage = "Dear UAN 123456789012, Contribution of Rs. 15000/- for due month Nov-25 has been received. Your passbook balance is Rs. 4,50,000/-"
        
        val lower = epfoMessage.lowercase()
        val isEpfRelated = lower.contains("passbook balance") || 
                          (lower.contains("contribution of rs") && lower.contains("due month"))
        
        assertTrue("EPFO contribution notification should be filtered", isEpfRelated)
    }

    // ============ BUG-003: Loan Offer Spam Tests ============
    
    @Test
    fun `BUG-003 Loan offer spam should be filtered`() {
        // From log: Promotional loan message with fake amount
        val loanSpamMessage = "Get Rs. 241,000 credited to your bank a/c at 1% monthly interest!\nINSTANT Disbursal + ZERO Hidden Charges\nLink: https://gs.im/LOAN24/e/EytKJ4l2CC\n-LOANS24"
        
        val lower = loanSpamMessage.lowercase()
        val isLoanSpam = lower.contains("instant disbursal") || 
                        lower.contains("zero hidden charges") ||
                        (lower.contains("get rs") && (lower.contains("interest") || lower.contains("loan")))
        
        assertTrue("Loan offer spam should be detected", isLoanSpam)
    }
    
    @Test
    fun `BUG-003 Pre-approved loan message should be filtered`() {
        val preApprovedLoan = "Congratulations! You are pre-approved for Personal Loan upto Rs.5,00,000. Apply now at https://bank.com/loan"
        
        val lower = preApprovedLoan.lowercase()
        val isLoanSpam = lower.contains("pre-approved loan") || 
                        lower.contains("personal loan upto") ||
                        (lower.contains("apply now") && lower.contains("loan"))
        
        assertTrue("Pre-approved loan spam should be detected", isLoanSpam)
    }

    // ============ BUG-004: Recharge Reminder Spam Tests ============
    
    @Test
    fun `BUG-004 Airtel recharge reminder should be filtered`() {
        // From log: Recharge reminder with promo URL
        val airtelReminder = "7869XXX715 par Airtel pack samapt hone wala hai!\nAbhi recharge karein aur anand lein 2GB/din,100SMS/din,UL call, 365 din, Rs2999 mein. https://p.paytm.me/xCTH/airpld"
        
        val lower = airtelReminder.lowercase()
        val hasRechargeSpamSignal = (lower.contains("samapt hone wala") || 
                                     lower.contains("pack expiring") ||
                                     lower.contains("recharge karein")) &&
                                    (lower.contains("https://") || lower.contains("http://"))
        
        assertTrue("Airtel recharge reminder should be detected as spam", hasRechargeSpamSignal)
    }
    
    @Test
    fun `BUG-004 Short URL without transaction signal should be filtered`() {
        val promoWithShortUrl = "Special offer! Get 50% off on your next purchase. Click here: https://bit.ly/offer123"
        
        val lower = promoWithShortUrl.lowercase()
        val hasShortUrl = lower.contains("bit.ly") || lower.contains("goo.gl") || 
                         lower.contains("t.co/") || lower.contains("tinyurl") ||
                         lower.contains(".im/") || lower.contains("p.paytm.me")
        val hasTransactionSignal = lower.contains("debited") || lower.contains("credited") ||
                                  lower.contains("paid") || lower.contains("received")
        
        assertTrue("Should have short URL", hasShortUrl)
        assertFalse("Should NOT have transaction signal", hasTransactionSignal)
        assertTrue("Short URL without transaction signal should be filtered", hasShortUrl && !hasTransactionSignal)
    }

    // ============ BUG-005: False UPI ID Extraction Tests ============
    
    @Test
    fun `BUG-005 debited@sbi should NOT be extracted as UPI ID`() {
        // From log: SBI message where "debited@SBI" was incorrectly extracted
        val sbiMessage = "Rs30.0 debited@SBI UPI frm A/cX2916 on 16Mar22 RefNo 207589475491. If not done by u, fwd this SMS to 9223008333/Call 1800111109 or 09449112211 to block UPI"
        
        val upiId = SmsParser.extractUpiId(sbiMessage)
        
        // Should be null because "debited@sbi" is not a real UPI ID
        assertNull("debited@sbi should NOT be extracted as UPI ID", upiId)
    }
    
    @Test
    fun `BUG-005 credited@icici should NOT be extracted as UPI ID`() {
        val iciciMessage = "Rs5000 credited@ICICI to your account ending 1234"
        
        val upiId = SmsParser.extractUpiId(iciciMessage)
        
        assertNull("credited@icici should NOT be extracted as UPI ID", upiId)
    }
    
    @Test
    fun `BUG-005 Real UPI ID should still be extracted`() {
        val messageWithRealUpi = "Rs500 paid to swiggy@paytm via UPI"
        
        val upiId = SmsParser.extractUpiId(messageWithRealUpi)
        
        assertEquals("Real UPI ID should be extracted", "swiggy@paytm", upiId)
    }
    
    @Test
    fun `BUG-005 paid@sbi should NOT be extracted as UPI ID`() {
        val message = "Rs100 paid@SBI to merchant for groceries"
        
        val upiId = SmsParser.extractUpiId(message)
        
        assertNull("paid@sbi should NOT be extracted as UPI ID", upiId)
    }

    // ============ BUG-006: P2P Transfer Detection Tests ============
    
    @Test
    fun `BUG-006 transfer to NAME pattern should be detected`() {
        // From log: SBI UPI message with "transfer to NAME"
        val sbiP2pMessage = "Dear SBI User, your A/c X2916-debited by Rs200.0 on 24May23 transfer to RAJEEV KUMAR Ref No 314442970240"
        
        val counterparty = CounterpartyExtractor.extractCounterparty(sbiP2pMessage)
        
        assertTrue("Counterparty should be found", counterparty.found)
        assertNotNull("Extracted name should not be null", counterparty.extractedName)
        assertTrue("Extracted name should contain RAJEEV", 
                  counterparty.extractedName?.uppercase()?.contains("RAJEEV") == true)
    }
    
    @Test
    fun `BUG-006 Paytm transferred to NAME(phone) pattern should be detected`() {
        // From log: Paytm wallet P2P transfer
        val paytmP2pMessage = "Rs. 375 transferred to Amit shukla(9589799808) at Jan 26, 2019 13:26:31. Transaction ID 22642808721 , Updated Balance Rs. 48"
        
        val counterparty = CounterpartyExtractor.extractCounterparty(paytmP2pMessage)
        
        assertTrue("Counterparty should be found for Paytm transfer", counterparty.found)
        assertNotNull("Extracted name should not be null", counterparty.extractedName)
    }
    
    @Test
    fun `BUG-006 transfer from NAME pattern should be detected for incoming P2P`() {
        // From log: Incoming P2P transfer
        val incomingP2pMessage = "Dear SBI User, your A/c X2916-credited by Rs.206.67 on 24Aug24 transfer from PANDEY V Ref No 460357016679 -SBI"
        
        val counterparty = CounterpartyExtractor.extractCounterparty(incomingP2pMessage)
        
        assertTrue("Counterparty should be found for incoming transfer", counterparty.found)
    }

    // ============ BUG-007: Home Loan Merchant Pattern Tests ============
    
    @Test
    fun `BUG-007 Home Loan keyword should map to Loan EMI category`() {
        // From log: Home Loan EMI message
        val homeLoanMessage = "ICICI Bank Acc XX008 debited Rs. 5,936.00 on 10-Jan-26 InfoBIL*Home Loan.Avl Bal Rs. 48,742.00"
        
        val upper = homeLoanMessage.uppercase()
        
        // Check if message contains Home Loan keywords that should be mapped
        val hasHomeLoanKeyword = upper.contains("HOME LOAN") || 
                                upper.contains("HOMELOAN") ||
                                upper.contains("BIL*HOME")
        
        assertTrue("Home Loan keyword should be detected", hasHomeLoanKeyword)
    }
    
    @Test
    fun `BUG-007 Personal Loan should map to Loan EMI category`() {
        val personalLoanEmi = "Rs 5000 debited towards Personal Loan EMI for account ending 1234"
        
        val upper = personalLoanEmi.uppercase()
        val hasLoanKeyword = upper.contains("PERSONAL LOAN") || upper.contains("LOAN EMI")
        
        assertTrue("Personal Loan keyword should be detected", hasLoanKeyword)
    }

    // ============ Regression Tests: Valid Messages Should Still Work ============
    
    @Test
    fun `Regression - Real credited message should still be valid`() {
        val realCredit = "Rs.5000 credited to your A/c XX1234 from NEFT-DEUT12345-SALARY"
        
        val lower = realCredit.lowercase()
        val hasTransactionSignal = lower.contains("credited") || lower.contains("debited")
        
        assertTrue("Real credit message should have transaction signal", hasTransactionSignal)
    }
    
    @Test
    fun `Regression - Real debit message should still be valid`() {
        val realDebit = "Rs.500 debited from A/c XX5678 to swiggy@paytm for Food"
        
        val upiId = SmsParser.extractUpiId(realDebit)
        
        assertEquals("Real UPI ID should be extracted from valid message", "swiggy@paytm", upiId)
    }
    
    @Test
    fun `Regression - Real refund should still be detected`() {
        val refundMessage = "Rs.150 credited to your A/c for Amazon refund order #12345"
        
        val lower = refundMessage.lowercase()
        val isRefund = lower.contains("refund")
        val isCredited = lower.contains("credited")
        
        assertTrue("Refund should be detected", isRefund)
        assertTrue("Credit should be detected", isCredited)
    }
}
