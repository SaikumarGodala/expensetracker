package com.saikumar.expensetracker.sms

import com.saikumar.expensetracker.data.entity.*
import com.saikumar.expensetracker.core.AppConstants

/**
 * Result of category classification including confidence score.
 */
data class CategoryResult(
    val category: String,
    val confidence: Int // 0-100 scale
)

object CategoryMapper {

    // Confidence score constants
    object Confidence {
        const val MANUAL = 100         // Manually entered/verified
        const val USER_RULE = 95       // User-defined rule match
        const val MERCHANT_EXACT = 90  // Exact merchant name match
        const val SALARY_PATTERN = 90  // Salary company name match
        const val SELF_TRANSFER = 85   // Self-transfer detection
        const val PATTERN_MATCH = 80   // Pattern-based detection (investment, interest)
        const val ML_PREDICTION = 75   // ML Model prediction
        const val TYPE_DEFAULT = 70    // TransactionType-based default
        const val FALLBACK = 50        // Generic fallback (Miscellaneous)
        const val UNCATEGORIZED = 30   // Uncategorized/Unknown
        const val UNVERIFIED = 25      // Unverified income (needs review)
    }

    private val MERCHANT_CATEGORIES = mapOf(

        // ATM / Cash
        "ATM" to "Cash Withdrawal",
        "SBI ATM" to "Cash Withdrawal",
        "CASH" to "Cash Withdrawal",
        "NFS" to "Cash Withdrawal",
        "CASH WDL" to "Cash Withdrawal",
        "WDL" to "Cash Withdrawal",
        "CASH WITHDRAWAL" to "Cash Withdrawal",
        "WITHDRAWN" to "Cash Withdrawal",

        // Cashback
        "BHIMCASHBACK" to "Cashback",

        // Clothing
        "AJIO" to "Clothing",
        "H&M" to "Clothing",
        "LEVIS" to "Clothing",
        "LIFESTYLE" to "Clothing",
        "LIFE STYLE" to "Clothing", // bank prints "Life Style Internation" with a space
        "MAX FASHION" to "Clothing",
        "MYNTRA" to "Clothing",
        "PANTALOONS" to "Clothing",
        "UNIQLO" to "Clothing",
        "WESTSIDE" to "Clothing",
        "ZARA" to "Clothing",
        "ZUDIO" to "Clothing",
        "JOCKEY" to "Clothing",


        // Credit Bill Payments
        "AMEX" to "Credit Bill Payments",
        "AXISCARD" to "Credit Bill Payments",
        "BILLDESK" to "Credit Bill Payments",
        "CCPAY" to "Credit Bill Payments",
        "CITIBANK" to "Credit Bill Payments",
        "CRED" to "Credit Bill Payments",
        "CRED CLUB" to "Credit Bill Payments",
        "HDFCCARD" to "Credit Bill Payments",
        "ONECARD" to "Credit Bill Payments",
        "SBICARD" to "Credit Bill Payments",
        "STATEMENT" to "Credit Card Statement",

        // Dining Out
        "ARABIAN REST" to "Dining Out",
        "ARUN ICE CREAMS" to "Dining Out",
        "BAKE CENTRAL" to "Dining Out",
        "CREAM STONE" to "Dining Out",
        "DINEOUT" to "Dining Out",
        "ELIOR FOOD LLP" to "Dining Out",
        "ELIORINDIAFOODS" to "Dining Out",
        "ELIOR" to "Dining Out", // catches the bank-truncated "Elior Food"/"Elior Foo"
        "NILOUFER" to "Dining Out", // Cafe Niloufer
        "PARAMPARA MITHAI" to "Dining Out",
        "GOKHANA" to "Dining Out",
        "HOUSE OF SPIRITS" to "Dining Out",
        "KINGS FAMILY" to "Dining Out",
        "KRITUNGA" to "Dining Out",
        "LASTHOUSECOFFEE" to "Dining Out",
        "MAHARAJA CHAT" to "Dining Out",
        "MAHARAJA CHAT AND FOODS" to "Dining Out",
        "MANDI KING" to "Dining Out",
        "MANDIKING" to "Dining Out",
        "MANDIKINGARABIANREST" to "Dining Out",
        "NAIDU GARI" to "Dining Out",
        "NALBHEEMA KITCH" to "Dining Out",
        "PARAMPARA MITHAI SHOP BHEL MIG" to "Dining Out",
        "PISTA HOUSE" to "Dining Out",
        "SPUDATOFOODS" to "Dining Out",
        "STONE SPOT" to "Dining Out",
        "THE GREAT WALL" to "Dining Out",
        "THE NAWAABS" to "Dining Out",
        "THE NAWAABS RESTAURANT" to "Dining Out",
        "YUM RESTAURANT" to "Dining Out",
        "YUM YUM TREE ARABIAN FOOD COURT" to "Dining Out",
        "ZAIKA RESTAURANT" to "Dining Out",
        "SWIGGYDINEIN" to "Dining Out",

        // Education
        // NOTE: must be "Education / Fees" - a category named just "Education" does not exist
        // in DefaultCategories, so this mapping previously resolved to a dead name and every
        // Udemy purchase silently fell back to Uncategorized.
        "UDEMY" to "Education / Fees",

        // Education / Fees
        "BYJU" to "Education / Fees",
        "COACHING" to "Education / Fees",
        "COLLEGE" to "Education / Fees",
        "SCHOOL" to "Education / Fees",
        "UNACADEMY" to "Education / Fees",
        "UNIVERSITY" to "Education / Fees",
        "VEDANTU" to "Education / Fees",

        // Electronics
        "APPLE" to "Electronics",
        "CROMA" to "Electronics",
        "DELL" to "Electronics",
        "HP STORE" to "Electronics",
        "LENOVO" to "Electronics",
        "MI STORE" to "Electronics",
        "ONEPLUS" to "Electronics",
        "SAMSUNG" to "Electronics",
        "VIJAY SALES" to "Electronics",
        "SHOPSAMS" to "Electronics",

        // Entertainment
        "AMOEBA" to "Entertainment",
        "BOOKMYSHOW" to "Entertainment",
        "BOOKMYS" to "Entertainment", // bank-truncated "BookMyShow"
        "DISTRICT MOVIES" to "Entertainment",
        "DISTRICT MOVIE" to "Entertainment", // "District Movie Ticket"
        "DREAM11" to "Entertainment",
        "GAMEON" to "Entertainment",
        "GAME ON" to "Entertainment",
        "GAMES" to "Entertainment",
        // Consistent with "GOOGLE PLAY" -> Subscriptions below; previously this spelling
        // mapped to Entertainment so the same store landed in two categories.
        "GOOGLEPLAY" to "Subscriptions",
        "NINTENDO" to "Entertainment",
        "PLAYSTATION" to "Entertainment",
        "PRASADS" to "Entertainment",
        "PRIVEPLEX" to "Entertainment",
        "RUMMY" to "Entertainment",
        "SCRATCHBOARDS" to "Entertainment",
        "SPOTIFY" to "Entertainment",
        "STEAM" to "Entertainment",
        "SVMBOWLING" to "Entertainment",
        "XBOX" to "Entertainment",

        // Fixed Deposits
        "SHIVALIK SMALL FINANCE BANK" to "Fixed Deposits",
        "SURYODAY SMALL FINANCE BANK" to "Fixed Deposits",

        // Food Delivery
        "CASSWIGGY" to "Food Delivery",
        "DOMINOS" to "Food Delivery",
        "KFC" to "Food Delivery",
        "LICIOUS" to "Food Delivery",
        "PIZZA HUT" to "Food Delivery",
        "PPSL SWIGGY" to "Food Delivery",
        "SUBWAY" to "Food Delivery",
        "SWIGGY" to "Food Delivery",
        "SWIGGY STORES" to "Food Delivery",
        "SWIGGYSTORES" to "Food Delivery",
        "ZOMATO" to "Food Delivery",

        // Fuel
        "BPCL" to "Fuel",
        "FUEL" to "Fuel",
        "HINDUSTAN PETRO" to "Fuel",
        "HP" to "Fuel",
        "IOCL" to "Fuel",
        "PETROL" to "Fuel",
        "VR FUELS" to "Fuel",

        // Furniture
        "GODREJ INTERIO" to "Furniture",
        "HOMETOWN" to "Furniture",
        "IKEA" to "Furniture",
        "IKEAWL" to "Furniture", // bank-truncated Ikea handle
        "FLOSLEEPSOLUTIO" to "Furniture", // Flo Sleep Solutions (mattress) - bank-truncated form
        "NILKAMAL" to "Furniture",
        "NILKAMALFURNITU" to "Furniture",
        "PEPPERFRY" to "Furniture",
        "URBAN LADDER" to "Furniture",

        // Gold
        "AUGMONT" to "Gold",
        "GOLD" to "Gold",
        "SAFEGOLD" to "Gold",

        // Groceries
        "AVENUE SUPERMAR" to "Groceries",
        "CASH WDL" to "Cash Withdrawal", // ATM: "debited Rs. 10,000.00 ... NFS*CASH WDL*"
        "NFS" to "Cash Withdrawal",
        "KIRANA" to "Groceries", // "Ganesh Kirana" & every other kirana store
        "FUELS" to "Fuel", // petrol pumps named "<X> Fuels"
        "FLIPKARTINTERNE" to "Shopping", // ICICI glues+truncates "FlipkartInterne"
        "MYJIO" to "Mobile + WiFi",
        "INDIAN RAILWAYS" to "Transportation",
        "BAKERS" to "Dining Out", // "Cakes Bakers"
        "DISTRICT MO" to "Entertainment", // truncated "District Movies"
        "AVENUE SUPERMART" to "Groceries", // full word - token matching can't hit the truncated key mid-word
        "AVENUE SUPERMARTS" to "Groceries",
        "TITAN COMPANY" to "Shopping", // watches/jewellery ("..TITAN COMPANY LI_" card spends)
        "TITAN" to "Shopping",
        "STATIONERY" to "Shopping", // "Sri Meenakshi Stationery" & other stationery shops
        "JEWEL PARK" to "Shopping",
        "JEWELLERS" to "Shopping",
        "BHARAT BAZAR" to "Groceries",
        "BIGBASKET" to "Groceries",
        "BLINKIT" to "Groceries",
        "BLINKITJKB" to "Groceries",
        "BUNDL TECHN" to "Groceries",
        "CP ZEPTO" to "Groceries",
        "CREDPAYZEPTO" to "Groceries",
        "DMART" to "Groceries",
        "DUNZO" to "Groceries",
        "GROFERS PAYTM" to "Groceries",
        "INSTAM" to "Groceries",
        "INSTAMART" to "Groceries",
        "KPN FF KOT" to "Groceries",
        "RATNADEEP" to "Groceries",
        "SMPOORNA" to "Groceries",
        "SWIGGY INSTAMART" to "Groceries",
        "VIJETHA" to "Groceries",
        "ZEPTO" to "Groceries",
        "ZEPTO MARKETPLA" to "Groceries",
        "ZEPTONOW ESBZ" to "Groceries",
        "ZEPTOONLINE" to "Groceries",

        // Gym & Fitness
        "CROSSFIT" to "Gym & Fitness",
        "CULT" to "Gym & Fitness",
        "DECATHLON" to "Gym & Fitness",
        "FITNESS" to "Gym & Fitness",
        "GYM" to "Gym & Fitness",

        // Insurance
        "ICICI PRU" to "Insurance",
        "IPRU" to "Insurance", // ICICI Prudential premium via BillDesk ("...at IPRU BILLDESK")
        "LIC" to "Insurance",
        "POLICYBAZAAR" to "Insurance",
        "WWW POLICYBAZAAR" to "Insurance",

        // Interest
        "IDFC FIRST BANK" to "Interest",
        "INT CREDIT" to "Interest",
        "INTEREST" to "Interest",

        // Medical
        "ALEKHYA DIAGNOS" to "Medical",
        "MEDIBUDDY PHASO" to "Medical",

        // Miscellaneous
        "EASEBUZZ" to "Miscellaneous",
        "GPAY" to "Miscellaneous",
        "RAZORPAY" to "Miscellaneous",
        "VYAPAR" to "Miscellaneous",

        // Mobile + WiFi
        // NOTE: "AIRTEL" alone is only a DEFAULT (most payments to Airtel are telecom
        // bills). It is refined two ways: "AIRTEL PAYMENTS BANK" below wins on
        // longest-key matching (that's a payment rail, not a bill - could be anything),
        // and BillReminderManager overrides the category when a matched bill reminder
        // says the amount was actually for DTH/broadband/etc.
        "ACT FIBERNET" to "Mobile + WiFi",
        "AIRTEL" to "Mobile + WiFi",
        "AIRTEL PAYMENTS BANK" to "Miscellaneous",
        "BSNL" to "Mobile + WiFi",
        "HATHWAY" to "Mobile + WiFi",
        "JIO" to "Mobile + WiFi",
        "PHONEPEBSNLSOUTH" to "Mobile + WiFi",

        // Mutual Funds
        "GROWW" to "Mutual Funds",
        "ICCL" to "Mutual Funds",
        "ICCL ZERODHA" to "Mutual Funds",
        "INDIAN CLEARING" to "Mutual Funds",
        "INDIAN CLEARING CORPORATION" to "Mutual Funds",
        "INDIAN CLEARING CORPORATION LIMITED" to "Mutual Funds",
        "CLEARING CORPORATION" to "Mutual Funds",
        "ZERODHA" to "Mutual Funds",
        "ZERODHA BROKING" to "Mutual Funds",

        // Offline Merchant
        "PAYTM" to "Offline Merchant",
        "PAYTMQR" to "Offline Merchant",

        // Parking & Tolls
        "FASTAG" to "Parking & Tolls",
        "NETC" to "Parking & Tolls",
        "PARKING" to "Parking & Tolls",
        "RTO" to "Parking & Tolls",
        "TELANGANA STATE" to "Parking & Tolls",
        "TELANGANA STATE ROAD TRANSPORT" to "Parking & Tolls",
        "TOLL" to "Parking & Tolls",

        // Personal Care
        "BARBER" to "Personal Care",
        "BEAUTY" to "Personal Care",
        "GREENTRENDS" to "Personal Care",
        "PARLOUR" to "Personal Care",
        "SALON" to "Personal Care",
        "SPA" to "Personal Care",

        // Salary
        "NEFT DEUTH IND" to AppConstants.Categories.SALARY,
        "OPEN TEXT" to AppConstants.Categories.SALARY,
        "OPEN TEXT TECHNOLOGIES" to AppConstants.Categories.SALARY,

        // Services ("Service" singular is not a real category name)
        "SIVAM AUTO" to "Services",
        "SRI MOTORS" to "Services",

        // Shopping
        "AMAZON" to "Shopping",
        "AMAZON CY" to "Shopping",
        "AVENUE E" to "Shopping",
        "DIVERSE RETAIL" to "Shopping",
        "FLIPKART" to "Shopping",
        "FLIPKART PAYMENTS" to "Shopping",
        // REMOVED: "IND" -> "Shopping" was a dangerously generic 3-letter key that, even with
        // word-boundary matching, is a common standalone token in bank/NEFT text ("SOUTH IND",
        // employer/entity names, country-code fragments) unrelated to any real "Shopping" merchant.
        "INFINITI LIMITEMUM" to "Shopping",
        "RELIANCE" to "Shopping",
        "SOUTH JEW" to "Shopping",

        // Stocks
        "5PAISA" to "Stocks",
        "ANGEL" to "Stocks",
        "ICICIDIRECT" to "Stocks",
        "KITE" to "Stocks",
        "KOTAK SEC" to "Stocks",
        "UPSTOX" to "Stocks",

        // Subscriptions
        "ADOBE PREMIERE" to "Subscriptions",
        "ADOBE SYSTEMS" to "Subscriptions",
        "EENADU TELEVISI" to "Subscriptions",
        "GOOGLE PLAY" to "Subscriptions",
        "GOOGLE PLAY APP" to "Subscriptions",
        "UDEMY SUBSCRIPT" to "Subscriptions",
        "YOUTUBE CYBS" to "Subscriptions",
        "NETFLIX" to "Subscriptions",
        "YOUTUBE" to "Subscriptions",
        "YOUTUBEGOOGLE" to "Subscriptions",
        "ZEEENTERTAINMEN" to "Subscriptions",
        "LINKEDIN" to "Subscriptions",
        "GRAMMARLY" to "Subscriptions",


        // Transportation
        "ABHIBUS" to "Transportation",
        "APSRTC" to "Transportation",
        "HYDMETROINAPP" to "Transportation",
        "IRCTC" to "Transportation",
        "IRCTCPGONLINE" to "Transportation",
        "METRO" to "Transportation",
        "NAMMA YATRI" to "Transportation",
        "OLA" to "Transportation",
        "RAPIDO" to "Transportation",
        "REDBUS" to "Transportation",
        "TSRTC" to "Transportation",
        "TTDCLBOATHOUSE" to "Transportation",
        "UBER" to "Transportation",
        "YULU" to "Transportation",

        // Utilities
        "BESCOM" to "Utilities",
        "ELECTRICITY" to "Utilities",
        "GAS" to "Utilities",
        "WATER" to "Utilities",
        "TSSPDCL" to "Utilities",
        "TSNPDCL" to "Utilities",
        "APSPDCL" to "Utilities",
        "TATA POWER" to "Utilities",
        "ADANI ELECTRICITY" to "Utilities",

        // ============ EXPANDED KNOWN-MERCHANT LIST ============
        // Curated additions so common Indian vendors/apps categorize instantly on first
        // scan. All values are verified category names from DefaultCategories; keys are
        // >= 4 chars (body-scan safe) or distinctive brand tokens.

        // Food Delivery / QSR
        "EATSURE" to "Food Delivery",
        "FAASOS" to "Food Delivery",
        "BEHROUZ" to "Food Delivery",
        "OVENSTORY" to "Food Delivery",
        "MCDONALD" to "Food Delivery",
        "BURGER KING" to "Food Delivery",
        "WOW MOMO" to "Food Delivery",
        "LA PINOZ" to "Food Delivery",

        // Dining Out
        "STARBUCKS" to "Dining Out",
        "CAFE COFFEE DAY" to "Dining Out",
        "BARBEQUE NATION" to "Dining Out",
        "PARADISE FOOD" to "Dining Out",
        "CHAI POINT" to "Dining Out",
        "THIRD WAVE" to "Dining Out",
        "BLUE TOKAI" to "Dining Out",
        "BAWARCHI" to "Dining Out",
        "SHAH GHOUSE" to "Dining Out",
        "MEHFIL" to "Dining Out",

        // Groceries / quick commerce
        "JIOMART" to "Groceries",
        "MILKBASKET" to "Groceries",
        "COUNTRY DELIGHT" to "Groceries",
        "SPENCERS" to "Groceries",
        "NATURES BASKET" to "Groceries",
        "MORE RETAIL" to "Groceries",
        "STAR BAZAAR" to "Groceries",
        "SMART BAZAAR" to "Groceries",

        // Shopping
        "MEESHO" to "Shopping",
        "NYKAA" to "Shopping",
        "TATA CLIQ" to "Shopping",
        "TATACLIQ" to "Shopping",
        "SNAPDEAL" to "Shopping",
        "SHOPSY" to "Shopping",
        "FIRSTCRY" to "Shopping",
        "PURPLLE" to "Shopping",
        "LENSKART" to "Shopping",
        "TANISHQ" to "Shopping",
        "CARATLANE" to "Shopping",

        // Travel
        "MAKEMYTRIP" to "Travel",
        "GOIBIBO" to "Travel",
        "CLEARTRIP" to "Travel",
        "YATRA" to "Travel",
        "IXIGO" to "Travel",
        "EASEMYTRIP" to "Travel",
        "OYO" to "Travel",
        "AIRBNB" to "Travel",
        "AGODA" to "Travel",
        "INDIGO" to "Travel",
        "AIR INDIA" to "Travel",
        "SPICEJET" to "Travel",
        "AKASA" to "Travel",
        "VISTARA" to "Travel",

        // Entertainment / OTT
        "HOTSTAR" to "Subscriptions",
        "DISNEY" to "Subscriptions",
        "SONYLIV" to "Subscriptions",
        "ZEE5" to "Subscriptions",
        "JIOCINEMA" to "Subscriptions",
        "PRIME VIDEO" to "Subscriptions",
        "AMAZON PRIME" to "Subscriptions",
        "SUNNXT" to "Subscriptions",
        "GAANA" to "Subscriptions",
        "WYNK" to "Subscriptions",
        "INOX" to "Entertainment",
        "PVR" to "Entertainment",

        // Software / AI subscriptions
        "ANTHROPIC" to "Subscriptions",
        "CLAUDE" to "Subscriptions",
        "OPENAI" to "Subscriptions",
        "CHATGPT" to "Subscriptions",
        "PERPLEXITY" to "Subscriptions",
        "GITHUB" to "Subscriptions",
        "GOOGLE ONE" to "Subscriptions",
        "ICLOUD" to "Subscriptions",

        // Medical / pharmacy
        "APOLLO PHARMACY" to "Medical",
        "APOLLO" to "Medical",
        "PHARMEASY" to "Medical",
        "NETMEDS" to "Medical",
        "TATA 1MG" to "Medical",
        "MEDPLUS" to "Medical",
        "PRACTO" to "Medical",
        // Hospitals (card swipes like "..YASHODA SUPER SP_", "AIG HOSPITALS")
        "YASHODA" to "Medical",
        "HOSPITAL" to "Medical",
        "HOSPITALS" to "Medical",
        "SUPER SPECIALITY" to "Medical",
        "SUPERSPECIALITY" to "Medical",
        "FORTIS" to "Medical",
        "MANIPAL" to "Medical",
        "NARAYANA" to "Medical",
        "MEDICOVER" to "Medical",
        "CARE HOSPITAL" to "Medical",
        "CONTINENTAL HOSPITAL" to "Medical",
        "RAINBOW CHILDREN" to "Medical",
        "AIG HOSPITAL" to "Medical",
        "DIAGNOSTIC" to "Medical",
        "DIAGNOSTICS" to "Medical",
        "PATHOLOGY" to "Medical",

        // Fuel
        "SHELL" to "Fuel",
        "NAYARA" to "Fuel",
        "INDIAN OIL" to "Fuel",
        "INDIANOIL" to "Fuel",
        "HPCL" to "Fuel",

        // Insurance
        "HDFC ERGO" to "Insurance",
        "STAR HEALTH" to "Insurance",
        "ACKO" to "Insurance",
        "DIGIT INSURANCE" to "Insurance",
        "TATA AIG" to "Insurance",
        "NIVA BUPA" to "Insurance",

        // Investments
        "KUVERA" to "Mutual Funds",
        "PAYTM MONEY" to "Mutual Funds",
        "ETMONEY" to "Mutual Funds",
        "INDMONEY" to "Mutual Funds",
        "SMALLCASE" to "Mutual Funds",

        // Transport
        "BLUSMART" to "Transportation",
        "INDRIVE" to "Transportation",

        // Education
        "COURSERA" to "Education / Fees",
        "UPGRAD" to "Education / Fees",
        "GREAT LEARNING" to "Education / Fees",
        "SIMPLILEARN" to "Education / Fees",

        // Home services
        "URBAN COMPANY" to "Services",
        "URBANCLAP" to "Services",
    )

