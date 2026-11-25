package com.example.devboxai.tools

data class CliTool(
    val name: String, val category: String, val description: String,
    val pkg: String?, val template: String, val defaultArg: String = "", val needsRoot: Boolean = false
)

object ToolsCatalog {
    val installList: List<String> = listOf(
        "nmap", "whois", "dnsutils", "traceroute", "netcat-openbsd", "curl", "wget", "sqlmap",
        "john", "ffuf", "gobuster", "git", "python", "nodejs", "tcpdump", "tshark",
        "file", "tree", "bind-utils", "apktool", "apksigner", "zipalign", "aapt", "openssl"
    )

    val tools: List<CliTool> = listOf(
        CliTool("apktool version", "APK Modding", "Check apktool", "apktool", "apktool --version"),
        CliTool("apktool decompile", "APK Modding", "Decompile APK", "apktool", "cd /sdcard/DevBoxAPK && apktool d -f {arg} -o decoded", "input.apk"),
        CliTool("apktool build", "APK Modding", "Recompile decoded folder", "apktool", "cd /sdcard/DevBoxAPK && apktool b decoded -o unsigned.apk"),
        CliTool("zipalign", "APK Modding", "Align unsigned APK", "zipalign", "cd /sdcard/DevBoxAPK && zipalign -p -f 4 unsigned.apk aligned.apk"),
        CliTool("apksigner verify", "APK Modding", "Verify signed APK", "apksigner", "cd /sdcard/DevBoxAPK && apksigner verify --verbose {arg}", "signed.apk"),
        CliTool("aapt badging", "APK Modding", "Show APK info", "aapt", "aapt dump badging {arg} | head -40", "/sdcard/DevBoxAPK/input.apk"),

        CliTool("nmap version scan", "Network Scan", "Port + service detect", "nmap", "nmap -sV -T4 {arg}", "scanme.nmap.org"),
        CliTool("nmap ping sweep", "Network Scan", "Discover live hosts", "nmap", "nmap -sn {arg}", "192.168.1.0/24"),
        CliTool("nmap OS detect", "Network Scan", "OS fingerprint", "nmap", "nmap -O {arg}", "scanme.nmap.org", needsRoot = true),

        CliTool("whois", "DNS Recon", "Domain registration", "whois", "whois {arg}", "example.com"),
        CliTool("dig", "DNS Recon", "DNS record query", "dnsutils", "dig {arg}", "example.com"),
        CliTool("dig ANY", "DNS Recon", "All records", "dnsutils", "dig ANY {arg}", "example.com"),
        CliTool("dig PTR", "DNS Recon", "Reverse lookup", "dnsutils", "dig -x {arg}", "8.8.8.8"),
        CliTool("host", "DNS Recon", "Host lookup", "dnsutils", "host {arg}", "example.com"),

        CliTool("ping", "Connectivity", "ICMP ping x4", null, "ping -c 4 {arg}", "1.1.1.1"),
        CliTool("traceroute", "Connectivity", "Route trace", "traceroute", "traceroute -m 20 {arg}", "google.com"),
        CliTool("curl headers", "Connectivity", "HTTP HEAD", "curl", "curl -sIL {arg}", "https://example.com"),

        CliTool("tcpdump", "Packets", "Capture 20 packets", "tcpdump", "su -c 'tcpdump -i any -c 20 -nn'", "", needsRoot = true),
        CliTool("tshark", "Packets", "Tshark 10 packets", "tshark", "su -c 'tshark -i any -c 10 -V'", "", needsRoot = true),

        CliTool("nc single port", "Ports", "Check single port", "netcat-openbsd", "nc -zv -w3 {arg}", "example.com 80"),
        CliTool("nc range", "Ports", "Scan 1-1000", "netcat-openbsd", "nc -zv -w1 {arg} 1-1000 2>&1 | grep -i open", "example.com"),

        CliTool("sqlmap", "Web Security", "SQLi scan", "sqlmap", "sqlmap -u '{arg}' --batch --level=1 --risk=1", "http://testphp.vulnweb.com/artists.php?artist=1"),
        CliTool("ffuf dirs", "Web Security", "Dir fuzz", "ffuf", "ffuf -u {arg}/FUZZ -w /data/data/com.termux/files/usr/share/wordlists/dirb/common.txt -mc 200,301,302 -t 20", "https://example.com"),

        CliTool("john", "Cracking", "Crack hash", "john", "john --wordlist=/data/data/com.termux/files/usr/share/wordlists/rockyou.txt {arg}", "~/hash.txt"),
        CliTool("john show", "Cracking", "Show cracked", "john", "john --show {arg}", "~/hash.txt"),

        CliTool("ip addr", "System", "Network interfaces", null, "ip a"),
        CliTool("ip route", "System", "Routing table", null, "ip route"),
        CliTool("ss", "System", "Sockets", null, "ss -tunap 2>/dev/null || netstat -tun"),
        CliTool("df", "System", "Disk usage", null, "df -h"),
        CliTool("free", "System", "Memory", null, "free -h"),
        CliTool("ps", "System", "Processes", null, "ps aux | head -20"),

        CliTool("file type", "Files", "Detect type", "file", "file {arg}", "/sdcard/Download/"),
        CliTool("tree", "Files", "Directory tree", "tree", "tree -L 2 {arg}", "~"),
        CliTool("sha256sum", "Files", "Hash", null, "sha256sum {arg}", "/sdcard/Download/file.bin"),

        CliTool("git status", "Dev", "Repo status", "git", "cd {arg} && git status -s", "~/storage/shared/repo"),
        CliTool("python", "Dev", "Python test", "python", "python --version && python -c 'print(\"hello\")'"),
        CliTool("node", "Dev", "Node version", "nodejs", "node --version"),

        CliTool("custom command", "Custom", "Run any bash", null, "{arg}", "echo hello && uname -a")
    )
}
