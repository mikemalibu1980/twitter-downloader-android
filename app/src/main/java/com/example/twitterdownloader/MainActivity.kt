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
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {
    private lateinit var urlInput: EditText
    private lateinit var loadButton: Button
    private lateinit var statusText: TextView
    private lateinit var resultsText: TextView
    private lateinit var downloadButton: Button
    private lateinit var progressBar: ProgressBar
    
    private val STORAGE_PERMISSION_CODE = 101
    private var foundUrls = listOf<String>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createSimpleLayout()
        checkPermissions()
        handleSharedContent()
    }
    
    private fun createSimpleLayout() {
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        
        // Title
        val title = TextView(this).apply {
            text = "🎬 X Media Downloader"
            textSize = 24f
            setPadding(0, 0, 0, 24)
            gravity = android.view.Gravity.CENTER
        }
        
        // URL input section
        val inputLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }
        
        urlInput = EditText(this).apply {
            hint = "Paste X/Twitter URL here..."
            setPadding(16, 16, 16, 16)
        }
        
        val pasteButton = Button(this).apply {
            text = "📋"
            setOnClickListener { pasteFromClipboard() }
        }
        
        // Set layout params properly
        val inputParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        urlInput.layoutParams = inputParams
        
        inputLayout.addView(urlInput)
        inputLayout.addView(pasteButton)
        
        // Progress bar
        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
            setPadding(0, 16, 0, 16)
        }
        
        // Load button
        loadButton = Button(this).apply {
            text = "🔍 Find Media"
            textSize = 16f
            setPadding(16, 16, 16, 16)
            setOnClickListener { 
                val url = urlInput.text.toString().trim()
                if (url.isNotEmpty()) {
                    findMedia(url)
                } else {
                    Toast.makeText(this@MainActivity, "Please enter a URL", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        // Status text
        statusText = TextView(this).apply {
            text = "🚀 Ready to find media!\n\n" +
                   "Based on xdownloader.com's method:\n" +
                   "• Fetches public post data\n" +
                   "• Extracts media URLs from HTML/JSON\n" +
                   "• Downloads directly from X's servers\n\n" +
                   "Paste a URL and tap 'Find Media'"
            setPadding(16, 16, 16, 16)
            textSize = 14f
        }
        
        // Results text (hidden initially)
        resultsText = TextView(this).apply {
            text = ""
            textSize = 12f
            setPadding(16, 16, 16, 16)
            setTextColor(android.graphics.Color.BLACK)
            background = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.edit_text)
            visibility = View.GONE
            setTextIsSelectable(true)
        }
        
        // Download button (hidden initially)
        downloadButton = Button(this).apply {
            text = "📥 Download All Found Media"
            textSize = 16f
            setPadding(16, 16, 16, 16)
            visibility = View.GONE
            setOnClickListener { downloadAllMedia() }
        }
        
        layout.addView(title)
        layout.addView(inputLayout)
        layout.addView(loadButton)
        layout.addView(progressBar)
        layout.addView(statusText)
        layout.addView(resultsText)
        layout.addView(downloadButton)
        
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
                statusText.text = "📱 Shared URL detected! Tap 'Find Media' to analyze."
            }
        }
    }
    
    private fun findMedia(postUrl: String) {
        loadButton.isEnabled = false
        progressBar.visibility = View.VISIBLE
        resultsText.visibility = View.GONE
        downloadButton.visibility = View.GONE
        
        statusText.text = "🔍 Finding media using xdownloader.com method...\n\nStep 1: Fetching post data..."
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) {
                    statusText.text = "🔍 Step 2: Parsing HTML and JSON for media URLs..."
                }
                
                val postData = fetchPostData(postUrl)
                val mediaUrls = extractAllMediaUrls(postData, postUrl)
                
                withContext(Dispatchers.Main) {
                    statusText.text = "🔍 Step 3: Verifying media accessibility..."
                }
                
                val workingUrls = verifyUrls(mediaUrls)
                
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    displayResults(mediaUrls, workingUrls)
                    loadButton.isEnabled = true
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    statusText.text = "❌ Media search failed: ${e.message}\n\n" +
                                    "Common causes:\n" +
                                    "• Post is private or deleted\n" +
                                    "• No media in this post\n" +
                                    "• Network connectivity issues\n\n" +
                                    "Try a different public post with videos/images."
                    loadButton.isEnabled = true
                }
            }
        }
    }
    
    private suspend fun fetchPostData(postUrl: String): String {
        return withContext(Dispatchers.IO) {
            // Try multiple user agents like xdownloader.com
            val userAgents = listOf(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) AppleWebKit/605.1.15",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                "Mozilla/5.0 (Android 12; Mobile; rv:109.0) Gecko/118.0 Firefox/118.0"
            )
            
            for ((index, userAgent) in userAgents.withIndex()) {
                try {
                    withContext(Dispatchers.Main) {
                        statusText.text = "🔍 Step 1: Trying fetch method ${index + 1}/3..."
                    }
                    
                    val connection = URL(postUrl).openConnection() as HttpURLConnection
                    connection.apply {
                        requestMethod = "GET"
                        setRequestProperty("User-Agent", userAgent)
                        setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        setRequestProperty("Accept-Language", "en-US,en;q=0.5")
                        connectTimeout = 15000
                        readTimeout = 15000
                    }
                    
                    if (connection.responseCode in 200..299) {
                        val html = connection.inputStream.bufferedReader().use { it.readText() }
                        if (html.length > 1000) { // Valid response
                            return@withContext html
                        }
                    }
                } catch (e: Exception) {
                    // Try next user agent
                }
            }
            
            throw Exception("Could not fetch post data")
        }
    }
    
    private fun extractAllMediaUrls(html: String, originalUrl: String): List<String> {
        val allUrls = mutableSetOf<String>()
        
        // Pattern 1: Direct video/image URLs in JSON
        val jsonPatterns = listOf(
            "\"(https://[^\"]*video\\.twimg\\.com[^\"]*\\.(mp4|webm|m3u8)[^\"]*)",
            "\"(https://[^\"]*pbs\\.twimg\\.com/media[^\"]*\\.(jpg|jpeg|png|gif|webp)[^\"]*)",
            "\"media_url_https?\"\\s*:\\s*\"([^\"]+)",
            "\"video_info\"[^}]*\"variants\"[^\\]]*\"url\"\\s*:\\s*\"([^\"]+)"
        )
        
        jsonPatterns.forEach { patternStr ->
            val pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(html)
            while (matcher.find()) {
                val url = matcher.group(1) ?: matcher.group(0)
                if (isValidMediaUrl(url)) {
                    allUrls.add(url.replace("\\", ""))
                }
            }
        }
        
        // Pattern 2: HTML src attributes
        val htmlPatterns = listOf(
            "src\\s*=\\s*[\"']([^\"']*\\.(mp4|jpg|jpeg|png|gif|webp|webm|m3u8)[^\"']*)[\"']",
            "data-src\\s*=\\s*[\"']([^\"']*\\.(mp4|jpg|jpeg|png|gif|webp|webm|m3u8)[^\"']*)[\"']"
        )
        
        htmlPatterns.forEach { patternStr ->
            val pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(html)
            while (matcher.find()) {
                val url = matcher.group(1)
                if (url != null && isValidMediaUrl(url)) {
                    allUrls.add(url)
                }
            }
        }
        
        // Pattern 3: Generate likely URLs based on tweet ID
        val tweetId = extractTweetId(originalUrl)
        if (tweetId != null) {
            val generatedUrls = generateLikelyUrls(tweetId)
            allUrls.addAll(generatedUrls)
        }
        
        return allUrls.toList()
    }
    
    private fun generateLikelyUrls(tweetId: String): List<String> {
        val urls = mutableListOf<String>()
        
        // Common video patterns
        val videoResolutions = listOf("1280x720", "720x720", "480x480", "640x640")
        videoResolutions.forEach { res ->
            urls.add("https://video.twimg.com/ext_tw_video/$tweetId/pu/vid/$res/video.mp4")
            urls.add("https://video.twimg.com/amplify_video/$tweetId/vid/$res/video.mp4")
        }
        
        // Common image patterns
        val imageFormats = listOf("jpg", "png")
        val imageSizes = listOf("large", "orig", "medium")
        imageFormats.forEach { format ->
            imageSizes.forEach { size ->
                urls.add("https://pbs.twimg.com/media/$tweetId?format=$format&name=$size")
            }
        }
        
        // GIF pattern
        urls.add("https://video.twimg.com/tweet_video/$tweetId.mp4")
        
        return urls
    }
    
    private suspend fun verifyUrls(urls: List<String>): List<String> {
        return withContext(Dispatchers.IO) {
            val working = mutableListOf<String>()
            
            urls.forEach { url ->
                try {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.apply {
                        requestMethod = "HEAD"
                        setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; MediaDownloader/1.0)")
                        setRequestProperty("Referer", "https://twitter.com/")
                        connectTimeout = 5000
                        readTimeout = 5000
                    }
                    
                    if (connection.responseCode in 200..299) {
                        working.add(url)
                    }
                } catch (e: Exception) {
                    // URL failed verification
                }
            }
            
            return@withContext working
        }
    }
    
    private fun displayResults(allUrls: List<String>, workingUrls: List<String>) {
        foundUrls = workingUrls
        
        val resultText = StringBuilder()
        resultText.append("📊 MEDIA SEARCH RESULTS\n")
        resultText.append("Total URLs found: ${allUrls.size}\n")
        resultText.append("Working URLs: ${workingUrls.size}\n\n")
        
        if (workingUrls.isEmpty()) {
            statusText.text = "❌ No accessible media found.\n\n" +
                             "Tried ${allUrls.size} potential URLs but none are accessible.\n\n" +
                             "This usually means:\n" +
                             "• Post has no media content\n" +
                             "• Media is protected/private\n" +
                             "• Post was deleted\n\n" +
                             "💡 Try a different public post with videos or images."
            
            if (allUrls.isNotEmpty()) {
                resultText.append("🔍 ATTEMPTED URLS:\n")
                resultText.append("(These didn't work, but you can copy and test manually)\n\n")
                allUrls.take(5).forEachIndexed { index, url ->
                    resultText.append("${index + 1}. ❌ ${getFileName(url)}\n$url\n\n")
                }
                if (allUrls.size > 5) {
                    resultText.append("... and ${allUrls.size - 5} more URLs")
                }
            }
        } else {
            statusText.text = "✅ SUCCESS! Found ${workingUrls.size} working media URL(s)!\n\n" +
                             "Media files are accessible and ready for download.\n" +
                             "Tap 'Download All' or copy individual URLs below."
            
            resultText.append("✅ WORKING MEDIA URLS:\n")
            resultText.append("(Long-press any URL to copy and test manually)\n\n")
            
            workingUrls.forEachIndexed { index, url ->
                val type = when {
                    url.contains(".mp4") || url.contains("video") -> "🎬 VIDEO"
                    url.contains(".jpg") || url.contains(".jpeg") || url.contains(".png") -> "🖼️ IMAGE"
                    url.contains(".gif") -> "🎞️ GIF"
                    else -> "📁 MEDIA"
                }
                
                resultText.append("${index + 1}. $type\n")
                resultText.append("${getFileName(url)}\n")
                resultText.append("$url\n\n")
            }
            
            downloadButton.visibility = View.VISIBLE
        }
        
        resultsText.text = resultText.toString()
        resultsText.visibility = View.VISIBLE
    }
    
    private fun downloadAllMedia() {
        if (foundUrls.isEmpty()) {
            Toast.makeText(this, "No media to download", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            
            foundUrls.forEachIndexed { index, url ->
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setTitle("X Media ${index + 1}")
                    setDescription("From: ${getFileName(url)}")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    
                    val filename = "x_media_${System.currentTimeMillis()}_${index + 1}.${getFileExtension(url)}"
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                    
                    setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                    
                    addRequestHeader("User-Agent", "Mozilla/5.0 (compatible; MediaDownloader/1.0)")
                    addRequestHeader("Referer", "https://twitter.com/")
                }
                
                downloadManager.enqueue(request)
            }
            
            Toast.makeText(this, "✅ Started ${foundUrls.size} downloads!\nCheck Downloads folder & notifications.", Toast.LENGTH_LONG).show()
            
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Download failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun extractTweetId(url: String): String? {
        val pattern = Pattern.compile("status(?:es)?/(\\d+)")
        val matcher = pattern.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }
    
    private fun isValidMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        return (lower.contains("pbs.twimg.com") || lower.contains("video.twimg.com") ||
                lower.contains(".mp4") || lower.contains(".jpg") || lower.contains(".jpeg") ||
                lower.contains(".png") || lower.contains(".gif") || lower.contains(".webp") ||
                lower.contains(".webm") || lower.contains(".m3u8")) &&
               !lower.contains("profile") && !lower.contains("avatar") && 
               !lower.contains("icon") && url.length > 15
    }
    
    private fun getFileName(url: String): String {
        return url.substringAfterLast("/").substringBefore("?").ifEmpty { "media_file" }
    }
    
    private fun getFileExtension(url: String): String {
        return when {
            url.contains(".mp4", true) -> "mp4"
            url.contains(".webm", true) -> "webm"
            url.contains(".png", true) -> "png"
            url.contains(".gif", true) -> "gif"
            url.contains(".webp", true) -> "webp"
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