    // Keys excluded from the SMS-body fallback scan (PRIORITY 5.5): words that occur in
    // ordinary bank-SMS prose or as generic nouns, where a body hit says nothing about the
    // merchant. They still apply to extracted counterparty names (PRIORITY 3).
    private val BODY_SCAN_EXCLUDED_KEYS = setOf(
        "CASH", "CASH WDL", "CASH WITHDRAWAL", "GOLD", "GAS", "WATER", "ELECTRICITY",
        "STATEMENT", "INTEREST", "INT CREDIT", "FUEL", "PETROL", "PARKING", "TOLL",
        "SCHOOL", "COLLEGE", "UNIVERSITY", "COACHING", "GAMES", "METRO", "KITE",
        "FITNESS", "BEAUTY", "SALON", "BARBER", "PARLOUR", "RELIANCE", "APPLE",
        // Bank names: these keys exist for counterparty-name matching (e.g. an interest
        // credit FROM the bank), but in the body they appear in every SMS that bank sends
        // ("Team IDFC FIRST Bank"), so a body hit carries no signal - matching them here
        // miscategorized ordinary IDFC card spends as Interest (which then flipped their
        // type to INCOME via category-based type resolution).
        "IDFC FIRST BANK", "CITIBANK", "SHIVALIK SMALL FINANCE BANK", "SURYODAY SMALL FINANCE BANK"
    )

