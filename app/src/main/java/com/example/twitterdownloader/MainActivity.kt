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
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {
    private lateinit var urlInput: EditText
    private lateinit var loadButton: Button
    private lateinit var downloadButton: Button
    private lateinit var statusText: TextView
    private lateinit var mediaListView: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var scrollView: ScrollView
    
    private val STORAGE_PERMISSION_CODE = 101
    private var foundMedia = listOf<MediaItem>()
    
    data class MediaItem(
        val url: String,
        val type: String, // "video", "image", "gif"
        val quality: String, // "HD", "SD", "original", etc.
        val size: String = "Unknown",
        val format: String // "mp4", "jpg", "png", "gif"
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createLayout()
        checkPermissions()
        handleSharedContent()
    }
    
    private fun createLayout() {
        scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        
        // Title
        val title = TextView(this).apply {
            text = "🎬 X Media Downloader"
            textSize = 26f
            setPadding(0, 0, 0, 24)
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.parseColor("#1DA1F2"))
        }
        
        // Subtitle
        val subtitle = TextView(this).apply {
            text = "Based on xdownloader.com's proven client-side method"
            textSize = 14f
            setPadding(16, 0, 16, 16)
            setTextColor(android.graphics.Color.GRAY)
            gravity = android.view.Gravity.CENTER
        }
        
        // URL input section
        val inputLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }
        
        urlInput = EditText(this).apply {
            hint = "🔗 Paste X/Twitter post URL here..."
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(16, 16, 16, 16)
            background = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.edit_text)
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
        
        // Load button
        loadButton = Button(this).apply {
            text = "🎬 Load Videos & Media"
            textSize = 18f
            setPadding(20, 20, 20, 20)
            setBackgroundColor(android.graphics.Color.parseColor("#1DA1F2"))
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener { 
                val url = urlInput.text.toString().trim()
                if (url.isNotEmpty()) {
                    loadMediaFromPost(url)
                } else {
                    Toast.makeText(this@MainActivity, "Please enter a URL first", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        // Status text
        statusText = TextView(this).apply {
            text = "🚀 Ready to load media!\n\n" +
                   "📱 How it works (like xdownloader.com):\n" +
                   "1. Fetches public post data via JavaScript-like requests\n" +
                   "2. Extracts direct media URLs from HTML/JSON\n" +
                   "3. Finds HLS streams (.m3u8) for videos\n" +
                   "4. Gets direct image/GIF URLs from X's CDN\n" +
                   "5. Downloads directly from X's servers\n\n" +
                   "💡 All processing happens locally - no data sent to third parties!"
            setPadding(16, 16, 16, 16)
            textSize = 14f
            setTextColor(android.graphics.Color.DKGRAY)
        }
        
        // Media list container
        mediaListView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = android.view.View.GONE
        }
        
        // Download all button
        downloadButton = Button(this).apply {
            text = "📥 Download Selected Media"
            textSize = 16f
            setPadding(16, 16, 16, 16)
            setBackgroundColor(android.graphics.Color.parseColor("#28a745"))
            setTextColor(android.graphics.Color.WHITE)
            visibility = android.view.View.GONE
            setOnClickListener { downloadSelectedMedia() }
        }
        
        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(inputLayout)
        layout.addView(loadButton)
        layout.addView(progressBar)
        layout.addView(statusText)
        layout.addView(mediaListView)
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
                Toast.makeText(this, "✅ URL pasted!", Toast.LENGTH_SHORT).show()
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
                statusText.text = "📱 Shared URL detected! Tap 'Load Videos & Media' to analyze."
            }
        }
    }
    
    private fun loadMediaFromPost(postUrl: String) {
        loadButton.isEnabled = false
        progressBar.visibility = android.view.View.VISIBLE
        mediaListView.visibility = android.view.View.GONE
        downloadButton.visibility = android.view.View.GONE
        
        statusText.text = "🔍 Step 1/4: Fetching post data...\n(Like xdownloader.com's JavaScript)"
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Step 1: Fetch the post's HTML page (public endpoint)
                withContext(Dispatchers.Main) {
                    statusText.text = "🔍 Step 2/4: Parsing HTML and embedded JSON...\n(Extracting media URLs from response)"
                }
                
                val postData = fetchPostData(postUrl)
                val mediaItems = extractMediaFromPostData(postData, postUrl)
                
                // Step 2: Look for HLS streams and direct URLs
                withContext(Dispatchers.Main) {
                    statusText.text = "🔍 Step 3/4: Finding HLS streams and direct URLs...\n(Scanning for .m3u8 and CDN links)"
                }
                
                val enhancedMedia = enhanceMediaUrls(mediaItems)
                
                // Step 3: Verify URLs work
                withContext(Dispatchers.Main) {
                    statusText.text = "🔍 Step 4/4: Verifying media accessibility...\n(Testing direct downloads from X's CDN)"
                }
                
                val verifiedMedia = verifyMediaUrls(enhancedMedia)
                
                // Display results
                withContext(Dispatchers.Main) {
                    progressBar.visibility = android.view.View.GONE
                    displayMediaOptions(verifiedMedia)
                    loadButton.isEnabled = true
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = android.view.View.GONE
                    statusText.text = "❌ Failed to load media: ${e.message}\n\n" +
                                    "This could happen if:\n" +
                                    "• Post is private or deleted\n" +
                                    "• No media in this post\n" +
                                    "• X is blocking requests\n" +
                                    "• Network connectivity issues\n\n" +
                                    "💡 Try a different public post with videos/images."
                    loadButton.isEnabled = true
                }
            }
        }
    }
    
    private suspend fun fetchPostData(postUrl: String): String {
        return withContext(Dispatchers.IO) {
            // Try multiple approaches like xdownloader.com
            val approaches = listOf(
                // Approach 1: Direct post fetch with mobile user-agent
                { fetchWithHeaders(postUrl, mobileHeaders()) },
                
                // Approach 2: Desktop user-agent
                { fetchWithHeaders(postUrl, desktopHeaders()) },
                
                // Approach 3: Try mobile.twitter.com version
                { fetchWithHeaders(postUrl.replace("x.com", "mobile.twitter.com").replace("twitter.com", "mobile.twitter.com"), mobileHeaders()) }
            )
            
            for ((index, approach) in approaches.withIndex()) {
                try {
                    withContext(Dispatchers.Main) {
                        statusText.text = "🔍 Step 1/4: Trying fetch method ${index + 1}/3...\n(Public endpoint access)"
                    }
                    
                    val result = approach()
                    if (result.isNotEmpty() && result.length > 1000) { // Valid HTML response
                        return@withContext result
                    }
                } catch (e: Exception) {
                    // Try next approach
                }
            }
            
            throw Exception("Could not fetch post data with any method")
        }
    }
    
    private fun fetchWithHeaders(url: String, headers: Map<String, String>): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.apply {
            requestMethod = "GET"
            headers.forEach { (key, value) ->
                setRequestProperty(key, value)
            }
            connectTimeout = 15000
            readTimeout = 15000
        }
        
        if (connection.responseCode !in 200..299) {
            throw Exception("HTTP ${connection.responseCode}: ${connection.responseMessage}")
        }
        
        return connection.inputStream.bufferedReader().use { it.readText() }
    }
    
    private fun mobileHeaders(): Map<String, String> = mapOf(
        "User-Agent" to "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.0 Mobile/15E148 Safari/604.1",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.5",
        "Accept-Encoding" to "gzip, deflate, br",
        "Upgrade-Insecure-Requests" to "1",
        "Sec-Fetch-Dest" to "document",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Site" to "none"
    )
    
    private fun desktopHeaders(): Map<String, String> = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.9",
        "Cache-Control" to "no-cache",
        "Pragma" to "no-cache"
    )
    
    private fun extractMediaFromPostData(html: String, originalUrl: String): List<MediaItem> {
        val mediaItems = mutableListOf<MediaItem>()
        
        // Method 1: Look for JSON data embedded in HTML (like xdownloader.com)
        val jsonPatterns = listOf(
            // Twitter's video_info structure
            Pattern.compile("\"video_info\":\\s*\\{[^}]*\"variants\":\\s*\\[(.*?)\\]", Pattern.DOTALL),
            
            // Media entities
            Pattern.compile("\"media_url_https?\"\\s*:\\s*\"([^\"]+)\""),
            
            // Extended entities
            Pattern.compile("\"extended_entities\"[^}]*\"media\"[^\\]]*\\[(.*?)\\]", Pattern.DOTALL),
            
            // Direct media URLs
            Pattern.compile("\"(https://[^\"]*(?:pbs\\.twimg\\.com/media|video\\.twimg\\.com)[^\"]+)\"")
        )
        
        jsonPatterns.forEach { pattern ->
            val matcher = pattern.matcher(html)
            while (matcher.find()) {
                val match = matcher.group(1) ?: matcher.group(0)
                
                if (match.startsWith("http")) {
                    // Direct URL found
                    val mediaItem = createMediaItem(match)
                    if (mediaItem != null) mediaItems.add(mediaItem)
                } else {
                    // JSON data found - parse it
                    try {
                        val jsonMediaItems = parseJsonForMedia(match)
                        mediaItems.addAll(jsonMediaItems)
                    } catch (e: Exception) {
                        // JSON parsing failed, continue
                    }
                }
            }
        }
        
        // Method 2: HTML parsing for direct src attributes
        val htmlPatterns = listOf(
            Pattern.compile("src\\s*=\\s*[\"']([^\"']*\\.(mp4|jpg|jpeg|png|gif|webp|m3u8)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("data-src\\s*=\\s*[\"']([^\"']*\\.(mp4|jpg|jpeg|png|gif|webp|m3u8)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
        )
        
        htmlPatterns.forEach { pattern ->
            val matcher = pattern.matcher(html)
            while (matcher.find()) {
                val url = matcher.group(1)
                if (url != null) {
                    val mediaItem = createMediaItem(url)
                    if (mediaItem != null && !mediaItems.any { it.url == mediaItem.url }) {
                        mediaItems.add(mediaItem)
                    }
                }
            }
        }
        
        return mediaItems
    }
    
    private fun parseJsonForMedia(jsonString: String): List<MediaItem> {
        val mediaItems = mutableListOf<MediaItem>()
        
        try {
            // Try to parse as variants array
            if (jsonString.contains("\"url\"") && jsonString.contains("\"content_type\"")) {
                val variants = JSONArray("[$jsonString]")
                for (i in 0 until variants.length()) {
                    val variant = variants.getJSONObject(i)
                    if (variant.has("url")) {
                        val url = variant.getString("url")
                        val contentType = variant.optString("content_type", "unknown")
                        val bitrate = variant.optInt("bitrate", 0)
                        
                        val quality = when {
                            bitrate >= 1280000 -> "HD"
                            bitrate >= 832000 -> "SD"
                            bitrate > 0 -> "Low"
                            else -> "Original"
                        }
                        
                        val mediaItem = MediaItem(
                            url = url,
                            type = if (contentType.contains("video")) "video" else "unknown",
                            quality = quality,
                            format = getFormatFromUrl(url)
                        )
                        mediaItems.add(mediaItem)
                    }
                }
            }
        } catch (e: Exception) {
            // JSON parsing failed
        }
        
        return mediaItems
    }
    
    private fun createMediaItem(url: String): MediaItem? {
        if (!isValidMediaUrl(url)) return null
        
        val type = when {
            url.contains("video") || url.contains(".mp4") || url.contains(".webm") || url.contains(".m3u8") -> "video"
            url.contains(".gif") -> "gif"
            url.contains(".jpg") || url.contains(".jpeg") || url.contains(".png") || url.contains(".webp") -> "image"
            else -> "unknown"
        }
        
        val quality = when {
            url.contains("1920x1080") || url.contains("1280x720") -> "HD"
            url.contains("720x720") || url.contains("640x640") -> "SD"
            url.contains("name=orig") -> "Original"
            url.contains("name=large") -> "Large"
            else -> "Standard"
        }
        
        return MediaItem(
            url = url,
            type = type,
            quality = quality,
            format = getFormatFromUrl(url)
        )
    }
    
    private suspend fun enhanceMediaUrls(mediaItems: List<MediaItem>): List<MediaItem> {
        // For now, return as-is. In a full implementation, this would:
        // 1. Resolve HLS streams to direct MP4 URLs
        // 2. Find higher quality versions
        // 3. Get file sizes
        return mediaItems
    }
    
    private suspend fun verifyMediaUrls(mediaItems: List<MediaItem>): List<MediaItem> {
        return withContext(Dispatchers.IO) {
            val verified = mutableListOf<MediaItem>()
            
            mediaItems.forEach { item ->
                try {
                    val connection = URL(item.url).openConnection() as HttpURLConnection
                    connection.apply {
                        requestMethod = "HEAD"
                        setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; MediaDownloader/1.0)")
                        setRequestProperty("Referer", "https://twitter.com/")
                        connectTimeout = 5000
                        readTimeout = 5000
                    }
                    
                    if (connection.responseCode in 200..299) {
                        val contentLength = connection.contentLength
                        val sizeStr = if (contentLength > 0) {
                            "${contentLength / 1024} KB"
                        } else {
                            "Unknown size"
                        }
                        
                        verified.add(item.copy(size = sizeStr))
                    }
                } catch (e: Exception) {
                    // URL not accessible
                }
            }
            
            return@withContext verified
        }
    }
    
    private fun displayMediaOptions(mediaItems: List<MediaItem>) {
        foundMedia = mediaItems
        
        if (mediaItems.isEmpty()) {
            statusText.text = "❌ No media found in this post.\n\n" +
                             "This could mean:\n" +
                             "• Post contains only text\n" +
                             "• Media is protected/private\n" +
                             "• Post was deleted\n\n" +
                             "💡 Try a different public post with videos or images."
            return
        }
        
        statusText.text = "✅ Found ${mediaItems.size} media item(s)!\n\n" +
                         "Select items to download. Files will be saved directly from X's servers to your Downloads folder."
        
        mediaListView.removeAllViews()
        
        mediaItems.forEachIndexed { index, item ->
            val itemView = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(16, 8, 16, 8)
                background = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.editbox_background)
            }
            
            val checkBox = CheckBox(this).apply {
                isChecked = true
            }
            
            val infoView = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(16, 0, 0, 0)
            }
            
            val titleText = TextView(this).apply {
                val icon = when (item.type) {
                    "video" -> "🎬"
                    "image" -> "🖼️"
                    "gif" -> "🎞️"
                    else -> "📁"
                }
                text = "$icon ${item.type.uppercase()} - ${item.quality}"
                textSize = 16f
                setTextColor(android.graphics.Color.BLACK)
            }
            
            val detailText = TextView(this).apply {
                text = "Format: ${item.format.uppercase()} • Size: ${item.size}"
                textSize = 12f
                setTextColor(android.graphics.Color.GRAY)
            }
            
            infoView.addView(titleText)
            infoView.addView(detailText)
            
            itemView.addView(checkBox)
            itemView.addView(infoView)
            
            // Store checkbox reference
            itemView.tag = checkBox
            
            mediaListView.addView(itemView)
            
            // Add spacing
            if (index < mediaItems.size - 1) {
                val spacer = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 
                        8
                    )
                }
                mediaListView.addView(spacer)
            }
        }
        
        mediaListView.visibility = android.view.View.VISIBLE
        downloadButton.visibility = android.view.View.VISIBLE
        
        // Scroll to show results
        scrollView.post {
            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }
    
    private fun downloadSelectedMedia() {
        val selectedItems = mutableListOf<MediaItem>()
        
        // Check which items are selected
        for (i in 0 until mediaListView.childCount) {
            val view = mediaListView.getChildAt(i)
            if (view.tag is CheckBox) {
                val checkBox = view.tag as CheckBox
                if (checkBox.isChecked) {
                    selectedItems.add(foundMedia[selectedItems.size])
                }
            }
        }
        
        if (selectedItems.isEmpty()) {
            Toast.makeText(this, "Please select at least one item to download", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            
            selectedItems.forEachIndexed { index, item ->
                val request = DownloadManager.Request(Uri.parse(item.url)).apply {
                    setTitle("X Media ${index + 1} - ${item.type.uppercase()}")
                    setDescription("${item.quality} ${item.format.uppercase()} • ${item.size}")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    
                    val filename = "x_${item.type}_${System.currentTimeMillis()}_${index + 1}.${item.format}"
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                    
                    setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                    
                    // Headers like xdownloader.com would use
                    addRequestHeader("User-Agent", "Mozilla/5.0 (compatible; MediaDownloader/1.0)")
                    addRequestHeader("Referer", "https://twitter.com/")
                    addRequestHeader("Accept", "*/*")
                }
                
                downloadManager.enqueue(request)
            }
            
            Toast.makeText(this, "✅ Started ${selectedItems.size} downloads!\nCheck Downloads folder & notifications.", Toast.LENGTH_LONG).show()
            
            // Show success dialog
            AlertDialog.Builder(this)
                .setTitle("🎉 Downloads Started!")
                .setMessage("Downloading ${selectedItems.size} media files directly from X's servers.\n\nFiles will appear in your Downloads folder.")
                .setPositiveButton("OK", null)
                .setNeutralButton("Clear URL") { _, _ -> urlInput.setText("") }
                .show()
            
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Download failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun isValidMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        return (lower.contains("pbs.twimg.com") || lower.contains("video.twimg.com") ||
                lower.contains(".mp4") || lower.contains(".jpg") || lower.contains(".jpeg") ||
                lower.contains(".png") || lower.contains(".gif") || lower.contains(".webp") ||
                lower.contains(".m3u8") || lower.contains(".webm")) &&
               !lower.contains("profile") && !lower.contains("avatar") && !lower.contains("icon")
    }
    
    private fun getFormatFromUrl(url: String): String {
        return when {
            url.contains(".mp4", true) -> "mp4"
            url.contains(".webm", true) -> "webm"
            url.contains(".m3u8", true) -> "m3u8"
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
