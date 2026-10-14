package com.fenix.mobilemcp.testutil

import com.fenix.mobilemcp.privacy.PlaceholderSubstitutor
import com.fenix.mobilemcp.privacy.PrivacyToolGate
import com.fenix.mobilemcp.privacy.ProcessedTree
import com.fenix.mobilemcp.privacy.PseudonymStore
import com.fenix.mobilemcp.services.accessibility.MultiWindowResult
import com.fenix.mobilemcp.services.screencapture.ScreenshotRedactor
import io.mockk.coEvery
import io.mockk.mockk

/**
 * Passthrough Privacy Mode collaborators for tool unit tests that do not exercise redaction.
 * The gate returns every field unchanged, the substitutor is identity (empty store), and the
 * screenshot redactor is the real no-op-when-unflagged implementation.
 */
object PrivacyToolTestDoubles {
    fun passthroughGate(): PrivacyToolGate {
        val gate = mockk<PrivacyToolGate>()
        coEvery { gate.text(any(), any()) } answers { firstArg<String?>() }
        coEvery { gate.texts(any()) } answers {
            firstArg<List<Pair<String?, String>>>().map { it.first }
        }
        coEvery { gate.tree(any()) } answers { ProcessedTree(firstArg<MultiWindowResult>(), emptyList()) }
        return gate
    }

    fun identitySubstitutor(): PlaceholderSubstitutor = PlaceholderSubstitutor(PseudonymStore())

    fun screenshotRedactor(): ScreenshotRedactor = ScreenshotRedactor()
}