    // AUDIT: Using unified TransactionType from data.entity
    private val TYPE_DEFAULT_CATEGORIES = mapOf(
        TransactionType.PENSION to AppConstants.Categories.MUTUAL_FUNDS, // Proxy for Pension
        TransactionType.INVESTMENT_CONTRIBUTION to AppConstants.Categories.MUTUAL_FUNDS,
        TransactionType.LIABILITY_PAYMENT to AppConstants.Categories.CREDIT_BILL_PAYMENTS,
        TransactionType.TRANSFER to AppConstants.Categories.P2P_TRANSFERS,
        TransactionType.INCOME to AppConstants.Categories.OTHER_INCOME,
        TransactionType.STATEMENT to "Credit Card Statement", // Separate category for Statements
        TransactionType.REFUND to AppConstants.Categories.REFUND
    )

    // Bank / credit-card fees charged by the bank itself. These are real expenses that carry
    // no merchant, so they'd otherwise fall to Uncategorized.
    private val BANK_FEE_REGEX = Regex(
        "annual fee|joining fee|membership fee|renewal fee|finance charge|late payment|late fee|" +
        "over.?limit fee|cash advance fee|markup fee|forex markup|card replacement|reissuance fee|" +
        "non.?maintenance|amb charge|min(?:imum)? balance charge|sms charge|sms alert charge|" +
        "annual charge|interest charge",
        RegexOption.IGNORE_CASE
    )

