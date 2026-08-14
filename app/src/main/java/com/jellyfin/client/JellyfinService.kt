package com.jellyfin.client

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.UUID

object JellyfinService {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun authenticate(
        serverUrl: String,
        username: String,
        password: String,
        callback: (success: Boolean, token: String?, userId: String?, error: String?) -> Unit
    ) {
        val cleanUrl = serverUrl.trimEnd('/')
        val url = "$cleanUrl/Users/AuthenticateByName"
        val deviceId = UUID.randomUUID().toString()
        val authHeader = "MediaBrowser Client=\"Jellyfin Client Android\", Device=\"Emulator\", DeviceId=\"$deviceId\", Version=\"1.0.0\""

        val bodyJson = JsonObject().apply {
            addProperty("Username", username)
            addProperty("Pw", password)
        }

        val request = Request.Builder()
            .url(url)
            .post(gson.toJson(bodyJson).toRequestBody("application/json".toMediaType()))
            .addHeader("X-Emby-Authorization", authHeader)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post {
                    callback(false, null, null, e.localizedMessage ?: "Gagal terhubung ke server.")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string()
                if (response.isSuccessful && !bodyStr.isNullOrEmpty()) {
                    try {
                        val json = gson.fromJson(bodyStr, JsonObject::class.java)
                        val accessToken = json.get("AccessToken").asString
                        val userObj = json.getAsJsonObject("User")
                        val userId = userObj.get("Id").asString
                        mainHandler.post {
                            callback(true, accessToken, userId, null)
                        }
                    } catch (e: Exception) {
                        mainHandler.post {
                            callback(false, null, null, "Format respon server tidak valid.")
                        }
                    }
                } else {
                    mainHandler.post {
                        callback(false, null, null, "Username atau password salah (Kode: ${response.code}).")
                    }
                }
            }
        })
    }

    fun fetchItems(
        serverUrl: String,
        accessToken: String,
        userId: String,
        itemType: String, // "Movie" atau "Series"
        callback: (List<JellyfinItem>) -> Unit
    ) {
        val cleanUrl = serverUrl.trimEnd('/')
        val url = "$cleanUrl/Items?userId=$userId&includeItemTypes=$itemType&recursive=true&fields=PrimaryImageAspectRatio,ProductionYear,Overview,BackdropImageTags"
        
        // Custom headers dengan token autentikasi
        val authHeader = "MediaBrowser Client=\"Jellyfin Client Android\", Device=\"Emulator\", DeviceId=\"device\", Version=\"1.0.0\", Token=\"$accessToken\""

        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("X-Emby-Authorization", authHeader)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { callback(emptyList()) }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string()
                if (response.isSuccessful && !bodyStr.isNullOrEmpty()) {
                    try {
                        val json = gson.fromJson(bodyStr, JsonObject::class.java)
                        val itemsArray = json.getAsJsonArray("Items")
                        val list = mutableListOf<JellyfinItem>()
                        for (i in 0 until itemsArray.size()) {
                            val itemObj = itemsArray.get(i).asJsonObject
                            val id = itemObj.get("Id").asString
                            val name = itemObj.get("Name").asString
                            val type = itemObj.get("Type").asString
                            val year = itemObj.get("ProductionYear")?.asInt
                            val overview = itemObj.get("Overview")?.asString
                            val backdropTags = itemObj.getAsJsonArray("BackdropImageTags")
                            val backdropUrl = if (backdropTags != null && backdropTags.size() > 0)
                                "$cleanUrl/Items/$id/Images/Backdrop/0?api_key=$accessToken" else null

                            val imageUrl = "$cleanUrl/Items/$id/Images/Primary?api_key=$accessToken"
                            val streamUrl = "$cleanUrl/Videos/$id/stream?static=true&api_key=$accessToken"

                            list.add(JellyfinItem(id, name, type, imageUrl, streamUrl, year = year, overview = overview, backdropUrl = backdropUrl))
                        }
                        mainHandler.post { callback(list) }
                    } catch (e: Exception) {
                        mainHandler.post { callback(emptyList()) }
                    }
                } else {
                    mainHandler.post { callback(emptyList()) }
                }
            }
        })
    }

    fun fetchLatestItems(
        serverUrl: String,
        accessToken: String,
        userId: String,
        itemType: String,
        limit: Int = 6,
        callback: (List<JellyfinItem>) -> Unit
    ) {
        val cleanUrl = serverUrl.trimEnd('/')
        val url = "$cleanUrl/Users/$userId/Items/Latest?includeItemTypes=$itemType&limit=$limit&fields=ProductionYear&imageTypeLimit=1"
        val authHeader = "MediaBrowser Client=\"Jellyfin Client Android\", Device=\"Emulator\", DeviceId=\"device\", Version=\"1.0.0\", Token=\"$accessToken\""

        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("X-Emby-Authorization", authHeader)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { callback(emptyList()) }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string()
                if (response.isSuccessful && !bodyStr.isNullOrEmpty()) {
                    try {
                        // /Items/Latest mengembalikan JSON array, bukan object
                        val arr = gson.fromJson(bodyStr, com.google.gson.JsonArray::class.java)
                        val list = mutableListOf<JellyfinItem>()
                        for (i in 0 until arr.size()) {
                            val obj = arr.get(i).asJsonObject
                            val id = obj.get("Id").asString
                            val name = obj.get("Name").asString
                            val type = obj.get("Type").asString
                            val year = obj.get("ProductionYear")?.asInt
                            val imageUrl = "$cleanUrl/Items/$id/Images/Primary?api_key=$accessToken"
                            val streamUrl = "$cleanUrl/Videos/$id/stream?static=true&api_key=$accessToken"
                            list.add(JellyfinItem(id, name, type, imageUrl, streamUrl, year = year))
                        }
                        mainHandler.post { callback(list) }
                    } catch (e: Exception) {
                        mainHandler.post { callback(emptyList()) }
                    }
                } else {
                    mainHandler.post { callback(emptyList()) }
                }
            }
        })
    }

    fun fetchResumeItems(
        serverUrl: String,
        accessToken: String,
        userId: String,
        callback: (List<JellyfinItem>) -> Unit
    ) {
        val cleanUrl = serverUrl.trimEnd('/')
        // MediaTypes=Video memastikan hanya video (bukan audio/ebook) yang dikembalikan
        // UserData diperlukan untuk PlaybackPositionTicks
        val url = "$cleanUrl/Users/$userId/Items/Resume?MediaTypes=Video&fields=UserData,PrimaryImageAspectRatio,BackdropImageTags,RunTimeTicks"
        val authHeader = "MediaBrowser Client=\"Jellyfin Client Android\", Device=\"Nexfin\", DeviceId=\"nexfin-android\", Version=\"1.0.0\", Token=\"$accessToken\""

        Log.d("Nexfin", "fetchResumeItems → $url")

        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("X-Emby-Authorization", authHeader)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("Nexfin", "fetchResumeItems FAILED: ${e.message}")
                mainHandler.post { callback(emptyList()) }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string()
                Log.d("Nexfin", "fetchResumeItems HTTP ${response.code}, body length=${bodyStr?.length}")
                if (response.isSuccessful && !bodyStr.isNullOrEmpty()) {
                    try {
                        val json = gson.fromJson(bodyStr, JsonObject::class.java)
                        val itemsArray = json.getAsJsonArray("Items")
                        Log.d("Nexfin", "fetchResumeItems: ${itemsArray?.size()} items from server")
                        val list = mutableListOf<JellyfinItem>()
                        for (i in 0 until (itemsArray?.size() ?: 0)) {
                            try {
                                val itemObj = itemsArray.get(i).asJsonObject
                                val id = itemObj.get("Id").asString
                                val name = itemObj.get("Name").asString
                                val type = itemObj.get("Type").asString

                                val backdropTags = itemObj.getAsJsonArray("BackdropImageTags")
                                val hasBackdrop = backdropTags != null && backdropTags.size() > 0
                                val imageUrl = "$cleanUrl/Items/$id/Images/Primary?api_key=$accessToken"
                                val backdropUrl = if (hasBackdrop) {
                                    "$cleanUrl/Items/$id/Images/Backdrop/0?api_key=$accessToken"
                                } else null
                                val streamUrl = "$cleanUrl/Videos/$id/stream?static=true&api_key=$accessToken"

                                val userData = itemObj.getAsJsonObject("UserData")
                                val posTicks = userData?.get("PlaybackPositionTicks")?.asLong ?: 0L
                                val runTimeTicks = itemObj.get("RunTimeTicks")?.asLong ?: 0L

                                val posMs = posTicks / 10000L
                                val durMs = runTimeTicks / 10000L

                                Log.d("Nexfin", "  resume item: $name ($type) pos=${posMs}ms dur=${durMs}ms")
                                list.add(JellyfinItem(id, name, type, imageUrl, streamUrl, posMs, durMs, backdropUrl = backdropUrl))
                            } catch (itemEx: Exception) {
                                Log.e("Nexfin", "fetchResumeItems: error parsing item $i: ${itemEx.message}")
                            }
                        }
                        mainHandler.post { callback(list) }
                    } catch (e: Exception) {
                        Log.e("Nexfin", "fetchResumeItems: JSON parse error: ${e.message}")
                        mainHandler.post { callback(emptyList()) }
                    }
                } else {
                    Log.e("Nexfin", "fetchResumeItems: not successful or empty body, code=${response.code}")
                    mainHandler.post { callback(emptyList()) }
                }
            }
        })
    }
    fun fetchItemDetails(
        serverUrl: String,
        accessToken: String,
        userId: String,
        itemId: String,
        callback: (JellyfinDetails?) -> Unit
    ) {
        val cleanUrl = serverUrl.trimEnd('/')
        val url = "$cleanUrl/Items/$itemId?userId=$userId"
        val authHeader = "MediaBrowser Client=\"Jellyfin Client Android\", Device=\"Emulator\", DeviceId=\"device\", Version=\"1.0.0\", Token=\"$accessToken\""

        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("X-Emby-Authorization", authHeader)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { callback(null) }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string()
                if (response.isSuccessful && !bodyStr.isNullOrEmpty()) {
                    try {
                        val itemObj = gson.fromJson(bodyStr, JsonObject::class.java)
                        val id = itemObj.get("Id").asString
                        val name = itemObj.get("Name").asString
                        val overview = itemObj.get("Overview")?.asString
                        val rating = itemObj.get("OfficialRating")?.asString
                        val communityRating = itemObj.get("CommunityRating")?.asFloat
                        val year = itemObj.get("ProductionYear")?.asInt
                        
                        val runTimeTicks = itemObj.get("RunTimeTicks")?.asLong ?: 0L
                        val runTimeMinutes = if (runTimeTicks > 0L) (runTimeTicks / 600000000L).toInt() else null

                        val genres = mutableListOf<String>()
                        itemObj.getAsJsonArray("Genres")?.forEach { genres.add(it.asString) }

                        val castList = mutableListOf<JellyfinPerson>()
                        itemObj.getAsJsonArray("People")?.forEach {
                            val pObj = it.asJsonObject
                            val pId = pObj.get("Id").asString
                            val pName = pObj.get("Name").asString
                            val pRole = pObj.get("Role")?.asString
                            val pType = pObj.get("Type")?.asString
                            
                            if (pType == "Actor") {
                                val pImg = "$cleanUrl/Items/$pId/Images/Primary?api_key=$accessToken"
                                castList.add(JellyfinPerson(pId, pName, pRole, pImg))
                            }
                        }

                        val backdropTags = itemObj.getAsJsonArray("BackdropImageTags")
                        val hasBackdrop = backdropTags != null && backdropTags.size() > 0
                        val backdropUrl = if (hasBackdrop) {
                            "$cleanUrl/Items/$id/Images/Backdrop/0?api_key=$accessToken"
                        } else {
                            "$cleanUrl/Items/$id/Images/Primary?api_key=$accessToken"
                        }

                        val userDataObj = itemObj.getAsJsonObject("UserData")
                        val playbackPositionTicks = userDataObj?.get("PlaybackPositionTicks")?.asLong ?: 0L

                        val details = JellyfinDetails(
                            id, name, overview, rating, communityRating, year, runTimeMinutes, genres, castList, playbackPositionTicks, backdropUrl
                        )
                        mainHandler.post { callback(details) }
                    } catch (e: Exception) {
                        mainHandler.post { callback(null) }
                    }
                } else {
                    mainHandler.post { callback(null) }
                }
            }
        })
    }

    fun fetchEpisodes(
        serverUrl: String,
        accessToken: String,
        userId: String,
        seriesId: String,
        callback: (List<JellyfinEpisode>) -> Unit
    ) {
        val cleanUrl = serverUrl.trimEnd('/')
        val url = "$cleanUrl/Shows/$seriesId/Episodes?userId=$userId&fields=Overview,UserData"
        val authHeader = "MediaBrowser Client=\"Jellyfin Client Android\", Device=\"Emulator\", DeviceId=\"device\", Version=\"1.0.0\", Token=\"$accessToken\""

        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("X-Emby-Authorization", authHeader)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { callback(emptyList()) }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string()
                if (response.isSuccessful && !bodyStr.isNullOrEmpty()) {
                    try {
                        val json = gson.fromJson(bodyStr, JsonObject::class.java)
                        val itemsArray = json.getAsJsonArray("Items")
                        val list = mutableListOf<JellyfinEpisode>()
                        for (i in 0 until itemsArray.size()) {
                            val epObj = itemsArray.get(i).asJsonObject
                            val epId = epObj.get("Id").asString
                            val epName = epObj.get("Name").asString
                            val seasonNumber = epObj.get("ParentIndexNumber")?.asInt ?: 1
                            val episodeNumber = epObj.get("IndexNumber")?.asInt ?: 1
                            val epOverview = epObj.get("Overview")?.asString
                            
                            val userDataObj = epObj.getAsJsonObject("UserData")
                            val playbackPositionTicks = userDataObj?.get("PlaybackPositionTicks")?.asLong ?: 0L
                            
                            val epImageUrl = "$cleanUrl/Items/$epId/Images/Primary?api_key=$accessToken"
                            val epStreamUrl = "$cleanUrl/Videos/$epId/stream?static=true&api_key=$accessToken"

                            list.add(JellyfinEpisode(epId, epName, seasonNumber, episodeNumber, epOverview, epImageUrl, epStreamUrl, playbackPositionTicks, seriesId))
                        }
                        mainHandler.post { callback(list) }
                    } catch (e: Exception) {
                        mainHandler.post { callback(emptyList()) }
                    }
                } else {
                    mainHandler.post { callback(emptyList()) }
                }
            }
        })
    }

    fun fetchSubtitles(
        serverUrl: String,
        accessToken: String,
        itemId: String,
        callback: (List<JellyfinSubtitle>) -> Unit
    ) {
        val cleanUrl = serverUrl.trimEnd('/')
        val url = "$cleanUrl/Items/$itemId"
        val authHeader = "MediaBrowser Client=\"Jellyfin Client Android\", Device=\"Emulator\", DeviceId=\"device\", Version=\"1.0.0\", Token=\"$accessToken\""

        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("X-Emby-Authorization", authHeader)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { callback(emptyList()) }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string()
                if (response.isSuccessful && !bodyStr.isNullOrEmpty()) {
                    try {
                        val itemObj = gson.fromJson(bodyStr, JsonObject::class.java)
                        val mediaSources = itemObj.getAsJsonArray("MediaSources")
                        val subList = mutableListOf<JellyfinSubtitle>()
                        if (mediaSources != null && mediaSources.size() > 0) {
                            val firstSource = mediaSources.get(0).asJsonObject
                            val mediaSourceId = firstSource.get("Id").asString
                            val mediaStreams = firstSource.getAsJsonArray("MediaStreams")
                            mediaStreams?.forEach { streamElement ->
                                val stream = streamElement.asJsonObject
                                val type = stream.get("Type")?.asString
                                if (type == "Subtitle") {
                                    val index = stream.get("Index").asInt
                                    val language = stream.get("Language")?.asString
                                    val displayTitle = stream.get("DisplayTitle")?.asString ?: "Subtitle"
                                    val codec = stream.get("Codec")?.asString ?: "vtt"
                                    
                                    val mimeType = when (codec.lowercase()) {
                                        "vtt", "webvtt" -> "text/vtt"
                                        "ass", "ssa" -> "text/x-ssa"
                                        else -> "application/x-subrip"
                                    }
                                    
                                    val subFormat = if (codec.lowercase() == "vtt") "vtt" else "srt"
                                    val subUrl = "$cleanUrl/Videos/$itemId/$mediaSourceId/Subtitles/$index/0/Stream.$subFormat?api_key=$accessToken"
                                    
                                    subList.add(JellyfinSubtitle(displayTitle, language, mimeType, subUrl))
                                }
                            }
                        }
                        mainHandler.post { callback(subList) }
                    } catch (e: Exception) {
                        mainHandler.post { callback(emptyList()) }
                    }
                } else {
                    mainHandler.post { callback(emptyList()) }
                }
            }
        })
    }

    fun reportPlaybackProgress(
        serverUrl: String,
        accessToken: String,
        itemId: String,
        positionMs: Long,
        isPaused: Boolean
    ) {
        val cleanUrl = serverUrl.trimEnd('/')
        val url = "$cleanUrl/Sessions/Playing/Progress"
        val authHeader = "MediaBrowser Client=\"Jellyfin Client Android\", Device=\"Emulator\", DeviceId=\"device\", Version=\"1.0.0\", Token=\"$accessToken\""
        
        val ticks = positionMs * 10000L
        val json = JsonObject().apply {
            addProperty("ItemId", itemId)
            addProperty("PositionTicks", ticks)
            addProperty("IsPaused", isPaused)
            addProperty("IsMuted", false)
            addProperty("PlayMethod", "DirectPlay")
        }

        val body = gson.toJson(json).toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("X-Emby-Authorization", authHeader)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }

    // Reset posisi playback ke 0 di server → item hilang dari Continue Watching saat refresh
    fun resetPlaybackPosition(
        serverUrl: String,
        accessToken: String,
        userId: String,
        itemId: String
    ) {
        val cleanUrl = serverUrl.trimEnd('/')
        val authHeader = "MediaBrowser Client=\"Jellyfin Client Android\", Device=\"Emulator\", DeviceId=\"device\", Version=\"1.0.0\", Token=\"$accessToken\""

        // Tandai sebagai sudah ditonton (hapus progress) lalu tandai belum ditonton lagi.
        // Ini cara standar Jellyfin untuk reset posisi resume.
        val urlPlayed = "$cleanUrl/Users/$userId/PlayedItems/$itemId"

        val markPlayed = Request.Builder()
            .url(urlPlayed)
            .post("".toRequestBody(null))
            .addHeader("X-Emby-Authorization", authHeader)
            .build()

        client.newCall(markPlayed).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                response.close()
                // Setelah marked played, langsung mark unplayed → posisi jadi 0, hilang dari resume
                val markUnplayed = Request.Builder()
                    .url(urlPlayed)
                    .delete()
                    .addHeader("X-Emby-Authorization", authHeader)
                    .build()
                client.newCall(markUnplayed).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {}
                    override fun onResponse(call: Call, response: Response) { response.close() }
                })
            }
        })
    }

    fun reportPlaybackStopped(
        serverUrl: String,
        accessToken: String,
        itemId: String,
        positionMs: Long
    ) {
        val cleanUrl = serverUrl.trimEnd('/')
        val url = "$cleanUrl/Sessions/Playing/Stopped"
        val authHeader = "MediaBrowser Client=\"Jellyfin Client Android\", Device=\"Emulator\", DeviceId=\"device\", Version=\"1.0.0\", Token=\"$accessToken\""
        
        val ticks = positionMs * 10000L
        val json = JsonObject().apply {
            addProperty("ItemId", itemId)
            addProperty("PositionTicks", ticks)
        }

        val body = gson.toJson(json).toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("X-Emby-Authorization", authHeader)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }

    // Ambil URL stream local trailer pertama untuk suatu item (film/serial)
    // Mengembalikan URL string atau null jika tidak ada local trailer
    fun fetchLocalTrailers(
        serverUrl: String,
        accessToken: String,
        userId: String,
        itemId: String,
        callback: (String?) -> Unit
    ) {
        val cleanUrl = serverUrl.trimEnd('/')
        val url = "$cleanUrl/Users/$userId/Items/$itemId/LocalTrailers"
        val authHeader = "MediaBrowser Client=\"Jellyfin Client Android\", Device=\"Nexfin\", DeviceId=\"nexfin-android\", Version=\"1.0.0\", Token=\"$accessToken\""

        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("X-Emby-Authorization", authHeader)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { callback(null) }
            }
            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string()
                if (response.isSuccessful && !bodyStr.isNullOrEmpty()) {
                    try {
                        val arr = gson.fromJson(bodyStr, com.google.gson.JsonArray::class.java)
                        val trailerId = if (arr.size() > 0) arr.get(0).asJsonObject.get("Id").asString else null
                        val trailerUrl = trailerId?.let {
                            "$cleanUrl/Videos/$it/stream?static=true&api_key=$accessToken"
                        }
                        mainHandler.post { callback(trailerUrl) }
                    } catch (e: Exception) {
                        mainHandler.post { callback(null) }
                    }
                } else {
                    mainHandler.post { callback(null) }
                }
            }
        })
    }

    // Tandai item sebagai sudah ditonton sepenuhnya → hilang dari Continue Watching di server
    fun markAsPlayed(
        serverUrl: String,
        accessToken: String,
        userId: String,
        itemId: String
    ) {
        val cleanUrl = serverUrl.trimEnd('/')
        val url = "$cleanUrl/Users/$userId/PlayedItems/$itemId"
        val authHeader = "MediaBrowser Client=\"Jellyfin Client Android\", Device=\"Nexfin\", DeviceId=\"nexfin-android\", Version=\"1.0.0\", Token=\"$accessToken\""

        val request = Request.Builder()
            .url(url)
            .post("".toRequestBody(null))
            .addHeader("X-Emby-Authorization", authHeader)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }
}

data class JellyfinSubtitle(
    val label: String,
    val language: String?,
    val mimeType: String,
    val url: String
)

data class JellyfinItem(
    val id: String,
    val name: String,
    val type: String,
    val imageUrl: String,
    val streamUrl: String,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val year: Int? = null,
    val overview: String? = null,
    val backdropUrl: String? = null
)

data class JellyfinDetails(
    val id: String,
    val name: String,
    val overview: String?,
    val rating: String?,
    val communityRating: Float?,
    val year: Int?,
    val runTimeMinutes: Int?,
    val genres: List<String>,
    val cast: List<JellyfinPerson>,
    val playbackPositionTicks: Long = 0L,
    val backdropUrl: String = ""
)

data class JellyfinPerson(
    val id: String,
    val name: String,
    val role: String?,
    val imageUrl: String
)

data class JellyfinEpisode(
    val id: String,
    val name: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val overview: String?,
    val imageUrl: String,
    val streamUrl: String,
    val playbackPositionTicks: Long = 0L,
    val seriesId: String = ""
)
