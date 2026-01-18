package com.saikumar.expensetracker.sms

object SenderClassifier {

    enum class SenderType {
        BANK, PENSION, INVESTMENT, INSURANCE, EXCLUDED
    }

    private val BANK_PATTERNS = listOf(
        "HDFCBK", "HDFCBN", "UNIONB", "ICICIB", "ICICIT",
        "SBIUPI", "SBIPSG", "SBIINB", "SCISMS", "ATMSBI", "CBSSBI", "BOIIND"
    )
    private val PENSION_PATTERNS = listOf("EPFOHO")
    private val INVESTMENT_PATTERNS = listOf("PTNNPS")
    private val INSURANCE_PATTERNS = listOf("ICICIP", "POLBAZ")
    private val EXCLUDED_PATTERNS = listOf(
        "LSKART", "RAPIDO", "GOFYND", "CREDIN", "MAMERT", "REDBUS", "FLPKRT",
        "JIOPAY", "JioPay", "AIRTEL", "AIRSLF", "AIROPT", "Docomo", "DOCOMO", "IMPINF", "RECHRG",
        "PHONPE", "PAYTM", "iPaytm", "IPAYTM", "TMESEV"
    )

    fun classify(senderId: String): SenderType {
        val upper = senderId.uppercase()
        val numericPart = senderId.filter { it.isDigit() }
        if (numericPart.length == 6 && numericPart.startsWith("65")) return SenderType.EXCLUDED
        if (EXCLUDED_PATTERNS.any { upper.contains(it.uppercase()) }) return SenderType.EXCLUDED
        if (BANK_PATTERNS.any { upper.contains(it) }) return SenderType.BANK
        if (PENSION_PATTERNS.any { upper.contains(it) }) return SenderType.PENSION
        if (INVESTMENT_PATTERNS.any { upper.contains(it) }) return SenderType.INVESTMENT
        if (INSURANCE_PATTERNS.any { upper.contains(it) }) return SenderType.INSURANCE
        return SenderType.EXCLUDED
    }

    fun isTransactionSender(senderId: String) = classify(senderId) != SenderType.EXCLUDED
}