    fun categorize(
        counterparty: CounterpartyExtractor.Counterparty,
        transactionType: TransactionType,
        rules: List<CategorizationRule> = emptyList(),
        categoryMap: Map<Long, String> = emptyMap(),
        userAccounts: List<UserAccount> = emptyList(),
        messageBody: String = "",
        salarySources: Set<Pair<String, String>> = emptySet(),
        salaryCompanyNames: Set<String> = emptySet(),
        merchantMemories: Map<String, Long> = emptyMap(), // ADAPTIVE CATEGORIZATION
        trace: MutableList<String>? = null
    ): String {
        val lowerBody = messageBody.lowercase()

        // A card PURCHASE ("Rs X spent on ICICI Card XX4001 at HP PAY", "spent on your ...
        // Credit Card ending XXnnnn at IPRU BILLDESK") is money SPENT via the card at a
        // merchant — it must count as spending under that merchant, NOT be netted out as a
        // card-bill payment, even when the merchant's name contains a bill-payment token
        // (CRED/BILLDESK) or the SMS names one of the user's own card numbers. Real bill
        // payments say "Sent ... To CRED Club" / "Payment received on your card" and never
        // "spent on/at", so that phrasing safely tells a spend apart from a payment.
        val isCardPurchase = lowerBody.contains("spent on") || lowerBody.contains("spent at")

        // BANK / CARD FEE: a charge levied by the bank (annual/late/finance/AMB fee...).
        // Not a card purchase, and excludes reversed/waived fees (those are refunds / no charge).
        if (!isCardPurchase &&
            transactionType != TransactionType.INCOME && transactionType != TransactionType.REFUND &&
            BANK_FEE_REGEX.containsMatchIn(lowerBody) &&
            !lowerBody.contains("reversed") && !lowerBody.contains("waiv") &&
            !lowerBody.contains("refunded") && !lowerBody.contains("not been charged") &&
            !lowerBody.contains("no annual") && !lowerBody.contains("lifetime free")) {
            trace?.add("Matched: bank/card fee keyword -> Bank Fees")
            return "Bank Fees"
        }

        // PRIORITY 0: Credit Card Bill Payment Check (HIGHEST PRIORITY)
        // This must come BEFORE any other logic to prevent misclassification.
        // Hard financial invariant - not even a User Rule should be able to mis-tag an
        // actual credit-card-bill payment as something else, since downstream liability
        // tracking depends on this being correct.
        if (counterparty.name != null) {
            val upper = counterparty.name.uppercase()
            // Shared token list lives in SmsConstants.isCreditCardServiceName (word-boundary
            // matching for "CRED" so it doesn't fire inside "CREDIT"/"CREDITED").
            val isCreditCardPayment = SmsConstants.isCreditCardServiceName(upper)

            if (isCreditCardPayment && transactionType == TransactionType.EXPENSE && !isCardPurchase) {
                trace?.add("OVERRIDE: Credit Card Payment Service detected -> Credit Bill Payments")
                return AppConstants.Categories.CREDIT_BILL_PAYMENTS
            }
        }

        // PRIORITY 1: User-Defined Rules
        // BUGFIX: This block used to run AFTER the NEFT self-transfer/salary-source/
        // salary-company-name/interest-keyword checks below, even though it was labeled
        // "Priority 1" and is the user's own explicit categorization intent. That meant a
        // user-created rule could never override those heuristics - it was silently dead
        // code for any merchant/payee that also happened to match one of those patterns.
        // User rules now run immediately after the (hard-invariant) credit card check.
        val sortedRules = rules.sortedBy { it.patternType.ordinal }

        for (rule in sortedRules) {
            val isMatch = when (rule.patternType) {
                PatternType.UPI_ID -> counterparty.upiId?.equals(rule.pattern, ignoreCase = true) == true
                PatternType.MERCHANT_NAME -> counterparty.name?.contains(rule.pattern, ignoreCase = true) == true
                PatternType.PAYEE_NAME -> counterparty.name?.contains(rule.pattern, ignoreCase = true) == true
                else -> false
            }

            if (isMatch) {
                trace?.add("Matched User Rule: ${rule.id} (${rule.pattern}) -> Category ID ${rule.categoryId}")
                return categoryMap[rule.categoryId] ?: AppConstants.Categories.UNCATEGORIZED
            }
        }
        if (rules.isNotEmpty()) trace?.add("No User Rules matched (${rules.size} checked)")

        // PRIORITY 2: Deterministic Pattern Overrides (NEFT self-transfer, known salary
        // source, user-configured salary company name, interest keyword)
        if (messageBody.contains("NEFT", ignoreCase = true)) {
            if (CounterpartyExtractor.isNeftSelfTransfer(messageBody)) {
                trace?.add("Matched: NEFT Self Transfer pattern")
                return AppConstants.Categories.SELF_TRANSFER
            }

            // Known Salary Source Check
            if (salarySources.isNotEmpty()) {
                val neftSource = CounterpartyExtractor.extractNeftSource(messageBody)
                if (neftSource != null && salarySources.contains(neftSource)) {
                    trace?.add("Matched: Known Salary Source (NEFT)")
                    return AppConstants.Categories.SALARY
                }
            }
        }

        // Salary Company Name Check (User-configured)
        if (transactionType == TransactionType.INCOME && salaryCompanyNames.isNotEmpty()) {
            val upperBody = messageBody.uppercase()
            for (companyName in salaryCompanyNames) {
                if (upperBody.contains(companyName.uppercase())) {
                    trace?.add("Matched: Salary Company Name '$companyName'")
                    return AppConstants.Categories.SALARY
                }
            }
        }

        // Interest Income Check (Body-based)
        if (transactionType == TransactionType.INCOME) {
             if (lowerBody.contains("interest") ||
                 lowerBody.contains("int. pd") ||
                 lowerBody.contains("int pd") ||
                 lowerBody.contains("int cr") ||
                 lowerBody.contains("int. earned")) {
                 trace?.add("Matched: Interest keyword in body")
                 return "Interest"
             }
        }

        // PRIORITY 3: Hardcoded Merchant Map
        // Runs before Adaptive Memory (Priority 5) so that system truth overrides
        // potentially-bad learned corrections.
        counterparty.name?.let { name ->
            val upper = name.uppercase()

            // Special case: Chits
            if (upper.contains("CHIT") || upper.contains("SHRIRAM")) {
                 if (transactionType == TransactionType.INCOME) {
                     trace?.add("Matched: Chit/Shriram -> Investment Redemption")
                     return "Investment Redemption"
                 } else {
                     trace?.add("Matched: Chit/Shriram -> Chits")
                     return "Chits"
                 }
            }

            // Scan for the LONGEST matching merchant key to ensure specificity
            // e.g. Match "SWIGGY INSTAMART" (length 16) over "SWIGGY" (length 6)
            //
            // BUGFIX: previously used `upper.contains(key)`, a raw substring check with no
            // word-boundary awareness. Short/generic keys matched inside unrelated words:
            //   "IND" (-> Shopping) matched "INDIA", "INDIAN CLEARING", "INDUSIND BANK"
            //   "CASH" (-> Cash Withdrawal) matched "CASHBACK", "CASHFREE"
            //   "GOLD" (-> Gold) matched "GOLDEN DRAGON RESTAURANT"
            //   "HP" (-> Fuel) matched "SHIP", "CHIP"
            // SmsConstants.containsToken requires the match not be directly adjacent to
            // another letter, which eliminates these false positives while still matching
            // merchant codes with trailing digits (e.g. "BPCL0091234").
            var bestMatchCategory: String? = null
            var maxMatchLength = 0
            var bestMatchKey = ""

            for ((key, defaultCategory) in MERCHANT_CATEGORIES) {
                if (SmsConstants.containsToken(upper, key)) {
                    if (key.length > maxMatchLength) {
                        // Investment Redemption check - for INCOME from investment entities
                        if (transactionType == TransactionType.INCOME) {
                            if (defaultCategory == "Mutual Funds" ||
                                key == "ICCL" ||
                                key == "ICCL ZERODHA" ||
                                key == "INDIAN CLEARING" ||
                                key == "INDIAN CLEARING CORPORATION" ||
                                key == "ZERODHA" ||
                                key == "ZERODHA BROKING") {
                                bestMatchCategory = "Investment Redemption"
                            } else {
                                bestMatchCategory = defaultCategory
                            }
                        } else {
                            // For expenses/debits, use the default category
                            bestMatchCategory = defaultCategory
                        }

                        // Additional override check (redundant but safe)
                        if (upper.contains("INDIAN CLEARING") || upper.contains("ICCL") || upper.contains("ZERODHA")) {
                             if (transactionType == TransactionType.INCOME) bestMatchCategory = "Investment Redemption"
                        }
                        
                        maxMatchLength = key.length
                        bestMatchKey = key
                    }
                }
            }
            
            if (bestMatchCategory != null) {
                // A card purchase whose (mis-)extracted merchant token maps to Credit Bill
                // Payments must not be netted out of spending — let it fall through to a real
                // spending category instead.
                if (!(isCardPurchase && bestMatchCategory == AppConstants.Categories.CREDIT_BILL_PAYMENTS)) {
                    trace?.add("Matched Hardcoded Merchant: $bestMatchKey -> $bestMatchCategory")
                    return enforceCategoryTypeCompatibility(bestMatchCategory, transactionType, trace)
                }
                trace?.add("SKIP Hardcoded Merchant $bestMatchKey -> Credit Bill (card purchase, keep as spend)")
            }
        }
        
        // PRIORITY 3.5: Account Discovery matches (Self-Transfer / CC Bill)
        // Check this BEFORE Memory but AFTER Hardcoded Map (to allow explicit CC names to map to Credit Bills)
        if (counterparty.name != null) {
            // IMPROVED: Better account number matching with validation
            val matchedAccount = userAccounts.find { myAccount ->
                isAccountNumberInText(counterparty.name, myAccount.accountNumberLast4)
            }
            // A card purchase names the user's own card number ("spent on ICICI Card XX0006
            // at IPRU BILLDESK") but the card is being CHARGED at a merchant, not paid — so
            // this is NOT a payment to that card. Only treat an own-account match as a
            // bill/self-transfer when it isn't a spend.
            if (matchedAccount != null && !isCardPurchase) {
                trace?.add("Matched: Discovered Account ${matchedAccount.accountNumberLast4}")
                return if (matchedAccount.accountType == AccountType.CREDIT_CARD) {
                    AppConstants.Categories.CREDIT_BILL_PAYMENTS // Payment to my own Credit Card
                } else {
                    AppConstants.Categories.SELF_TRANSFER // Transfer to my own Bank Account
                }
            }

            // Check if user's own name appears in counterparty
            if (isUserOwnName(counterparty.name, userAccounts)) {
                trace?.add("Matched: User Own Name detected (Self Transfer)")
                return AppConstants.Categories.SELF_TRANSFER
            }
        }

        // Check UPI VPA for self-transfer
        if (!counterparty.upiId.isNullOrBlank()) {
            val matchedVpaAccount = userAccounts.find { account ->
                account.upiVpa != null && counterparty.upiId.equals(account.upiVpa, ignoreCase = true)
            }
            if (matchedVpaAccount != null) {
                trace?.add("Matched: User Own UPI VPA ${counterparty.upiId} (Self Transfer)")
                return AppConstants.Categories.SELF_TRANSFER
            }
        }

        // PRIORITY 4a: Recurring Deposit Check
        if (SmsConstants.RD_PATTERNS.any { lowerBody.contains(it) }) {
             trace?.add("Matched: Recurring Deposit pattern")
             return AppConstants.Categories.RECURRING_DEPOSITS
        }

        // PRIORITY 4b: Cashback Check
        if (transactionType != TransactionType.EXPENSE &&
            (messageBody.contains("cashback", ignoreCase = true) ||
             messageBody.contains("reward", ignoreCase = true) ||
             (counterparty.name?.contains("cashback", ignoreCase = true) == true))) {

            trace?.add("Matched: Cashback/Reward keyword (Non-Expense)")
            return AppConstants.Categories.CASHBACK
        }

        // PRIORITY 5: ADAPTIVE MEMORY (Learned from User Corrections)
        if (!counterparty.name.isNullOrBlank()) {
            val normalized = counterparty.name.uppercase().trim()
            var memoryCategoryId = merchantMemories[normalized]

            // Try fuzzy match if exact match fails.
            // BUGFIX: previously used raw `normalized.contains(it) || it.contains(normalized)`,
            // which could match a short learned merchant key inside an unrelated name (e.g. a
            // remembered merchant "ATM" would match any name containing "ATM" as a substring,
            // like "ATMOSPHERE RESTAURANT"). Now uses word-boundary matching and, like the
            // hardcoded merchant map above, picks the longest (most specific) matching key
            // instead of an arbitrary/first one.
            if (memoryCategoryId == null) {
                 val bestKey = merchantMemories.keys
                     .filter { SmsConstants.containsToken(normalized, it) || SmsConstants.containsToken(it, normalized) }
                     .maxByOrNull { it.length }
                 if (bestKey != null) memoryCategoryId = merchantMemories[bestKey]
            }

            if (memoryCategoryId != null) {
                val learnedCategory = categoryMap[memoryCategoryId]
                if (learnedCategory != null) {
                    trace?.add("Matched: Learned Memory for '${counterparty.name}' -> $learnedCategory")
                    return enforceCategoryTypeCompatibility(learnedCategory, transactionType, trace)
                }
            }
        }

        // PRIORITY 5.5: BODY SCAN for known merchants.
        // Counterparty extraction fails on bank formats without a template, but the merchant
        // name is usually still right there in the SMS text ("...spent on your IDFC FIRST Bank
        // Credit Card ending XX4969 at SWIGGY IN on..."). Previously the merchant map only ever
        // saw counterparty.name, so all of these fell through to Uncategorized. Scan the body
        // as a fallback, with two safety constraints: keys must be >= 4 chars, and generic
        // English words that legitimately appear in bank prose are excluded.
        run {
            val upperBody = messageBody.uppercase()
            var bodyMatchCategory: String? = null
            var bodyMatchKey = ""
            var bodyMaxLen = 0
            for ((key, defaultCategory) in MERCHANT_CATEGORIES) {
                if (key.length < 4 || key in BODY_SCAN_EXCLUDED_KEYS) continue
                // A card purchase's body always contains a bill-payment token (it was paid via
                // BillDesk/CRED), but the card was SPENT, not paid off — skip those keys so the
                // real merchant token (e.g. IPRU) can win and it stays counted as spending.
                if (isCardPurchase && defaultCategory == AppConstants.Categories.CREDIT_BILL_PAYMENTS) continue
                if (key.length > bodyMaxLen && SmsConstants.containsToken(upperBody, key)) {
                    bodyMatchCategory = defaultCategory
                    bodyMatchKey = key
                    bodyMaxLen = key.length
                }
            }
            if (bodyMatchCategory != null) {
                trace?.add("Matched Merchant in SMS body: $bodyMatchKey -> $bodyMatchCategory")
                return enforceCategoryTypeCompatibility(bodyMatchCategory, transactionType, trace)
            }
        }

        // PRIORITY 6: ML Classifier - currently disabled (no model wired up).
        // Kept as an explicit no-op step (rather than silently absent) so the priority
        // numbering below stays accurate if/when a model is reintroduced.

        // PRIORITY 7: Type-based Fallback
        val defaultCategory = TYPE_DEFAULT_CATEGORIES[transactionType] ?: AppConstants.Categories.UNCATEGORIZED

        // 7a. Handling Generic/Offline Merchants
        // Q-Code / Card-Machine UPI (Generic Offline)
        if (counterparty.upiId?.matches(Regex("(?i)^q\\d+.*")) == true) {
             trace?.add("Matched: Q-Code VPA -> Offline Merchant")
             return enforceCategoryTypeCompatibility("Offline Merchant", transactionType, trace)
        }

        // Offline QR gateway VPAs (BharatPe / GPay terminal / PaytmQR / Ezetap): these are
        // in-person shop payments where the VPA carries no merchant name. Previously they
        // fell all the way through to Uncategorized; Offline Merchant is the accurate bucket.
        val vpaPrefix = counterparty.upiId?.substringBefore("@")?.lowercase()
        if (vpaPrefix != null && transactionType == TransactionType.EXPENSE &&
            (vpaPrefix.startsWith("bharatpe") ||
             vpaPrefix.matches(Regex("^gpay-?\\d+.*")) ||
             vpaPrefix.startsWith("paytmqr") ||
             vpaPrefix.startsWith("ezetap"))) {
            trace?.add("Matched: Offline QR gateway VPA '$vpaPrefix' -> Offline Merchant")
            return enforceCategoryTypeCompatibility("Offline Merchant", transactionType, trace)
        }

        // 7b. Generic Fallback
        if (defaultCategory == "Uncategorized" &&
            (counterparty.type == CounterpartyExtractor.CounterpartyType.MERCHANT || 
             (transactionType == TransactionType.EXPENSE && !counterparty.name.isNullOrBlank()))) {
            trace?.add("Fallback: Generic Merchant/Expense with Name -> Miscellaneous")
            return enforceCategoryTypeCompatibility("Miscellaneous", transactionType, trace)
        }
        
        // Flag large unverified income
        if (defaultCategory == AppConstants.Categories.OTHER_INCOME && transactionType == TransactionType.INCOME) {
            val hasNoIdentifiedSource = !lowerBody.contains("neft") && 
                                        !lowerBody.contains("imps") && 
                                        !lowerBody.contains("upi") && 
                                        !lowerBody.contains("salary") &&
                                        counterparty.name.isNullOrBlank()
            if (hasNoIdentifiedSource) {
                trace?.add("WARN: Large unverified credit with unknown source -> Unverified Income (Needs Review)")
                return "Unverified Income"
            }
        }
        
        trace?.add("Fallback: Type-based default -> $defaultCategory")
        return defaultCategory
    }
    
