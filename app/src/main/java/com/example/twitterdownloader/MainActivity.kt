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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {
    private lateinit var urlInput: EditText
    private lateinit var analyzeButton: Button
    private lateinit var downloadButton: Button
    private lateinit var statusText: TextView
    private lateinit var urlsFoundText: TextView
    private lateinit var progressBar: ProgressBar
    
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
            text = "🐦 Debug: URL Finder"
            textSize = 24f
            setPadding(0, 0, 0, 24)
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.parseColor("#1DA1F2"))
        }
        
        // URL input with paste button
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
        
        // Analyze button
        analyzeButton = Button(this).apply {
            text = "🔍 Find Media URLs"
            textSize = 16f
            setBackgroundColor(android.graphics.Color.parseColor("#1DA1F2"))
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener { 
                val url = urlInput.text.toString().trim()
                if (url.isNotEmpty()) {
                    analyzeTweet(url)
                } else {
                    Toast.makeText(this@MainActivity, "Please enter a URL first", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        // Download button (hidden initially)
        downloadButton = Button(this).apply {
            text = "📥 Test Download"
            textSize = 16f
            setBackgroundColor(android.graphics.Color.parseColor("#28a745"))
            setTextColor(android.graphics.Color.WHITE)
            visibility = android.view.View.GONE
            setOnClickListener { downloadFoundUrls() }
        }
        
        // Status text
        statusText = TextView(this).apply {
            text = "🔍 DEBUG MODE\n\n" +
                   "This will show you exactly what URLs are found so you can:\n" +
                   "1. Copy URLs and test in browser\n" +
                   "2. See if it's a detection or download issue\n\n" +
                   "Paste a Twitter URL above and tap 'Find Media URLs'"
            setPadding(16, 16, 16, 16)
            textSize = 14f
        }
        
        // URLs display (hidden initially)
        urlsFoundText = TextView(this).apply {
            text = ""
            textSize = 12f
            setPadding(16, 16, 16, 16)
            setTextColor(android.graphics.Color.BLACK)
            background = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.edit_text)
            visibility = android.view.View.GONE
            setTextIsSelectable(true) // Important: allows copying text
        }
        
        layout.addView(title)
        layout.addView(inputLayout)
        layout.addView(analyzeButton)
        layout.addView(progressBar)
        layout.addView(downloadButton)
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
            } else {
                Toast.makeText(this, "Nothing to paste", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Paste failed: ${e.message}", Toast.LENGTH_SHORT).show()
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
                statusText.text = "📱 Shared URL detected! Tap 'Find Media URLs' to analyze."
            }
        }
    }
    
    private fun analyzeTweet(tweetUrl: String) {
        analyzeButton.isEnabled = false
        downloadButton.visibility = android.view.View.GONE
        urlsFoundText.visibility = android.view.View.GONE
        progressBar.visibility = android.view.View.VISIBLE
        
        statusText.text = "🔍 Analyzing: $tweetUrl\n\nStep 1: Extracting tweet ID..."
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val allUrls = mutableSetOf<String>()
                
                // Update status
                withContext(Dispatchers.Main) {
                    statusText.text = "🔍 Step 2: Trying different URL patterns..."
                }
                
                // Method 1: Extract tweet ID and try direct patterns
                val tweetId = extractTweetId(tweetUrl)
                if (tweetId != null) {
                    val directUrls = generateDirectUrls(tweetId)
                    allUrls.addAll(directUrls)
                }
                
                // Method 2: Try Nitter
                withContext(Dispatchers.Main) {
                    statusText.text = "🔍 Step 3: Trying Nitter instances..."
                }
                
                val nitterUrls = tryNitter(tweetUrl)
                allUrls.addAll(nitterUrls)
                
                // Method 3: Try to scrape original URL
                withContext(Dispatchers.Main) {
                    statusText.text = "🔍 Step 4: Checking original URL..."
                }
                
                val originalUrls = tryOriginalUrl(tweetUrl)
                allUrls.addAll(originalUrls)
                
                // Test which URLs actually work
                withContext(Dispatchers.Main) {
                    statusText.text = "🔍 Step 5: Testing found URLs..."
                }
                
                val workingUrls = testUrls(allUrls.toList())
                
                // Display results
                withContext(Dispatchers.Main) {
                    progressBar.visibility = android.view.View.GONE
                    displayResults(allUrls.toList(), workingUrls, tweetId)
                    analyzeButton.isEnabled = true
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = android.view.View.GONE
                    statusText.text = "❌ Analysis failed!\n\nError: ${e.message}\n\n" +
                                    "This could mean:\n" +
                                    "• No internet connection\n" +
                                    "• Tweet is private/deleted\n" +
                                    "• Twitter is blocking requests"
                    analyzeButton.isEnabled = true
                }
            }
        }
    }
    
    private fun extractTweetId(url: String): String? {
        val patterns = listOf(
            "status/(\\d+)",
            "statuses/(\\d+)"
        )
        
        patterns.forEach { patternStr ->
            val pattern = Pattern.compile(patternStr)
            val matcher = pattern.matcher(url)
            if (matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }
    
    private fun generateDirectUrls(tweetId: String): List<String> {
        return listOf(
            // Common image patterns
            "https://pbs.twimg.com/media/${tweetId}?format=jpg&name=large",
            "https://pbs.twimg.com/media/${tweetId}?format=png&name=large", 
            "https://pbs.twimg.com/media/${tweetId}?format=jpg&name=orig",
            "https://pbs.twimg.com/media/${tweetId}?format=png&name=orig",
            
            // Common video patterns
            "https://video.twimg.com/ext_tw_video/${tweetId}/pu/vid/1280x720/video.mp4",
            "https://video.twimg.com/ext_tw_video/${tweetId}/pu/vid/720x720/video.mp4",
            "https://video.twimg.com/ext_tw_video/${tweetId}/pu/vid/640x640/video.mp4",
            "https://video.twimg.com/amplify_video/${tweetId}/vid/1280x720/video.mp4",
            
            // Alternative patterns
            "https://pbs.twimg.com/tweet_video/${tweetId}.mp4",
            "https://pbs.twimg.com/media/${tweetId}.jpg",
            "https://pbs.twimg.com/media/${tweetId}.png"
        )
    }
    
    private suspend fun tryNitter(tweetUrl: String): List<String> {
        return withContext(Dispatchers.IO) {
            val urls = mutableSetOf<String>()
            val tweetId = extractTweetId(tweetUrl) ?: return@withContext emptyList()
            
            val nitterInstances = listOf("nitter.net", "nitter.it")
            
            for (instance in nitterInstances) {
                try {
                    val nitterUrl = "https://$instance/i/status/$tweetId"
                    val connection = URL(nitterUrl).openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 8000
                    connection.readTimeout = 8000
                    
                    if (connection.responseCode == 200) {
                        val html = connection.inputStream.bufferedReader().use { it.readText() }
                        val foundUrls = extractMediaFromHtml(html)
                        urls.addAll(foundUrls)
                        if (urls.isNotEmpty()) break // Stop if we found something
                    }
                } catch (e: Exception) {
                    // Try next instance
                }
            }
            
            return@withContext urls.toList()
        }
    }
    
    private suspend fun tryOriginalUrl(tweetUrl: String): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(tweetUrl).openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android 13; Mobile)")
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                
                if (connection.responseCode == 200) {
                    val html = connection.inputStream.bufferedReader().use { it.readText() }
                    return@withContext extractMediaFromHtml(html)
                }
            } catch (e: Exception) {
                // Original URL failed
            }
            
            return@withContext emptyList()
        }
    }
    
    private fun extractMediaFromHtml(html: String): List<String> {
        val urls = mutableSetOf<String>()
        
        // Simple patterns to find media URLs
        val patterns = listOf(
            "https://[^\\s\"']*pbs\\.twimg\\.com/media/[^\\s\"'?]*\\.(jpg|jpeg|png|gif|webp)",
            "https://[^\\s\"']*video\\.twimg\\.com/[^\\s\"'?]*\\.(mp4|mov)",
            "src=\"([^\"]*\\.(mp4|jpg|jpeg|png|gif|webp)[^\"]*)\""
        )
        
        patterns.forEach { patternStr ->
            val pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(html)
            while (matcher.find()) {
                val url = matcher.group(1) ?: matcher.group(0)
                if (url != null && isValidMediaUrl(url)) {
                    urls.add(url)
                }
            }
        }
        
        return urls.toList()
    }
    
    private suspend fun testUrls(urls: List<String>): List<String> {
        return withContext(Dispatchers.IO) {
            val workingUrls = mutableListOf<String>()
            
            urls.forEach { url ->
                try {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.requestMethod = "HEAD"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    
                    if (connection.responseCode in 200..299) {
                        workingUrls.add(url)
                    }
                } catch (e: Exception) {
                    // URL not accessible
                }
            }
            
            return@withContext workingUrls
        }
    }
    
    private fun displayResults(allUrls: List<String>, workingUrls: List<String>, tweetId: String?) {
        foundUrls = workingUrls // Store for downloading
        
        val resultText = StringBuilder()
        resultText.append("📊 ANALYSIS COMPLETE\n")
        resultText.append("Tweet ID: ${tweetId ?: "Not found"}\n")
        resultText.append("Total URLs found: ${allUrls.size}\n")
        resultText.append("Working URLs: ${workingUrls.size}\n\n")
        
        if (allUrls.isEmpty()) {
            statusText.text = "❌ No media URLs found!\n\n" +
                             "This could mean:\n" +
                             "• Tweet has no images/videos\n" +
                             "• Tweet is private or deleted\n" +
                             "• Twitter is blocking our requests\n\n" +
                             "Try a different public tweet with media."
            return
        }
        
        resultText.append("🔗 ALL FOUND URLS:\n")
        resultText.append("(Long press any URL to copy and test in browser)\n\n")
        
        allUrls.forEachIndexed { index, url ->
            val status = if (workingUrls.contains(url)) "✅ WORKS" else "❌ FAILS"
            resultText.append("${index + 1}. $status\n")
            resultText.append("$url\n\n")
        }
        
        if (workingUrls.isNotEmpty()) {
            resultText.append("💡 NEXT STEPS:\n")
            resultText.append("1. Copy a ✅ URL and test in browser\n")
            resultText.append("2. If URL works in browser, try 'Test Download'\n")
            resultText.append("3. If download fails, it's a permissions issue")
            
            statusText.text = "✅ SUCCESS! Found ${workingUrls.size} working URLs!\n\n" +
                             "The URLs are shown below. Try copying one to your browser to verify it works, then use 'Test Download'."
            downloadButton.visibility = android.view.View.VISIBLE
        } else {
            resultText.append("⚠️ No URLs responded to connection test.\n")
            resultText.append("But try copying them anyway - some might still work!")
            
            statusText.text = "⚠️ Found ${allUrls.size} URLs but none passed connection test.\n\n" +
                             "Copy the URLs below and try them in your browser anyway - they might still work!"
        }
        
        urlsFoundText.text = resultText.toString()
        urlsFoundText.visibility = android.view.View.VISIBLE
    }
    
    private fun downloadFoundUrls() {
        if (foundUrls.isEmpty()) {
            Toast.makeText(this, "No working URLs to download", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            
            foundUrls.forEachIndexed { index, url ->
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setTitle("Debug Test ${index + 1}")
                    setDescription("Testing: ${getFileName(url)}")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    
                    val filename = "test_${System.currentTimeMillis()}_${index + 1}.${getFileExtension(url)}"
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                    
                    setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                }
                
                downloadManager.enqueue(request)
            }
            
            Toast.makeText(this, "Started ${foundUrls.size} test downloads! Check Downloads folder.", Toast.LENGTH_LONG).show()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun isValidMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        return (lower.contains(".mp4") || lower.contains(".jpg") || lower.contains(".jpeg") ||
                lower.contains(".png") || lower.contains(".gif") || lower.contains(".webp") ||
                lower.contains("pbs.twimg.com") || lower.contains("video.twimg.com")) &&
               !lower.contains("profile") && !lower.contains("avatar")
    }
    
    private fun getFileName(url: String): String {
        return url.substringAfterLast("/").substringBefore("?").ifEmpty { "media" }
    }
    
    private fun getFileExtension(url: String): String {
        return when {
            url.contains(".mp4", true) -> "mp4"
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
                Toast.makeText(this, "⚠️ Storage permission denied - downloads will fail", Toast.LENGTH_LONG).show()
            }
        }
    }
}
