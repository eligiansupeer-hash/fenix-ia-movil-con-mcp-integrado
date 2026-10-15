package com.fenix.mobilemcp.privacy

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sanitizador local de datos sensibles y PII (Named Entity Recognition liviano sin costo).
 * Anonimiza información sensible (números de serie de dispositivos, IMEIs, tokens API,
 * correos electrónicos y tarjetas de crédito) antes de guardarla en SQLite o enviarla a un LLM.
 */
@Singleton
class LocalNerSanitizer @Inject constructor() {

    companion object {
        private val API_KEY_REGEX = Regex("""(?i)\b(sk-[A-Za-z0-9_-]{20,}|gsk_[A-Za-z0-9_-]{20,}|AIza[0-9A-Za-z-_]{35}|Bearer\s+[A-Za-z0-9_.-]{25,})\b""")
        private val EMAIL_REGEX = Regex("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b""")
        private val IMEI_REGEX = Regex("""\b\d{2}[-\s]?\d{6}[-\s]?\d{6}[-\s]?\d\b""")
        private val HARDWARE_SERIAL_REGEX = Regex("""\b(ZY22MK7B2P|RS4HQ4YHQKZLLB4T|[A-Z0-9]{12,18})\b""")
        private val CREDIT_CARD_REGEX = Regex("""\b(?:\d{4}[-\s]?){3}\d{4}\b|\b\d{15,16}\b""")
        private val MAC_ADDRESS_REGEX = Regex("""\b([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})\b""")
    }

    /**
     * Sanitiza el texto de entrada reemplazando entidades sensibles por marcadores genéricos.
     */
    fun sanitize(input: String): String {
        if (input.isBlank()) return input

        var sanitized = input

        // 1. Ocultar API keys y tokens de autenticación
        sanitized = API_KEY_REGEX.replace(sanitized) { "[REDACTED_API_KEY]" }

        // 2. Ocultar IMEIs (15 dígitos de telefonía móvil)
        sanitized = IMEI_REGEX.replace(sanitized) { match ->
            val digits = match.value.filter { it.isDigit() }
            if (digits.length == 15) {
                "[REDACTED_IMEI]"
            } else {
                match.value
            }
        }

        // 3. Ocultar números de tarjeta de crédito (validando con Luhn)
        sanitized = CREDIT_CARD_REGEX.replace(sanitized) { match ->
            val digits = match.value.filter { it.isDigit() }
            if (digits.length in 13..19 && isValidLuhn(digits)) {
                "[REDACTED_CARD]"
            } else {
                match.value
            }
        }

        // 4. Ocultar direcciones de correo
        sanitized = EMAIL_REGEX.replace(sanitized) { "[REDACTED_EMAIL]" }

        // 5. Ocultar direcciones MAC
        sanitized = MAC_ADDRESS_REGEX.replace(sanitized) { "[REDACTED_MAC]" }

        // 6. Ocultar números de serie específicos de hardware del clúster
        sanitized = HARDWARE_SERIAL_REGEX.replace(sanitized) { match ->
            val value = match.value
            if (value == "ZY22MK7B2P" || value == "RS4HQ4YHQKZLLB4T") {
                "[REDACTED_SERIAL]"
            } else {
                value
            }
        }

        return sanitized
    }

    private fun isValidLuhn(number: String): Boolean {
        var sum = 0
        var alternate = false
        for (i in number.length - 1 downTo 0) {
            val digit = number[i].digitToIntOrNull() ?: return false
            var n = digit
            if (alternate) {
                n *= 2
                if (n > 9) n = (n % 10) + 1
            }
            sum += n
            alternate = !alternate
        }
        return (sum % 10 == 0)
    }
}

