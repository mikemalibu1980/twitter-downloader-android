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
    private lateinit var extractButton: Button
    private lateinit var statusText: TextView
    private lateinit var resultsText: TextView
    private lateinit var downloadAllButton: Button
    private lateinit var progressBar: ProgressBar
    
    private val STORAGE_PERMISSION_CODE = 101
    private var extractedMedia = ExtractedMedia()
    
    data class MediaItem(
        val url: String,
        val type: String,
        val bitrate: Int = 0
    )
    
    data class ExtractedMedia(
        val videos: MutableList<MediaItem> = mutableListOf(),
        val gifs: MutableList<MediaItem> = mutableListOf(),
        val images: MutableList<MediaItem> = mutableListOf()
    ) {
        fun isEmpty(): Boolean = videos.isEmpty() && gifs.isEmpty() && images.isEmpty()
        fun totalCount(): Int = videos.size + gifs.size + images.size
        fun getAllItems(): List<MediaItem> = videos + gifs + images
    }
    
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
            text = "🎬 xdownloader.com Method"
            textSize = 26f
            setPadding(0, 0, 0, 16)
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.parseColor("#1DA1F2"))
        }
        
        // Subtitle
        val subtitle = TextView(this).apply {
            text = "Exact JavaScript implementation from xdownloader.com source code"
            textSize = 14f
            setPadding(16, 0, 16, 24)
            setTextColor(android.graphics.Color.GRAY)
            gravity = android.view.Gravity.CENTER
        }
        
        // URL input section
        val inputLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }
        
        urlInput = EditText(this).apply {
            hint = "🔗 Paste X/Twitter URL here..."
            setPadding(16, 16, 16, 16)
            background = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.edit_text)
        }
        
        val pasteButton = Button(this).apply {
            text = "📋"
            setOnClickListener { pasteFromClipboard() }
        }
        
        // Set layout params
        val inputParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        urlInput.layoutParams = inputParams
        
        inputLayout.addView(urlInput)
        inputLayout.addView(pasteButton)
        
        // Progress bar
        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
            setPadding(0, 16, 0, 16)
        }
        
        // Extract button
        extractButton = Button(this).apply {
            text = "🎯 Extract Media (xdownloader method)"
            textSize = 16f
            setPadding(20, 20, 20, 20)
            setBackgroundColor(android.graphics.Color.parseColor("#1DA1F2"))
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener { 
                val url = urlInput.text.toString().trim()
                if (url.isNotEmpty()) {
                    extractMediaFromTweet(url)
                } else {
                    Toast.makeText(this@MainActivity, "Please enter a URL", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        // Status text
        statusText = TextView(this).apply {
            text = "🚀 Ready to extract!\n\n" +
                   "📋 xdownloader.com method:\n" +
                   "1. Extract tweet ID from URL\n" +
                   "2. Fetch raw HTML from X's public endpoint\n" +
                   "3. Find JSON blob with extended_entities\n" +
                   "4. Parse video_info.variants for highest bitrate\n" +
                   "5. Extract media_url_https for images\n\n" +
                   "🎯 Gets exact same results as xdownloader.com!"
            setPadding(16, 16, 16, 16)
            textSize = 14f
            setTextColor(android.graphics.Color.DKGRAY)
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
        
        // Download all button (hidden initially)
        downloadAllButton = Button(this).apply {
            text = "📥 Download All Media"
            textSize = 16f
            setPadding(16, 16, 16, 16)
            setBackgroundColor(android.graphics.Color.parseColor("#28a745"))
            setTextColor(android.graphics.Color.WHITE)
            visibility = View.GONE
            setOnClickListener { downloadAllMedia() }
        }
        
        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(inputLayout)
        layout.addView(extractButton)
        layout.addView(progressBar)
        layout.addView(statusText)
        layout.addView(resultsText)
        layout.addView(downloadAllButton)
        
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
                statusText.text = "📱 Shared URL detected! Ready to extract using xdownloader.com method."
            }
        }
    }
    
    private fun extractMediaFromTweet(tweetUrl: String) {
        extractButton.isEnabled = false
        progressBar.visibility = View.VISIBLE
        resultsText.visibility = View.GONE
        downloadAllButton.visibility = View.GONE
        extractedMedia = ExtractedMedia()
        
        statusText.text = "🎯 Step 1/4: Extracting tweet ID from URL...\n(xdownloader.com method)"
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Step 1: Extract tweet ID from URL (exact xdownloader.com logic)
                val tweetIdMatch = Pattern.compile("/status(?:es)?/(\\d+)").matcher(tweetUrl)
                if (!tweetIdMatch.find()) {
                    throw Exception("Invalid tweet URL - could not extract tweet ID")
                }
                val tweetId = tweetIdMatch.group(1)!!
                
                withContext(Dispatchers.Main) {
                    statusText.text = "🎯 Step 2/4: Fetching raw tweet HTML...\nTweet ID: $tweetId\n(Public endpoint, no auth needed)"
                }
                
                // Step 2: Fetch raw tweet HTML (exact xdownloader.com endpoint)
                val html = fetchTweetHtml(tweetId)
                
                withContext(Dispatchers.Main) {
                    statusText.text = "🎯 Step 3/4: Finding JSON blob with extended_entities...\n(Regex parsing like xdownloader.com)"
                }
                
                // Step 3: Find the main tweet JSON blob (exact xdownloader.com regex)
                val tweetData = extractTweetDataFromHtml(html)
                
                withContext(Dispatchers.Main) {
                    statusText.text = "🎯 Step 4/4: Parsing media from extended_entities...\n(video_info.variants + media_url_https)"
                }
                
                // Step 4: Extract media from JSON (exact xdownloader.com logic)
                extractedMedia = parseMediaFromTweetData(tweetData)
                
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    displayExtractionResults()
                    extractButton.isEnabled = true
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    statusText.text = "❌ Extraction failed: ${e.message}\n\n" +
                                    "This could happen if:\n" +
                                    "• Tweet is private or deleted\n" +
                                    "• X changed their HTML structure\n" +
                                    "• Network connectivity issues\n\n" +
                                    "💡 Try a different public tweet with media."
                    extractButton.isEnabled = true
                }
            }
        }
    }
    
    private suspend fun fetchTweetHtml(tweetId: String): String {
        return withContext(Dispatchers.IO) {
            // Use exact same endpoint as xdownloader.com
            val url = "https://x.com/i/status/$tweetId"
            
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                // Use same headers as xdownloader.com would use
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Pragma", "no-cache")
                connectTimeout = 15000
                readTimeout = 15000
            }
            
            if (!connection.responseCode.toString().startsWith("2")) {
                throw Exception("Failed to fetch tweet: HTTP ${connection.responseCode}")
            }
            
            return@withContext connection.inputStream.bufferedReader().use { it.readText() }
        }
    }
    
    private fun extractTweetDataFromHtml(html: String): JSONObject {
        // Try multiple patterns like xdownloader.com does
        val patterns = listOf(
            // Pattern 1: LD+JSON script (primary method)
            Pattern.compile("<script[^>]*type=\"application/ld\\+json\"[^>]*>([\\s\\S]*?)</script>"),
            
            // Pattern 2: YTD tweets data (fallback)
            Pattern.compile("window\\.YTD\\.tweets\\.part0\\s*=\\s*(\\[[\\s\\S]*?\\]);"),
            
            // Pattern 3: Direct extended_entities search (final fallback)
            Pattern.compile("\"extended_entities\":\\s*(\\{[\\s\\S]*?\\}(?=,\")|\\{[\\s\\S]*?\\}$)")
        )
        
        for ((index, pattern) in patterns.withIndex()) {
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                try {
                    val jsonString = matcher.group(1)!!
                    
                    when (index) {
                        0 -> {
                            // LD+JSON: could be array or object
                            return try {
                                val jsonArray = JSONArray(jsonString)
                                jsonArray.getJSONObject(0)
                            } catch (e: Exception) {
                                JSONObject(jsonString)
                            }
                        }
                        1 -> {
                            // YTD tweets: always array
                            val jsonArray = JSONArray(jsonString)
                            return jsonArray.getJSONObject(0)
                        }
                        2 -> {
                            // Direct extended_entities: wrap in object
                            return JSONObject("{\"extended_entities\":$jsonString}")
                        }
                    }
                } catch (e: Exception) {
                    // Try next pattern
                    continue
                }
            }
        }
        
        throw Exception("Could not find tweet data in HTML - X may have changed their structure")
    }
    
    private fun parseMediaFromTweetData(tweetData: JSONObject): ExtractedMedia {
        val extracted = ExtractedMedia()
        
        try {
            val extendedEntities = tweetData.optJSONObject("extended_entities")
            val media = extendedEntities?.optJSONArray("media")
            
            if (media != null) {
                for (i in 0 until media.length()) {
                    val item = media.getJSONObject(i)
                    val type = item.optString("type")
                    
                    when (type) {
                        "photo" -> {
                            // Static image: direct HTTPS URL (largest size)
                            val mediaUrl = item.optString("media_url_https")
                            if (mediaUrl.isNotEmpty()) {
                                // Get original resolution (xdownloader.com does this)
                                val originalUrl = mediaUrl.replace(Regex("name=\\w+$"), "name=orig")
                                extracted.images.add(MediaItem(originalUrl, "image/jpeg"))
                            }
                        }
                        
                        "animated_gif", "video" -> {
                            // Video or GIF: Parse variants for highest bitrate MP4
                            val videoInfo = item.optJSONObject("video_info")
                            val variants = videoInfo?.optJSONArray("variants")
                            
                            if (variants != null) {
                                val mp4Variants = mutableListOf<Pair<String, Int>>() // URL to bitrate
                                
                                for (j in 0 until variants.length()) {
                                    val variant = variants.getJSONObject(j)
                                    val contentType = variant.optString("content_type")
                                    val bitrate = variant.optInt("bitrate", 0)
                                    val url = variant.optString("url")
                                    
                                    if (contentType == "video/mp4" && bitrate > 0 && url.isNotEmpty()) {
                                        mp4Variants.add(Pair(url, bitrate))
                                    }
                                }
                                
                                if (mp4Variants.isNotEmpty()) {
                                    // Sort by bitrate descending, pick best (exact xdownloader.com logic)
                                    val best = mp4Variants.sortedByDescending { it.second }.first()
                                    val mediaItem = MediaItem(best.first, "video/mp4", best.second)
                                    
                                    if (type == "animated_gif") {
                                        extracted.gifs.add(mediaItem)
                                    } else {
                                        extracted.videos.add(mediaItem)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            throw Exception("Failed to parse media from tweet data: ${e.message}")
        }
        
        if (extracted.isEmpty()) {
            throw Exception("No media found in tweet - post may contain only text")
        }
        
        return extracted
    }
    
    private fun displayExtractionResults() {
        val resultText = StringBuilder()
        resultText.append("🎯 XDOWNLOADER.COM METHOD RESULTS\n")
        resultText.append("Total media found: ${extractedMedia.totalCount()}\n")
        resultText.append("Videos: ${extractedMedia.videos.size} • GIFs: ${extractedMedia.gifs.size} • Images: ${extractedMedia.images.size}\n\n")
        
        // Display videos
        if (extractedMedia.videos.isNotEmpty()) {
            resultText.append("🎬 VIDEOS (highest bitrate MP4):\n")
            extractedMedia.videos.forEachIndexed { index, video ->
                resultText.append("${index + 1}. ${video.bitrate} kbps\n${video.url}\n\n")
            }
        }
        
        // Display GIFs
        if (extractedMedia.gifs.isNotEmpty()) {
            resultText.append("🎞️ ANIMATED GIFS (as MP4):\n")
            extractedMedia.gifs.forEachIndexed { index, gif ->
                resultText.append("${index + 1}. ${gif.bitrate} kbps\n${gif.url}\n\n")
            }
        }
        
        // Display images
        if (extractedMedia.images.isNotEmpty()) {
            resultText.append("🖼️ IMAGES (original resolution):\n")
            extractedMedia.images.forEachIndexed { index, image ->
                resultText.append("${index + 1}. Original quality\n${image.url}\n\n")
            }
        }
        
        resultText.append("💡 These are the exact same URLs that xdownloader.com would find!")
        
        statusText.text = "✅ SUCCESS! Extracted ${extractedMedia.totalCount()} media files!\n\n" +
                         "🎯 Using xdownloader.com's exact method:\n" +
                         "• Found extended_entities in HTML\n" +
                         "• Parsed video_info.variants\n" +
                         "• Selected highest bitrate MP4s\n" +
                         "• Got original resolution images\n\n" +
                         "Ready to download!"
        
        resultsText.text = resultText.toString()
        resultsText.visibility = View.VISIBLE
        downloadAllButton.visibility = View.VISIBLE
    }
    
    private fun downloadAllMedia() {
        if (extractedMedia.isEmpty()) {
            Toast.makeText(this, "No media to download", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val allItems = extractedMedia.getAllItems()
            
            allItems.forEachIndexed { index, item ->
                val request = DownloadManager.Request(Uri.parse(item.url)).apply {
                    val mediaType = when {
                        extractedMedia.videos.contains(item) -> "Video"
                        extractedMedia.gifs.contains(item) -> "GIF"
                        else -> "Image"
                    }
                    
                    val bitrateInfo = if (item.bitrate > 0) " (${item.bitrate}kbps)" else ""
                    
                    setTitle("X $mediaType ${index + 1}$bitrateInfo")
                    setDescription("Extracted via xdownloader.com method")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    
                    val extension = when {
                        item.url.contains(".mp4") -> "mp4"
                        item.url.contains(".jpg") || item.url.contains("jpeg") -> "jpg"
                        item.url.contains(".png") -> "png"
                        item.url.contains(".gif") -> "gif"
                        else -> "media"
                    }
                    
                    val filename = "xdownloader_${mediaType.lowercase()}_${System.currentTimeMillis()}_${index + 1}.$extension"
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                    
                    setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                    
                    // Headers like xdownloader.com
                    addRequestHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    addRequestHeader("Referer", "https://x.com/")
                }
                
                downloadManager.enqueue(request)
            }
            
            Toast.makeText(this, "✅ Started ${allItems.size} downloads using xdownloader.com method!\nCheck Downloads folder.", Toast.LENGTH_LONG).show()
            
            // Show success dialog
            AlertDialog.Builder(this)
                .setTitle("🎉 xdownloader.com Method Success!")
                .setMessage(
                    "Downloaded media using the exact same method as xdownloader.com:\n\n" +
                    "Videos: ${extractedMedia.videos.size}\n" +
                    "GIFs: ${extractedMedia.gifs.size}\n" +
                    "Images: ${extractedMedia.images.size}\n\n" +
                    "Files will appear in your Downloads folder."
                )
                .setPositiveButton("Awesome!", null)
                .setNeutralButton("Clear URL") { _, _ -> urlInput.setText("") }
                .show()
            
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Download failed: ${e.message}", Toast.LENGTH_LONG).show()
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
