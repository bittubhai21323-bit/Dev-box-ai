package com.example.devboxai.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

/**
 * Basic website scanner: fetches a URL and reports status, redirects,
 * server info, TLS certificate, and security headers with a simple grade.
 * Read-only inspection — the same checks public tools like
 * securityheaders.com perform.
 */
object WebScanner {

    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val SECURITY_HEADERS = listOf(
        "Strict-Transport-Security" to "HSTS",
        "Content-Security-Policy" to "CSP",
        "X-Frame-Options" to "Clickjacking protection",
        "X-Content-Type-Options" to "MIME sniffing protection",
        "Referrer-Policy" to "Referrer control",
        "Permissions-Policy" to "Feature permissions"
    )

    suspend fun scan(rawUrl: String): String = withContext(Dispatchers.IO) {
        var url = rawUrl.trim()
        if (url.isBlank()) return@withContext "URL likho, jaise: /scan https://example.com"
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"

        val sb = StringBuilder()
        sb.append("🌐 Website Scan\n")
        sb.append("URL: $url\n")

        try {
            val start = System.currentTimeMillis()
            var current = url
            var hops = 0
            var finalCode = -1
            var headers: Map<String, String> = emptyMap()
            var contentType = ""
            var contentLength = -1L
            var bodyText = ""
            var setCookies: List<String> = emptyList()

            while (hops < 6) {
                val req = Request.Builder().url(current)
                    .header("User-Agent", "DevBoxAI-Scanner/1.0")
                    .build()
                client.newCall(req).execute().use { res ->
                    finalCode = res.code
                    headers = res.headers.names().associateWith { res.header(it) ?: "" }
                    contentType = res.header("Content-Type") ?: ""
                    contentLength = res.body?.contentLength() ?: -1L
                    if (res.isRedirect) {
                        val loc = res.header("Location")
                        if (loc.isNullOrBlank()) return@use
                        val next = URL(URL(current), loc).toString()
                        sb.append("↪ $finalCode → $next\n")
                        current = next
                        hops++
                        return@use
                    }
                    setCookies = res.headers("Set-Cookie")
                    bodyText = try { res.body?.string()?.take(200_000) ?: "" } catch (_: Exception) { "" }
                    hops = 99 // done
                }
                if (hops == 99) break
            }

            val ms = System.currentTimeMillis() - start
            sb.append("\nStatus: $finalCode ${statusText(finalCode)}\n")
            sb.append("Final URL: $current\n")
            sb.append("Response time: ${ms}ms\n")
            headers["Server"]?.let { sb.append("Server: $it\n") }
            if (contentType.isNotBlank()) sb.append("Content-Type: $contentType\n")
            if (contentLength > 0) sb.append("Size: ${contentLength / 1024} KB\n")
            headers["X-Powered-By"]?.let { sb.append("Powered-By: $it\n") }

            // TLS certificate info
            if (current.startsWith("https://")) {
                tlsInfo(current)?.let { sb.append(it) }
            } else {
                sb.append("⚠ HTTPS nahi hai — connection secure nahi hai\n")
            }

            // Security headers grade
            sb.append("\n🛡 Security Headers:\n")
            var score = 0
            for ((header, label) in SECURITY_HEADERS) {
                val present = headers.keys.any { it.equals(header, ignoreCase = true) }
                if (present) score++
                sb.append("${if (present) "✓" else "✗"} $header ($label)\n")
            }
            val grade = when (score) {
                6 -> "A+ (Bahut badhiya)"
                5 -> "A (Badhiya)"
                4 -> "B (Theek hai)"
                3 -> "C (Sudhaar chahiye)"
                2 -> "D (Kamzor)"
                else -> "F (Bahut kamzor)"
            }
            sb.append("\nGrade: $grade — $score/6 headers\n")
            if (!current.startsWith("https://")) sb.append("Note: HTTPS na hone se grade asal me aur kam hai.\n")

            // ---- Kamiyan (findings) ----
            val findings = mutableListOf<String>()
            val isHttps = current.startsWith("https://")
            if (!isHttps) findings += "HTTPS nahi hai — data plain text me jata hai, beech me koi bhi padh sakta hai"
            for ((header, label) in SECURITY_HEADERS) {
                if (headers.keys.none { it.equals(header, ignoreCase = true) }) {
                    findings += "$header gayab hai ($label) — ${headerRisk(header)}"
                }
            }
            val server = headers["Server"] ?: ""
            if (server.matches(Regex(".*\\d+\\.\\d+.*"))) {
                findings += "Server ka version dikh raha hai ($server) — hacker ko pata chalta hai kaun sa software chal raha hai"
            }
            headers["X-Powered-By"]?.let {
                findings += "X-Powered-By dikh raha hai ($it) — technology ka pata chalta hai"
            }
            for (cookie in setCookies) {
                val name = cookie.substringBefore("=").trim()
                if (isHttps && !cookie.contains("Secure", ignoreCase = true)) {
                    findings += "Cookie '$name' par Secure flag nahi — cookie bina encryption ke bhi ja sakti hai"
                }
                if (!cookie.contains("HttpOnly", ignoreCase = true)) {
                    findings += "Cookie '$name' par HttpOnly nahi — JavaScript cookie chura sakta hai (XSS)"
                }
            }
            if (isHttps && bodyText.isNotEmpty()) {
                val mixed = Regex("""(?:src|href)\s*=\s*["']http://""").findAll(bodyText).count()
                if (mixed > 0) findings += "Mixed content: page me $mixed jagah http:// (bina secure) wale resource hai"
            }
            certFindings(current).forEach { findings += it }

            sb.append("\n⚠ Kamiyan (${findings.size}):\n")
            if (findings.isEmpty()) sb.append("✓ Koi badi kami nahi mili — website theek se secure hai\n")
            else findings.forEachIndexed { i, f -> sb.append("${i + 1}. $f\n") }
        } catch (e: Exception) {
            sb.append("\n✗ Scan fail: ${e.message}\n")
            sb.append("Check karo: URL sahi hai? Internet chal raha hai?")
        }
        sb.toString()
    }

