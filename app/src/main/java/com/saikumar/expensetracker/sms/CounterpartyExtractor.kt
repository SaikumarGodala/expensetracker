package com.saikumar.expensetracker.sms

import com.saikumar.expensetracker.data.entity.TransactionType
import java.util.regex.Pattern

/**
 * Robust Counterparty Extractor - Template Based
 *
 * This extractor uses a template-based approach rather than generic regex patterns.
 * Each bank SMS format is matched against a known template, and the counterparty is
 * extracted from the correct position in the template.
 */
object CounterpartyExtractor {

    data class Counterparty(
        val name: String?,
        val upiId: String?,
        val type: CounterpartyType,
        val trace: List<String> = emptyList(),
        val confidence: Float = 1.0f
    )

    enum class CounterpartyType {
        MERCHANT, PERSON, BANK_ACCOUNT, UNKNOWN
    }
    
    // Cashback/Reward VPA patterns - these are MERCHANT/SYSTEM, not PERSON
    private val CASHBACK_VPA_PATTERNS = listOf(
        "cashback", "reward", "promo", "refund", "bhimcashback",
        "googlepay", "gpay", "phoneperefund", 
        // Merchant/Gateway prefixes that indicate NOT a person
        "razorpay", "payu", "cashfree", "billdesk", "ccavenue", "paytmqr",
        "swiggy", "zomato", "uber", "ola", "licious", "instamart", "blinkit", "zepto"
    )

    // --- OPTIMIZED COMPILED REGEXES ---
    private val REGEX_VPA_TRAILING_DIGITS = Regex("\\d+$")
    private val REGEX_VPA_SEPARATORS = Regex("[._-]")
    private val REGEX_SPACES = Regex("\\s{2,}")
    private val REGEX_EMPLOYER_NOISE = Regex("(?i)(PRIVATE\\s+LIMITED|PVT\\s+LTD|LIMITED|CORPORATION|CLEARING|INDIA)")
    private val REGEX_PREPOSITION_PREFIX = Regex("(?i)^(At|To|From)\\s+")
    private val REGEX_TRAILING_DOTS_DIGITS_CHECK = Regex(".*[.\\d]+$")
    private val REGEX_TRAILING_DOTS_DIGITS = Regex("[.\\d]+$")
    private val REGEX_ALPHA_NUM_DIGITS_CHECK = Regex("^[a-zA-Z]+\\d+$")
    private val REGEX_NEWLINE_REST = Regex("[\\r\\n]+.*")
    private val REGEX_TRAILING_LOCATION_NOISE = Regex("(?i)^(Gur|new|On)\\s.*")
    private val REGEX_DIGITS_ONLY_SHORT = Regex("^\\d{2,4}$")

    // --- TEMPLATE PATTERNS ---
    
    // HDFC "Sent Rs.XXX To <NAME>" pattern (UPI P2P). Name class allows digits and
    // M/S.-style punctuation ("REVANTH 31112", "M/S.KARACHI INC") - cleanName strips noise.
    private val HDFC_SENT_TO_PATTERN = Pattern.compile(
        "Sent\\s+Rs\\.?[\\d,\\.]+\\s+(?:From\\s+HDFC[^\\n]*\\n)?To\\s+([A-Z][A-Za-z0-9./&\\s]{2,50})\\s*\\nOn",
        Pattern.CASE_INSENSITIVE
    )

    // Single-line HDFC sent, incl. executed UPI-Mandate SIP debits:
    // "UPI Mandate: Sent Rs.12000.00 from HDFC Bank A/c 2725 To Indian Clearing Corpor 02/07/26 Ref..."
    // "Sent Rs.953.82 From HDFC Bank A/C *2725 To CRED Club On 25/06/26 Ref..."
    // The name runs until the date (with or without "On").
    // Name class allows digits and M/S.-style punctuation ("REVANTH 31112", "M/S.KARACHI
    // INC") - cleanName strips the trailing digit noise afterwards.
    private val HDFC_SENT_TO_INLINE_PATTERN = Pattern.compile(
        "Sent\\s+Rs\\.?[\\d,\\.]+\\s+from\\s+HDFC[^\\n]*?\\s+To\\s+([A-Za-z][A-Za-z0-9./&\\s]{2,50}?)\\s+(?:On\\s+)?\\d{2}/\\d{2}",
        Pattern.CASE_INSENSITIVE
    )

    // "Dear UPI user A/C X5171 debited by 6000.0 on date 24Jan25 trf to Saikumar Reddy G
    // Refno 502433493739" (Union Bank & co)
    private val UPI_TRF_TO_PATTERN = Pattern.compile(
        "debited\\s+by\\s+[\\d.]+\\s+on\\s+date\\s+\\S+\\s+trf\\s+to\\s+([A-Za-z][A-Za-z\\s]{2,50}?)\\s+Ref",
        Pattern.CASE_INSENSITIVE
    )
    
    // HDFC "Spent Rs.XXX At <MERCHANT>" pattern (Credit Card POS)
    // Leading [.\s]* and trailing [_.]* tolerate HDFC's junk around the merchant:
    // "At ..AVENUE SUPERMART_ On 2026-07-05" must still capture "AVENUE SUPERMART".
    private val HDFC_SPENT_AT_PATTERN = Pattern.compile(
        "Spent\\s+Rs\\.?[\\d,\\.]+\\s+On\\s+HDFC[^\\n]*?At\\s+[._\\s]*([A-Za-z][A-Za-z0-9\\s]{2,50}?)(?:[_.]*\\s+(?:Gur|new|On\\s+\\d))",
        Pattern.CASE_INSENSITIVE
    )

    // Amount-first HDFC card-spend variant:
    // "Rs.34369 spent on HDFC Bank Card x7725 at ..TITAN COMPANY LI_ on 2025-04-12:19:23:27"
    private val HDFC_AMOUNT_SPENT_AT_PATTERN = Pattern.compile(
        "Rs\\.?\\s*[\\d,\\.]+\\s+spent\\s+on\\s+HDFC[^\\n]*?\\s+at\\s+[.\\s]*([A-Za-z][A-Za-z0-9\\s]{2,50}?)(?:[_.]*\\s+on\\s+\\d)",
        Pattern.CASE_INSENSITIVE
    )

    // HDFC "Txn Rs.XXX On HDFC Bank Card At <VPA>" pattern (Card via UPI)
    // Captures full VPA prefix including merchant names like "svmbowling.67079608"
    private val HDFC_CARD_UPI_PATTERN = Pattern.compile(
        "Txn\\s+Rs\\.?[\\d,\\.]+\\s+On\\s+HDFC[^\\n]*?At\\s+([a-zA-Z][a-zA-Z0-9._-]*)@[a-zA-Z]+",
        Pattern.CASE_INSENSITIVE
    )

    // Same as above but the handle has NO "@bank" suffix - HDFC sometimes prints just the
    // raw QR/merchant handle on its own line: "At paytmqr281005050101tnofn2 \nby UPI 654…"
    // or "At mandikingarabianrest.6603\nby UPI 712…". Captured up to the "by UPI" line so the
    // handle becomes the identity (and a real merchant like mandikingarabianrest is recovered).
    // Trailing "@?" tolerates a truncated handle with no bank suffix ("...frqws02@\nby UPI").
    private val HDFC_CARD_HANDLE_PATTERN = Pattern.compile(
        "Txn\\s+Rs\\.?[\\d,\\.]+\\s+On\\s+HDFC[\\s\\S]*?At\\s+([a-zA-Z0-9][a-zA-Z0-9._-]{2,})@?\\s+by\\s+UPI",
        Pattern.CASE_INSENSITIVE
    )
    
