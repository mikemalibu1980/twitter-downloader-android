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
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
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
                   "Updated method based on xdownloader.com and guest API:\n" +
                   "• Uses guest token for better compatibility (incl. sensitive content)\n" +
                   "• Fetches tweet data via API v2\n" +
                   "• Extracts highest quality media URLs\n" +
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
        
        statusText.text = "🔍 Finding media using guest API method...\n\nStep 1: Obtaining guest token..."
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tweetId = extractTweetId(postUrl) ?: throw Exception("Invalid URL - no tweet ID found")
                
                val bearer = "AAAAAAAAAAAAAAAAAAAAANRILgAAAAAAnNwIzUejRCOuH5E6I8xnZz4puTs%3D1Zv7ttfk8LF81IUq16cHjhLTvJu4FA33AGWWjCpTnA"
                
                val guestToken = getGuestToken(bearer)
                
                withContext(Dispatchers.Main) {
                    statusText.text = "🔍 Step 2: Fetching tweet data with guest token..."
                }
                
                val tweetJson = fetchTweetJson(tweetId, bearer, guestToken)
                
                withContext(Dispatchers.Main) {
                    statusText.text = "🔍 Step 2: Parsing JSON for media URLs..."
                }
                
                val mediaUrls = extractAllMediaUrls(tweetJson)
                
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
                                    "• Age-restricted content requires login (not supported)\n" +
                                    "• No media in this post\n" +
                                    "• Network connectivity issues or X API changes\n\n" +
                                    "Try a different public post or check if the post is age-restricted."
                    loadButton.isEnabled = true
                }
            }
        }
    }
    
    private suspend fun getGuestToken(bearer: String): String {
        return withContext(Dispatchers.IO) {
            val url = URL("https://api.twitter.com/1.1/guest/activate.json")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $bearer")
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
            connection.connect()
            
            if (connection.responseCode !in 200..299) {
                throw Exception("Failed to get guest token: ${connection.responseCode}")
            }
            
            val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(jsonStr)
            json.getString("guest_token")
        }
    }
    
    private suspend fun fetchTweetJson(tweetId: String, bearer: String, guestToken: String): String {
        return withContext(Dispatchers.IO) {
            val apiUrl = """https://api.twitter.com/2/timeline/conversation/$tweetId.json?
                |include_profile_interstitial_type=1&include_blocking=1&include_blocked_by=1&
                |include_followed_by=1&include_want_retweets=1&include_mute_edge=1&include_can_dm=1&
                |include_can_media_tag=1&include_ext_has_nft_avatar=1&include_ext_is_blue_verified=1&
                |include_ext_verified_type=1&include_ext_profile_image_shape=1&skip_status=1&
                |cards_platform=Web-12&include_cards=1&include_ext_alt_text=true&
                |include_ext_limited_action_results=true&include_quote_count=true&include_reply_count=1&
                |tweet_mode=extended&include_ext_views=true&include_entities=true&include_user_entities=true&
                |include_ext_media_color=true&include_ext_article_color=true&include_ext_bg_color=true&
                |include_users=1&include_ext_csp_report_only=true&include_ext_sso=true&
                |include_ext_no_scribe=true&include_ext_session=true&include_ext_settings=true""".trimMargin()
            
            val connection = URL(apiUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $bearer")
            connection.setRequestProperty("x-guest-token", guestToken)
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
            connection.setRequestProperty("Accept", "application/json")
            
            if (connection.responseCode !in 200..299) {
                throw Exception("Failed to fetch tweet data: ${connection.responseCode}")
            }
            
            connection.inputStream.bufferedReader().use { it.readText() }
        }
    }
    
    private fun extractAllMediaUrls(jsonStr: String): List<String> {
        val allUrls = mutableListOf<String>()
        
        try {
            val json = JSONObject(jsonStr)
            val instructions = json.getJSONObject("data")
                .getJSONObject("threaded_conversation_with_injections_v2")
                .getJSONArray("instructions")
            val entries = instructions.getJSONObject(0).getJSONArray("entries")
            
            for (i in 0 until entries.length()) {
                val entry = entries.getJSONObject(i)
                if (entry.getString("entryId").startsWith("tweet-$tweetId")) {
                    val tweetResult = entry.getJSONObject("content")
                        .getJSONObject("itemContent")
                        .getJSONObject("tweet_results")
                        .getJSONObject("result")
                    val legacy = tweetResult.optJSONObject("legacy") ?: continue
                    val extendedEntities = legacy.optJSONObject("extended_entities") ?: continue
                    val mediaArray = extendedEntities.optJSONArray("media") ?: continue
                    
                    for (j in 0 until mediaArray.length()) {
                        val media = mediaArray.getJSONObject(j)
                        val type = media.optString("type", "")
                        if (type == "photo") {
                            var url = media.getString("media_url_https")
                            if (!url.contains(":orig")) {
                                url += ":orig"
                            }
                            allUrls.add(url)
                        } else if (type == "video" || type == "animated_gif") {
                            val videoInfo = media.optJSONObject("video_info") ?: continue
                            val variants = videoInfo.optJSONArray("variants") ?: continue
                            var bestUrl = ""
                            var maxBitrate = -1
                            for (k in 0 until variants.length()) {
                                val variant = variants.getJSONObject(k)
                                val contentType = variant.optString("content_type", "")
                                if (contentType == "video/mp4") {
                                    val bitrate = variant.optInt("bitrate", 0)
                                    if (bitrate > maxBitrate) {
                                        maxBitrate = bitrate
                                        bestUrl = variant.getString("url")
                                    }
                                }
                            }
                            if (bestUrl.isNotEmpty()) {
                                allUrls.add(bestUrl)
                            }
                        }
                    }
                    break
                }
            }
        } catch (e: JSONException) {
            // Fallback or log; let outer catch handle UI
            throw e  // Propagate to main catch for proper handling
        }
        
        return allUrls.distinct()
    }
    
    private suspend fun verifyUrls(urls: List<String>): List<String> {
        return withContext(Dispatchers.IO) {
            val working = mutableListOf<String>()
            
            urls.forEach { url ->
                try {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.requestMethod = "HEAD"
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; MediaDownloader/1.0)")
                    connection.setRequestProperty("Referer", "https://x.com/")
                    connectTimeout = 5000
                    readTimeout = 5000
                    
                    if (connection.responseCode in 200..299) {
                        working.add(url)
                    }
                } catch (e: Exception) {
                    // URL failed verification
                }
            }
            
            working
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
                             "• Media is protected/private/age-restricted\n" +
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
                    addRequestHeader("Referer", "https://x.com/")
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
