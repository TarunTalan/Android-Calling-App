package com.example.myapplication.utils

import android.os.Build
import android.telephony.PhoneNumberUtils
import java.util.*

/**
 * Formats a raw digit string into a human-readable phone number using the
 * platform formatter where possible. Falls back to a simple grouping when
 * platform formatting isn't available or fails.
 * Examples (may vary by locale):
 *  - "+14155552671" -> "+1 415-555-2671" (or locale-specific)
 *  - "9876543210" -> "98765 43210" (fallback grouping)
 */
fun formatPhoneDisplay(number: String): String {
    if (number.isEmpty()) return ""

    // Keep plus sign if present and remove other non-digits for formatting
    val cleaned = number.trim()

    // Try Android's PhoneNumberUtils for locale-aware formatting. Use the
    // locale country as hint when API supports the overload.
    try {
        val formatted: String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // formatNumber(number, defaultCountryIso) returns null on failure
            val country = Locale.getDefault().country ?: ""
            PhoneNumberUtils.formatNumber(cleaned, country)
        } else {
            // Older platforms: use the simpler formatter
            PhoneNumberUtils.formatNumber(cleaned)
        }
        if (!formatted.isNullOrBlank()) return formatted
    } catch (_: Exception) {
        // Fall through to fallback grouping
    }

    // Fallback: simple human-friendly grouping (5/5/...) similar to previous behavior
    val digitsOnly = cleaned.filter { it.isDigit() || it == '+' }
    return when {
        digitsOnly.length <= 5 -> digitsOnly
        digitsOnly.length <= 10 -> "${digitsOnly.take(5)} ${digitsOnly.drop(5)}"
        else -> "${digitsOnly.take(5)} ${digitsOnly.drop(5).take(5)} ${digitsOnly.drop(10)}"
    }
}
