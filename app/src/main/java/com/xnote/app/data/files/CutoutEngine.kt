package com.xnote.app.data.files

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.nio.FloatBuffer
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

// -- Type Definitions

class CutoutEngine(context: Context) {
    // -- Constants

    companion object {
        const val ModelSha256 = "309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8"
        const val ModelBytes = 4_574_861L
        private const val ModelUrl = "https://github.com/danielgatis/rembg/releases/download/v0.0.0/u2netp.onnx"
        private const val Side = 320
        // -- State and Variables

        private val inferenceMutex = Mutex()
        private val client = OkHttpClient.Builder().callTimeout(120, TimeUnit.SECONDS).build()
    }

    // -- State and Variables

    private val directory = File(context.applicationContext.filesDir, "cutout-models")

    // -- Functions

    suspend fun process(source: Bitmap, onProgress: (String) -> Unit): IntArray = withContext(Dispatchers.IO) {
        inferenceMutex.withLock {
            val model = prepareModel(onProgress)
            currentCoroutineContext().ensureActive()
            onProgress("正在加载抠图模型")
            val environment = OrtEnvironment.getEnvironment()
            OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(2)
                options.setInterOpNumThreads(1)
                options.setCPUArenaAllocator(false)
                options.setMemoryPatternOptimization(false)
                environment.createSession(model.absolutePath, options).use { session ->
                    currentCoroutineContext().ensureActive()
                    onProgress("正在识别主体")
                    val input = prepareInput(source)
                    OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), longArrayOf(1, 3, 320, 320)).use { tensor ->
                        session.run(mapOf("input.1" to tensor), setOf("1959")).use { outputs ->
                            currentCoroutineContext().ensureActive()
                            val buffer = (outputs[0] as OnnxTensor).floatBuffer
                            val values = FloatArray(buffer.remaining()).also(buffer::get)
                            check(values.size == Side * Side && values.all { it.isFinite() }) { "模型返回无效蒙版" }
                            val low = values.min()
                            val range = values.max() - low
                            check(range > 0.000001f) { "未能识别主体，请更换图片或重试" }
                            val colors = IntArray(values.size) { index ->
                                val alpha = ((values[index] - low) / range * 255).toInt().coerceIn(0, 255)
                                0xff000000.toInt() or (alpha shl 16) or (alpha shl 8) or alpha
                            }
                            val small = Bitmap.createBitmap(colors, Side, Side, Bitmap.Config.ARGB_8888)
                            val mask = Bitmap.createScaledBitmap(small, source.width, source.height, true)
                            try {
                                IntArray(source.width * source.height).also { pixels ->
                                    mask.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
                                    pixels.indices.forEach { pixels[it] = pixels[it] and 255 }
                                }
                            } finally {
                                if (mask !== small) mask.recycle()
                                small.recycle()
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun prepareModel(onProgress: (String) -> Unit): File {
        check(directory.isDirectory || directory.mkdirs())
        val target = File(directory, "u2netp.onnx")
        if (target.isFile && target.length() == ModelBytes && sha256(target) == ModelSha256) return target
        val partial = File(directory, "u2netp.download")
        try {
            onProgress("下载抠图模型 0%（4.36 MiB）")
            coroutineScope {
                val call = client.newCall(Request.Builder().url(ModelUrl).build())
                val cancellation = launch(Dispatchers.Default) {
                    try { awaitCancellation() } finally { call.cancel() }
                }
                try {
                    call.execute().use { response ->
                        check(response.isSuccessful) { "模型下载失败（${response.code}）" }
                        val body = response.body
                        body.byteStream().use { input ->
                            partial.outputStream().use { output ->
                                val buffer = ByteArray(16 * 1024)
                                var total = 0L
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    check(total <= ModelBytes) { "模型文件大小不符" }
                                    output.write(buffer, 0, count)
                                    onProgress("下载抠图模型 ${total * 100 / ModelBytes}%（4.36 MiB）")
                                }
                            }
                        }
                    }
                } finally { cancellation.cancel() }
            }
            check(partial.length() == ModelBytes && sha256(partial) == ModelSha256) { "模型校验失败，请重试" }
            currentCoroutineContext().ensureActive()
            check(partial.renameTo(target)) { "无法保存抠图模型" }
            return target
        } finally {
            partial.delete()
        }
    }

    private fun prepareInput(source: Bitmap): FloatArray {
        val scaled = Bitmap.createScaledBitmap(source, Side, Side, true)
        try {
            val pixels = IntArray(Side * Side)
            scaled.getPixels(pixels, 0, Side, 0, 0, Side, Side)
            val maximum = pixels.maxOf { maxOf((it ushr 16) and 255, (it ushr 8) and 255, it and 255) }.coerceAtLeast(1)
            val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
            val deviation = floatArrayOf(0.229f, 0.224f, 0.225f)
            return FloatArray(3 * pixels.size) { index ->
                val channel = index / pixels.size
                val value = (pixels[index % pixels.size] ushr (16 - channel * 8)) and 255
                (value.toFloat() / maximum - mean[channel]) / deviation[channel]
            }
        } finally {
            if (scaled !== source) scaled.recycle()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
