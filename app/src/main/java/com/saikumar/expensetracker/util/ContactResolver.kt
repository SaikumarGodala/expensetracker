package com.saikumar.expensetracker.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves phone-number UPI handles ("9505458713@ybl", "9573724969-2@ybl") to contact
 * names from the device address book.
 *
 * A large share of P2P UPI transactions carry ONLY a phone-number VPA - no payee name in
 * the SMS at all - so they render as raw numbers and can never match the Transfer Circle
 * (which is name-based). Looking the number up in contacts fixes both: rows show
 * "Ramesh Kumar" instead of "9505458713@ybl", and trust-circle matching starts working
 * for these transactions.
 *
 * Entirely optional: without READ_CONTACTS permission every call is a silent no-op.
 * Lookups are cached per-process (contacts don't change mid-scan; a full inbox scan
 * would otherwise hit the ContentResolver thousands of times).
 */
object ContactResolver {
    private const val TAG = "ContactResolver"

    // phone -> display name; empty string = looked up before, no contact found
    private val cache = ConcurrentHashMap<String, String>()

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Extract a plausible Indian mobile number from a VPA prefix.
     *
     * A phone VPA can carry more than just the bare number: bank-added suffixes
     * ("9573724969-2@ybl"), country codes ("919505458713@ybl"), a leading 0, or
     * trailing letters ("8247610779tb@ibl", "9505458713xyz@paytm"). The old approach
     * (reject if >2 letters, then match the total digit count exactly) missed all of
     * those. Instead, split the prefix into maximal digit-runs (separators and letters
     * become boundaries) and look for a valid 10-digit Indian mobile in any run:
     *   - a clean 10-digit run starting 6-9  -> that's the mobile
     *   - 12 digits "91" + 10-digit mobile   -> drop the country code
     *   - 11 digits "0" + 10-digit mobile    -> drop the leading zero
     *
     * This also naturally rejects merchant handles without a blocklist: Q-codes
     * ("Q004878614" -> 9-digit run), BharatPe refs ("BHARATPE.90071624296" -> straight
     * 11-digit run, not 0/91-prefixed) and QR order numbers (13+ digit runs) match no rule.
     */
    // Payment-gateway / QR handles: the embedded number is a merchant reference or the
    // merchant's own line, never the counterparty we want to name. A shop owner's number
    // could legitimately be in the user's contacts, which would otherwise mis-tag the
    // shop payment as a person, so reject these outright.
    private val MERCHANT_GATEWAY_PREFIXES = listOf(
        "bharatpe", "paytmqr", "paytm", "razorpay", "payu", "ezetap", "pinelabs", "mswipe"
    )

    fun extractPhoneFromVpa(upiId: String?): String? {
        if (upiId == null) return null
        val prefix = upiId.substringBefore("@")
        val lower = prefix.lowercase()
        if (MERCHANT_GATEWAY_PREFIXES.any { lower.startsWith(it) }) return null
        // Q-codes ("Q004878614") are card-machine merchant handles, not people
        if (lower.startsWith("q") && lower.getOrNull(1)?.isDigit() == true) return null
        // Maximal digit runs (letters, dots, dashes are separators)
        val runs = prefix.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }
        for (run in runs) {
            val mobile = when {
                run.length == 10 && run[0] in '6'..'9' -> run
                run.length == 12 && run.startsWith("91") && run[2] in '6'..'9' -> run.substring(2)
                run.length == 11 && run[0] == '0' && run[1] in '6'..'9' -> run.substring(1)
                else -> null
            }
            if (mobile != null) return mobile
        }
        return null
    }

    /** Contact display name for [phone], or null (no permission / not in contacts). */
    fun lookup(context: Context, phone: String): String? {
        if (!hasPermission(context)) return null

        cache[phone]?.let { return it.ifEmpty { null } }

        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phone)
            )
            val name = context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

            cache[phone] = name ?: ""
            name
        } catch (e: Exception) {
            Log.w(TAG, "Contact lookup failed: ${e.message}")
            null
        }
    }

    /** Convenience: VPA in, contact name out. */
    fun resolveVpaToContactName(context: Context, upiId: String?): String? {
        val phone = extractPhoneFromVpa(upiId) ?: return null
        return lookup(context, phone)
    }
}