    // ICICI "INR XXX spent using ICICI Bank Card on DD-MON-YY on <MERCHANT>" pattern.
    // Also supports USD and other currencies. Lazy capture with an explicit terminator:
    // "." was inside the class, so "on L FUELS. Avl Limit: ..." captured "Fuels Avl Limit".
    private val ICICI_SPENT_ON_PATTERN = Pattern.compile(
        "(?:INR|USD|EUR|GBP|AED|SGD)\\s+[\\d,\\.]+\\s+spent\\s+using\\s+ICICI[^\\n]*on\\s+\\d{2}-[A-Z][a-z]{2}-\\d{2}\\s+on\\s+([A-Z][A-Za-z0-9,\\s]{2,30}?)(?:\\.|\\s+Avl\\b)",
        Pattern.CASE_INSENSITIVE
    )

    // SBI Credit Card "Rs.XXX spent on your SBI Credit Card at <MERCHANT>" pattern
    // BUGFIX: the greedy `[^\n]*at\s+` here (and in ICICI_SPENT_AT below) matched the LAST
    // "at " in the message - which in real SBI/ICICI SMS is the dispute link ("Report at
    // https://sbicard.com/...", "Know more ... at icici.co/..."), so the captured "merchant"
    // was "https" or "icici" and the real merchant was lost. Lazy `[^\n]*?` stops at the
    // first "at " after the card phrase, which is the merchant.
    // The capture allows the gateway separator "*" ("RAZ*Kaloji Narayana Ra", "PYU*BookMyShow")
    // so cleanName can strip the RAZ/PYU/… prefix; previously the class excluded "*" and the
    // merchant came out as just the gateway code "Raz". Lazy + terminated on " on <date>" / "."
    // / end so it doesn't run into the trailing date.
    private val SBI_SPENT_AT_PATTERN = Pattern.compile(
        "Rs\\.?[\\d,\\.]+\\s+spent\\s+on\\s+your\\s+SBI\\s+Credit\\s+Card[^\\n]*?at\\s+([A-Z][A-Za-z0-9\\s*&_-]{2,40}?)(?:\\s+on\\s+\\d|\\s*\\.|$)",
        Pattern.CASE_INSENSITIVE
    )

    // ICICI "Rs X,XXX spent on ICICI Bank Card XX... on DD-Mon-YY at <MERCHANT>" pattern.
    // "*" allowed so gateway-wrapped merchants ("RAZ*HP Pay") survive; cleanName strips RAZ*.
    // Terminates at "." OR " Avl" - some bodies have no period before "Avl Lmt", which made
    // the captured merchant "Sri XX Fuels Avl Limit".
    private val ICICI_SPENT_AT_PATTERN = Pattern.compile(
        "Rs\\s+[\\d,\\.]+\\s+spent\\s+on\\s+ICICI[^\\n]*?at\\s+([A-Z][A-Za-z0-9*\\s\\.]+?)(?:\\.|\\s+Avl\\b)",
        Pattern.CASE_INSENSITIVE
    )

    // Generic "spent on your <ANY BANK> Credit Card ending XX1234 at <MERCHANT> on <DATE>"
    // Covers IDFC FIRST ("Happy Shopping! INR 508.00 spent on your IDFC FIRST Bank Credit
    // Card ending XX4969 at SWIGGY IN on 22 JUN 2026...") and similar formats from banks
    // without a dedicated template. Anchored on "ending XXnnnn at ... on <digit>" so the
    // merchant capture can't run into the trailing date or dispute text.
    private val GENERIC_CARD_ENDING_AT_PATTERN = Pattern.compile(
        // '*' included in the capture class for gateway-prefixed merchants like "RAZ*Swiggy"
        "(?:INR|Rs\\.?)\\s*[\\d,\\.]+\\s+spent\\s+on\\s+your\\s+[A-Za-z][A-Za-z\\s]{1,30}Card\\s+ending\\s+(?:XX)?\\d{4}\\s+at\\s+([A-Za-z][A-Za-z0-9\\s&\\.\\*-]{2,40}?)\\s+on\\s+\\d",
        Pattern.CASE_INSENSITIVE
    )

    // Axis slash-separated format:
    // "Spent / Card no. XX8887 / INR 670.35 / 17-08-25 19:30:23 / ZOMATO / Avl Lmt INR ..."
    // Merchant is the 5th slash-separated field.
    private val AXIS_SLASH_PATTERN = Pattern.compile(
        "Spent\\s*/\\s*Card\\s+no\\.\\s*XX\\d{4}\\s*/\\s*[A-Z]{3}\\s*[\\d,\\.]+\\s*/\\s*[^/]*?/\\s*([^/]{2,40}?)\\s*/",
        Pattern.CASE_INSENSITIVE
    )

    // FASTag toll payment: "Rs.70 paid at Shamshabad for TG07AP7269 on ... with ICICI Bank FASTag"
    // Captures the toll plaza name; CategoryMapper routes FASTag bodies to Parking & Tolls.
    private val FASTAG_PAID_AT_PATTERN = Pattern.compile(
        "Rs\\.?\\s*[\\d,\\.]+\\s+paid\\s+at\\s+([A-Za-z][A-Za-z0-9\\s]{2,30}?)\\s+for\\s+[A-Z]{2}\\s?\\d{2}",
        Pattern.CASE_INSENSITIVE
    )

    // ATM withdrawal (HDFC format): "Withdrawn Rs.10000 From HDFC Bank Card x5454 At
    // +SERILINGAMPALLY On 2026-03-29..." - the existing WITHDRAWN_AT pattern only handles
    // "withdrawn at <X> from", not this From-then-At ordering.
    private val WITHDRAWN_FROM_AT_PATTERN = Pattern.compile(
        "Withdrawn\\s+Rs\\.?\\s*[\\d,\\.]+\\s+From\\s+[^\\n]*?At\\s+\\+?([A-Za-z][A-Za-z0-9\\s]{2,30}?)\\s+On",
        Pattern.CASE_INSENSITIVE
    )
    
    // ICICI "Acct debited for Rs XXX; <NAME> credited" pattern
    private val ICICI_DEBITED_CREDITED_PATTERN = Pattern.compile(
        "debited\\s+for\\s+Rs\\s+[\\d,\\.]+[^;]*;\\s*([A-Za-z][A-Za-z0-9\\s]{2,50})\\s+credited",
        Pattern.CASE_INSENSITIVE
    )
    
    // IDFC FIRST Bank "A/c debited by Rs XXX; <NAME> credited" pattern
    private val IDFC_DEBITED_CREDITED_PATTERN = Pattern.compile(
        "debited\\s+by\\s+Rs\\.?\\s*[\\d,\\.]+[^;]*;\\s*([A-Za-z][A-Za-z0-9\\s]{2,50})\\s+credited",
        Pattern.CASE_INSENSITIVE
    )

    // Axis Bank "Spent INR XXX ... PYU*Swiggy" pattern
    // Also handles "Razorpay*Sw", "AIRTEL PAYM", etc.
    // Handles optional timestamp line between Card No and Merchant
    private val AXIS_SPENT_PATTERN = Pattern.compile(
        "Spent\\s+INR\\s+[\\d,\\.]+\\s+Axis\\s+Bank\\s+Card\\s+no\\.\\s+XX\\d{4}[^\\n]*\\n(?:[^\\n]*\\d{2}:\\d{2}[^\\n]*\\n)?([A-Za-z0-9\\*\\s\\._-]+)\\n",
        Pattern.CASE_INSENSITIVE
    )

