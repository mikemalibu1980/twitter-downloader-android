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
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
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
            text = "🐦 Twitter Media Downloader"
            textSize = 26f
            setPadding(0, 0, 0, 40)
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.parseColor("#1DA1F2"))
        }
        
        // Instructions
        val instructions = TextView(this).apply {
            text = "📱 Paste a Twitter/X URL below or share directly from the Twitter app"
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
            text = "📥 Download All Media"
            textSize = 18f
            setPadding(24, 24, 24, 24)
            setBackgroundColor(android.graphics.Color.parseColor("#1DA1F2"))
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener { 
                val url = urlInput.text.toString().trim()
                if (url.isNotEmpty()) {
                    if (isValidTwitterUrl(url)) {
                        downloadTwitterMedia(url)
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
            text = "✨ Ready to download!\n\n" +
                   "✅ Supported:\n" +
                   "• Twitter.com posts\n" +
                   "• X.com posts\n" +
                   "• Images (JPG, PNG, GIF)\n" +
                   "• Videos (MP4)\n\n" +
                   "💡 Tip: Share URLs directly from Twitter!"
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
        return url.contains("twitter.com/") || url.contains("x.com/")
    }
    
    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissions = mutableListOf<String>()
            
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
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
                    statusText.text = "📱 Shared URL detected!\nReady to download media from this tweet."
                }
            }
        }
    }
    
    private fun downloadTwitterMedia(tweetUrl: String) {
        downloadButton.isEnabled = false
        progressBar.visibility = android.view.View.VISIBLE
        statusText.text = "🔍 Analyzing tweet for media content..."
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val mediaUrls = extractMediaUrls(tweetUrl)
                
                withContext(Dispatchers.Main) {
                    progressBar.visibility = android.view.View.GONE
                    
                    if (mediaUrls.isNotEmpty()) {
                        statusText.text = "✅ Found ${mediaUrls.size} media file(s)!\n\n" +
                                        "📥 Starting downloads...\n" +
                                        "Check your Downloads folder and notification bar."
                        downloadFiles(mediaUrls)
                        
                        // Show success dialog
                        showSuccessDialog(mediaUrls.size)
                        
                    } else {
                        statusText.text = "❌ No media found in this tweet.\n\n" +
                                        "This could happen if:\n" +
                                        "• The tweet has no images/videos\n" +
                                        "• The tweet is private/protected\n" +
                                        "• The content was removed\n" +
                                        "• Rate limiting is in effect\n\n" +
                                        "💡 Try a different tweet or wait a moment!"
                    }
                    
                    downloadButton.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = android.view.View.GONE
                    val errorMsg = when {
                        e.message?.contains("timeout") == true -> "⏱️ Connection timeout. Check your internet connection."
                        e.message?.contains("404") == true -> "❌ Tweet not found. It may be deleted or private."
                        e.message?.contains("403") == true -> "🔒 Access denied. The tweet may be private."
                        else -> "❌ Error: ${e.message ?: "Unknown error occurred"}"
                    }
                    
                    statusText.text = "$errorMsg\n\n💡 Try:\n• Checking your internet\n• Using a different URL\n• Waiting a moment and trying again"
                    downloadButton.isEnabled = true
                }
            }
        }
    }
    
    private suspend fun extractMediaUrls(tweetUrl: String): List<String> {
        return withContext(Dispatchers.IO) {
            val allUrls = mutableSetOf<String>()
            val normalizedUrl = tweetUrl.replace("x.com", "twitter.com")
            val tweetId = extractTweetId(normalizedUrl)
            
            if (tweetId == null) {
                throw Exception("Invalid Twitter URL format")
            }
            
            // Method 1: Try multiple Nitter instances
            val nitterInstances = listOf(
                "nitter.net",
                "nitter.it", 
                "nitter.pussthecat.org",
                "nitter.fdn.fr"
            )
            
            for (instance in nitterInstances) {
                try {
                    val nitterUrl = "https://$instance/i/status/$tweetId"
                    val urls = scrapeNitter(nitterUrl)
                    allUrls.addAll(urls)
                    
                    if (allUrls.isNotEmpty()) {
                        break // Stop if we found media
                    }
                } catch (e: Exception) {
                    // Try next instance
                    continue
                }
            }
            
            // Method 2: Try Twitter's mobile site (sometimes works without API)
            if (allUrls.isEmpty()) {
                try {
                    val mobileUrl = "https://mobile.twitter.com/i/status/$tweetId"
                    val mobileUrls = scrapeMobileTwitter(mobileUrl)
                    allUrls.addAll(mobileUrls)
                } catch (e: Exception) {
                    // Continue to next method
                }
            }
            
            // Method 3: Try syndication endpoint (public tweets)
            if (allUrls.isEmpty()) {
                try {
                    val syndicationUrl = "https://syndication.twitter.com/srv/timeline-profile/screen-name?id=$tweetId"
                    val syndicationUrls = scrapeSyndication(syndicationUrl)
                    allUrls.addAll(syndicationUrls)
                } catch (e: Exception) {
                    // Final fallback failed
                }
            }
            
            return@withContext allUrls.toList()
        }
    }
    
    private suspend fun scrapeNitter(nitterUrl: String): List<String> {
        return withContext(Dispatchers.IO) {
            val connection = URL(nitterUrl).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android 13; Mobile; rv:109.0) Gecko/118.0 Firefox/118.0")
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                setRequestProperty("Accept-Language", "en-US,en;q=0.5")
                connectTimeout = 15000
                readTimeout = 15000
            }
            
            if (connection.responseCode != 200) {
                throw Exception("Nitter returned ${connection.responseCode}")
            }
            
            val html = connection.inputStream.bufferedReader().use { it.readText() }
            val urls = mutableSetOf<String>()
            
            // Enhanced patterns for better media detection
            val patterns = listOf(
                // Video patterns
                Pattern.compile("src=[\"']([^\"']*\\.(mp4|mov|webm)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE),
                Pattern.compile("href=[\"']([^\"']*\\.(mp4|mov|webm)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE),
                
                // Image patterns  
                Pattern.compile("src=[\"']([^\"']*pic[^\"']*\\.(jpg|jpeg|png|gif|webp)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE),
                Pattern.compile("href=[\"']([^\"']*pic[^\"']*\\.(jpg|jpeg|png|gif|webp)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE),
                
                // Twitter media patterns
                Pattern.compile("https://[^\\s\"']*pbs\\.twimg\\.com/media/[^\\s\"']*", Pattern.CASE_INSENSITIVE),
                Pattern.compile("https://[^\\s\"']*video\\.twimg\\.com/[^\\s\"']*", Pattern.CASE_INSENSITIVE)
            )
            
            patterns.forEach { pattern ->
                val matcher = pattern.matcher(html)
                while (matcher.find()) {
                    val url = matcher.group(1) ?: matcher.group(0)
                    if (url != null && isValidMediaUrl(url)) {
                        val fullUrl = when {
                            url.startsWith("http") -> url
                            url.startsWith("/") -> "https://nitter.net$url"
                            else -> url
                        }
                        urls.add(fullUrl)
                    }
                }
            }
            
            return@withContext urls.toList()
        }
    }
    
    private suspend fun scrapeMobileTwitter(mobileUrl: String): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(mobileUrl).openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 14_7_1 like Mac OS X) AppleWebKit/605.1.15")
                    connectTimeout = 10000
                    readTimeout = 10000
                }
                
                val html = connection.inputStream.bufferedReader().use { it.readText() }
                val urls = mutableSetOf<String>()
                
                // Look for Twitter media URLs in mobile version
                val mediaPattern = Pattern.compile("https://pbs\\.twimg\\.com/media/[^\\s\"']*")
                val matcher = mediaPattern.matcher(html)
                
                while (matcher.find()) {
                    val url = matcher.group(0)
                    if (isValidMediaUrl(url)) {
                        urls.add(url)
                    }
                }
                
                return@withContext urls.toList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    private suspend fun scrapeSyndication(syndicationUrl: String): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(syndicationUrl).openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    connectTimeout = 10000
                    readTimeout = 10000
                }
                
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val urls = mutableSetOf<String>()
                
                // Try to parse as JSON
                try {
                    val json = JSONObject(response)
                    // Look for media in the JSON response
                    extractMediaFromJson(json, urls)
                } catch (e: Exception) {
                    // If not JSON, try regex
                    val mediaPattern = Pattern.compile("https://[^\\s\"']*\\.(jpg|jpeg|png|gif|mp4|mov)")
                    val matcher = mediaPattern.matcher(response)
                    
                    while (matcher.find()) {
                        val url = matcher.group(0)
                        if (isValidMediaUrl(url)) {
                            urls.add(url)
                        }
                    }
                }
                
                return@withContext urls.toList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    private fun extractMediaFromJson(json: JSONObject, urls: MutableSet<String>) {
        // Recursively search JSON for media URLs
        json.keys().forEach { key ->
            when (val value = json.get(key)) {
                is String -> {
                    if (isValidMediaUrl(value)) {
                        urls.add(value)
                    }
                }
                is JSONObject -> extractMediaFromJson(value, urls)
                is JSONArray -> {
                    for (i in 0 until value.length()) {
                        when (val item = value.get(i)) {
                            is String -> {
                                if (isValidMediaUrl(item)) {
                                    urls.add(item)
                                }
                            }
                            is JSONObject -> extractMediaFromJson(item, urls)
                        }
                    }
                }
            }
        }
    }
    
    private fun isValidMediaUrl(url: String): Boolean {
        return url.matches(Regex(".*\\.(jpg|jpeg|png|gif|webp|mp4|mov).*", RegexOption.IGNORE_CASE)) &&
               !url.contains("profile") && 
               !url.contains("avatar") &&
               !url.contains("icon") &&
               url.length > 10
    }
    
    private fun extractTweetId(url: String): String? {
        val patterns = listOf(
            Pattern.compile("status/(\\d+)"),
            Pattern.compile("statuses/(\\d+)"),
            Pattern.compile("/status/(\\d+)"),
            Pattern.compile("twitter\\.com/[^/]+/status/(\\d+)"),
            Pattern.compile("x\\.com/[^/]+/status/(\\d+)")
        )
        
        for (pattern in patterns) {
            val matcher = pattern.matcher(url)
            if (matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }
    
    private fun downloadFiles(urls: List<String>) {
        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        
        urls.forEachIndexed { index, url ->
            try {
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setTitle("Twitter Media ${index + 1} of ${urls.size}")
                    setDescription("Downloaded via Twitter Downloader")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    
                    val extension = getFileExtension(url)
                    val filename = "twitter_${System.currentTimeMillis()}_${index + 1}.$extension"
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                    
                    setAllowedNetworkTypes(
                        DownloadManager.Request.NETWORK_WIFI or 
                        DownloadManager.Request.NETWORK_MOBILE
                    )
                    
                    addRequestHeader("User-Agent", "TwitterDownloader/1.0")
                    addRequestHeader("Referer", "https://twitter.com/")
                }
                
                downloadManager.enqueue(request)
                
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Failed to start download ${index + 1}", Toast.LENGTH_SHORT).show()
            }
        }
        
        Toast.makeText(this, "✅ ${urls.size} downloads started!\nCheck Downloads folder & notifications", Toast.LENGTH_LONG).show()
    }
    
    private fun getFileExtension(url: String): String {
        return when {
            url.contains(".mp4", true) -> "mp4"
            url.contains(".mov", true) -> "mov"
            url.contains(".webm", true) -> "webm"
            url.contains(".webp", true) -> "webp"
            url.contains(".png", true) -> "png"
            url.contains(".gif", true) -> "gif"
            url.contains(".jpeg", true) -> "jpeg"
            else -> "jpg"
        }
    }
    
    private fun showError(message: String) {
        statusText.text = message
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun showSuccessDialog(mediaCount: Int) {
        AlertDialog.Builder(this)
            .setTitle("🎉 Downloads Started!")
            .setMessage("Started downloading $mediaCount media file(s).\n\nFiles will be saved to your Downloads folder. Check your notification bar for progress.")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .setNeutralButton("Clear URL") { _, _ -> urlInput.setText("") }
            .show()
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            STORAGE_PERMISSION_CODE -> {
                if (grantResults.isEmpty() || grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
                    AlertDialog.Builder(this)
                        .setTitle("⚠️ Permission Required")
                        .setMessage("Storage permission is needed to download files to your device. Please grant the permission in Settings.")
                        .setPositiveButton("Settings") { _, _ ->
                            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            intent.data = Uri.parse("package:$packageName")
                            startActivity(intent)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }
    }
}
