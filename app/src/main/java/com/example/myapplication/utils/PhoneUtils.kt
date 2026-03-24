package com.example.myapplication.utils

/**
 * Formats a raw digit string into a human-readable phone number.
 * e.g. "9876543210" → "98765 43210"
 *      "012345678901" → "01234 56789 01"
 */
fun formatPhoneDisplay(number: String): String {
    if (number.isEmpty()) return ""
    return when {
        number.length <= 5 -> number
        number.length <= 10 -> "${number.take(5)} ${number.drop(5)}"
        else -> "${number.take(5)} ${number.drop(5).take(5)} ${number.drop(10)}"
    }
}
