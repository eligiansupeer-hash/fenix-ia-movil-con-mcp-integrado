package com.fenix.mobilemcp.privacy.detectors

import com.fenix.mobilemcp.privacy.DetectionContext
import com.fenix.mobilemcp.privacy.PiiDetection

/** A single deterministic (rule/checksum-based) PII detector over a piece of [text]. */
interface DeterministicDetector {
    fun detect(
        text: String,
        context: DetectionContext,
    ): List<PiiDetection>
}
