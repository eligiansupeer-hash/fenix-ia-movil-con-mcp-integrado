package com.fenix.mobilemcp.privacy.detectors

import com.fenix.mobilemcp.privacy.DetectionContext
import com.fenix.mobilemcp.privacy.PiiCategory
import com.fenix.mobilemcp.privacy.PiiDetection
import javax.inject.Inject

/**
 * Structural credential detector: flags the whole text as a credential when the field is a password
 * field, or when it is an editable field whose context contains a credential keyword.
 */
class CredentialDetector
    @Inject
    constructor() : DeterministicDetector {
        override fun detect(
            text: String,
            context: DetectionContext,
        ): List<PiiDetection> {
            if (text.isBlank()) return emptyList()
            val flagged =
                context.isPassword ||
                    (context.isEditable && ContextKeywords.matches(context, ContextKeywords.CREDENTIAL))
            return if (flagged) {
                listOf(PiiDetection(PiiCategory.CREDENTIALS, 0, text.length, PiiDetection.Source.STRUCTURAL))
            } else {
                emptyList()
            }
        }
    }
