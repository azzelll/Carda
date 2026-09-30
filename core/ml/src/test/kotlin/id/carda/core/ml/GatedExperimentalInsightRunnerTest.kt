package id.carda.core.ml

import id.carda.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class GatedExperimentalInsightRunnerTest {
    private val spec = InsightModelSpec("synthetic-test-only", "a".repeat(64), 100, 512, 128.0, "fixture-input-v1")
    private val device = DeviceProfile("Fixture", "Fake phone", 36, "arm64-v8a", "test", "fixture-pipeline",
        true, true, 1280, 720, 30.0, null, null, emptySet(), 1.0, SupportClassification.COMPATIBLE)
    private val quality = QualityReport(true, 1.0, emptySet(), 1200, 40_000, "fixture-pipeline")
    private val evidence = InsightReleaseEvidence(spec.sha256, "fixture-pipeline",
        "synthetic-unit-test-not-product-evidence", "synthetic-unit-test-not-product-evidence",
        "synthetic-unit-test-not-product-evidence", "synthetic-unit-test-not-product-evidence",
        "synthetic-unit-test-not-product-evidence", 0.8,
        setOf(EvaluatedInsightConditions("Fixture", "Fake phone", 36, "test", 1280, 720, "fixture-condition",
            "arm64-v8a", 29.0, 31.0, setOf(SupportClassification.COMPATIBLE))))

    private fun window(quality: QualityReport = this.quality, samples: Int = 512,
        rate: Double = 128.0, inputVersion: String = "fixture-input-v1", condition: String = "fixture-condition",
        deviceProfile: DeviceProfile = device) =
        InsightSignalWindow(FloatArray(samples), rate, inputVersion, quality, deviceProfile, condition)

    private class FakeBackend(private val prediction: InsightPrediction = InsightPrediction(FloatArray(512), 0.9)) : InsightBackend {
        var calls = 0
        var closed = false
        var failure: Throwable? = null
        override fun infer(samples: FloatArray): InsightPrediction {
            calls++
            failure?.let { throw it }
            return prediction
        }
        override fun close() { closed = true }
    }

    @Test fun rejectedQualityNeverLoadsModel() = runTest {
        val runner = GatedExperimentalInsightRunner(spec, evidence, { error("Must not load") }, StandardTestDispatcher(testScheduler))
        val bad = quality.copy(passed = false, issues = setOf(QualityIssue.POOR_CONTACT))
        assertEquals(ExperimentalInsightResult.Unavailable(InsightUnavailableReason.QUALITY_REJECTED), runner.run(window(bad)))
    }

    @Test fun absentModelOrEvidenceNeverLoads() = runTest {
        val factory = InsightBackendFactory { error("Must not load") }
        assertEquals(ExperimentalInsightResult.Unavailable(InsightUnavailableReason.MODEL_UNAVAILABLE),
            GatedExperimentalInsightRunner(null, null, factory).run(window()))
        assertEquals(ExperimentalInsightResult.Unavailable(InsightUnavailableReason.METHOD_NOT_VALIDATED),
            GatedExperimentalInsightRunner(spec, null, factory).run(window()))
        assertEquals(ExperimentalInsightResult.Unavailable(InsightUnavailableReason.METHOD_NOT_VALIDATED),
            GatedExperimentalInsightRunner(spec, evidence.copy(modelSha256 = "b".repeat(64)), factory).run(window()))
    }

    @Test fun incompatibleShapeCadencePreprocessingOrDurationNeverLoads() = runTest {
        val runner = GatedExperimentalInsightRunner(spec, evidence, { error("Must not load") })
        listOf(window(samples = 511), window(rate = 30.0), window(inputVersion = "other"),
            window(quality.copy(validDurationMillis = 1000))).forEach {
            assertEquals(ExperimentalInsightResult.Unavailable(InsightUnavailableReason.INPUT_INCOMPATIBLE), runner.run(it))
        }
    }

    @Test fun conditionsOutsideEvaluationNeverLoad() = runTest {
        val runner = GatedExperimentalInsightRunner(spec, evidence, { error("Must not load") })
        assertEquals(ExperimentalInsightResult.Unavailable(InsightUnavailableReason.UNSUPPORTED_CONDITIONS),
            runner.run(window(condition = "not-evaluated")))
    }

    @Test fun changedCameraCadenceOrUnsupportedProfileCannotUseEvaluatedConditions() = runTest {
        listOf(device.copy(observedFramesPerSecond = 20.0), device.copy(observedFramesPerSecond = null),
            device.copy(supportClassification = SupportClassification.NOT_SUPPORTED),
            device.copy(hasTorch = false), device.copy(abi = "different-abi")).forEach { changed ->
            val runner = GatedExperimentalInsightRunner(spec, evidence, { FakeBackend() }, StandardTestDispatcher(testScheduler))
            assertEquals(ExperimentalInsightResult.Unavailable(InsightUnavailableReason.UNSUPPORTED_CONDITIONS),
                runner.run(window(deviceProfile = changed)))
        }
    }

    @Test fun waveformOnlyModelCannotSubstituteSqiForConfidence() = runTest {
        val backend = FakeBackend(InsightPrediction(FloatArray(512), null))
        val runner = GatedExperimentalInsightRunner(spec, evidence, { backend }, StandardTestDispatcher(testScheduler))
        assertEquals(ExperimentalInsightResult.Unavailable(InsightUnavailableReason.CONFIDENCE_UNAVAILABLE), runner.run(window()))
        assertTrue(backend.closed)
    }

    @Test fun confidenceRejectionClosesBackend() = runTest {
        val backend = FakeBackend(InsightPrediction(FloatArray(512), 0.79))
        val runner = GatedExperimentalInsightRunner(spec, evidence, { backend }, StandardTestDispatcher(testScheduler))
        assertEquals(ExperimentalInsightResult.Unavailable(InsightUnavailableReason.CONFIDENCE_BELOW_THRESHOLD), runner.run(window()))
        assertTrue(backend.closed)
    }

    @Test fun nonfiniteOrWrongShapePredictionFailsSafely() = runTest {
        listOf(InsightPrediction(floatArrayOf(0f), 0.9), InsightPrediction(FloatArray(512) { Float.NaN }, 0.9),
            InsightPrediction(FloatArray(512), Double.NaN)).forEach { prediction ->
            val backend = FakeBackend(prediction)
            assertEquals(ExperimentalInsightResult.Unavailable(InsightUnavailableReason.INFERENCE_FAILED),
                GatedExperimentalInsightRunner(spec, evidence, { backend }, StandardTestDispatcher(testScheduler)).run(window()))
            assertTrue(backend.closed)
        }
    }

    @Test fun loadingFailureLeavesResultUnavailable() = runTest {
        val runner = GatedExperimentalInsightRunner(spec, evidence, { throw UnsatisfiedLinkError("unsupported") },
            StandardTestDispatcher(testScheduler))
        assertEquals(ExperimentalInsightResult.Unavailable(InsightUnavailableReason.INFERENCE_FAILED), runner.run(window()))
    }

    @Test fun cancellationPropagatesAndClosesResources() = runTest {
        val backend = FakeBackend().apply { failure = CancellationException("cancel") }
        try {
            GatedExperimentalInsightRunner(spec, evidence, { backend }, StandardTestDispatcher(testScheduler)).run(window())
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertTrue(backend.closed)
        }
    }

    @Test fun cancellationDuringLoadingClosesBackendWithoutStartingInference() = runBlocking<Unit> {
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val backend = FakeBackend()
        val runner = GatedExperimentalInsightRunner(spec, evidence, {
            entered.complete(Unit)
            check(release.await(5, TimeUnit.SECONDS))
            backend
        }, Dispatchers.Default)
        val job = launch { runner.run(window()) }
        try {
            withTimeout(5000) { entered.await() }
            job.cancel()
            release.countDown()
            job.join()
            assertEquals(0, backend.calls)
            assertTrue(backend.closed)
        } finally { release.countDown() }
    }

    @Test fun acceptedFixtureIsTransientAndDefensivelyCopied() = runTest {
        val waveform = FloatArray(512) { 0.5f }
        val backend = FakeBackend(InsightPrediction(waveform, 0.9))
        waveform.fill(Float.NaN)
        val result = GatedExperimentalInsightRunner(spec, evidence, { backend }, StandardTestDispatcher(testScheduler))
            .run(window()) as ExperimentalInsightResult.Available
        assertEquals(0.5f, result.copyWaveform()[0])
        result.copyWaveform().fill(Float.NaN)
        assertEquals(0.5f, result.copyWaveform()[0])
        assertFalse(result.toString().contains("0.5"))
        assertTrue(backend.closed)
    }

    @Test fun acceptedSyntheticWaveformCarriesOptionalTimingWithoutChangingConfidenceGate() = runTest {
        val waveform = FloatArray(512)
        for (peak in listOf(64, 192, 320, 448)) waveform[peak] = 1f
        val backend = FakeBackend(InsightPrediction(waveform, 0.9))
        val result = GatedExperimentalInsightRunner(spec, evidence, { backend },
            StandardTestDispatcher(testScheduler)).run(window()) as ExperimentalInsightResult.Available
        assertEquals(128.0, result.sampleRateHz, 0.0)
        assertEquals(1_000.0, result.estimatedTiming!!.meanRrMillis, 0.001)
        assertEquals(listOf(64, 192, 320, 448), result.estimatedTiming!!.copyPeakSampleIndices())
        assertTrue(backend.closed)
    }
}