    private fun statusText(code: Int): String = when (code) {
        200 -> "OK"
        301, 302, 307, 308 -> "Redirect"
        403 -> "Forbidden"
        404 -> "Not Found"
        500 -> "Server Error"
        else -> ""
    }

    private fun tlsInfo(url: String): String? {
        return try {
            val conn = URL(url).openConnection() as HttpsURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.connect()
            val cert = conn.serverCertificates.firstOrNull() as? X509Certificate
            conn.disconnect()
            if (cert == null) return null
            val fmt = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
            val issuer = cert.issuerX500Principal.name
                .split(",").firstOrNull { it.trim().startsWith("O=") }?.trim()?.removePrefix("O=") ?: issuerShort(cert)
            "🔒 SSL: $issuer\nCertificate valid till: ${fmt.format(cert.notAfter)}\n"
        } catch (_: Exception) {
            null
        }
    }

    private fun issuerShort(cert: X509Certificate): String =
        cert.issuerX500Principal.name.take(60)

    private fun headerRisk(header: String): String = when (header) {
        "Strict-Transport-Security" -> "browser ko HTTPS par majboor nahi karta, downgrade attack ho sakta hai"
        "Content-Security-Policy" -> "XSS (script injection) attack ka risk badh jata hai"
        "X-Frame-Options" -> "site ko dusri site ke iframe me daal kar clickjacking ho sakta hai"
        "X-Content-Type-Options" -> "browser file type guess karke galat file chala sakta hai"
        "Referrer-Policy" -> "dusri sites ko pata chalta hai user kahan se aaya"
        "Permissions-Policy" -> "camera/mic/location jaise feature bina rok ke use ho sakte hai"
        else -> "security kamzor hoti hai"
    }

    private fun certFindings(url: String): List<String> {
        if (!url.startsWith("https://")) return emptyList()
        return try {
            val conn = URL(url).openConnection() as HttpsURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.connect()
            val cert = conn.serverCertificates.firstOrNull() as? X509Certificate
            conn.disconnect()
            if (cert == null) return emptyList()
            val out = mutableListOf<String>()
            val daysLeft = (cert.notAfter.time - System.currentTimeMillis()) / 86_400_000
            when {
                daysLeft < 0 -> out += "SSL certificate EXPIRE ho chuka hai — browser warning dikhayega"
                daysLeft < 30 -> out += "SSL certificate sirf $daysLeft din me expire hoga — renew karna padega"
            }
            if (cert.sigAlgName.contains("SHA1", ignoreCase = true)) {
                out += "Certificate kamzor SHA1 signature use karta hai — purana aur asurakshit hai"
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }
}
