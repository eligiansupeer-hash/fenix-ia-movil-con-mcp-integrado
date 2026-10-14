package com.fenix.mobilemcp.benchmark

import com.fenix.mobilemcp.benchmark.corpus.BenchmarkSample
import com.fenix.mobilemcp.data.model.PrivacyModeConfig
import com.fenix.mobilemcp.privacy.ContextExtractor
import com.fenix.mobilemcp.privacy.DeterministicEngine
import com.fenix.mobilemcp.privacy.PiiDetection
import com.fenix.mobilemcp.privacy.PseudonymStore
import com.fenix.mobilemcp.privacy.RedactionEngine
import com.fenix.mobilemcp.privacy.Redactor
import com.fenix.mobilemcp.privacy.TextItem
import com.fenix.mobilemcp.privacy.detectors.CardDetector
import com.fenix.mobilemcp.privacy.detectors.CredentialDetector
import com.fenix.mobilemcp.privacy.detectors.EmailDetector
import com.fenix.mobilemcp.privacy.detectors.IbanDetector
import com.fenix.mobilemcp.privacy.detectors.NationalIdDetector
import com.fenix.mobilemcp.privacy.detectors.PhoneDetector
import com.fenix.mobilemcp.privacy.model.PrivacyModelStore
import com.fenix.mobilemcp.privacy.ner.NerCache
import com.fenix.mobilemcp.privacy.ner.NerEngine
import com.fenix.mobilemcp.privacy.ner.NerSegment
import com.fenix.mobilemcp.privacy.ner.OrtPiiModelRunner
import com.fenix.mobilemcp.privacy.ner.PiiModelInference
import java.io.File

enum class Layer { DETERMINISTIC, MODEL, FULL }

/**
 * Production pipeline components wired manually (no DI) against the benchmark cache dir. A FRESH
 * NerEngine + NerCache is built per layer run so per-layer durations stay comparable — no
 * cross-layer cache warm-up (review finding P59-012). Tests inject a fake [PiiModelInference] to
 * exercise the layer plumbing without the real model (review finding P59-007).
 */
class BenchmarkPipeline(
    cacheDir: File,
    private val inferenceOverride: PiiModelInference? = null,
) {
    val store = PrivacyModelStore(cacheDir)
    val runner = OrtPiiModelRunner(store)
    private val deterministicEngine =
        DeterministicEngine(
            CredentialDetector(),
            CardDetector(),
            IbanDetector(),
            EmailDetector(),
            PhoneDetector(),
            NationalIdDetector(),
        )
    private val contextExtractor = ContextExtractor()
    private val redactor = Redactor(PseudonymStore())

    /** All-categories-on config: the published numbers measure the full default protection surface. */
    val fullConfig = PrivacyModeConfig(enabled = true)

    private fun newNerEngine(): NerEngine = NerEngine(inferenceOverride ?: runner, NerCache())

    private fun newRedactionEngine(): RedactionEngine =
        RedactionEngine(
            deterministicEngine,
            newNerEngine(),
            contextExtractor,
            redactor,
        )

    suspend fun detect(
        layer: Layer,
        samples: List<BenchmarkSample>,
    ): List<List<PiiDetection>> =
        when (layer) {
            Layer.DETERMINISTIC -> {
                samples.map {
                    DeterministicEngine.mergeOverlaps(deterministicEngine.detectAll(it.text, it.context))
                }
            }

            Layer.MODEL -> {
                val engine = newNerEngine()
                samples.chunked(CHUNK).flatMap { detectModelChunk(engine, it) }
            }

            Layer.FULL -> {
                detectFull(samples)
            }
        }

    /**
     * FULL layer: production detections ([RedactionEngine.detect]) + production rendering
     * ([Redactor.apply]) in a SINGLE model pass — the exact composition of
     * [RedactionEngine.redactTexts] without re-running inference for the redacted texts.
     */
    suspend fun detectAndRedactFull(samples: List<BenchmarkSample>): Pair<List<List<PiiDetection>>, List<String>> {
        val detections = detectFull(samples)
        val redacted =
            samples.mapIndexed { index, sample ->
                redactor.apply(sample.text, detections[index], fullConfig)
            }
        return detections to redacted
    }

    private suspend fun detectFull(samples: List<BenchmarkSample>): List<List<PiiDetection>> {
        val engine = newRedactionEngine()
        return samples.chunked(CHUNK).flatMap { chunk ->
            engine.detect(chunk.map { TextItem(it.text, it.context) }, fullConfig)
        }
    }

    private suspend fun detectModelChunk(
        engine: NerEngine,
        chunk: List<BenchmarkSample>,
    ): List<List<PiiDetection>> {
        val segments =
            chunk.mapIndexedNotNull { index, sample ->
                if (sample.text.isBlank()) {
                    null
                } else {
                    NerSegment(index.toString(), sample.context.contextText(), sample.text)
                }
            }
        if (segments.isEmpty()) return chunk.map { emptyList() }
        val results = engine.detect(segments)
        return chunk.indices.map { DeterministicEngine.mergeOverlaps(results[it.toString()].orEmpty()) }
    }

    companion object {
        const val CHUNK = 256
    }
}
