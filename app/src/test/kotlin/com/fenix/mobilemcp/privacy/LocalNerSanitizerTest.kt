package com.fenix.mobilemcp.privacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LocalNerSanitizerTest {

    private lateinit var sanitizer: LocalNerSanitizer

    @BeforeEach
    fun setup() {
        sanitizer = LocalNerSanitizer()
    }

    @Test
    fun testSanitizeApiKeys() {
        val input = "Mi clave es gsk_1234567890abcdef1234567890 y la de OpenAI es sk-proj-1234567890abcdef1234567890"
        val result = sanitizer.sanitize(input)
        assertFalse(result.contains("gsk_"))
        assertFalse(result.contains("sk-proj-"))
        assertTrue(result.contains("[REDACTED_API_KEY]"))
    }

    @Test
    fun testSanitizeClusterSerials() {
        val input = "Dispositivo Moto G77 serie ZY22MK7B2P y Netbook RS4HQ4YHQKZLLB4T conectados por USB"
        val result = sanitizer.sanitize(input)
        assertFalse(result.contains("ZY22MK7B2P"))
        assertFalse(result.contains("RS4HQ4YHQKZLLB4T"))
        assertEquals("Dispositivo Moto G77 serie [REDACTED_SERIAL] y Netbook [REDACTED_SERIAL] conectados por USB", result)
    }

    @Test
    fun testSanitizeEmails() {
        val input = "Contactar a soporte en usuario.delivery@empresa.com.ar para mas info"
        val result = sanitizer.sanitize(input)
        assertFalse(result.contains("usuario.delivery@empresa.com.ar"))
        assertTrue(result.contains("[REDACTED_EMAIL]"))
    }

    @Test
    fun testSanitizeImei() {
        val input = "El IMEI del equipo es 356938035643809 verificado"
        val result = sanitizer.sanitize(input)
        assertFalse(result.contains("356938035643809"))
        assertTrue(result.contains("[REDACTED_IMEI]"))
    }

    @Test
    fun testSanitizeCreditCardWithLuhnValidation() {
        // Tarjeta de prueba válida (Visa test card 4000 0012 3456 7899 -> Luhn válido)
        val validCard = "Pagar con tarjeta 4000001234567899 ahora"
        val resultValid = sanitizer.sanitize(validCard)
        assertTrue(resultValid.contains("[REDACTED_CARD]"))

        // Cadena de 16 números arbitrarios con Luhn inválido no debe ser censurada como tarjeta
        val nonCard = "Referencia numerica 1111222233334440 del paquete"
        val resultNonCard = sanitizer.sanitize(nonCard)
        assertEquals(nonCard, resultNonCard)
    }
}

