package com.jellyfin.client

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateMapOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

// Status: "downloading" | "done" | "error"
data class DownloadEntry(
    val id: String,
    val name: String,
    val type: String,        // "Movie" atau "Episode"
    val imageUrl: String,
    val streamUrl: String,   // sumber unduhan (berisi api_key)
    val localPath: String,   // file:// URI hasil unduhan
    val status: String = "downloading",
    val progress: Float = 0f,
    val timestamp: Long = 0L
)

object DownloadManager {
    // State observable untuk Compose (key = itemId)
    val downloads = mutableStateMapOf<String, DownloadEntry>()

    private val client = OkHttpClient()
    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeCalls = HashMap<String, Call>()

    private fun dir(context: Context): File {
        val d = File(context.filesDir, "downloads")
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun persist(context: Context) {
        val prefs = context.getSharedPreferences("DownloadPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("downloads", gson.toJson(downloads.values.toList())).apply()
    }

    private fun update(context: Context, entry: DownloadEntry, alsoPersist: Boolean = false) {
        mainHandler.post {
            downloads[entry.id] = entry
            if (alsoPersist) persist(context)
        }
    }

    /** Muat daftar unduhan tersimpan saat app dibuka. */
    fun load(context: Context) {
        val prefs = context.getSharedPreferences("DownloadPrefs", Context.MODE_PRIVATE)
        val json = prefs.getString("downloads", null) ?: return
        val type = object : TypeToken<List<DownloadEntry>>() {}.type
        val stored: List<DownloadEntry> = try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        downloads.clear()
        for (e in stored) {
            when (e.status) {
                "done" -> {
                    // Pastikan file masih ada
                    val path = Uri.parse(e.localPath).path
                    if (path != null && File(path).exists()) downloads[e.id] = e
                }
                // Unduhan yang terputus saat app ditutup → tandai error
                else -> downloads[e.id] = e.copy(status = "error")
            }
        }
        persist(context)
    }

    fun isDownloaded(id: String): Boolean = downloads[id]?.status == "done"

    /** Mulai mengunduh sebuah item ke penyimpanan internal app. */
    fun startDownload(
        context: Context,
        id: String,
        name: String,
        type: String,
        imageUrl: String,
        streamUrl: String
    ) {
        val existing = downloads[id]
        if (existing != null && existing.status != "error") return // sudah ada / sedang jalan

        val file = File(dir(context), "$id.mp4")
        val localUri = Uri.fromFile(file).toString()
        val base = DownloadEntry(
            id = id, name = name, type = type, imageUrl = imageUrl,
            streamUrl = streamUrl, localPath = localUri,
            status = "downloading", progress = 0f, timestamp = System.currentTimeMillis()
        )
        update(context, base, alsoPersist = true)

        val request = Request.Builder().url(streamUrl).build()
        val call = client.newCall(request)
        activeCalls[id] = call

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activeCalls.remove(id)
                if (call.isCanceled()) return // dibatalkan → entry sudah dihapus
                update(context, base.copy(status = "error"), alsoPersist = true)
            }

            override fun onResponse(call: Call, response: Response) {
                activeCalls.remove(id)
                val body = response.body
                if (!response.isSuccessful || body == null) {
                    response.close()
                    update(context, base.copy(status = "error"), alsoPersist = true)
                    return
                }
                try {
                    val total = body.contentLength()
                    val input = body.byteStream()
                    val output = FileOutputStream(file)
                    val buffer = ByteArray(8192)
                    var downloaded = 0L
                    var lastPct = -1
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        if (call.isCanceled()) {
                            output.close(); input.close(); file.delete()
                            return
                        }
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            val pct = (downloaded * 100 / total).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                update(context, base.copy(progress = pct / 100f))
                            }
                        }
                    }
                    output.flush(); output.close(); input.close()
                    update(context, base.copy(status = "done", progress = 1f), alsoPersist = true)
                } catch (e: Exception) {
                    file.delete()
                    update(context, base.copy(status = "error"), alsoPersist = true)
                } finally {
                    response.close()
                }
            }
        })
    }

    fun retry(context: Context, id: String) {
        val e = downloads[id] ?: return
        startDownload(context, e.id, e.name, e.type, e.imageUrl, e.streamUrl)
    }

    /** Batalkan (jika sedang berjalan) dan hapus file + entry. */
    fun delete(context: Context, id: String) {
        activeCalls[id]?.cancel()
        activeCalls.remove(id)
        val e = downloads[id]
        if (e != null) {
            val path = Uri.parse(e.localPath).path
            if (path != null) File(path).delete()
        }
        downloads.remove(id)
        persist(context)
    }
}
