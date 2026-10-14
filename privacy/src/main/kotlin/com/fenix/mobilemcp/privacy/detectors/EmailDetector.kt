package com.fenix.mobilemcp.privacy.detectors

import com.fenix.mobilemcp.privacy.DetectionContext
import com.fenix.mobilemcp.privacy.PiiCategory
import com.fenix.mobilemcp.privacy.PiiDetection
import javax.inject.Inject

/** Email detector: flags standard `local@domain.tld` addresses. */
class EmailDetector
    @Inject
    constructor() : DeterministicDetector {
        override fun detect(
            text: String,
            context: DetectionContext,
        ): List<PiiDetection> =
            EMAIL
                .findAll(text)
                .map { match ->
                    PiiDetection(
                        PiiCategory.EMAILS,
                        match.range.first,
                        match.range.last + 1,
                        PiiDetection.Source.DETERMINISTIC,
                    )
                }.toList()

        companion object {
            private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
        }
    }
