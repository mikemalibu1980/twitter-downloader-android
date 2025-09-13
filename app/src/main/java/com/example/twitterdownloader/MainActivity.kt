package com.example.twitterdownloader

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {
    private lateinit var urlInput: EditText
    private lateinit var downloadButton: Button
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    
    private val STORAGE_PERMISSION_CODE = 101
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createLayout()
        checkPermissions()
        handleSharedContent()
    }
    
    private fun createLayout() {
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        
        // Title
        val title = TextView(this).apply {
            text = "🐦 Twitter/X Media Extractor"
            textSize = 26f
            setPadding(0, 0, 0, 40)
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.parseColor("#1DA1F2"))
        }
        
        // Instructions
        val instructions = TextView(this).apply {
            text = "📱 Advanced media extraction using source code inspection"
            textSize = 16f
            setPadding(16, 0, 16, 24)
            setTextColor(android.graphics.Color.GRAY)
            gravity = android.view.Gravity.CENTER
        }
        
        // URL input
        urlInput = EditText(this).apply {
            hint = "🔗 https://twitter.com/username/status/... or https://x.com/..."
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            setPadding(20, 20, 20, 20)
            textSize = 14f
            background = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.edit_text)
        }
        
        // Progress bar
        progressBar = ProgressBar(this).apply {
            visibility = android.view.View.GONE
            setPadding(0, 24, 0, 24)
        }
        
        // Download button
        downloadButton = Button(this).apply {
            text = "🔍 Extract & Download Media"
            textSize = 18f
            setPadding(24, 24, 24, 24)
            setBackgroundColor(android.graphics.Color.parseColor("#1DA1F2"))
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener { 
                val url = urlInput.text.toString().trim()
                if (url.isNotEmpty()) {
                    if (isValidTwitterUrl(url)) {
                        extractAndDownloadMedia(url)
                    } else {
                        showError("❌ Please enter a valid Twitter or X.com URL")
                    }
                } else {
                    showError("❌ Please enter a URL first")
                }
            }
        }
        
        // Status text
        statusText = TextView(this).apply {
            text = "✨ Ready to extract media!\n\n" +
                   "🔍 This app uses advanced source code inspection to find direct media URLs, similar to browser developer tools.\n\n" +
                   "📋 How it works:\n" +
                   "1. Fetches the tweet's HTML source\n" +
                   "2. Searches for <video> and <img> tags\n" +
                   "3. Extracts direct media URLs\n" +
                   "4. Downloads files using Android's Download Manager\n\n" +
                   "💡 Works best with public tweets!"
            setPadding(16, 32, 16, 0)
            textSize = 14f
            setTextColor(android.graphics.Color.DKGRAY)
        }
        
        layout.addView(title)
        layout.addView(instructions)
        layout.addView(urlInput)
        layout.addView(progressBar)
        layout.addView(downloadButton)
        layout.addView(statusText)
        
        scrollView.addView(layout)
        setContentView(scrollView)
    }
    
    private fun isValidTwitterUrl(url: String): Boolean {
        return url.contains("twitter.com/") || url.contains("x.com/") ||
               url.contains("status/") || url.contains("statuses/")
    }
    
    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissions = mutableListOf<String>()
            
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            
            if (permissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, permissions.toTypedArray(), STORAGE_PERMISSION_CODE)
            }
        }
    }
    
    private fun handleSharedContent() {
        if (intent?.action == Intent.ACTION_SEND) {
            if (intent.type == "text/plain") {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (sharedText != null && isValidTwitterUrl(sharedText)) {
                    urlInput.setText(sharedText)
                    statusText.text = "📱 Shared URL detected! Ready to extract media."
                }
            }
        }
    }
    
    private fun extractAndDownloadMedia(tweetUrl: String) {
        downloadButton.isEnabled = false
        progressBar.visibility = android.view.View.VISIBLE
        statusText.text = "🔍 Step 1: Fetching tweet source code..."
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Step 1: Fetch the HTML source
                withContext(Dispatchers.Main) {
                    statusText.text = "🔍 Step 1: Fetching tweet source code...\n📄 Loading HTML content..."
                }
                
                val htmlContent = fetchTweetHtml(tweetUrl)
                
                // Step 2: Extract media URLs from HTML
                withContext(Dispatchers.Main) {
                    statusText.text = "🔍 Step 2: Parsing HTML for media elements...\n🎯 Looking for <video> and <img> tags..."
                }
                
                val mediaUrls = extractMediaFromHtml(htmlContent, tweetUrl)
                
                // Step 3: Process results
                withContext(Dispatchers.Main) {
                    progressBar.visibility = android.view.View.GONE
                    
                    if (mediaUrls.isNotEmpty()) {
                        statusText.text = "✅ SUCCESS! Found ${mediaUrls.size} media file(s)!\n\n" +
                                        "📥 Starting downloads...\n\n" +
                                        "Found URLs:\n${mediaUrls.joinToString("\n") { "• ${getFileName(it)}" }}\n\n" +
                                        "Check Downloads folder & notifications!"
                        
                        downloadFiles(mediaUrls)
                        showSuccessDialog(mediaUrls)
                        
                    } else {
                        statusText.text = "❌ No media found in HTML source.\n\n" +
                                        "Possible reasons:\n" +
                                        "• Tweet has no images/videos\n" +
                                        "• Content is behind login wall\n" +
                                        "• Tweet was deleted/made private\n" +
                                        "• JavaScript-only content (needs browser)\n\n" +
                                        "💡 Try opening the URL in a browser and using 'Inspect Element' manually."
                        
                        showInspectionGuideDialog()
                    }
                    
                    downloadButton.isEnabled = true
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = android.view.View.GONE
                    
                    val errorMsg = when {
                        e.message?.contains("403") == true -> "🔒 Access denied - Tweet may be private or account protected"
                        e.message?.contains("404") == true -> "❌ Tweet not found - May be deleted"
                        e.message?.contains("timeout") == true -> "⏱️ Connection timeout - Check internet connection"
                        e.message?.contains("SSL") == true -> "🔐 SSL/Certificate error"
                        else -> "❌ Network error: ${e.message}"
                    }
                    
                    statusText.text = "$errorMsg\n\n" +
                                    "🛠️ Troubleshooting:\n" +
                                    "• Check if tweet is public\n" +
                                    "• Verify internet connection\n" +
                                    "• Try different tweet URL\n" +
                                    "• Use browser inspection method\n\n" +
                                    "💡 Manual method: Open tweet in browser → Right-click video → Inspect → Find <video> tag → Copy src URL"
                    
                    downloadButton.isEnabled = true
                    showInspectionGuideDialog()
                }
            }
        }
    }
    
    private suspend fun fetchTweetHtml(tweetUrl: String): String {
        return withContext(Dispatchers.IO) {
            val cleanUrl = tweetUrl.replace("x.com", "twitter.com")
            
            // Try multiple approaches to fetch HTML
            val approaches = listOf(
                // Approach 1: Direct fetch with mobile user agent
                { fetchWithUserAgent(cleanUrl, "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) AppleWebKit/605.1.15") },
                
                // Approach 2: Desktop user agent
                { fetchWithUserAgent(cleanUrl, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36") },
                
                // Approach 3: Try mobile.twitter.com
                { fetchWithUserAgent(cleanUrl.replace("twitter.com", "mobile.twitter.com"), "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X)") },
                
                // Approach 4: Try nitter instance
                { fetchNitterVersion(cleanUrl) }
            )
            
            for ((index, approach) in approaches.withIndex()) {
                try {
                    withContext(Dispatchers.Main) {
                        statusText.text = "🔍 Step 1: Trying fetch method ${index + 1}/4..."
                    }
                    
                    val result = approach()
                    if (result.isNotEmpty()) {
                        return@withContext result
                    }
                } catch (e: Exception) {
                    // Try next approach
                    continue
                }
            }
            
            throw Exception("Could not fetch tweet content with any method")
        }
    }
    
    private fun fetchWithUserAgent(url: String, userAgent: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            setRequestProperty("Accept-Language", "en-US,en;q=0.5")
            setRequestProperty("Accept-Encoding", "gzip, deflate")
            setRequestProperty("Connection", "keep-alive")
            setRequestProperty("Upgrade-Insecure-Requests", "1")
            connectTimeout = 15000
            readTimeout = 15000
        }
        
        if (connection.responseCode != 200) {
            throw Exception("HTTP ${connection.responseCode}: ${connection.responseMessage}")
        }
        
        return connection.inputStream.bufferedReader().use { it.readText() }
    }
    
    private fun fetchNitterVersion(tweetUrl: String): String {
        val tweetId = extractTweetId(tweetUrl) ?: throw Exception("Could not extract tweet ID")
        val nitterUrl = "https://nitter.net/i/status/$tweetId"
        return fetchWithUserAgent(nitterUrl, "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
    }
    
    private fun extractMediaFromHtml(html: String, originalUrl: String): List<String> {
        val mediaUrls = mutableSetOf<String>()
        
        // Enhanced patterns for better media detection (like browser dev tools would find)
        val patterns = listOf(
            // Video source patterns
            Pattern.compile("<video[^>]*>.*?<source[^>]+src=[\"']([^\"']+)[\"'][^>]*>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE),
            Pattern.compile("<video[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("src=[\"']([^\"']*\\.(mp4|mov|webm|m3u8)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE),
            
            // Image patterns
            Pattern.compile("<img[^>]+src=[\"']([^\"']*\\.(jpg|jpeg|png|gif|webp)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE),
            
            // Twitter-specific patterns
            Pattern.compile("https://[^\\s\"']*pbs\\.twimg\\.com/media/[^\\s\"'?]*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("https://[^\\s\"']*video\\.twimg\\.com/[^\\s\"'?]*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("https://[^\\s\"']*abs\\.twimg\\.com/[^\\s\"'?]*", Pattern.CASE_INSENSITIVE),
            
            // Data attributes and JSON embedded content
            Pattern.compile("data-[^=]*=[\"']([^\"']*\\.(mp4|jpg|jpeg|png|gif|webp)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE),
            
            // Background images
            Pattern.compile("background-image:\\s*url\\([\"']?([^\"'\\)]*\\.(jpg|jpeg|png|gif|webp))[\"']?\\)", Pattern.CASE_INSENSITIVE)
        )
        
        patterns.forEach { pattern ->
            val matcher = pattern.matcher(html)
            while (matcher.find()) {
                val url = matcher.group(1)
                if (url != null && isValidMediaUrl(url)) {
                    val fullUrl = resolveUrl(url, originalUrl)
                    mediaUrls.add(fullUrl)
                }
            }
        }
        
        // Also look for JSON data containing media URLs
        extractMediaFromJsonInHtml(html, mediaUrls)
        
        return mediaUrls.toList()
    }
    
    private fun extractMediaFromJsonInHtml(html: String, mediaUrls: MutableSet<String>) {
        // Look for JSON data in script tags or data attributes
        val jsonPatterns = listOf(
            Pattern.compile("\"media_url_https?\":\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\"video_info\"[^}]*\"variants\"[^\\]]*\"url\":\\s*\"([^\"]+\\.mp4[^\"]*)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\"expanded_url\":\\s*\"([^\"]*\\.(jpg|jpeg|png|gif|webp|mp4)[^\"]*)", Pattern.CASE_INSENSITIVE)
        )
        
        jsonPatterns.forEach { pattern ->
            val matcher = pattern.matcher(html)
            while (matcher.find()) {
                val url = matcher.group(1)?.replace("\\", "")
                if (url != null && isValidMediaUrl(url)) {
                    mediaUrls.add(url)
                }
            }
        }
    }
    
    private fun isValidMediaUrl(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return (lowerUrl.contains(".jpg") || lowerUrl.contains(".jpeg") || 
                lowerUrl.contains(".png") || lowerUrl.contains(".gif") || 
                lowerUrl.contains(".webp") || lowerUrl.contains(".mp4") ||
                lowerUrl.contains(".mov") || lowerUrl.contains(".webm") ||
                lowerUrl.contains("pbs.twimg.com") || lowerUrl.contains("video.twimg.com")) &&
               !lowerUrl.contains("profile") && 
               !lowerUrl.contains("avatar") &&
               !lowerUrl.contains("icon") &&
               url.length > 10
    }
    
    private fun resolveUrl(url: String, baseUrl: String): String {
        return when {
            url.startsWith("http") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> {
                val domain = URL(baseUrl).host
                "https://$domain$url"
            }
            else -> url
        }
    }
    
    private fun extractTweetId(url: String): String? {
        val pattern = Pattern.compile("status(?:es)?/(\\d+)")
        val matcher = pattern.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }
    
    private fun getFileName(url: String): String {
        val fileName = url.substringAfterLast("/").substringBefore("?")
        return if (fileName.isNotEmpty()) fileName else "media_file"
    }
    
    private fun downloadFiles(urls: List<String>) {
        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        
        urls.forEachIndexed { index, url ->
            try {
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setTitle("Twitter Media ${index + 1}")
                    setDescription("Extracted from: ${getFileName(url)}")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    
                    val extension = getFileExtension(url)
                    val filename = "twitter_extracted_${System.currentTimeMillis()}_${index + 1}.$extension"
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                    
                    setAllowedNetworkTypes(
                        DownloadManager.Request.NETWORK_WIFI or 
                        DownloadManager.Request.NETWORK_MOBILE
                    )
                    
                    addRequestHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    addRequestHeader("Referer", "https://twitter.com/")
                }
                
                downloadManager.enqueue(request)
                
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to start download ${index + 1}: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        Toast.makeText(this, "✅ Started ${urls.size} downloads! Check notifications.", Toast.LENGTH_LONG).show()
    }
    
    private fun getFileExtension(url: String): String {
        return when {
            url.contains(".mp4", true) -> "mp4"
            url.contains(".mov", true) -> "mov" 
            url.contains(".webm", true) -> "webm"
            url.contains(".png", true) -> "png"
            url.contains(".gif", true) -> "gif"
            url.contains(".webp", true) -> "webp"
            url.contains(".jpeg", true) -> "jpeg"
            else -> "jpg"
        }
    }
    
    private fun showError(message: String) {
        statusText.text = message
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun showSuccessDialog(mediaUrls: List<String>) {
        val urlList = mediaUrls.joinToString("\n") { "• ${getFileName(it)}" }
        
        AlertDialog.Builder(this)
            .setTitle("🎉 Media Extracted Successfully!")
            .setMessage("Found ${mediaUrls.size} media files:\n\n$urlList\n\nDownloads started! Check your Downloads folder.")
            .setPositiveButton("OK", null)
            .setNeutralButton("Clear") { _, _ -> urlInput.setText("") }
            .show()
    }
    
    private fun showInspectionGuideDialog() {
        AlertDialog.Builder(this)
            .setTitle("💡 Manual Inspection Guide")
            .setMessage(
                "Try the manual browser method:\n\n" +
                "1. Open tweet in browser\n" +
                "2. Right-click on video/image\n" +
                "3. Select 'Inspect Element'\n" +
                "4. Find <video> or <img> tag\n" +
                "5. Copy the 'src' URL\n" +
                "6. Paste in download manager\n\n" +
                "This method works even when automatic extraction fails!"
            )
            .setPositiveButton("Open in Browser") { _, _ ->
                val url = urlInput.text.toString()
                if (url.isNotEmpty()) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                }
            }
            .setNegativeButton("OK", null)
            .show()
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            STORAGE_PERMISSION_CODE -> {
                if (grantResults.isEmpty() || grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
                    Toast.makeText(this, "Storage permission needed for downloads", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
