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
            if (bodyText.contains("Index of /")) {
                findings += "Directory listing khuli hai — server ki file list sabko dikh rahi hai"
            }
            // Page-level deep checks
            if (bodyText.isNotEmpty()) {
                val lower = bodyText.lowercase()
                val tech = mutableListOf<String>()
                if (lower.contains("wp-content") || lower.contains("wp-includes")) tech += "WordPress"
                if (lower.contains("__next_data__")) tech += "Next.js"
                if (lower.contains("data-reactroot") || lower.contains("react")) tech += "React"
                if (lower.contains("ng-version")) tech += "Angular"
                if (lower.contains("bootstrap")) tech += "Bootstrap"
                val jq = Regex("""jquery[-.](\d+\.\d+\.\d+)""").find(lower)?.groupValues?.getOrNull(1)
                if (jq != null) {
                    tech += "jQuery $jq"
                    val major = jq.substringBefore(".").toIntOrNull() ?: 3
                    if (major < 3) findings += "Purana jQuery $jq hai — isme jaane-maane XSS bugs hai, update chahiye"
                }
                if (tech.isNotEmpty()) sb.append("\nTechnology: ${tech.joinToString(", ")}\n")
                if (!isHttps && lower.contains("type=\"password\"")) {
                    findings += "Password wala form bina HTTPS ke hai — password plain text me jayega"
                }
                if (isHttps && Regex("""<form[^>]+action\s*=\s*["']http://""").containsMatchIn(lower)) {
                    findings += "Form ka data http:// (bina secure) par ja raha hai"
                }
                val emails = Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""").findAll(bodyText)
                    .map { it.value }.distinct().take(5).toList()
                if (emails.isNotEmpty()) sb.append("Page par mile email: ${emails.joinToString(", ")}\n")
                if (Regex("""<!--[^>]*(password|secret|api[_-]?key|token)""").containsMatchIn(lower)) {
                    findings += "Page ke HTML comment me password/secret jaisi cheez likhi hai — sabko dikh sakti hai"
                }
                val extScripts = Regex("""<script[^>]+src\s*=\s*["']https?://([^/"']+)""").findAll(lower)
                    .map { it.groupValues[1] }.distinct().take(8).toList()
                if (extScripts.isNotEmpty()) sb.append("Bahar ki script sites: ${extScripts.joinToString(", ")}\n")
                val evals = Regex("""\beval\s*\(""").findAll(lower).count()
                if (evals > 0) findings += "Page me eval() $evals baar use hua hai — code injection ka risk badhta hai"
            }
            for (cookie in setCookies) {
                val name = cookie.substringBefore("=").trim()
                if (!cookie.contains("SameSite", ignoreCase = true)) {
                    findings += "Cookie '$name' par SameSite nahi — dusri site se request bhej kar CSRF ho sakta hai"
                }
            }

            // ---- Deep scan ----
            val host = try { URL(current).host } catch (_: Exception) { "" }
            if (host.isNotBlank()) {
                sb.append(deepScan(host, current, findings))
            }

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
            val sans = try {
                cert.subjectAlternativeNames?.mapNotNull { it.getOrNull(1)?.toString() }?.take(8) ?: emptyList()
            } catch (_: Exception) { emptyList() }
            val sanLine = if (sans.isNotEmpty()) "Certificate wale domains: ${sans.joinToString(", ")}\n" else ""
            "🔒 SSL: $issuer\nCertificate valid till: ${fmt.format(cert.notAfter)}\n$sanLine"
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

    /** Deep checks: DNS records, TLS protocol, HTTP methods, exposed paths, subdomains. */
    private fun deepScan(host: String, baseUrl: String, findings: MutableList<String>): String {
        val sb = StringBuilder("\n🔍 Deep Scan:\n")

        // DNS records via Cloudflare DoH
        try {
            val a = doh(host, "A")
            if (a.isNotEmpty()) sb.append("IP: ${a.joinToString(", ")}\n")
            val mx = doh(host, "MX")
            if (mx.isNotEmpty()) sb.append("Mail server (MX): ${mx.joinToString(", ")}\n")
            val ns = doh(host, "NS")
            if (ns.isNotEmpty()) sb.append("Nameserver: ${ns.joinToString(", ")}\n")
            val txt = doh(host, "TXT")
            val spf = txt.any { it.contains("v=spf1") }
            if (mx.isNotEmpty() && !spf) findings += "SPF record nahi hai — koi bhi is domain se nakli email bhej sakta hai (spoofing)"
            val dmarc = doh("_dmarc.$host", "TXT").any { it.contains("v=DMARC1") }
            if (mx.isNotEmpty() && !dmarc) findings += "DMARC record nahi hai — email spoofing rokne ka system nahi hai"
            if (spf) sb.append("✓ SPF hai\n")
            if (dmarc) sb.append("✓ DMARC hai\n")
        } catch (_: Exception) { }

        // TLS protocol + cipher
        if (baseUrl.startsWith("https://")) {
            try {
                val conn = URL(baseUrl).openConnection() as HttpsURLConnection
                conn.connectTimeout = 10000; conn.readTimeout = 10000
                conn.connect()
                sb.append("TLS: ${conn.cipherSuite}\n")
                if (conn.cipherSuite.contains("TLSv1.0") || conn.cipherSuite.contains("TLSv1.1")) {
                    findings += "Purana TLS protocol (${conn.cipherSuite}) — kamzor encryption"
                }
                conn.disconnect()
            } catch (_: Exception) { }
        }

        // HTTP methods
        try {
            val req = Request.Builder().url(baseUrl).method("OPTIONS", null)
                .header("User-Agent", "DevBoxAI-Scanner/1.0").build()
            client.newCall(req).execute().use { res ->
                val allow = res.header("Allow") ?: res.header("Access-Control-Allow-Methods") ?: ""
                if (allow.isNotBlank()) {
                    sb.append("HTTP methods: $allow\n")
                    val risky = listOf("PUT", "DELETE", "TRACE").filter { allow.contains(it, ignoreCase = true) }
                    if (risky.isNotEmpty()) findings += "Khatarnak HTTP methods khule hai (${risky.joinToString(", ")}) — file badalna/mitana possible ho sakta hai"
                }
            }
        } catch (_: Exception) { }

        // Exposed sensitive paths (status check only)
        val base = baseUrl.trimEnd('/')
        val sensitive = listOf(
            "/.git/HEAD" to "Git folder khula hai — pura source code mil sakta hai",
            "/.env" to ".env file khuli hai — passwords/keys leak ho sakte hai",
            "/wp-login.php" to "WordPress login page mila — brute-force ka nishana banta hai",
            "/phpmyadmin/" to "phpMyAdmin khula hai — database login page public hai",
            "/server-status" to "Server status page khula hai — andar ki jaankari leak hoti hai",
            "/backup/" to "Backup folder mila — purani files mil sakti hai",
            "/.DS_Store" to ".DS_Store file mili — folder ki file list leak hoti hai"
        )
        for ((path, risk) in sensitive) {
            try {
                val req = Request.Builder().url(base + path).head()
                    .header("User-Agent", "DevBoxAI-Scanner/1.0").build()
                client.newCall(req).execute().use { res ->
                    if (res.code == 200) {
                        sb.append("⚠ $path → 200 (khula hai)\n")
                        findings += "$path khula hai — $risk"
                    }
                }
            } catch (_: Exception) { }
        }
        // robots.txt / sitemap (info only)
        for (path in listOf("/robots.txt", "/sitemap.xml")) {
            try {
                val req = Request.Builder().url(base + path).head()
                    .header("User-Agent", "DevBoxAI-Scanner/1.0").build()
                client.newCall(req).execute().use { res ->
                    if (res.code == 200) sb.append("✓ $path hai\n")
                }
            } catch (_: Exception) { }
        }

        // WAF / CDN detection from headers
        val hdr = try {
            val req = Request.Builder().url(baseUrl).head()
                .header("User-Agent", "DevBoxAI-Scanner/1.0").build()
            client.newCall(req).execute().use { res ->
                res.headers.names().associateWith { (res.header(it) ?: "").lowercase() }
            }
        } catch (_: Exception) { emptyMap() }
        val waf = when {
            hdr.containsKey("cf-ray") || (hdr["server"]?.contains("cloudflare") == true) -> "Cloudflare"
            hdr.containsKey("x-amz-cf-id") -> "AWS CloudFront"
            hdr.containsKey("x-akamai-transformed") || (hdr["server"]?.contains("akamai") == true) -> "Akamai"
            hdr.containsKey("x-sucuri-id") -> "Sucuri"
            hdr.containsKey("x-iinfo") -> "Incapsula/Imperva"
            else -> ""
        }
        if (waf.isNotBlank()) sb.append("🛡 WAF/CDN: $waf\n")
        else findings += "WAF/CDN nahi dikha — website seedha server par hai, attack rokne wali parat nahi hai"

        // CORS check: does the site reflect any Origin?
        try {
            val req = Request.Builder().url(baseUrl).head()
                .header("User-Agent", "DevBoxAI-Scanner/1.0")
                .header("Origin", "https://evil-test.example")
                .build()
            client.newCall(req).execute().use { res ->
                val acao = res.header("Access-Control-Allow-Origin") ?: ""
                val acac = res.header("Access-Control-Allow-Credentials") ?: ""
                if (acao == "https://evil-test.example" && acac.equals("true", ignoreCase = true)) {
                    findings += "CORS khula hai — koi bhi website user ke login ke saath data padh sakti hai"
                } else if (acao == "*") {
                    sb.append("CORS: sabke liye khula (*) — public API ke liye theek hai\n")
                }
            }
        } catch (_: Exception) { }

        // Old TLS versions still accepted?
        if (baseUrl.startsWith("https://")) {
            for (proto in listOf("TLSv1.1", "TLSv1")) {
                try {
                    val factory = javax.net.ssl.SSLSocketFactory.getDefault() as javax.net.ssl.SSLSocketFactory
                    val socket = factory.createSocket() as javax.net.ssl.SSLSocket
                    socket.enabledProtocols = arrayOf(proto)
                    socket.connect(java.net.InetSocketAddress(host, 443), 4000)
                    socket.startHandshake()
                    socket.close()
                    findings += "Purana $proto ab bhi chalta hai — kamzor encryption, band hona chahiye"
                } catch (_: Exception) { }
            }
        }

        // Common ports
        val ports = listOf(
            21 to "FTP", 22 to "SSH", 25 to "SMTP", 80 to "HTTP", 443 to "HTTPS",
            3306 to "MySQL", 5432 to "PostgreSQL", 6379 to "Redis",
            8080 to "HTTP-Alt", 8443 to "HTTPS-Alt", 27017 to "MongoDB"
        )
        val openPorts = mutableListOf<String>()
        for ((port, name) in ports) {
            try {
                java.net.Socket().use { s ->
                    s.connect(java.net.InetSocketAddress(host, port), 700)
                    openPorts += "$port ($name)"
                    if (port in listOf(3306, 5432, 6379, 27017)) {
                        findings += "Database port $port ($name) internet par khula hai — seedha database attack ho sakta hai"
                    }
                }
            } catch (_: Exception) { }
        }
        if (openPorts.isNotEmpty()) sb.append("Khule ports: ${openPorts.joinToString(", ")}\n")

        // Admin / login pages
        for (path in listOf("/admin", "/admin/", "/login", "/wp-admin/", "/administrator/")) {
            try {
                val req = Request.Builder().url(base + path).head()
                    .header("User-Agent", "DevBoxAI-Scanner/1.0").build()
                client.newCall(req).execute().use { res ->
                    if (res.code == 200 || res.code == 401 || res.code == 403) {
                        sb.append("🔑 $path → ${res.code} (login/admin page mila)\n")
                    }
                }
            } catch (_: Exception) { }
        }

        // security.txt
        try {
            val req = Request.Builder().url("$base/.well-known/security.txt").head()
                .header("User-Agent", "DevBoxAI-Scanner/1.0").build()
            client.newCall(req).execute().use { res ->
                if (res.code == 200) sb.append("✓ security.txt hai (security contact diya hai)\n")
            }
        } catch (_: Exception) { }

        // robots.txt hidden paths (info)
        try {
            val req = Request.Builder().url("$base/robots.txt")
                .header("User-Agent", "DevBoxAI-Scanner/1.0").build()
            client.newCall(req).execute().use { res ->
                val txt = res.body?.string() ?: ""
                val dis = txt.lines().filter { it.trim().startsWith("Disallow:", ignoreCase = true) }
                    .map { it.substringAfter(":").trim() }.filter { it.isNotBlank() && it != "/" }
                if (dis.isNotEmpty()) sb.append("robots.txt me chhupaye raste: ${dis.take(8).joinToString(", ")}\n")
            }
        } catch (_: Exception) { }

        // More ports + service banners
        val morePorts = listOf(
            110 to "POP3", 143 to "IMAP", 2375 to "Docker API", 5984 to "CouchDB",
            5900 to "VNC", 8000 to "HTTP-8000", 9000 to "HTTP-9000",
            9200 to "Elasticsearch", 11211 to "Memcached"
        )
        for ((port, name) in morePorts) {
            try {
                java.net.Socket().use { s ->
                    s.connect(java.net.InetSocketAddress(host, port), 700)
                    sb.append("Khula port: $port ($name)\n")
                    if (port in listOf(2375, 9200, 11211, 5984, 5900)) {
                        findings += "$name port $port khula hai — bina password ke andar ka data/control mil sakta hai"
                    }
                }
            } catch (_: Exception) { }
        }
        // SSH/FTP banner
        for ((port, name) in listOf(22 to "SSH", 21 to "FTP")) {
            try {
                java.net.Socket().use { s ->
                    s.connect(java.net.InetSocketAddress(host, port), 1500)
                    s.soTimeout = 1500
                    val banner = s.getInputStream().bufferedReader().readLine()?.take(60)
                    if (!banner.isNullOrBlank()) sb.append("$name banner: $banner\n")
                }
            } catch (_: Exception) { }
        }
        // Docker / Elasticsearch without password?
        for ((port, path, name) in listOf(Triple(2375, "/version", "Docker"), Triple(9200, "/", "Elasticsearch"))) {
            try {
                val req = Request.Builder().url("http://$host:$port$path")
                    .header("User-Agent", "DevBoxAI-Scanner/1.0").build()
                client.newCall(req).execute().use { res ->
                    if (res.code == 200) findings += "$name bina password ke khula hai (port $port) — pura control/data mil sakta hai"
                }
            } catch (_: Exception) { }
        }

        // Backup / config files
        val files = listOf(
            "/backup.sql" to "Database backup file khuli hai — pura data mil sakta hai",
            "/db.sql" to "Database file khuli hai",
            "/dump.sql" to "Database dump khula hai",
            "/.env.bak" to "Purani .env file khuli hai — keys leak ho sakti hai",
            "/wp-config.php.bak" to "WordPress config backup khula hai — DB password mil sakta hai",
            "/.git/config" to "Git config khula hai — repo ki jaankari leak hoti hai",
            "/composer.json" to "composer.json khula hai — use hue package dikhte hai",
            "/package.json" to "package.json khula hai — use hue package dikhte hai",
            "/.svn/entries" to "SVN folder khula hai — source code mil sakta hai"
        )
        for ((path, risk) in files) {
            try {
                val req = Request.Builder().url(base + path).head()
                    .header("User-Agent", "DevBoxAI-Scanner/1.0").build()
                client.newCall(req).execute().use { res ->
                    if (res.code == 200) {
                        sb.append("⚠ $path → 200 (khula hai)\n")
                        findings += "$path khula hai — $risk"
                    }
                }
            } catch (_: Exception) { }
        }

        // API docs / endpoints
        for (path in listOf("/api", "/api/docs", "/swagger", "/swagger-ui.html", "/openapi.json", "/graphql", "/.well-known/openid-configuration")) {
            try {
                val req = Request.Builder().url(base + path).head()
                    .header("User-Agent", "DevBoxAI-Scanner/1.0").build()
                client.newCall(req).execute().use { res ->
                    if (res.code == 200) sb.append("🔌 API mila: $path\n")
                }
            } catch (_: Exception) { }
        }

        // WordPress deep: version + user list
        try {
            val req = Request.Builder().url("$base/readme.html")
                .header("User-Agent", "DevBoxAI-Scanner/1.0").build()
            client.newCall(req).execute().use { res ->
                val txt = res.body?.string() ?: ""
                val ver = Regex("""Version (\d+\.\d+(?:\.\d+)?)""").find(txt)?.groupValues?.getOrNull(1)
                if (ver != null) {
                    sb.append("WordPress version: $ver\n")
                    findings += "WordPress version $ver dikh raha hai — purana hai to uske jaane-maane bugs use ho sakte hai"
                }
            }
        } catch (_: Exception) { }
        try {
            val req = Request.Builder().url("$base/wp-json/wp/v2/users")
                .header("User-Agent", "DevBoxAI-Scanner/1.0").build()
            client.newCall(req).execute().use { res ->
                val txt = res.body?.string() ?: ""
                if (res.code == 200 && txt.contains("\"slug\"")) {
                    val users = Regex(""""slug"\s*:\s*"([^"]+)"""").findAll(txt).map { it.groupValues[1] }.take(5).toList()
                    if (users.isNotEmpty()) {
                        sb.append("WordPress users: ${users.joinToString(", ")}\n")
                        findings += "WordPress user list khuli hai (${users.joinToString(", ")}) — login naam mil gaya, password guess karna asaan hota hai"
                    }
                }
            }
        } catch (_: Exception) { }

        // Error page leaks version/stack?
        try {
            val req = Request.Builder().url("$base/devbox-test-404-${System.currentTimeMillis() % 100000}")
                .header("User-Agent", "DevBoxAI-Scanner/1.0").build()
            client.newCall(req).execute().use { res ->
                val txt = (res.body?.string() ?: "").lowercase()
                val leaks = listOf("traceback", "stack trace", "php/", "apache/", "nginx/", "fatal error", "sql syntax")
                    .filter { txt.contains(it) }
                if (leaks.isNotEmpty()) findings += "Error page andar ki jaankari dikha raha hai (${leaks.joinToString(", ")}) — hacker ko rasta milta hai"
            }
        } catch (_: Exception) { }

        // DNS extras: IPv6, CAA, DKIM
        try {
            val aaaa = doh(host, "AAAA")
            if (aaaa.isNotEmpty()) sb.append("IPv6: ${aaaa.joinToString(", ")}\n")
            val caa = doh(host, "CAA")
            if (caa.isNotEmpty()) sb.append("✓ CAA record hai (certificate par rok hai)\n")
            val dkim = doh("default._domainkey.$host", "TXT")
            if (dkim.isNotEmpty()) sb.append("✓ DKIM hai (email signature)\n")
        } catch (_: Exception) { }

        // Meta generator (version disclosure)
        try {
            val req = Request.Builder().url(baseUrl)
                .header("User-Agent", "DevBoxAI-Scanner/1.0").build()
            client.newCall(req).execute().use { res ->
                sb.append("Protocol: ${res.protocol}\n")
                val enc = res.header("Content-Encoding") ?: ""
                if (enc.isNotBlank()) sb.append("Compression: $enc\n")
                val html = (res.body?.string() ?: "").take(200_000)
                val gen = Regex("""<meta[^>]+name\s*=\s*["']generator["'][^>]+content\s*=\s*["']([^"']+)""", RegexOption.IGNORE_CASE)
                    .find(html)?.groupValues?.getOrNull(1)
                if (!gen.isNullOrBlank()) {
                    sb.append("Generator: $gen\n")
                    findings += "Generator tag version bata raha hai ($gen) — purana hai to uske bugs use ho sakte hai"
                }
                // API endpoints hidden in page/JS
                val eps = Regex("""["'](/(?:api|v1|v2|graphql|rest)/[a-zA-Z0-9/_-]{1,60})["']""").findAll(html)
                    .map { it.groupValues[1] }.distinct().take(10).toList()
                if (eps.isNotEmpty()) sb.append("Page me mile API raste: ${eps.joinToString(", ")}\n")
                // First JS file: endpoints + source map
                val jsSrc = Regex("""<script[^>]+src\s*=\s*["']([^"']+\.js)["']""", RegexOption.IGNORE_CASE)
                    .find(html)?.groupValues?.getOrNull(1)
                if (!jsSrc.isNullOrBlank()) {
                    val jsUrl = try { URL(URL(baseUrl), jsSrc).toString() } catch (_: Exception) { "" }
                    if (jsUrl.isNotBlank()) {
                        try {
                            val jreq = Request.Builder().url(jsUrl)
                                .header("User-Agent", "DevBoxAI-Scanner/1.0").build()
                            client.newCall(jreq).execute().use { jres ->
                                val js = (jres.body?.string() ?: "").take(300_000)
                                val jeps = Regex("""["'](/(?:api|v1|v2|graphql|rest)/[a-zA-Z0-9/_-]{1,60})["']""").findAll(js)
                                    .map { it.groupValues[1] }.distinct().take(10).toList()
                                if (jeps.isNotEmpty()) sb.append("JS file me mile API raste: ${jeps.joinToString(", ")}\n")
                                if (Regex("""(api[_-]?key|secret|token)\s*[:=]\s*["'][A-Za-z0-9_-]{16,}""", RegexOption.IGNORE_CASE).containsMatchIn(js)) {
                                    findings += "JS file me API key/secret jaisi cheez dikhi — sabko mil sakti hai"
                                }
                            }
                            val mreq = Request.Builder().url("$jsUrl.map").head()
                                .header("User-Agent", "DevBoxAI-Scanner/1.0").build()
                            client.newCall(mreq).execute().use { mres ->
                                if (mres.code == 200) findings += "Source map (.js.map) khula hai — website ka asal source code mil sakta hai"
                            }
                        } catch (_: Exception) { }
                    }
                }
                val lower = html.lowercase()
                if (lower.contains("type=\"file\"")) sb.append("📤 Page par file upload wala form hai\n")
                if (lower.contains("recaptcha") || lower.contains("hcaptcha")) sb.append("✓ Captcha hai (bot rokne ke liye)\n")
                else if (lower.contains("type=\"password\"")) findings += "Login form par captcha nahi hai — bot baar-baar password try kar sakta hai"
            }
        } catch (_: Exception) { }

        // Wayback Machine: purane snapshot
        try {
            val req = Request.Builder().url("https://archive.org/wayback/available?url=$host")
                .header("User-Agent", "DevBoxAI-Scanner/1.0").build()
            client.newCall(req).execute().use { res ->
                val body = res.body?.string() ?: ""
                if (body.contains("\"available\": true") || body.contains("\"available\":true")) {
                    sb.append("🕰 Wayback Machine par is website ka purana snapshot hai\n")
                }
            }
        } catch (_: Exception) { }

        // Subdomains via crt.sh (public certificate logs)
        try {
            val root = host.removePrefix("www.")
            val req = Request.Builder().url("https://crt.sh/?q=%25.$root&output=json")
                .header("User-Agent", "DevBoxAI-Scanner/1.0").build()
            client.newCall(req).execute().use { res ->
                val body = res.body?.string() ?: ""
                if (res.isSuccessful && body.startsWith("[")) {
                    val arr = org.json.JSONArray(body)
                    val subs = sortedSetOf<String>()
                    for (i in 0 until arr.length()) {
                        val name = arr.optJSONObject(i)?.optString("common_name") ?: continue
                        if (name.endsWith(root) && !name.contains("*")) subs += name
                    }
                    if (subs.isNotEmpty()) {
                        sb.append("Subdomains (${subs.size}): ${subs.take(12).joinToString(", ")}")
                        if (subs.size > 12) sb.append(" …")
                        sb.append("\n")
                    }
                }
            }
        } catch (_: Exception) { }

        return sb.toString()
    }

    private fun doh(name: String, type: String): List<String> {
        val req = Request.Builder()
            .url("https://cloudflare-dns.com/dns-query?name=$name&type=$type")
            .header("Accept", "application/dns-json")
            .build()
        return client.newCall(req).execute().use { res ->
            val body = res.body?.string() ?: return emptyList()
            val answers = org.json.JSONObject(body).optJSONArray("Answer") ?: return emptyList()
            (0 until answers.length()).mapNotNull { i ->
                answers.optJSONObject(i)?.optString("data")?.trim('"')
            }
        }
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
