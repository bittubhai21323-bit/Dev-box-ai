package com.example.devboxai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class WeatherRepository {
    private val client = OkHttpClient()

    suspend fun current(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code"
        val request = Request.Builder().url(url).build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: return@withContext "No response"
            if (!response.isSuccessful) return@withContext "Weather error: $body"

            val current = JSONObject(body).getJSONObject("current")
            "Temp: ${current.getDouble("temperature_2m")}°C\nWeather code: ${current.getInt("weather_code")}"
        }
    }
}
