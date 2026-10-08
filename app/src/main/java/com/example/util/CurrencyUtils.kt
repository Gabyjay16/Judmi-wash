package com.example.util

import java.text.NumberFormat
import java.util.Locale

object CurrencyUtils {
    /**
     * Formats an amount into CFA Francs (e.g., "15,000 FCFA")
     */
    fun formatCfa(amount: Double): String {
        val rounded = Math.round(amount).toLong()
        val formatted = NumberFormat.getNumberInstance(Locale.US).format(rounded)
        return "$formatted FCFA"
    }
}
