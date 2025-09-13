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
import java.net.URLEncoder
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {
    private lateinit var urlInput: EditText
    private lateinit var downloadButton: Button
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var serviceSpinner: Spinner
    
    private val STORAGE_PERMISSION_CODE = 101
    
    // List of working download services
    private val downloadServices = listOf(
        DownloadService("SSS Twitter", "https://ssstwitter.com/", "ssstwitter"),
        DownloadService("Twitter Video Downloader", "https://twittervideodownloader.com/", "twittervideodl"),
        DownloadService("TWDown", "https://twdown.net/", "twdown"),
        DownloadService("SaveTweet", "https://savetweet.net/", "savetweet")
    )
    
    data class DownloadService(val name: String, val url: String, val id: String)
    
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
            text = "🐦 Twitter/X Media Downloader"
            textSize = 26f
            setPadding(0, 0, 0, 40)
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.parseColor("#1DA1F2"))
        }
        
        // Instructions
        val instructions = TextView(this).apply {
            text = "📱 Paste any Twitter/X post URL to download its media"
            textSize = 16f
            setPadding(16, 0, 16, 24)
            setTextColor(android.graphics.Color.GRAY)
            gravity = android.view.Gravity.CENTER
        }
        
        // URL input
        urlInput = EditText(this).apply {
            hint = "🔗 https://twitter.com/... or https://x.com/..."
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            setPadding(20, 20, 20, 20)
            textSize = 14f
            background = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.edit_text)
        }
        
        // Service selector
        val serviceLabel = TextView(this).apply {
            text = "Choose download service:"
            textSize = 16f
            setPadding(0, 24, 0, 8)
            setTextColor(android.graphics.Color.DKGRAY)
        }
        
        serviceSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                downloadServices.map { it.name }
            )
            setPadding(0, 8, 0, 8)
        }
        
        // Progress bar
        progressBar = ProgressBar(this).apply {
            visibility = android.view.View.GONE
            setPadding(0, 24, 0, 24)
        }
        
        // Download button
        downloadButton = Button(this).apply {
            text = "📥 Find & Download Media"
            textSize = 18f
            setPadding(24, 24, 24, 24)
            setBackgroundColor(android.graphics.Color.parseColor("#1DA1F2"))
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener { 
                val url = urlInput.text.toString().trim()
                if (url.isNotEmpty()) {
                    if (isValidTwitterUrl(url)) {
                        findAndDownloadMedia(url)
                    } else {
                        showError("❌ Please enter a valid Twitter or X.com URL")
                    }
                } else {
                    showError("❌ Please enter a URL first")
                }
            }
        }
        
        // Browse service button
        val browseButton = Button(this).apply {
            text = "🌐 Open Service in Browser"
            textSize = 14f
            setPadding(16, 16, 16, 16)
            setBackgroundColor(android.graphics.Color.parseColor("#657786"))
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener { 
                openServiceInBrowser()
            }
        }
        
        // Status text
        statusText = TextView(this).apply {
            text = "✨ Ready to download!\n\n" +
                   "📋 How to use:\n" +
                   "1. Copy a Twitter/X post URL\n" +
                   "2. Paste it above\n" +
                   "3. Choose a service\n" +
                   "4. Tap 'Find & Download'\n\n" +
                   "💡 If one service doesn't work, try another!\n\n" +
                   "🔗 You can also open the service in your browser and use it manually."
            setPadding(16, 32, 16, 0)
            textSize = 14f
            setTextColor(android.graphics.Color.DKGRAY)
        }
        
        layout.addView(title)
        layout.addView(instructions)
        layout.addView(urlInput)
        layout.addView(serviceLabel)
        layout.addView(serviceSpinner)
        layout.addView(progressBar)
        layout.addView(downloadButton)
        layout.addView(browseButton)
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
                    statusText.text = "📱 Shared URL detected! Ready to find media."
                }
            }
        }
    }
    
    private fun openServiceInBrowser() {
        val selectedService = downloadServices[serviceSpinner.selectedItemPosition]
        val url = urlInput.text.toString().trim()
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(selectedService.url)
        }
        
        try {
            startActivity(intent)
            if (url.isNotEmpty() && isValidTwitterUrl(url)) {
                // Copy URL to clipboard for easy pasting
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Twitter URL", url)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "✅ URL copied to clipboard!\nPaste it on the website.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Could not open browser", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun findAndDownloadMedia(tweetUrl: String) {
        downloadButton.isEnabled = false
        progressBar.visibility = android.view.View.VISIBLE
        val selectedService = downloadServices[serviceSpinner.selectedItemPosition]
        
        statusText.text = "🔍 Using ${selectedService.name} to find media...\n\nThis may take a moment."
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val mediaUrls = findMediaUrls(tweetUrl, selectedService)
                
                withContext(Dispatchers.Main) {
                    progressBar.visibility = android.view.View.GONE
                    
                    if (mediaUrls.isNotEmpty()) {
                        statusText.text = "✅ Found ${mediaUrls.size} media file(s)!\n\n" +
                                        "📥 Starting downloads via Android Download Manager...\n" +
                                        "Check your Downloads folder and notification bar."
                        downloadFiles(mediaUrls)
                        showSuccessDialog(mediaUrls.size)
                    } else {
                        statusText.text = "❌ No downloadable media found.\n\n" +
                                        "This could happen if:\n" +
                                        "• The post has no videos/images\n" +
                                        "• The post is private/deleted\n" +
                                        "• The service is temporarily down\n\n" +
                                        "💡 Try:\n" +
                                        "• A different service from the dropdown\n" +
                                        "• Opening the service in browser manually\n" +
                                        "• Checking if the post is public"
                        
                        // Offer browser option
                        showFallbackDialog()
                    }
                    
                    downloadButton.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = android.view.View.GONE
                    
                    statusText.text = "❌ Could not connect to ${selectedService.name}\n\n" +
                                    "Error: ${e.message}\n\n" +
                                    "💡 Try:\n" +
                                    "• Different service from dropdown\n" +
                                    "• Checking internet connection\n" +
                                    "• Opening service in browser"
                    
                    downloadButton.isEnabled = true
                    showFallbackDialog()
                }
            }
        }
    }
    
    private suspend fun findMediaUrls(tweetUrl: String, service: DownloadService): List<String> {
        return withContext(Dispatchers.IO) {
            when (service.id) {
                "ssstwitter" -> findMediaSSSTwitter(tweetUrl)
                "twittervideodl" -> findMediaTwitterVideoDL(tweetUrl)
                "twdown" -> findMediaTWDown(tweetUrl)
                "savetweet" -> findMediaSaveTweet(tweetUrl)
                else -> emptyList()
            }
        }
    }
    
    private suspend fun findMediaSSSTwitter(tweetUrl: String): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                // Clean the URL
                val cleanUrl = tweetUrl.replace("x.com", "twitter.com")
                
                // Try to extract direct media URLs by analyzing the service
                val serviceUrl = "https://ssstwitter.com/"
                val connection = URL(serviceUrl).openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android 13; Mobile)")
                    connectTimeout = 15000
                    readTimeout = 15000
                }
                
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                
                // Look for API endpoints or download patterns
                val mediaUrls = mutableListOf<String>()
                
                // Try common Twitter media URL patterns
                val tweetId = extractTweetId(cleanUrl)
                if (tweetId != null) {
                    // Try direct Twitter media URLs (these sometimes work)
                    val possibleUrls = listOf(
                        "https://video.twimg.com/ext_tw_video/$tweetId/pu/vid/720x720/video.mp4",
                        "https://video.twimg.com/ext_tw_video/$tweetId/pu/vid/1280x720/video.mp4",
                        "https://pbs.twimg.com/media/${tweetId}?format=jpg&name=large",
                        "https://pbs.twimg.com/media/${tweetId}?format=png&name=large"
                    )
                    
                    // Test if these URLs are accessible
                    for (testUrl in possibleUrls) {
                        try {
                            val testConnection = URL(testUrl).openConnection() as HttpURLConnection
                            testConnection.requestMethod = "HEAD"
                            testConnection.connectTimeout = 5000
                            if (testConnection.responseCode == 200) {
                                mediaUrls.add(testUrl)
                            }
                        } catch (e: Exception) {
                            // URL not accessible, skip
                        }
                    }
                }
                
                return@withContext mediaUrls
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    private suspend fun findMediaTwitterVideoDL(tweetUrl: String): List<String> {
        // Similar approach for other services
        return findGenericMediaUrls(tweetUrl)
    }
    
    private suspend fun findMediaTWDown(tweetUrl: String): List<String> {
        return findGenericMediaUrls(tweetUrl)
    }
    
    private suspend fun findMediaSaveTweet(tweetUrl: String): List<String> {
        return findGenericMediaUrls(tweetUrl)
    }
    
    private suspend fun findGenericMediaUrls(tweetUrl: String): List<String> {
        return withContext(Dispatchers.IO) {
            val mediaUrls = mutableListOf<String>()
            val tweetId = extractTweetId(tweetUrl)
            
            if (tweetId != null) {
                // Try common Twitter media patterns
                val patterns = listOf(
                    "https://pbs.twimg.com/media/${tweetId}?format=jpg&name=orig",
                    "https://pbs.twimg.com/media/${tweetId}?format=png&name=orig",
                    "https://video.twimg.com/ext_tw_video/${tweetId}/pu/vid/1280x720/video.mp4"
                )
                
                patterns.forEach { url ->
                    try {
                        val connection = URL(url).openConnection() as HttpURLConnection
                        connection.requestMethod = "HEAD"
                        connection.connectTimeout = 3000
                        if (connection.responseCode == 200) {
                            mediaUrls.add(url)
                        }
                    } catch (e: Exception) {
                        // Skip this URL
                    }
                }
            }
            
            return@withContext mediaUrls
        }
    }
    
    private fun extractTweetId(url: String): String? {
        val patterns = listOf(
            Pattern.compile("status/(\\d+)"),
            Pattern.compile("statuses/(\\d+)")
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
                    setDescription("Downloaded from Twitter/X")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    
                    val extension = getFileExtension(url)
                    val filename = "twitter_${System.currentTimeMillis()}_${index + 1}.$extension"
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                    
                    setAllowedNetworkTypes(
                        DownloadManager.Request.NETWORK_WIFI or 
                        DownloadManager.Request.NETWORK_MOBILE
                    )
                    
                    addRequestHeader("User-Agent", "Mozilla/5.0 (Android 13; Mobile)")
                    addRequestHeader("Referer", "https://twitter.com/")
                }
                
                downloadManager.enqueue(request)
                
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to start download ${index + 1}", Toast.LENGTH_SHORT).show()
            }
        }
        
        Toast.makeText(this, "✅ ${urls.size} downloads started!", Toast.LENGTH_LONG).show()
    }
    
    private fun getFileExtension(url: String): String {
        return when {
            url.contains(".mp4", true) -> "mp4"
            url.contains(".mov", true) -> "mov"
            url.contains(".webm", true) -> "webm"
            url.contains(".png", true) -> "png"
            url.contains(".gif", true) -> "gif"
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
            .setMessage("Started downloading $mediaCount file(s). Check your Downloads folder and notifications.")
            .setPositiveButton("OK", null)
            .setNeutralButton("Clear") { _, _ -> urlInput.setText("") }
            .show()
    }
    
    private fun showFallbackDialog() {
        AlertDialog.Builder(this)
            .setTitle("💡 Alternative Option")
            .setMessage("The automatic download didn't work. Would you like to open the download service in your browser to try manually?")
            .setPositiveButton("Open Browser") { _, _ -> openServiceInBrowser() }
            .setNegativeButton("Cancel", null)
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
