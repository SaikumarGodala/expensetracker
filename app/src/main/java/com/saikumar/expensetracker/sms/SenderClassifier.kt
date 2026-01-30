package com.saikumar.expensetracker.sms

object SenderClassifier {

    enum class SenderType {
        BANK, PENSION, INVESTMENT, INSURANCE, VIRTUAL_CARD, EXCLUDED
    }

    private val BANK_PATTERNS = listOf(
        // HDFC Bank
        "HDFCBK", "HDFCBN", "HDFCCC", "HDFCNB",
        // ICICI Bank
        "ICICIB", "ICICIT", "ICICIS", "ICICIW", "ICIBNK", "ICICI",
        // SBI
        "SBIUPI", "SBIPSG", "SBIINB", "SCISMS", "ATMSBI", "CBSSBI", "SBICRD",
        // Union Bank
        "UNIONB", "UNBINB",
        // Bank of India
        "BOIIND", "BOIALR",
        // Axis Bank
        "AXISBK", "AXSBNK", "AXISMF", "AXBANK",
        // Kotak Mahindra Bank
        "KOTAKB", "KOTAKM", "KOTKBK", "KOTAK",
        // YES Bank
        "YESBK", "YESBNK",
        // IndusInd Bank
        "INDUSB", "INDUSL", "INDBNK",
        // IDFC First Bank
        "IDFCFB", "IDFCBK", "IDFC",
        // Federal Bank
        "FEDBNK", "FEDBK",
        // RBL Bank
        "RBLBNK", "RBLBK",
        // Canara Bank
        "CANBNK", "CANARA", "CNRBIB",
        // Punjab National Bank
        "PUNJNB", "PNBSMS", "PNBALR",
        // Bank of Baroda
        "BOBNEW", "BOBIBN", "BOBSMS",
        // Indian Bank
        "INDIANB",
        // Central Bank
        "CNTBNK", "CENBNK",
        // Bank of Maharashtra
        "BOMBNK", "MAHABNK",
        // UCO Bank
        "UCOBNK", "UCOBK",
        // Indian Overseas Bank
        "IOBSMS", "IOBBNK",
        // South Indian Bank
        "SIBSMS", "SIBNEW",
        // Karur Vysya Bank
        "KVBSMS", "KVBANK",
        // City Union Bank
        "CUBSMS", "CITYUB",
        // DBS Bank
        "DBSBNK", "DBSSMS",
        // Standard Chartered
        "SCBSMS", "STANCB"
    )
    
    // Virtual/Fintech Card Senders
    private val VIRTUAL_CARD_PATTERNS = listOf(
        // Slice
        "SLCOIN", "SLICEP", "SLICE",
        // OneCard
        "ONEFIN", "ONECRD", "ONECAR",
        // Jupiter Bank
        "JUPITB", "JUPTRB", "JUPITER",
        // Fi Money
        "FIBANK", "FIMONY", "FIMONE",
        // NiyoX
        "NIYPAY", "NIYOBK", "NIYOX",
        // Uni Card
        "UNICRD", "UNIAPP",
        // Fibe (EarlySalary)
        "FIBEIN", "EARLYS",
        // LazyPay
        "LAZYPY", "LAZYPAY",
        // Simpl
        "SIMPLP", "SIMPL",
        // Amazon Pay ICICI
        "AMZNPY", "APICIC"
    )
    private val PENSION_PATTERNS = listOf("EPFOHO")
    private val INVESTMENT_PATTERNS = listOf("PTNNPS")
    private val INSURANCE_PATTERNS = listOf("ICICIP", "POLBAZ")
    private val EXCLUDED_PATTERNS = listOf(
        "LSKART", "RAPIDO", "GOFYND", "CREDIN", "MAMERT", "REDBUS", "FLPKRT",
        "JIOPAY", "AIRTEL", "AIRSLF", "AIROPT", "DOCOMO", "IMPINF", "RECHRG",
        "PHONPE", "PAYTM", "IPAYTM", "TMESEV"
    )

    fun classify(senderId: String): SenderType {
        val upper = senderId.uppercase()
        val numericPart = senderId.filter { it.isDigit() }
        if (numericPart.length == 6 && numericPart.startsWith("65")) return SenderType.EXCLUDED
        if (EXCLUDED_PATTERNS.any { upper.contains(it) }) return SenderType.EXCLUDED
        if (BANK_PATTERNS.any { upper.contains(it) }) return SenderType.BANK
        if (VIRTUAL_CARD_PATTERNS.any { upper.contains(it) }) return SenderType.VIRTUAL_CARD
        if (PENSION_PATTERNS.any { upper.contains(it) }) return SenderType.PENSION
        if (INVESTMENT_PATTERNS.any { upper.contains(it) }) return SenderType.INVESTMENT
        if (INSURANCE_PATTERNS.any { upper.contains(it) }) return SenderType.INSURANCE
        return SenderType.EXCLUDED
    }

    fun isTransactionSender(senderId: String) = classify(senderId) != SenderType.EXCLUDED
}
