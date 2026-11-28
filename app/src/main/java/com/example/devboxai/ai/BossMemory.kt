package com.example.devboxai.ai

import android.content.Context
import org.json.JSONArray

/**
 * DevBox AI ki Boss-wali yaad: Boss jo "yaad rakho ..." bolta hai,
 * wo yahan save hota hai aur har AI jawab me system prompt ke saath jata hai.
 */
object BossMemory {
    private const val PREFS = "devbox_boss_memory"
    private const val KEY = "memories"

    fun all(context: Context): List<String> = try {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        List(arr.length()) { arr.optString(it) }.filter { it.isNotBlank() }
    } catch (_: Exception) { emptyList() }

    fun add(context: Context, memory: String): Boolean {
        val m = memory.trim()
        if (m.isBlank()) return false
        val list = all(context).toMutableList()
        if (list.contains(m)) return true
        list.add(m)
        save(context, list)
        return true
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    private fun save(context: Context, list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    /** System prompt me jaane wala hissa: Boss ka profile + uski yaadein + examples. */
    fun promptBlock(context: Context?): String {
        val memories = context?.let { all(it) } ?: emptyList()
        val memText = if (memories.isEmpty()) "" else
            "\nBoss ne ye baatein yaad rakhne ko kaha hai, inhe hamesha dhyaan me rakho:\n" +
                memories.joinToString("\n") { "- $it" }
        return "\nAbout Boss: Boss speaks Hindi/Hinglish (Roman Hindi), lives in India, uses only an Android phone (no PC). " +
            "His projects: DevBox AI (this app), JarvisApp/Maya voice assistant, BK Universe YouTube channel, " +
            "Instagram @_kushawha_bittu_ and @soljar_kumari_. He is learning Termux and Python. He plays Free Fire. " +
            "He likes short, direct answers with no extra talk." + memText + "\n" +
            "Examples of how you answer Boss:\n" +
            "Boss: mausam kaisa hai\nYou: Boss, abhi 28°C hai, halki dhoop hai.\n" +
            "Boss: ek joke sunao\nYou: Boss, ek suno — teacher: kal school kyun nahi aaye? Pappu: sir, baarish me bheeg gaya tha… sapne me! 😄\n" +
            "Boss: ye kaam kar do\nYou: Ho gaya Boss. ✅"
    }
}