    // Older Axis multi-line format where "Spent" / "Card no." / "INR <amt>" / "<date>" /
    // "<MERCHANT>" are each on their own line:
    //   Spent
    //   Card no. XX8887
    //   INR 215.71
    //   15-08-25 08:46:40
    //   AIRTEL PAYM
    //   Avl Lmt INR ...
    // Merchant is the line after the timestamp, before "Avl". Without this the merchant
    // came out null and these fell to Uncategorized while the newer single-line format
    // for the SAME merchant categorized fine - a confusing inconsistency.
    private val AXIS_SPENT_MULTILINE_PATTERN = Pattern.compile(
        "Spent\\s*\\n\\s*Card\\s+no\\.\\s+XX\\d{4}\\s*\\n\\s*INR\\s+[\\d,\\.]+\\s*\\n\\s*[\\d:\\-\\s/]+\\n\\s*([A-Za-z0-9\\*\\s\\._-]+?)\\s*\\n",
        Pattern.CASE_INSENSITIVE
    )


    // Credit Alert "credited from VPA <VPA>"
    private val CREDIT_ALERT_VPA_PATTERN = Pattern.compile(
        "credited\\s+to\\s+[^\\n]+from\\s+VPA\\s+([a-zA-Z0-9._-]+@[a-zA-Z]+)",
        Pattern.CASE_INSENSITIVE
    )

    // ICICI incoming UPI credit with the SENDER'S NAME in plain text:
    // "Acct XX294 is credited with Rs 12000.00 on 13-Nov-25 from SAIKUMAR REDDY . UPI:568322545657"
    // This was the single biggest gap for received money - the name is right there.
    private val ICICI_CREDITED_FROM_PATTERN = Pattern.compile(
        "is\\s+credited\\s+with\\s+Rs\\.?\\s*[\\d,\\.]+\\s+on\\s+\\S+\\s+from\\s+([A-Za-z][A-Za-z\\s\\.]{2,50}?)\\s*[\\.,;]",
        Pattern.CASE_INSENSITIVE
    )

    // HDFC incoming IMPS with the sender's name between dashes:
    // "Received! INR 1,00,000.00 in HDFC Bank A/c xx2725 On 09-03-26 For IMPS -MEKALA NIVAS- 606811815340"
    // Honorific (Mr/Mrs/Ms) skipped so self-transfers ("-Mr GODALA SAIKUMAR R-") match the user's name.
    private val HDFC_IMPS_RECEIVED_PATTERN = Pattern.compile(
        "For\\s+IMPS\\s*-\\s*(?:Mr|Mrs|Ms)?\\.?\\s*([A-Za-z][A-Za-z\\s\\.]{1,50}?)\\s*-\\s*\\d",
        Pattern.CASE_INSENSITIVE
    )
    
    // NEFT Salary pattern: "NEFT Cr-XXXX-<EMPLOYER>-<EMPLOYEE>-XXXX"
    private val NEFT_SALARY_PATTERN = Pattern.compile(
        "for\\s+NEFT\\s+Cr-[A-Z0-9]+-([A-Z][A-Za-z\\s]+(?:PRIVATE|PVT|LTD|LIMITED|TECHNOLOGIES|INDIA|CORP|INC|SOLUTIONS)?[^-]*)-",
        Pattern.CASE_INSENSITIVE
    )

    // ICICI NEFT pattern: "Info NEFT-<REF>-<SENDER>."
    // Example: "Info NEFT-DEUTH05533207350-ZF IND. Available Balance"
    private val ICICI_NEFT_PATTERN = Pattern.compile(
        "Info\\s+NEFT-[A-Z0-9]+-([A-Z][A-Za-z0-9\\s&]+?)\\.",
        Pattern.CASE_INSENSITIVE
    )

    // FT/Clearing deposit: "FT-XXXX-XXXX - <ENTITY> - Avl bal"
    // Captures entity name between the dashes after FT reference
    // Example: "for FT- 2334233915-XXXXXXXXXXXX9159 - INDIAN CLEARING CORPORATION LIMITED - Avl bal"
    // Note: Handles optional space after "FT-"
    private val FT_DEPOSIT_PATTERN = Pattern.compile(
        "for\\s+FT-\\s*[\\dX]+(?:-[\\dX]+)?\\s*-\\s*([^-]+?)\\s*-",
        Pattern.CASE_INSENSITIVE
    )

    // ATM Withdrawal: "Rs.XXXX withdrawn at <ATM_ID/LOCATION> from A/c"
    private val WITHDRAWN_AT_PATTERN = Pattern.compile(
        "withdrawn\\s+at\\s+([A-Z0-9\\s]+?)\\s+(?:from|on)",
        Pattern.CASE_INSENSITIVE
    )
    
    // UPI ID extraction (fallback)
    private val UPI_PATTERN = Pattern.compile("([a-zA-Z0-9._-]+@[a-zA-Z]+)")

    // Purpose of a fee/charge/EMI debit: "debited by Rs 236 towards Debit Card Annual Fees on
    // 08/07/26", "deducted ... towards Bank of Baroda Loan". Gives these otherwise-nameless
    // bank-charge rows a real label. Only "debited/charged/deducted ... towards" (not the CC
    // bill "payment received towards your card", which has no such verb before "towards").
    private val DEBITED_TOWARDS_PATTERN = Pattern.compile(
        "(?:debited|charged|deducted)\\s+(?:by\\s+)?(?:(?:Rs\\.?|INR)?\\s*[\\d,\\.]+\\s+)?towards\\s+([A-Za-z][A-Za-z\\s]{2,40}?)(?:\\s+on\\s+\\d|[.,;]|\\s+UMRN|$)",
        Pattern.CASE_INSENSITIVE
    )

