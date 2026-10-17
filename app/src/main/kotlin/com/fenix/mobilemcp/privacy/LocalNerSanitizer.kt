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
        private val API_KEY_REGEX = Regex(
            """(?i)\b(sk-[A-Za-z0-9_-]{16,}|gsk_[A-Za-z0-9_-]{16,}|ghp_[A-Za-z0-9]{20,}|AIza[0-9A-Za-z-_]{35}|eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}|Bearer\s+[A-Za-z0-9_.-]{20,})\b"""
        )
        private val CBU_REGEX = Regex("""\b\d{22}\b""")
        private val CUIT_REGEX = Regex("""\b(?:20|23|24|27|30|33|34)[-\s]?\d{8}[-\s]?\d\b""")
        private val PHONE_AR_REGEX = Regex("""\+54[\s-]?(?:9[\s-]?)?(?:11|[23]\d{2,3})[\s-]?\d{3,4}[\s-]?\d{4}\b""")
        private val EMAIL_REGEX = Regex("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b""")
        private val IMEI_REGEX = Regex("""\b\d{2}[-\s]?\d{6}[-\s]?\d{6}[-\s]?\d\b""")
        private val HARDWARE_SERIAL_REGEX = Regex("""\b(ZY22MK7B2P|RS4HQ4YHQKZLLB4T)\b""")
        private val CREDIT_CARD_REGEX = Regex("""\b(?:\d{4}[-\s]?){3}\d{4}\b|\b\d{15,16}\b""")
        private val DNI_REGEX = Regex("""(?i)\b(?:dni[\s:#-]*)?\d{1,2}\.\d{3}\.\d{3}\b|\b(?:dni[\s:#-]*)\d{7,8}\b|\b\d{8}\b""")
        private val MAC_ADDRESS_REGEX = Regex("""\b([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})\b""")
    }

    /**
     * Sanitiza el texto de entrada reemplazando entidades sensibles por marcadores genéricos.
     */
    fun sanitize(input: String): String {
        if (input.isBlank()) return input

        var sanitized = input

        // 1. Ocultar API keys, tokens GitHub (ghp_), JWTs (eyJ...) y Bearer tokens
        sanitized = API_KEY_REGEX.replace(sanitized) { "[REDACTED_API_KEY]" }

        // 2. Ocultar CBU bancario argentino (22 dígitos exactos)
        sanitized = CBU_REGEX.replace(sanitized) { "[REDACTED_CBU]" }

        // 3. Ocultar CUIT / CUIL argentino
        sanitized = CUIT_REGEX.replace(sanitized) { "[REDACTED_CUIT]" }

        // 4. Ocultar teléfonos móviles/fijos con prefijo internacional +54
        sanitized = PHONE_AR_REGEX.replace(sanitized) { "[REDACTED_PHONE]" }

        // 5. Ocultar IMEIs (15 dígitos de telefonía móvil)
        sanitized = IMEI_REGEX.replace(sanitized) { match ->
            val digits = match.value.filter { it.isDigit() }
            if (digits.length == 15) {
                "[REDACTED_IMEI]"
            } else {
                match.value
            }
        }

        // 6. Ocultar números de tarjeta de crédito (validando con Luhn)
        sanitized = CREDIT_CARD_REGEX.replace(sanitized) { match ->
            val digits = match.value.filter { it.isDigit() }
            if (digits.length in 13..19 && isValidLuhn(digits)) {
                "[REDACTED_CARD]"
            } else {
                match.value
            }
        }

        // 7. Ocultar DNI argentino (con puntos o 7-8 dígitos)
        sanitized = DNI_REGEX.replace(sanitized) { "[REDACTED_DNI]" }

        // 8. Ocultar direcciones de correo
        sanitized = EMAIL_REGEX.replace(sanitized) { "[REDACTED_EMAIL]" }

        // 9. Ocultar direcciones MAC
        sanitized = MAC_ADDRESS_REGEX.replace(sanitized) { "[REDACTED_MAC]" }

        // 10. Ocultar números de serie específicos de hardware del clúster
        sanitized = HARDWARE_SERIAL_REGEX.replace(sanitized) { "[REDACTED_SERIAL]" }

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

