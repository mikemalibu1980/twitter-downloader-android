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
    private lateinit var findButton: Button
    private lateinit var downloadButton: Button
    private lateinit var statusText: TextView
    private lateinit var urlsFoundText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var apiKeyButton: Button
    
    private val STORAGE_PERMISSION_CODE = 101
    private var foundUrls = listOf<String>()
    
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
            setPadding(24, 24, 24, 24)
        }
        
        // Title
        val title = TextView(this).apply {
            text = "🐦 Smart Twitter Media Finder"
            textSize = 24f
            setPadding(0, 0, 0, 24)
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.parseColor("#1DA1F2"))
        }
        
        // Info about extended entities
        val infoText = TextView(this).apply {
            text = "💡 Uses knowledge of Twitter's extended_entities structure for better detection"
            textSize = 14f
            setPadding(16, 0, 16, 16)
            setTextColor(android.graphics.Color.GRAY)
            gravity = android.view.Gravity.CENTER
        }
        
        // URL input
        val inputLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }
        
        urlInput = EditText(this).apply {
            hint = "Paste Twitter URL here..."
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(16, 16, 16, 16)
        }
        
        val pasteButton = Button(this).apply {
            text = "📋"
            setOnClickListener { pasteFromClipboard() }
        }
        
        inputLayout.addView(urlInput)
        inputLayout.addView(pasteButton)
        
        // Progress bar
        progressBar = ProgressBar(this).apply {
            visibility = android.view.View.GONE
            setPadding(0, 16, 0, 16)
        }
        
        // Find button
        findButton = Button(this).apply {
            text = "🔍 Smart Media Detection"
            textSize = 16f
            setBackgroundColor(android.graphics.Color.parseColor("#1DA1F2"))
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener { 
                val url = urlInput.text.toString().trim()
                if (url.isNotEmpty()) {
                    smartMediaDetection(url)
                } else {
                    Toast.makeText(this@MainActivity, "Please enter a URL first", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        // Download button
        downloadButton = Button(this).apply {
            text = "📥 Download Found Media"
            textSize = 16f
            setBackgroundColor(android.graphics.Color.parseColor("#28a745"))
            setTextColor(android.graphics.Color.WHITE)
            visibility = android.view.View.GONE
            setOnClickListener { downloadFoundUrls() }
        }
        
        // API Key info button
        apiKeyButton = Button(this).apply {
            text = "🔑 Get Twitter API Key (Better Results)"
            textSize = 14f
            setBackgroundColor(android.graphics.Color.parseColor("#657786"))
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener { showApiKeyInfo() }
        }
        
        // Status text
        statusText = TextView(this).apply {
            text = "🧠 SMART DETECTION MODE\n\n" +
                   "Based on Twitter's extended_entities structure:\n" +
                   "• Looks for video_info.variants arrays\n" +
                   "• Searches for multiple bitrate versions\n" +
                   "• Checks both MP4 and WebM formats\n" +
                   "• Uses proper Twitter media URL patterns\n\n" +
                   "💡 For best results, use Twitter's official API"
            setPadding(16, 16, 16, 16)
            textSize = 14f
            setTextColor(android.graphics.Color.DKGRAY)
        }
        
        // Results display
        urlsFoundText = TextView(this).apply {
            text = ""
            textSize = 12f
            setPadding(16, 16, 16, 16)
            setTextColor(android.graphics.Color.BLACK)
            background = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.edit_text)
            visibility = android.view.View.GONE
            setTextIsSelectable(true)
        }
        
        layout.addView(title)
        layout.addView(infoText)
        layout.addView(inputLayout)
        layout.addView(findButton)
        layout.addView(progressBar)
        layout.addView(downloadButton)
        layout.addView(apiKeyButton)
        layout.addView(statusText)
        layout.addView(urlsFoundText)
        
        scrollView.addView(layout)
        setContentView(scrollView)
    }
    
    private fun pasteFromClipboard() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = clipboard.primaryClip
            
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: ""
                urlInput.setText(text)
                Toast.makeText(this, "✅ Pasted!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Paste failed", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 
                    STORAGE_PERMISSION_CODE)
            }
        }
    }
    
    private fun handleSharedContent() {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (sharedText != null) {
                urlInput.setText(sharedText)
                statusText.text = "📱 Shared URL detected! Ready for smart detection."
            }
        }
    }
    
    private fun smartMediaDetection(tweetUrl: String) {
        findButton.isEnabled = false
        downloadButton.visibility = android.view.View.GONE
        urlsFoundText.visibility = android.view.View.GONE
        progressBar.visibility = android.view.View.VISIBLE
        
        statusText.text = "🧠 Starting smart detection...\n\nStep 1: Extracting tweet metadata..."
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val allUrls = mutableSetOf<String>()
                
                // Extract tweet ID
                val tweetId = extractTweetId(tweetUrl)
                if (tweetId == null) {
                    throw Exception("Could not extract tweet ID from URL")
                }
                
                withContext(Dispatchers.Main) {
                    statusText.text = "🧠 Step 2: Generating extended_entities-based URLs...\nTweet ID: $tweetId"
                }
                
                // Generate URLs based on extended_entities knowledge
                val smartUrls = generateSmartUrls(tweetId)
                allUrls.addAll(smartUrls)
                
                withContext(Dispatchers.Main) {
                    statusText.text = "🧠 Step 3: Trying alternative detection methods..."
                }
                
                // Try to scrape for JSON data containing extended_entities
                val scrapedUrls = scrapeForExtendedEntities(tweetUrl, tweetId)
                allUrls.addAll(scrapedUrls)
                
                withContext(Dispatchers.Main) {
                    statusText.text = "🧠 Step 4: Testing URL accessibility..."
                }
                
                // Test which URLs work
                val workingUrls = testUrls(allUrls.toList())
                
                withContext(Dispatchers.Main) {
                    progressBar.visibility = android.view.View.GONE
                    displaySmartResults(allUrls.toList(), workingUrls, tweetId)
                    findButton.isEnabled = true
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = android.view.View.GONE
                    statusText.text = "❌ Smart detection failed: ${e.message}\n\n" +
                                    "💡 This is likely because:\n" +
                                    "• Twitter requires authenticated API access\n" +
                                    "• extended_entities data is not publicly accessible\n" +
                                    "• Tweet is private or has no media\n\n" +
                                    "Consider using Twitter's official API for reliable access."
                    findButton.isEnabled = true
                }
            }
        }
    }
    
    private fun generateSmartUrls(tweetId: String): List<String> {
        val urls = mutableListOf<String>()
        
        // Based on extended_entities structure, Twitter videos come in multiple variants
        val videoBitrates = listOf("256000", "320000", "832000", "1280000")
        val videoResolutions = listOf(
            "240x240", "480x480", "720x720", "1280x720", "1920x1080"
        )
        
        // Generate video URLs based on known patterns from extended_entities
        videoResolutions.forEach { resolution ->
            urls.add("https://video.twimg.com/ext_tw_video/${tweetId}/pu/vid/${resolution}/video.mp4")
            urls.add("https://video.twimg.com/ext_tw_video/${tweetId}/pu/vid/${resolution}/video.webm")
        }
        
        // Amplify video patterns
        videoResolutions.forEach { resolution ->
            urls.add("https://video.twimg.com/amplify_video/${tweetId}/vid/${resolution}/video.mp4")
        }
        
        // Image patterns from media entities
        val imageFormats = listOf("jpg", "png", "webp")
        val imageSizes = listOf("thumb", "small", "medium", "large", "orig")
        
        imageFormats.forEach { format ->
            imageSizes.forEach { size ->
                urls.add("https://pbs.twimg.com/media/${tweetId}?format=${format}&name=${size}")
            }
        }
        
        // GIF patterns
        urls.add("https://video.twimg.com/tweet_video/${tweetId}.mp4")
        urls.add("https://pbs.twimg.com/tweet_video_thumb/${tweetId}.jpg")
        
        // Playlist formats (as seen in extended_entities)
        urls.add("https://video.twimg.com/ext_tw_video/${tweetId}/pu/pl/playlist.m3u8")
        urls.add("https://video.twimg.com/ext_tw_video/${tweetId}/pu/pl/manifest.mpd")
        
        return urls
    }
    
    private suspend fun scrapeForExtendedEntities(tweetUrl: String, tweetId: String): List<String> {
        return withContext(Dispatchers.IO) {
            val urls = mutableSetOf<String>()
            
            // Try to find embedded JSON data that might contain extended_entities
            try {
                val connection = URL(tweetUrl).openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    connectTimeout = 10000
                    readTimeout = 10000
                }
                
                if (connection.responseCode == 200) {
                    val html = connection.inputStream.bufferedReader().use { it.readText() }
                    
                    // Look for JSON data containing video_info or extended_entities patterns
                    val jsonPatterns = listOf(
                        "\"video_info\":\\s*\\{[^}]*\"variants\":\\s*\\[([^\\]]+)\\]",
                        "\"extended_entities\":[^}]*\"media\":[^}]*\"video_info\"",
                        "\"media_url_https\":\\s*\"([^\"]+\\.(mp4|jpg|png|gif|webp))",
                        "https://video\\.twimg\\.com/ext_tw_video/$tweetId/[^\"\\s]+",
                        "https://pbs\\.twimg\\.com/media/[^\"\\s]*($tweetId|\\w+)\\.(jpg|png|gif|webp)"
                    )
                    
                    jsonPatterns.forEach { patternStr ->
                        val pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE)
                        val matcher = pattern.matcher(html)
                        while (matcher.find()) {
                            val match = matcher.group(1) ?: matcher.group(0)
                            if (match.startsWith("http") && isValidMediaUrl(match)) {
                                urls.add(match)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Scraping failed
            }
            
            return@withContext urls.toList()
        }
    }
    
    private suspend fun testUrls(urls: List<String>): List<String> {
        return withContext(Dispatchers.IO) {
            val workingUrls = mutableListOf<String>()
            
            urls.forEach { url ->
                try {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.apply {
                        requestMethod = "HEAD"
                        setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; TwitterMediaBot/1.0)")
                        setRequestProperty("Referer", "https://twitter.com/")
                        connectTimeout = 5000
                        readTimeout = 5000
                    }
                    
                    if (connection.responseCode in 200..299) {
                        workingUrls.add(url)
                    }
                } catch (e: Exception) {
                    // URL failed
                }
            }
            
            return@withContext workingUrls
        }
    }
    
    private fun displaySmartResults(allUrls: List<String>, workingUrls: List<String>, tweetId: String) {
        foundUrls = workingUrls
        
        val resultText = StringBuilder()
        resultText.append("🧠 SMART DETECTION RESULTS\n")
        resultText.append("Tweet ID: $tweetId\n")
        resultText.append("Generated URLs: ${allUrls.size}\n")
        resultText.append("Working URLs: ${workingUrls.size}\n\n")
        
        if (workingUrls.isEmpty()) {
            statusText.text = "❌ No working media URLs found.\n\n" +
                             "🔍 This happens because:\n" +
                             "• Twitter's extended_entities requires API authentication\n" +
                             "• Media URLs are not publicly accessible\n" +
                             "• Tweet may be private or deleted\n\n" +
                             "💡 For reliable access, use Twitter's official API with proper credentials."
            
            resultText.append("🔍 ATTEMPTED SMART PATTERNS:\n")
            resultText.append("(These are the URLs we tried based on extended_entities knowledge)\n\n")
            
            // Show some example URLs that were tried
            allUrls.take(10).forEachIndexed { index, url ->
                resultText.append("${index + 1}. ❌ $url\n")
            }
            
            if (allUrls.size > 10) {
                resultText.append("... and ${allUrls.size - 10} more URLs\n")
            }
            
        } else {
            statusText.text = "✅ SUCCESS! Found ${workingUrls.size} working URLs!\n\n" +
                             "🎯 Smart detection based on extended_entities patterns worked!\n" +
                             "Copy URLs below to test, or use 'Download Found Media'."
            
            resultText.append("✅ WORKING URLS:\n")
            resultText.append("(Copy any URL to test in browser)\n\n")
            
            workingUrls.forEachIndexed { index, url ->
                val type = when {
                    url.contains(".mp4") -> "📹 VIDEO"
                    url.contains(".jpg") || url.contains(".png") -> "🖼️ IMAGE"
                    url.contains(".gif") -> "🎞️ GIF"
                    else -> "📁 MEDIA"
                }
                resultText.append("${index + 1}. $type\n$url\n\n")
            }
            
            downloadButton.visibility = android.view.View.VISIBLE
        }
        
        urlsFoundText.text = resultText.toString()
        urlsFoundText.visibility = android.view.View.VISIBLE
    }
    
    private fun downloadFoundUrls() {
        if (foundUrls.isEmpty()) return
        
        try {
            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            
            foundUrls.forEachIndexed { index, url ->
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setTitle("Smart Download ${index + 1}")
                    setDescription("From extended_entities: ${getFileName(url)}")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    
                    val filename = "smart_${System.currentTimeMillis()}_${index + 1}.${getFileExtension(url)}"
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                    
                    setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                    addRequestHeader("User-Agent", "Mozilla/5.0 (compatible; TwitterMediaBot/1.0)")
                    addRequestHeader("Referer", "https://twitter.com/")
                }
                
                downloadManager.enqueue(request)
            }
            
            Toast.makeText(this, "✅ Started ${foundUrls.size} smart downloads!", Toast.LENGTH_LONG).show()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun showApiKeyInfo() {
        AlertDialog.Builder(this)
            .setTitle("🔑 Twitter API for Better Results")
            .setMessage(
                "For 100% reliable media access, use Twitter's official API:\n\n" +
                "1. Go to developer.twitter.com\n" +
                "2. Create a developer account\n" +
                "3. Get API keys\n" +
                "4. Use 'statuses/show' endpoint with:\n" +
                "   • include_entities: true\n" +
                "   • tweet_mode: extended\n\n" +
                "This gives you access to extended_entities.media[].video_info.variants with all video URLs and bitrates.\n\n" +
                "Without API access, we can only guess URL patterns."
            )
            .setPositiveButton("Open Developer Site") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://developer.twitter.com/"))
                startActivity(intent)
            }
            .setNegativeButton("OK", null)
            .show()
    }
    
    private fun extractTweetId(url: String): String? {
        val pattern = Pattern.compile("status(?:es)?/(\\d+)")
        val matcher = pattern.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }
    
    private fun isValidMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        return (lower.contains(".mp4") || lower.contains(".jpg") || lower.contains(".jpeg") ||
                lower.contains(".png") || lower.contains(".gif") || lower.contains(".webp") ||
                lower.contains("video.twimg.com") || lower.contains("pbs.twimg.com")) &&
               !lower.contains("profile") && !lower.contains("avatar")
    }
    
    private fun getFileName(url: String): String {
        return url.substringAfterLast("/").substringBefore("?").ifEmpty { "media" }
    }
    
    private fun getFileExtension(url: String): String {
        return when {
            url.contains(".mp4", true) -> "mp4"
            url.contains(".webm", true) -> "webm"
            url.contains(".png", true) -> "png"
            url.contains(".gif", true) -> "gif"
            url.contains(".jpeg", true) -> "jpeg"
            else -> "jpg"
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "⚠️ Storage permission needed for downloads", Toast.LENGTH_LONG).show()
            }
        }
    }
}