    /**
     * Enforce compatibility between Category and Transaction Type.
     * Prevents ML/Keyword logic from assigning "Dining Out" (Expense) to an INCOME transaction.
     */
    private fun enforceCategoryTypeCompatibility(
        category: String,
        type: TransactionType,
        trace: MutableList<String>?
    ): String {
        // 1. Check strict overrides first
        if (type == TransactionType.REFUND || type == TransactionType.CASHBACK) return category
        
        // 2. Map string category to a type using DefaultCategories as a heuristic
        // If the category matches a distinct expense type but the transaction is INCOME, flag conflict.
        
        if (type == TransactionType.INCOME) {
            // Expenses cannot be Income.
            // Check if this category is KNOWN to be an expense type or name
            val isLikelyExpense = com.saikumar.expensetracker.data.common.DefaultCategories.ALL_CATEGORIES
                .find { it.name.equals(category, ignoreCase = true) }
                ?.type?.let { 
                    it == CategoryType.FIXED_EXPENSE || 
                    it == CategoryType.VARIABLE_EXPENSE || 
                    it == CategoryType.VEHICLE ||
                    it == CategoryType.LIABILITY
                } ?: false
                
            if (isLikelyExpense) {
                trace?.add("Incompatibility detected: INCOME type cannot be '$category' -> Reverting to 'Other Income'")
                return AppConstants.Categories.OTHER_INCOME
            }
        }

        return category
    }
    
