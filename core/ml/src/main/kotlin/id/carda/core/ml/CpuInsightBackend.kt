package id.carda.core.ml

import android.os.Looper
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.security.MessageDigest

/** Bundled LiteRT CPU runtime: no network, Play services initialization, GPU or NPU dependency. */
class CpuInsightBackend private constructor(
    private val interpreter: Interpreter,
    private val sampleCount: Int,
) : InsightBackend {
    private var closed = false

    @Synchronized
    override fun infer(samples: FloatArray): InsightPrediction {
        checkWorkerThread()
        check(!closed)
        require(samples.size == sampleCount && samples.all { it.isFinite() })
        val input = ByteBuffer.allocateDirect(sampleCount * 4).order(ByteOrder.nativeOrder())
        input.asFloatBuffer().put(samples)
        val output = ByteBuffer.allocateDirect(sampleCount * 4).order(ByteOrder.nativeOrder())
        interpreter.run(input, output)
        val waveform = FloatArray(sampleCount)
        output.rewind()
        output.asFloatBuffer().get(waveform)
        check(waveform.all { it.isFinite() })
        return InsightPrediction(waveform, calibratedConfidence = null)
    }

    @Synchronized
    override fun close() {
        if (!closed) {
            closed = true
            interpreter.close()
        }
    }

    companion object {
        fun open(file: File, spec: InsightModelSpec): CpuInsightBackend {
            checkWorkerThread()
            require(file.isFile && file.length() == spec.bytes) { "Model artifact unavailable or size differs" }
            // Hash and map the same open file handle, including if the pathname is replaced.
            val mapped = RandomAccessFile(file, "r").use { source ->
                require(source.length() == spec.bytes)
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(32_768)
                var bytesRead = 0L
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    bytesRead += count
                    require(bytesRead <= spec.bytes)
                    digest.update(buffer, 0, count)
                }
                require(bytesRead == spec.bytes)
                val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                require(actual == spec.sha256) { "Model artifact hash differs" }
                source.channel.map(FileChannel.MapMode.READ_ONLY, 0, spec.bytes)
            }
            // Conservative CPU kernels; XNNPACK initialization SIGILLs on the current ARM64 AVD.
            // Acceleration can be introduced only as a separately tested optional path.
            val interpreter = Interpreter(mapped, Interpreter.Options().setNumThreads(1).setUseXNNPACK(false))
            try {
                require(interpreter.inputTensorCount == 1 && interpreter.outputTensorCount == 1)
                val shape = intArrayOf(1, spec.samples)
                require(interpreter.getInputTensor(0).shape().contentEquals(shape))
                require(interpreter.getOutputTensor(0).shape().contentEquals(shape))
                require(interpreter.getInputTensor(0).dataType() == DataType.FLOAT32)
                require(interpreter.getOutputTensor(0).dataType() == DataType.FLOAT32)
                return CpuInsightBackend(interpreter, spec.samples)
            } catch (failure: Throwable) {
                interpreter.close()
                throw failure
            }
        }

        private fun checkWorkerThread() {
            check(Looper.myLooper() != Looper.getMainLooper()) { "Model work must run off the main thread" }
        }
    }
}