    // --- MAIN ENTRY POINT ---
    fun extract(body: String, transactionType: TransactionType): Counterparty {
        // Template matching in order of specificity
        val trace = mutableListOf<String>()
        
        // 1. HDFC "Sent To <NAME>" (P2P Transfer / UPI Payment)
        HDFC_SENT_TO_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: HDFC_SENT_TO")
                val name = cleanName(rawName, trace)
                if (isValidName(name)) {
                    return Counterparty(name, extractUpiId(body), classifyName(name, transactionType), trace)
                } else trace.add("Invalid name rejected: $name")
            }
        }

        // 1b. Single-line HDFC sent (executed UPI-Mandate SIP debits and inline "To X On date")
        HDFC_SENT_TO_INLINE_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: HDFC_SENT_TO_INLINE")
                val name = cleanName(rawName, trace)
                if (isValidName(name)) {
                    return Counterparty(name, extractUpiId(body), classifyName(name, transactionType), trace)
                } else trace.add("Invalid name rejected: $name")
            }
        }

        // 1c. "A/C ... debited by <amt> on date <d> trf to <NAME> Refno ..." (Union Bank etc.)
        UPI_TRF_TO_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: UPI_TRF_TO")
                val name = cleanName(rawName, trace)
                if (isValidName(name)) {
                    return Counterparty(name, extractUpiId(body), classifyName(name, transactionType), trace)
                } else trace.add("Invalid name rejected: $name")
            }
        }

        // 2. HDFC "Spent At <MERCHANT>" (CC POS)
        HDFC_SPENT_AT_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: HDFC_SPENT_AT")
                val name = cleanName(rawName, trace)
                if (isValidName(name)) {
                    return Counterparty(name, null, CounterpartyType.MERCHANT, trace)
                } else trace.add("Invalid name rejected: $name")
            }
        }
        
        // 2b. Amount-first HDFC card spend ("Rs.X spent on HDFC Bank Card x7725 at ..NAME_")
        HDFC_AMOUNT_SPENT_AT_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: HDFC_AMOUNT_SPENT_AT")
                val name = cleanName(rawName, trace)
                if (isValidName(name)) {
                    return Counterparty(name, null, CounterpartyType.MERCHANT, trace)
                } else trace.add("Invalid name rejected: $name")
            }
        }

        // 3. HDFC Card + UPI - Extract merchant from VPA prefix
        HDFC_CARD_UPI_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val vpaPrefix = m.group(1) ?: return@let
                trace.add("Matched Template: HDFC_CARD_UPI")
                val merchantName = extractMerchantFromVpa(vpaPrefix)
                return if (merchantName != null && isValidName(merchantName)) {
                    trace.add("Extracted Merchant from VPA prefix: $vpaPrefix -> $merchantName")
                    Counterparty(merchantName, extractUpiId(body), CounterpartyType.MERCHANT, trace)
                } else {
                    trace.add("VPA prefix '$vpaPrefix' deemed junk/invalid")
                    // Keep the raw VPA even when the prefix is a junk gateway handle
                    // (bharatpe.../gpay-123...): CategoryMapper uses it to classify these
                    // QR payments as Offline Merchant instead of Uncategorized.
                    Counterparty(null, extractUpiId(body), CounterpartyType.UNKNOWN, trace)
                }
            }
        }
        
        // 3b. HDFC Card + bare handle (no "@bank" suffix). The handle itself is the identity.
        HDFC_CARD_HANDLE_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val handle = m.group(1) ?: return@let
                trace.add("Matched Template: HDFC_CARD_HANDLE")
                val merchantName = extractMerchantFromVpa(handle)
                return if (merchantName != null && isValidName(merchantName)) {
                    Counterparty(merchantName, handle, CounterpartyType.MERCHANT, trace)
                } else {
                    // Junk/QR handle - keep it as the upiId so the VPA fallback shows it.
                    Counterparty(null, handle, CounterpartyType.UNKNOWN, trace)
                }
            }
        }

        // 4. ICICI "spent using ICICI Bank Card on <MERCHANT>"
        ICICI_SPENT_ON_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: ICICI_SPENT_ON")
                val name = cleanName(rawName, trace)
                if (isValidName(name)) {
                    return Counterparty(name, null, CounterpartyType.MERCHANT, trace)
                } else trace.add("Invalid name rejected: $name")
            }
        }
        
        // 5. ICICI "spent on ICICI Bank Card at <MERCHANT>"
        ICICI_SPENT_AT_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: ICICI_SPENT_AT")
                val name = cleanName(rawName, trace)
                if (isValidName(name)) {
                    return Counterparty(name, null, CounterpartyType.MERCHANT, trace)
                } else trace.add("Invalid name rejected: $name")
            }
        }

        // 5b. Axis Bank "Spent INR ... <MERCHANT>" pattern (captures messy merchant names like PYU*Swiggy)
        AXIS_SPENT_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: AXIS_SPENT")
                val name = cleanName(rawName, trace)
                if (isValidName(name)) {
                    return Counterparty(name, null, CounterpartyType.MERCHANT, trace)
                } else trace.add("Invalid name rejected: $name")
            }
        }

        // 5b-ii. Older Axis multi-line format (merchant on its own line after the timestamp)
        AXIS_SPENT_MULTILINE_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: AXIS_SPENT_MULTILINE")
                val name = cleanName(rawName, trace)
                if (isValidName(name)) {
                    return Counterparty(name, null, CounterpartyType.MERCHANT, trace)
                } else trace.add("Invalid name rejected: $name")
            }
        }

        // 5c. SBI Credit Card "spent at <MERCHANT>" pattern
        SBI_SPENT_AT_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: SBI_SPENT_AT")
                val name = cleanName(rawName, trace)
                if (isValidName(name)) {
                    return Counterparty(name, null, CounterpartyType.MERCHANT, trace)
                } else trace.add("Invalid name rejected: $name")
            }
        }

        // 5d. Generic "<BANK> Credit Card ending XXNNNN at <MERCHANT> on <DATE>" (IDFC etc.)
        GENERIC_CARD_ENDING_AT_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: GENERIC_CARD_ENDING_AT")
                val name = cleanName(rawName, trace)
                if (isValidName(name)) {
                    return Counterparty(name, null, CounterpartyType.MERCHANT, trace)
                } else trace.add("Invalid name rejected: $name")
            }
        }

        // 5e. Axis slash-separated "Spent / Card no. XXNNNN / INR X / <date> / <MERCHANT> /"
        AXIS_SLASH_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: AXIS_SLASH")
                val name = cleanName(rawName.trim(), trace)
                if (isValidName(name)) {
                    return Counterparty(name, null, CounterpartyType.MERCHANT, trace)
                } else trace.add("Invalid name rejected: $name")
            }
        }
        
        // 6. ICICI "debited; <NAME> credited" - Usually P2P but check for merchants first
        ICICI_DEBITED_CREDITED_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: ICICI_DEBITED_CREDITED")
                val name = cleanName(rawName, trace)
                if (isValidName(name)) {
                    // Check if this is a known merchant/credit card service, not a person
                    val type = classifyDebitedCreditedName(name)
                    trace.add("Classified as ${type.name}")
                    return Counterparty(name, extractUpiId(body), type, trace)
                } else trace.add("Invalid name rejected: $name")
            }
        }

        // 6b. IDFC FIRST Bank "debited by Rs XXX; <NAME> credited" - Usually P2P but check for merchants first
        IDFC_DEBITED_CREDITED_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: IDFC_DEBITED_CREDITED")
                val name = cleanName(rawName, trace)
                if (isValidName(name)) {
                    val type = classifyDebitedCreditedName(name)
                    trace.add("Classified as ${type.name}")
                    return Counterparty(name, extractUpiId(body), type, trace)
                } else trace.add("Invalid name rejected: $name")
            }
        }
        
        // 7. NEFT Salary deposits
        NEFT_SALARY_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: NEFT_SALARY")
                val name = cleanEmployerName(rawName)
                if (name.isNotBlank() && name.length >= 3) {
                    trace.add("Cleaned Employer Name")
                    return Counterparty(name, null, CounterpartyType.MERCHANT, trace) // Employer is like a merchant
                }
            }
        }

        // 7b. ICICI NEFT deposits (alternative format)
        ICICI_NEFT_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: ICICI_NEFT")
                val name = cleanEmployerName(rawName)
                if (name.isNotBlank() && name.length >= 3) {
                    trace.add("Cleaned Employer Name: $name")
                    return Counterparty(name, null, CounterpartyType.MERCHANT, trace)
                }
            }
        }

        // 8. FT/Clearing deposits (stock sales, etc.)
        FT_DEPOSIT_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: FT_DEPOSIT")
                val name = cleanEmployerName(rawName)
                if (name.isNotBlank() && name.length >= 3) {
                    trace.add("Cleaned Entity Name")
                    return Counterparty(name, null, CounterpartyType.MERCHANT, trace)
                }
            }
        }

        // 8.5 ATM "withdrawn at" pattern
        WITHDRAWN_AT_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: WITHDRAWN_AT")
                val name = cleanName(rawName, trace)
                if (isValidName(name)) {
                    // ATM withdrawals are effectively Merchant transactions (Cash)
                    return Counterparty(name, null, CounterpartyType.MERCHANT, trace)
                }
            }
        }

        // 8.6 ATM "Withdrawn Rs.X From <BANK> Card xNNNN At <LOCATION> On" (HDFC ordering)
        WITHDRAWN_FROM_AT_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: WITHDRAWN_FROM_AT")
                val name = cleanName(rawName, trace)
                if (isValidName(name)) {
                    return Counterparty(name, null, CounterpartyType.MERCHANT, trace)
                }
            }
        }

        // 8.7 FASTag toll: "Rs.X paid at <PLAZA> for <VEHICLE_NO> ... FASTag"
        if (body.contains("FASTag", ignoreCase = true)) {
            FASTAG_PAID_AT_PATTERN.matcher(body).let { m ->
                if (m.find()) {
                    val rawName = m.group(1) ?: return@let
                    trace.add("Matched Template: FASTAG_PAID_AT")
                    val name = cleanName(rawName, trace)
                    if (isValidName(name)) {
                        return Counterparty("FASTag $name", null, CounterpartyType.MERCHANT, trace)
                    }
                }
            }
        }
        
        // 8.8 ICICI incoming credit with sender name ("is credited with Rs X ... from NAME .")
        ICICI_CREDITED_FROM_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: ICICI_CREDITED_FROM")
                val name = cleanName(rawName, trace)
                if (isValidName(name)) {
                    return Counterparty(name, extractUpiId(body), classifyName(name, transactionType), trace)
                } else trace.add("Invalid name rejected: $name")
            }
        }

        // 8.9 HDFC incoming IMPS with sender name ("For IMPS -MEKALA NIVAS- 6068...")
        HDFC_IMPS_RECEIVED_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: HDFC_IMPS_RECEIVED")
                val name = cleanName(rawName, trace)
                if (isValidName(name)) {
                    return Counterparty(name, null, classifyName(name, transactionType), trace)
                } else trace.add("Invalid name rejected: $name")
            }
        }

        // 9. Credit Alert VPA
        CREDIT_ALERT_VPA_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                trace.add("Matched Template: CREDIT_ALERT_VPA")
                val upi = m.group(1) ?: return@let
                val name = extractNameFromUpi(upi)
                val type = classifyVpa(upi, transactionType)
                return if (name != null && type != CounterpartyType.UNKNOWN) {
                    trace.add("Extracted Name from UPI: $name")
                    Counterparty(name, upi, type, trace)
                } else {
                    trace.add("Could not extract valid name/type from VPA")
                    Counterparty(null, upi, CounterpartyType.UNKNOWN, trace)
                }
            }
        }
        
        // 10. Fallback: Extract UPI ID if present
        val upi = extractUpiId(body)
        if (upi != null) {
            trace.add("Fallback: Found UPI ID")
            val name = extractNameFromUpi(upi)
            val type = classifyVpa(upi, transactionType)
            return if (name != null && type != CounterpartyType.UNKNOWN) {
                trace.add("Extracted Name from UPI: $name")
                Counterparty(name, upi, type, trace)
            } else {
                trace.add("Could not extract valid name/type from VPA")
                Counterparty(null, upi, CounterpartyType.UNKNOWN, trace)
            }
        }
        
        // 11. Fee/charge/EMI purpose ("debited ... towards <purpose>") - late fallback so it
        // only names rows nothing else could (bank charges, direct-debit EMIs).
        DEBITED_TOWARDS_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val name = cleanName(m.group(1) ?: return@let, trace)
                trace.add("Matched Template: DEBITED_TOWARDS")
                if (isValidName(name)) {
                    return Counterparty(name, null, CounterpartyType.MERCHANT, trace)
                }
            }
        }

        // No counterparty found
        trace.add("No Template Matched")
        return Counterparty(null, null, CounterpartyType.UNKNOWN, trace)
    }

    // --- UTILITIES ---

    // NOTE: cleanVpaMerchantName/splitCamelCase were removed here - they were dead code
    // (only referenced each other, never called from the extraction flow, which uses
    // extractMerchantFromVpa instead).

    private fun extractUpiId(body: String): String? {
        val m = UPI_PATTERN.matcher(body)
        return if (m.find()) m.group(1) else null
    }
    
    /**
     * Extract a human-readable name from a UPI ID.
     * Returns null for junk VPAs.
     */
    private fun extractNameFromUpi(upiId: String): String? {
        val prefix = upiId.substringBefore("@")
        val lower = prefix.lowercase()
        
        // Junk VPA prefixes - return null (incl. paytmqr…; the VPA itself becomes the identity)
        if (isJunkVpaPrefix(lower)) {
            return null
        }

        // Clean the prefix
        val cleaned = prefix
            .replace(".", " ")
            .replace("_", " ")
            .replace("-", " ")
            .trim()

        return if (cleaned.length >= 3 && cleaned.any { it.isLetter() || it.isDigit() }) {
            cleaned
        } else null
    }
    
    /**
     * Check if a VPA prefix is junk (paytmqr, q12345, phone numbers, etc.)
     * Returns false for known merchant VPAs that should be extracted.
     */
    private fun isJunkVpaPrefix(lower: String): Boolean {
        // First check if it's a known merchant - those are NEVER junk
        for (pattern in KNOWN_MERCHANT_VPAS.keys) {
            if (lower.contains(pattern)) return false
        }

        // Check for meaningful merchant VPAs (not generic payment gateways)
        // These VPAs have actual merchant info embedded
        if (lower.contains(".rzp") ||  // blinkitjkb.rzp
            lower.endsWith("rzp") ||
            lower.endsWith("jkb") ||
            lower.endsWith("esbz")) {
            // These might have merchant names, let extraction proceed
            return false
        }

        // Generic/junk VPA patterns. Paytm QR handles (paytmqr…, paytm.<code>, paytm-<id>)
        // are anonymous per-shop terminals - keep the VPA as identity so different shops stay
        // distinct instead of all collapsing to "Paytm".
        return lower.startsWith("paytmqr") ||
               lower.startsWith("paytm.") ||
               lower.startsWith("paytm-") ||
               lower.startsWith("bharatpe") && !lower.contains("merchant") ||
               lower.startsWith("phonemerchant") ||
               lower.startsWith("gpay-") && lower.matches(Regex("gpay-\\d+.*")) ||  // gpay-12345 is junk
               lower.startsWith("ezetap") ||
               lower.startsWith("vyapar.") && lower.matches(Regex("vyapar\\.\\d+.*")) ||  // vyapar.123 is junk
               lower.startsWith("googlepay") ||
               lower.matches(Regex("^\\d{10}.*")) ||  // Phone numbers as VPA
               lower.matches(Regex("^q\\d{6,}.*"))    // Q-codes without merchant info
    }

    private val KNOWN_MERCHANT_VPAS = mapOf(
        // Food Delivery
        "zeptonow" to "Zepto",
        "swiggy" to "Swiggy",
        "zomato" to "Zomato",
        "licious" to "Licious",
        "cf.licious" to "Licious",
        "blinkit" to "Blinkit",
        "instamart" to "Instamart",
        "bigbasket" to "BigBasket",
        "gokhana" to "Gokhana",
        "dunzo" to "Dunzo",

        // Shopping
        "myntra" to "Myntra",
        "flipkart" to "Flipkart",
        "amazon" to "Amazon",
        "ajio" to "Ajio",
        "nykaa" to "Nykaa",
        "meesho" to "Meesho",

        // Entertainment
        "dineout" to "Dineout",
        "bookmyshow" to "BookMyShow",
        "priveplex" to "PVR",
        "pvrinox" to "PVR Inox",
        "gameonlevel" to "Game On",
        "netflix" to "Netflix",
        "hotstar" to "Hotstar",
        "spotify" to "Spotify",

        // Restaurants/Food
        "mandikinga" to "Mandi King",
        "mandiking" to "Mandi King",
        "svmbowling" to "SVM Bowling",
        "lasthousecoffee" to "Last House Coffee",
        "starbucks" to "Starbucks",
        "dominos" to "Dominos",
        "mcdonalds" to "McDonalds",
        "kfc" to "KFC",
        "subway" to "Subway",
        "burgerking" to "Burger King",
        "pizzahut" to "Pizza Hut",

        // Travel
        "uber" to "Uber",
        "ola" to "Ola",
        "rapido" to "Rapido",
        "redbus" to "RedBus",
        "makemytrip" to "MakeMyTrip",
        "goibibo" to "Goibibo",
        "irctc" to "IRCTC",
        "cleartrip" to "Cleartrip",

        // Payments/Fintech
        "cred" to "CRED",
        // NOTE: "paytm" is deliberately NOT here. It's a payment GATEWAY, not a merchant -
        // "paytmqr6lrp6h@ptys" is an anonymous shop QR. Mapping it to "Paytm" collapsed every
        // distinct shop into one name AND (via isJunkVpaPrefix's known-merchant bypass) blocked
        // the VPA fallback. Left out so these keep their VPA as identity.
        "phonepe" to "PhonePe",
        "googlepay" to "Google Pay",
        "amazonpay" to "Amazon Pay",

        // Utilities/Bills
        "airtel" to "Airtel",
        "jio" to "Jio",
        "tatasky" to "Tata Sky",
        "tatapay" to "Tata Payments",

        // Others
        "zerodha" to "Zerodha",
        "groww" to "Groww",
        "upstox" to "Upstox",
        "udemy" to "Udemy",
        "adobe" to "Adobe",
        "google" to "Google",
        "microsoft" to "Microsoft",
        "apple" to "Apple",
        "youtube" to "YouTube",
        "claude" to "Claude AI",
        "anthropic" to "Anthropic",
        "openai" to "OpenAI",
        "jiomart" to "JioMart",
        "pharmeasy" to "PharmEasy",
        "netmeds" to "Netmeds",
        "eatsure" to "EatSure",
        "sonyliv" to "SonyLIV",
        "urbancompany" to "Urban Company",
        "lenskart" to "Lenskart",
        "firstcry" to "FirstCry",
        "snapdeal" to "Snapdeal",
        "ixigo" to "Ixigo",
        "easemytrip" to "EaseMyTrip"
    )
    
    private fun extractMerchantFromVpa(vpaPrefix: String): String? {
        val lower = vpaPrefix.lowercase()

        // Known merchant VPA patterns - check first
        for ((pattern, name) in KNOWN_MERCHANT_VPAS) {
            if (lower.contains(pattern)) return name
        }

        // Junk VPA prefixes - return null (no valid merchant)
        if (isJunkVpaPrefix(lower)) {
            return null
        }

        // Handle VPA patterns like "blinkitjkb.rzp" or "svmbowling.67079608"
        // Extract the meaningful part before dots/numbers
        var cleaned = vpaPrefix
            .substringBefore(".")  // Take part before first dot
            .substringBefore("@")  // Remove @bank suffix if present

        // Remove gateway prefixes
        val gatewayPrefixes = listOf("paytm", "bharatpe", "ezetap", "gpay", "phonepe", "razorpay")
        for (prefix in gatewayPrefixes) {
            if (cleaned.lowercase().startsWith(prefix) && cleaned.length > prefix.length + 2) {
                cleaned = cleaned.substring(prefix.length)
            }
        }

        // Remove trailing digits and common suffixes
        cleaned = cleaned
            .replace(REGEX_VPA_TRAILING_DIGITS, "")  // Remove trailing numbers
            .replace(REGEX_VPA_SEPARATORS, " ")      // Replace separators with spaces
            .replace(Regex("(?i)(rzp|payu|jkb|esbz)$"), "")  // Remove gateway suffixes
            .trim()

        // Final validation and formatting
        return if (cleaned.length >= 3 && cleaned.any { it.isLetter() }) {
            // Check known merchants one more time with cleaned name
            val cleanedLower = cleaned.lowercase()
            for ((pattern, name) in KNOWN_MERCHANT_VPAS) {
                if (cleanedLower.contains(pattern)) return name
            }

            // Format as title case
            cleaned.split(" ")
                .filter { it.isNotBlank() && it.length > 1 }
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
                .takeIf { it.isNotBlank() && !isJunkVpaPrefix(it.lowercase()) }
        } else null
    }
    
    /**
     * Clean employer name from NEFT/FT messages
     * IMPORTANT: For FT deposits, preserve entity names like "INDIAN CLEARING CORPORATION"
     * which are crucial for investment redemption detection
     */
    private fun cleanEmployerName(rawName: String): String {
        val cleaned = rawName
            .replace(REGEX_SPACES, " ")
            .trim()

        // DON'T remove CORPORATION/LIMITED/CLEARING for known investment entities
        val upper = cleaned.uppercase()
        val isInvestmentEntity = upper.contains("INDIAN CLEARING") ||
                                 upper.contains("ICCL") ||
                                 upper.contains("ZERODHA") ||
                                 upper.contains("CLEARING CORPORATION")

        val final = if (isInvestmentEntity) {
            // Keep full name for investment entities (up to 5 words)
            cleaned
                .split(" ")
                .filter { it.isNotBlank() }
                .take(5)
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
        } else {
            // Remove corporate suffixes for employers/salary sources
            cleaned
                .replace(REGEX_EMPLOYER_NOISE, "")
                .trim()
                .split(" ")
                .filter { it.isNotBlank() }
                .take(3) // Max 3 words
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
        }

        return final
    }
    
    private fun cleanName(name: String, trace: MutableList<String>? = null): String {
        val original = name
        var cleaned = name.trim()
        
        // Remove common patterns
        val preReplace = cleaned
        cleaned = cleaned.replace(REGEX_PREPOSITION_PREFIX, "")
        if (preReplace != cleaned) trace?.add("Removed Preposition prefix")
        
        // Fix Axis/Payment Gateway prefixes
        if (cleaned.contains("*")) {
            val parts = cleaned.split("*")
            if (parts.size > 1) {
                // Handle cases like "PYU*Swiggy", "Razorpay*Sw", "CAS*Swiggy"
                val prefix = parts[0].uppercase()
                val suffix = parts[1]
                
                // Known gateway prefixes ("RAZ" = Razorpay's short code on IDFC card SMS)
                if (prefix in listOf("PYU", "CAS", "RAZ", "RAZORPAY", "PAYU", "CCAVENUE", "HDFC", "AXIS", "ICICI", "AIRTEL", "PAYTM")) {
                    cleaned = suffix
                    trace?.add("Stripped Gateway Prefix: $prefix")
                }
            }
        }
        
        // Handle truncated merchant names (common in Axis Bank SMS)
        val truncatedMerchants = mapOf(
            "sw" to "Swiggy",
            "swi" to "Swiggy",
            "swiggy" to "Swiggy",
            "instama" to "Instamart",
            "bundl techn" to "Bundle Technologies",
            "bundl" to "Bundle",
            "airtel paym" to "Airtel",
            "airtel" to "Airtel",
            "tatapay" to "Tata Payments",
            "tatapayments" to "Tata Payments",
            "amazon pay in e" to "Amazon",
            "amazon india cy" to "Amazon",
            "amazon" to "Amazon",
            "youtubegoogle" to "YouTube",
            "google play" to "Google Play",
            "netflix" to "Netflix",
            "udemy subscript" to "Udemy",
            "adobe premiere" to "Adobe",
            "claude.ai subsc" to "Claude AI",
            "avenue supermar" to "Avenue Supermarts",
            "dineout" to "Dineout",
            "bookmyshow" to "BookMyShow",
            "gameonlevelupyourf" to "Game On",
            "gameonlevelupyour" to "Game On",
            "prasads" to "Prasads",
            "mapro garden llp" to "Mapro Garden",
            "a food affair" to "A Food Affair"
        )

        val lowerCleaned = cleaned.lowercase().trim()
        for ((partial, fullName) in truncatedMerchants) {
            if (lowerCleaned == partial || lowerCleaned.startsWith(partial)) {
                trace?.add("Normalized truncated '$cleaned' -> $fullName")
                cleaned = fullName
                break
            }
        }
        
        // Clean trailing dots and digits (e.g. "MANDIKING.6603" -> "MANDIKING")
        if (cleaned.matches(REGEX_TRAILING_DOTS_DIGITS_CHECK)) {
             val pre = cleaned
             cleaned = cleaned.replace(REGEX_TRAILING_DOTS_DIGITS, "")
             if (pre != cleaned) trace?.add("Stripped trailing dots/digits")
        }
        
        // Clean UP digits in the middle (SWIGGY8@ybl -> SWIGGY)
        // If it's a VPA-like string or just has digits at end purely
        if (cleaned.matches(REGEX_ALPHA_NUM_DIGITS_CHECK)) {
             val pre = cleaned
             cleaned = cleaned.replace(REGEX_VPA_TRAILING_DIGITS, "")
             if (pre != cleaned) trace?.add("Stripped end-digits from alpha-numeric name")
        }

        cleaned = cleaned
            .replace(REGEX_SPACES, " ")  // Collapse multiple spaces
            .replace(REGEX_NEWLINE_REST, "")  // Remove everything after newline
            .replace(REGEX_TRAILING_LOCATION_NOISE, "")  // Remove trailing noise
            .trim()
            
        if (cleaned != original && trace?.isEmpty() == true) {
             trace.add("General cleanup (spacing/newlines)")
        }
        return cleaned
    }
    
    private fun isValidName(name: String): Boolean {
        val lower = name.lowercase()
        
        // Blocklist
        val blocked = setOf(
            "not you", "on", "at", "to", "via", "from", "for", "by",
            "upi", "imps", "neft", "ref", "info", "update", "alert",
            "your card", "card ending", "hdfc bank", "icici bank", "sbi card",
            "rs", "inr", "payment", "transaction", "txn", "https", "http",
            "call", "sms", "block"
        )
        
        // Word-boundary prefix match: "not you? call" is junk, but a real person whose name
        // merely BEGINS with a blocked token must survive - "BYRA TARUN TEJA" starts with
        // "by", "Atul" with "at", "Onkar" with "on". Raw startsWith silently discarded them.
        if (blocked.any { b ->
                lower == b || (lower.startsWith(b) && lower.length > b.length &&
                    !lower[b.length].isLetterOrDigit())
            }) return false
        if (name.length < 3) return false
        if (name.all { it.isDigit() || it.isWhitespace() }) return false
        if (lower.matches(REGEX_DIGITS_ONLY_SHORT)) return false  // Just numbers like "100"
        
        return true
    }
    
    /**
     * Special classification for "debited; <NAME> credited" patterns.
     * This pattern can be either P2P (person-to-person) or merchant payment (like credit card bills).
     * Check for known merchants first before defaulting to PERSON.
     */
    private fun classifyDebitedCreditedName(name: String): CounterpartyType {
        val upper = name.uppercase()

        // Credit Card Payment Services / payment apps - these are MERCHANTS, not PERSON.
        // Core issuer/service list is shared via SmsConstants.isCreditCardServiceName
        // (word-boundary "CRED" matching); payment-app names are specific to this check.
        if (SmsConstants.isCreditCardServiceName(upper) ||
            upper.contains("BILL DESK") ||
            upper.contains("PAYTM") ||
            upper.contains("PHONEPE") ||
            upper.contains("GOOGLEPAY") ||
            upper.contains("GOOGLE PAY")) {
            return CounterpartyType.MERCHANT
        }

        // Known Merchants that might appear in this pattern
        for ((pattern, _) in KNOWN_MERCHANT_VPAS) {
            if (SmsConstants.containsToken(upper, pattern.uppercase())) {
                return CounterpartyType.MERCHANT
            }
        }

        // Check for company/business indicators
        // BUGFIX: "INC" and "CORP" as raw substrings matched inside ordinary names/words
        // (e.g. "INC" inside "PRINCE", "CORP" inside "CORPUS"). Word-boundary matching avoids
        // misclassifying a person named e.g. "Prince Kumar" as a business.
        if (upper.contains("TECHNOLOGIES") ||
            upper.contains("PRIVATE") ||
            upper.contains("LIMITED") ||
            upper.contains("BROKING") ||
            upper.contains("PVT LTD") ||
            upper.endsWith("RTC") ||   // state road transport: APSRTC, TSRTC, KSRTC, MSRTC
            SmsConstants.containsToken(upper, "LLC") ||
            SmsConstants.containsToken(upper, "INC") ||
            SmsConstants.containsToken(upper, "CORP")) {
            return CounterpartyType.MERCHANT
        }

        // Default to PERSON for this pattern (typical P2P transfer)
        return CounterpartyType.PERSON
    }

    private fun classifyName(name: String, transactionType: TransactionType): CounterpartyType {
        // For Expenses (card spends, shopping), always MERCHANT
        if (transactionType == TransactionType.EXPENSE) {
            return CounterpartyType.MERCHANT
        }
        
        // For TRANSFER and INCOME, check if looks like a person name (2-4 words, all alphabetic)
    val words = name.split(" ").filter { it.isNotBlank() }
    val allAlphabetic = words.all { w -> w.all { it.isLetter() || it == '.' } }
    val upper = name.uppercase()

    // KNOWN MERCHANTS that look like people or have 2-3 words
    if (upper.contains("ZERODHA") || 
        upper.contains("ICCL") || 
        upper.contains("INDIAN CLEARING") || 
        upper.contains("TECHNOLOGIES") || 
        upper.contains("PRIVATE") || 
        upper.contains("LIMITED") ||
        upper.contains("BROKING") ||
        upper.contains("CRED CLUB") ||
        upper.contains("CRED APP") ||
        upper.contains("AMEX") ||
        upper.contains("ONE CARD") ||
        upper.contains("SBI CARD")) {
        return CounterpartyType.MERCHANT
    }
    
    return if (words.size in 2..4 && allAlphabetic) {
            CounterpartyType.PERSON
        } else if (words.size == 1 && words[0].all { it.isLetter() } && words[0].length >= 3) {
            // Single word names like "SHIVKUMAR", "PRATIKSHA" are also PERSON
            CounterpartyType.PERSON
        } else {
            CounterpartyType.MERCHANT
        }
    }
    
    private fun classifyVpa(upiId: String, transactionType: TransactionType): CounterpartyType {
        val prefix = upiId.substringBefore("@").lowercase()
        
        // 0. Known Merchants -> MERCHANT
        for (pattern in KNOWN_MERCHANT_VPAS.keys) {
            if (prefix.contains(pattern)) return CounterpartyType.MERCHANT
        }

        // Q-codes (Card-UPI) -> MERCHANT (Offline Merchant)
        if (prefix.matches(Regex("^q\\d+.*"))) {
            return CounterpartyType.MERCHANT
        }
        
        // Junk VPAs
        if (isJunkVpaPrefix(prefix)) {
            return CounterpartyType.UNKNOWN
        }
        
        // FIX #2: Cashback/Reward VPAs are MERCHANT (system), not PERSON
        // Example: bhimcashback@hdfcbank, googlepay@axisbank (rewards), etc.
        if (CASHBACK_VPA_PATTERNS.any { prefix.contains(it) }) {
            return CounterpartyType.MERCHANT
        }
        
        // For Expenses, default to MERCHANT (even for VPAs)
        if (transactionType == TransactionType.EXPENSE) {
            return CounterpartyType.MERCHANT
        }
        
        // For Income/Transfer, VPAs are likely PERSON
        return CounterpartyType.PERSON
    }
    
    /**
     * Extract the account holder name from NEFT/deposit messages.
     * 
     * NEFT format: "NEFT Cr-{IFSC}-{SENDER}-{RECEIVER_NAME}-{REF}"
     * Example: "NEFT Cr-BOFA0CN6215-OPEN TEXT TECHNOLOGIES-GODALA SAIKUMAR REDDY-BOFAH25364"
     * 
     * Returns the receiver name (which is the user's account holder name) or null.
     */
    fun extractAccountHolderName(body: String): String? {
        // Pattern 1: NEFT Cr-IFSC-SENDER-RECEIVER-REF
        // Use non-greedy match with [^-]+ for sender/receiver
        val neftPattern = Pattern.compile(
            "NEFT\\s+Cr-([A-Z0-9]+)-([^-]+)-([^-]+)-([A-Z0-9]+)",
            Pattern.CASE_INSENSITIVE
        )
        
        neftPattern.matcher(body).let { m ->
            if (m.find()) {
                val name = m.group(3)?.trim() // Receiver is group 3
                if (name != null && name.length >= 3 && name.contains(" ")) {
                    // Skip if looks like a reference number (mostly digits)
                    if (name.count { it.isDigit() } > name.length / 2) return@let
                    return name.uppercase()
                }
            }
        }
        
        // Pattern 2: credited to HDFC Bank A/c XX... for NEFT...-SENDER-RECEIVER-REF
        val depositPattern = Pattern.compile(
            "for\\s+NEFT[^-]*-([^-]+)-([^-]+)-[A-Z0-9]+",
            Pattern.CASE_INSENSITIVE
        )
        
        depositPattern.matcher(body).let { m ->
            if (m.find()) {
                val name = m.group(2)?.trim() // Receiver is group 2
                if (name != null && name.length >= 3 && name.contains(" ")) {
                    if (name.count { it.isDigit() } > name.length / 2) return@let
                    return name.uppercase()
                }
            }
        }
        
        return null
    }
    
    /**
     * Detect if an NEFT message is a self-transfer.
     *
     * Self-transfer pattern: NEFT Cr-{IFSC}-{SENDER_NAME}-{RECEIVER_NAME}-{REF}
     * When SENDER_NAME == RECEIVER_NAME (or very similar), it's a self-transfer.
     *
     * Example: "NEFT Cr-SURY0BK0000-GODALA SAIKUMAR REDDY-GODALA SAIKUMAR REDDY-SURYN25351683110"
     * This is money moving from user's Suryoday account to user's HDFC account.
     */
    fun isNeftSelfTransfer(body: String): Boolean {
        // Pattern to extract both sender and receiver names from NEFT
        // NEFT Cr-{IFSC}-{SENDER}-{RECEIVER}-{REF}
        // Use non-greedy match for names (anything between dashes that isn't a dash)
        val neftPattern = Pattern.compile(
            "NEFT\\s+Cr-([A-Z0-9]+)-([^-]+)-([^-]+)-([A-Z0-9]+)",
            Pattern.CASE_INSENSITIVE
        )

        neftPattern.matcher(body).let { m ->
            if (m.find()) {
                val sender = m.group(2)?.trim()?.uppercase() ?: return false
                val receiver = m.group(3)?.trim()?.uppercase() ?: return false

                // Skip if either name looks like a reference number (mostly digits)
                if (sender.count { it.isDigit() } > sender.length / 2) return false
                if (receiver.count { it.isDigit() } > receiver.length / 2) return false

                // Check if sender and receiver are the same or very similar
                if (sender == receiver) {
                    return true
                }

                // IMPROVED: Better fuzzy matching for name variations
                if (areNamesEquivalent(sender, receiver)) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Improved name matching that handles various name formats:
     * - Initials: "S KUMAR" matches "SAIKUMAR"
     * - Partial names: "GODALA S REDDY" matches "GODALA SAIKUMAR REDDY"
     * - Word order variations: "KUMAR SAIKUMAR" matches "SAIKUMAR KUMAR"
     * - Middle name variations: "GODALA SAIKUMAR" matches "GODALA SAIKUMAR REDDY"
     *
     * Delegates to the shared [com.saikumar.expensetracker.util.NameMatcher] so this
     * logic has a single source of truth (previously duplicated in 3 places).
     */
    private fun areNamesEquivalent(name1: String, name2: String): Boolean =
        com.saikumar.expensetracker.util.NameMatcher.areNamesEquivalent(name1, name2)

    /**
     * Extract NEFT source information (IFSC + sender) for salary pattern tracking.
     * 
     * NEFT format: "NEFT Cr-{IFSC}-{SENDER}-{RECEIVER}-{REF}"
     * Example: "NEFT Cr-BOFA0CN6215-OPEN TEXT TECHNOLOGIES-GODALA SAIKUMAR-BOFAH25364"
     * 
     * @return Pair of (IFSC, Sender) or null if not an NEFT message
     */
    fun extractNeftSource(body: String): Pair<String, String>? {
        // Pattern to extract IFSC and sender from NEFT credit
        // NEFT Cr-{IFSC}-{SENDER}-{RECEIVER}-{REF}
        // Use non-greedy match with [^-]+ for sender
        val neftPattern = Pattern.compile(
            "NEFT\\s+Cr-([A-Z0-9]+)-([^-]+)-([^-]+)-([A-Z0-9]+)",
            Pattern.CASE_INSENSITIVE
        )
        
        neftPattern.matcher(body).let { m ->
            if (m.find()) {
                val ifsc = m.group(1)?.trim()?.uppercase() ?: return null
                val sender = m.group(2)?.trim()?.uppercase() ?: return null
                
                // Skip if sender looks like a reference number (mostly digits)
                if (sender.count { it.isDigit() } > sender.length / 2) return@let
                
                // Normalize sender (remove trailing spaces, normalize whitespace)
                val normalizedSender = sender.split("\\s+".toRegex())
                    .filter { it.isNotEmpty() }
                    .joinToString(" ")
                
                if (normalizedSender.length >= 3) {
                    return Pair(ifsc, normalizedSender)
                }
            }
        }
        
        // Also check ICICI format: "NEFT-IFSC-SENDER"
        val iciciPattern = Pattern.compile(
            "NEFT-([A-Z0-9]+)-([^-\\.]+)",
            Pattern.CASE_INSENSITIVE
        )
        
        iciciPattern.matcher(body).let { m ->
            if (m.find()) {
                val ifsc = m.group(1)?.trim()?.uppercase() ?: return null
                val sender = m.group(2)?.trim()?.uppercase() ?: return null
                
                if (sender.count { it.isDigit() } > sender.length / 2) return@let
                
                val normalizedSender = sender.split("\\s+".toRegex())
                    .filter { it.isNotEmpty() }
                    .joinToString(" ")
                
                if (normalizedSender.length >= 3) {
                    return Pair(ifsc, normalizedSender)
                }
            }
        }
        
        return null
    }
}
