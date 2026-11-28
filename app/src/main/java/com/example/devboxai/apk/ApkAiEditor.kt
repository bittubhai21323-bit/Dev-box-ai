package com.example.devboxai.apk

import android.content.Context
import java.io.File

/**
 * Lets the chat AI edit a decompiled APK: reads the relevant decoded files,
 * asks the AI to apply the user's instruction, writes the edited files back.
 * Triggered from chat via "/apk edit <instruction>", "/apk build", or natural
 * language like "apk ka naam badal ke X kar do".
 */
class ApkAiEditor(
    private val context: Context,
    private val askAi: suspend (String) -> String
) {
    private val modder = ApkModder(context)
    private val keystore = KeystoreManager(context)

    companion object {
        private val EDIT_WORDS = listOf(
            "edit", "badal", "badlo", "change", "fix", "theek", "naam", "name",
            "version", "icon", "title", "label", "package", "permission", "color", "rang"
        )

        /** Returns the edit instruction if [text] is an APK-edit request, else null. */
        fun detectInstruction(text: String): String? {
            val t = text.trim()
            val lower = t.lowercase()
            if (lower.startsWith("/apk")) {
                val rest = t.removePrefix("/apk").trim()
                return rest.ifBlank { "help" }
            }
            if (lower.contains("apk") && EDIT_WORDS.any { lower.contains(it) }) return t
            return null
        }
    }

    suspend fun handle(instruction: String): String {
        val lower = instruction.lowercase().trim()
        return when {
            lower == "help" || lower.isBlank() -> HELP
            lower.startsWith("build") -> buildAll()
            lower.startsWith("edit ") -> edit(lower.removePrefix("edit ").trim().ifBlank { instruction })
            else -> edit(instruction)
        }
    }

    private suspend fun edit(instruction: String): String {
        val sb = StringBuilder()
        // Make sure the APK is decompiled first
        if (!File(ApkModder.DECODED_DIR).exists()) {
            if (!File(ApkModder.INPUT_APK).exists()) {
                return "Pehle APK tab me jaake step 1 se APK pick karo, phir mujhe bolo kya edit karna hai."
            }
            sb.append("APK decompile kar raha hoon…\n")
            modder.ensureWorkspace()
            val d = modder.decompile()
            if (!d.ok) return sb.toString() + "Decompile fail ho gaya:\n${d.log.takeLast(800)}"
            sb.append("✓ Decompile ho gaya\n")
        }

        val candidates = pickFiles(instruction)
        if (candidates.isEmpty()) return sb.toString() + "Edit karne layak file nahi mili (decoded folder khaali lagta hai)."

        val changed = mutableListOf<String>()
        for (path in candidates) {
            val content = modder.readFileText(path) ?: continue
            if (content.length > 120_000) {
                sb.append("• $path bahut badi hai, skip kiya\n")
                continue
            }
            val prompt = """
You are editing a file from a decompiled Android APK (apktool output).
File path: $path
User instruction: $instruction

Return ONLY the complete edited file content. No explanation, no markdown fences.
If this file does not need any change for the instruction, return exactly: NOCHANGE

--- FILE CONTENT ---
$content
""".trimIndent()
            val reply = try { askAi(prompt).trim() } catch (e: Exception) { "NOCHANGE" }
            val cleaned = stripFences(reply)
            if (cleaned.isBlank() || cleaned == "NOCHANGE" || cleaned == content.trim()) continue
            val ok = modder.writeFileText(path, cleaned)
            if (ok) changed.add(path) else sb.append("• $path likhne me fail\n")
        }

        if (changed.isEmpty()) {
            sb.append("Koi file change nahi hui. Instruction thoda saaf karke bolo, jaise: /apk edit app ka naam 'Mera App' kar do")
        } else {
            sb.append("✓ Edit ho gaya:\n")
            changed.forEach { sb.append("• $it\n") }
            sb.append("\nAb naya APK banane ke liye bolo: /apk build")
        }
        return sb.toString()
    }

    private suspend fun buildAll(): String {
        val sb = StringBuilder("APK build kar raha hoon…\n")
        val r = modder.recompile()
        sb.append(if (r.ok) "✓ Recompile ho gaya\n" else "✗ Recompile fail:\n${r.log.takeLast(800)}\n")
        if (!r.ok) return sb.toString()
        val a = modder.zipalign()
        sb.append(if (a.ok) "✓ Zipalign ho gaya\n" else "✗ Zipalign fail:\n${a.log.takeLast(500)}\n")
        if (!a.ok) return sb.toString()
        val ks = keystore.generate("boss12345")
        if (!ks.ok) return sb.toString() + "Keystore fail: ${ks.message}"
        val s = modder.sign("boss12345")
        sb.append(if (s.ok) "✓ Sign ho gaya\n" else "✗ Sign fail:\n${s.log.takeLast(500)}\n")
        if (!s.ok) return sb.toString()
        val v = modder.verify()
        sb.append(if (v.ok) "✓ Verify OK — naya APK taiyaar hai: ${ApkModder.SIGNED_APK}" else "⚠ Verify me dikkat:\n${v.log.takeLast(500)}")
        return sb.toString()
    }

    /** Pick the decoded files most likely relevant to the instruction. */
    private fun pickFiles(instruction: String): List<String> {
        val lower = instruction.lowercase()
        val files = mutableListOf("AndroidManifest.xml")
        if (listOf("naam", "name", "title", "label", "text", "string").any { lower.contains(it) }) {
            files.add("res/values/strings.xml")
        }
        if (listOf("version", "package", "sdk").any { lower.contains(it) }) {
            files.add("apktool.yml")
        }
        if (listOf("color", "rang", "theme").any { lower.contains(it) }) {
            files.add("res/values/colors.xml")
            files.add("res/values/styles.xml")
        }
        // Only keep files that actually exist in the decoded dir
        return files.filter { File("${ApkModder.DECODED_DIR}/$it").exists() }
    }

    private fun stripFences(text: String): String {
        var t = text.trim()
        if (t.startsWith("```")) {
            t = t.removePrefix("```").trim()
            // drop optional language tag on first line
            val nl = t.indexOf('\n')
            if (nl > 0 && t.substring(0, nl).length <= 12 && !t.substring(0, nl).contains(' ')) {
                t = t.substring(nl + 1)
            }
            if (t.endsWith("```")) t = t.removeSuffix("```")
        }
        return t.trim()
    }

    private val HELP = """
APK AI Editor — chat se APK edit karo:
• /apk edit <kya badalna hai> — AI decoded files edit karega
  Udaaharan: /apk edit app ka naam "Mera App" kar do
• /apk build — recompile + zipalign + sign + verify, naya APK taiyaar
Pehle APK tab me step 1 se APK pick karna zaroori hai.
""".trimIndent()
}
