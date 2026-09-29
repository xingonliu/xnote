package com.xnote.app

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.FloatBuffer
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

// -- Type Definitions

/** Opt-in hardware probe; a passing probe still requires reviewing latency, memory and mask quality. */
class S12ModelPreflightTest {
    // -- Constants

    private val modelHash = "309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8"
    private val sampleHash = "8d13d397fcbc4c3742d1d30d00cabe53c7b1ecd0a8cbf5a73ab0f78330fca12f"
    private val side = 320

    // -- Functions

    @Test
    fun cpuModelLoadsAndProducesTransparentPngOnDevice() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Run explicitly with s12Preflight=true after preparing assets", arguments.getString("s12Preflight") == "true")
        val context = InstrumentationRegistry.getInstrumentation().context
        val model = context.assets.open("u2netp.onnx").use { it.readBytes() }
        val source = context.assets.open("sample.jpg").use { it.readBytes() }
        assertEquals(modelHash, sha256(model))
        assertEquals(sampleHash, sha256(source))
        val original = requireNotNull(BitmapFactory.decodeByteArray(source, 0, source.size))
        val resized = Bitmap.createScaledBitmap(original, side, side, true)
        val pixels = IntArray(side * side)
        resized.getPixels(pixels, 0, side, 0, 0, side, side)
        val maximum = pixels.maxOf { maxOf((it ushr 16) and 255, (it ushr 8) and 255, it and 255) }.coerceAtLeast(1)
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val deviation = floatArrayOf(0.229f, 0.224f, 0.225f)
        val input = FloatArray(3 * side * side) { index ->
            val channel = index / pixels.size
            val value = (pixels[index % pixels.size] ushr (16 - channel * 8)) and 255
            (value.toFloat() / maximum - mean[channel]) / deviation[channel]
        }
        val arena = arguments.getString("s12Arena") == "true"
        val pssBefore = Debug.getPss()
        val peakPss = AtomicLong(pssBefore)
        val sampling = AtomicBoolean(true)
        val sampler = thread(name = "s12-memory-probe") {
            while (sampling.get()) {
                peakPss.accumulateAndGet(Debug.getPss(), ::maxOf)
                SystemClock.sleep(20)
            }
        }
        val report = JSONObject()
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("sdk", Build.VERSION.SDK_INT)
            .put("abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
            .put("ort", OrtEnvironment.getEnvironment().version)
            .put("modelSha256", modelHash)
            .put("modelBytes", model.size)
            .put("cpuArenaAndMemoryPattern", arena)
            .put("pssBeforeKiB", pssBefore)
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "s12-preflight")
            .apply { check(isDirectory || mkdirs()) }
        try {
            OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(2)
                options.setInterOpNumThreads(1)
                options.setCPUArenaAllocator(arena)
                options.setMemoryPatternOptimization(arena)
                val environment = OrtEnvironment.getEnvironment()
                val started = SystemClock.elapsedRealtimeNanos()
                environment.createSession(model, options).use { session ->
                    report.put("loadMs", (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0)
                    OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), longArrayOf(1, 3, 320, 320)).use { tensor ->
                        val timings = JSONArray()
                        repeat(5) { iteration ->
                            val start = SystemClock.elapsedRealtimeNanos()
                            session.run(mapOf("input.1" to tensor), setOf("1959")).use { result ->
                                timings.put((SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0)
                                val mask = (result[0] as OnnxTensor).floatBuffer
                                assertEquals(side * side, mask.remaining())
                                val values = FloatArray(mask.remaining()).also(mask::get)
                                assertTrue(values.all { it.isFinite() && it in 0f..1f })
                                val low = values.min()
                                val high = values.max()
                                assertTrue("Sample must separate foreground and background", high - low > 0.5f)
                                if (iteration == 4) {
                                    report.put("maskMin", low).put("maskMax", high)
                                    writePreview(original, values, low, high, File(directory, "result-arena-$arena.png"))
                                }
                            }
                        }
                        report.put("inferenceMs", timings)
                    }
                }
            }
        } finally {
            sampling.set(false)
            sampler.join()
            if (resized !== original) resized.recycle()
            original.recycle()
            report.put("sampledPeakPssKiB", peakPss.get()).put("pssAfterCloseKiB", Debug.getPss())
            File(directory, "report-arena-$arena.json").writeText(report.toString(2))
            Log.i("S12Preflight", report.toString())
        }
    }

    private fun writePreview(original: Bitmap, values: FloatArray, low: Float, high: Float, file: File) {
        val maskPixels = IntArray(values.size) { index ->
            val value = ((values[index] - low) / (high - low) * 255).toInt().coerceIn(0, 255)
            (255 shl 24) or (value shl 16) or (value shl 8) or value
        }
        val small = Bitmap.createBitmap(maskPixels, side, side, Bitmap.Config.ARGB_8888)
        val mask = Bitmap.createScaledBitmap(small, original.width, original.height, true)
        val pixels = IntArray(original.width * original.height)
        val alpha = IntArray(pixels.size)
        original.getPixels(pixels, 0, original.width, 0, 0, original.width, original.height)
        mask.getPixels(alpha, 0, original.width, 0, 0, original.width, original.height)
        pixels.indices.forEach { index -> pixels[index] = (pixels[index] and 0x00ffffff) or ((alpha[index] and 255) shl 24) }
        val result = Bitmap.createBitmap(pixels, original.width, original.height, Bitmap.Config.ARGB_8888)
        try {
            assertTrue(pixels.any { (it ushr 24) < 10 })
            assertTrue(pixels.any { (it ushr 24) > 245 })
            file.outputStream().use { assertTrue(result.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            result.recycle()
            if (mask !== small) mask.recycle()
            small.recycle()
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
}