    /**
     * Check if the counterparty name matches the user's own name.
     * This helps detect self-transfers when money moves between user's accounts at different banks.
     *
     * Uses account holder names discovered from NEFT/salary deposits and UPI VPAs.
     */
    fun isUserOwnName(name: String, userAccounts: List<UserAccount>): Boolean {
        val lower = name.lowercase()

        // Check against account holder names
        val holderNames = userAccounts.mapNotNull { it.accountHolderName }.distinct()
        for (holderName in holderNames) {
            if (areNamesEquivalent(name, holderName)) {
                return true
            }
        }

        // Check against registered UPI VPAs (phone numbers, custom VPAs)
        // BUGFIX: previously a raw substring check, so a short VPA prefix like "sai"
        // (from e.g. "sai@okhdfcbank") would match any counterparty name containing that
        // substring - including unrelated merchants like "Sai Traders" or "Sai Electronics" -
        // wrongly tagging real purchases as Self Transfer. Require a word-boundary match,
        // and ignore VPA prefixes too short to be meaningful (<4 chars, e.g. numeric handles).
        val userVpas = userAccounts.mapNotNull { it.upiVpa }.distinct()
        for (vpa in userVpas) {
            // Extract VPA prefix (before @)
            val vpaPrefix = vpa.substringBefore("@").lowercase()
            if (vpaPrefix.length < 4) continue
            if (SmsConstants.containsToken(lower, vpaPrefix) || SmsConstants.containsToken(vpaPrefix, lower)) {
                return true
            }
        }

        return false
    }

