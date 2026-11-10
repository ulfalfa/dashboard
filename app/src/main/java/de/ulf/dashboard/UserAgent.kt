package de.ulf.dashboard

/** Builds the desktop user agent: sites sniff "Android", "Mobile", "wv" and "Version/x" to serve their mobile layout. */
object UserAgent {
    private val CHROME_TOKEN = Regex("""Chrome/[\d.]+""")

    /** Desktop Linux Chrome UA with the WebView's own Chrome version, or null if [webViewUserAgent] has no Chrome token. */
    fun desktop(webViewUserAgent: String): String? {
        val chrome = CHROME_TOKEN.find(webViewUserAgent)?.value ?: return null
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) $chrome Safari/537.36"
    }
}
