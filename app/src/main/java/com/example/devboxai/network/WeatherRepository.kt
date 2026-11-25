package com.example.devboxai.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class WeatherRepository {
    private val client = OkHttpClient()
    suspend fun current(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code")
                .build()
            client.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                if (!res.isSuccessful) return@withContext "Weather error: $body"
                val c = JSONObject(body).getJSONObject("current")
                "Temp: ${c.getDouble("temperature_2m")}°C\nCode: ${c.getInt("weather_code")}"
            }
        } catch (e: Exception) { "Weather error: ${e.message}" }
    }
}