    /**
     * Check if an account number (last 4 digits) appears in text with proper context.
     * Validates that the digits are actually part of an account number pattern, not just random digits.
     */
    private fun isAccountNumberInText(text: String, last4: String): Boolean {
        if (last4.length != 4 || !last4.all { it.isDigit() }) return false

        val upper = text.uppercase()

        // Pattern 1: "A/c XX1234", "A/c *1234", "Account XX1234"
        if (Regex("(?:A/C|ACCT|ACCOUNT)\\s+[*X]*(${Regex.escape(last4)})").containsMatchIn(upper)) {
            return true
        }

        // Pattern 2: "Card XX1234", "Card ending 1234"
        if (Regex("(?:CARD|CREDIT CARD)\\s+(?:ENDING\\s+)?[*X]*(${Regex.escape(last4)})").containsMatchIn(upper)) {
            return true
        }

        // Pattern 3: Just contains the 4 digits preceded by XX or **
        if (Regex("[*X]{2,}(${Regex.escape(last4)})").containsMatchIn(upper)) {
            return true
        }

        // Pattern 4: Four digits at the end of the text (common in merchant names)
        if (Regex("(${Regex.escape(last4)})\\s*$").containsMatchIn(text)) {
            return true
        }

        return false
    }

    /**
     * Improved name matching that handles various name formats.
     * Delegates to the shared [com.saikumar.expensetracker.util.NameMatcher] so this
     * logic has a single source of truth (previously duplicated in 3 places, and this
     * copy in particular had drifted out of sync with CounterpartyExtractor's version -
     * it was missing initials matching, e.g. "S REDDY" vs "SAIKUMAR REDDY").
     */
    private fun areNamesEquivalent(name1: String, name2: String): Boolean =
        com.saikumar.expensetracker.util.NameMatcher.areNamesEquivalent(name1, name2)

    fun calculateConfidence(category: String, wasUserRule: Boolean = false): Int {
        if (wasUserRule) return Confidence.USER_RULE
        
        return when (category) {
            AppConstants.Categories.SELF_TRANSFER -> Confidence.SELF_TRANSFER
            AppConstants.Categories.SALARY -> Confidence.SALARY_PATTERN
            "Interest" -> Confidence.PATTERN_MATCH
            "Investment Redemption" -> Confidence.PATTERN_MATCH
            
            in MERCHANT_CATEGORIES.values -> Confidence.MERCHANT_EXACT
            
            AppConstants.Categories.MUTUAL_FUNDS, 
            AppConstants.Categories.CREDIT_BILL_PAYMENTS, 
            AppConstants.Categories.P2P_TRANSFERS, 
            AppConstants.Categories.OTHER_INCOME -> Confidence.TYPE_DEFAULT
            
            AppConstants.Categories.MISCELLANEOUS -> Confidence.FALLBACK
            AppConstants.Categories.UNCATEGORIZED, "Unknown Expense" -> Confidence.UNCATEGORIZED
            "Unverified Income" -> Confidence.UNVERIFIED
            
            else -> Confidence.PATTERN_MATCH
        }
    }
}
