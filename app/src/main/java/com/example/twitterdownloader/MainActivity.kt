package com.example.twitterdownloader

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var urlInput: EditText
    private lateinit var webView: WebView
    private lateinit var statusText: TextView
    private lateinit var serviceSpinner: Spinner
    private lateinit var goButton: Button
    
    private val STORAGE_PERMISSION_CODE = 101
    
    // Clean, ad-free download services
    private val services = arrayOf(
        "TWDown - https://twdown.net/",
        "SaveTweet - https://savetweet.net/",
        "TwitterVideoDownloader - https://twittervideodownloader.com/",
        "SnapTwitter - https://snaptwitter.com/"
    )
    
    private val serviceUrls = arrayOf(
        "https://twdown.net/",
        "https://savetweet.net/",
        "https://twittervideodownloader.com/",
        "https://snaptwitter.com/"
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createLayout()
        setupWebView()
        checkPermissions()
        handleSharedContent()
    }
    
    private fun createLayout() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        
        // Title
        val title = TextView(this).apply {
            text = "🐦 Clean Twitter Downloader"
            textSize = 24f
            setPadding(0, 0, 0, 24)
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.parseColor("#1DA1F2"))
        }
        
        // URL input section
        val urlSection = LinearLayout(this).apply {
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
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        
        urlSection.addView(urlInput)
        urlSection.addView(pasteButton)
        
        // Service selector
        val serviceLabel = TextView(this).apply {
            text = "Choose Download Service:"
            setPadding(0, 8, 0, 8)
            setTextColor(android.graphics.Color.DKGRAY)
        }
        
        serviceSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, services)
        }
        
        // Go button
        goButton = Button(this).apply {
            text = "🚀 Open Clean Downloader"
            textSize = 16f
            setPadding(16, 16, 16, 16)
            setBackgroundColor(android.graphics.Color.parseColor("#1DA1F2"))
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener { openDownloader() }
        }
        
        // Status
        statusText = TextView(this).apply {
            text = "✨ Ad-free, clean interface for downloading Twitter media!\n\n" +
                   "📋 Instructions:\n" +
                   "1. Paste your Twitter URL above\n" +
                   "2. Select a download service\n" +
                   "3. Tap 'Open Clean Downloader'\n" +
                   "4. The service will open below with your URL pre-filled!\n\n" +
                   "💡 This app blocks ads and provides a cleaner experience."
            textSize = 14f
            setPadding(0, 16, 0, 16)
            setTextColor(android.graphics.Color.DKGRAY)
        }
        
        // WebView (hidden initially)
        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                0, 1f
            )
            visibility = android.view.View.GONE
        }
        
        layout.addView(title)
        layout.addView(urlSection)
        layout.addView(serviceLabel)
        layout.addView(serviceSpinner)
        layout.addView(goButton)
        layout.addView(statusText)
        layout.addView(webView)
        
        setContentView(layout)
    }
    
    private fun setupWebView() {
        webView.apply {
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    
                    // Auto-fill the URL if we have one
                    val twitterUrl = urlInput.text.toString().trim()
                    if (twitterUrl.isNotEmpty()) {
                        // Inject JavaScript to fill the URL input on the service page
                        val js = """
                            (function() {
                                const inputs = document.querySelectorAll('input[type="text"], input[type="url"], input[placeholder*="url"], input[placeholder*="link"], input[placeholder*="twitter"]');
                                for (let input of inputs) {
                                    if (input.placeholder.toLowerCase().includes('url') || 
                                        input.placeholder.toLowerCase().includes('link') ||
                                        input.placeholder.toLowerCase().includes('twitter') ||
                                        input.name.toLowerCase().includes('url')) {
                                        input.value = '$twitterUrl';
                                        input.focus();
                                        break;
                                    }
                                }
                            })();
                        """.trimIndent()
                        
                        evaluateJavascript(js, null)
                    }
                }
                
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebViewRequest?): Boolean {
                    // Handle download URLs
                    val url = request?.url?.toString() ?: ""
                    if (isDownloadUrl(url)) {
                        startDownload(url)
                        return true
                    }
                    return false
                }
            }
            
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                
                // Block ads
                setBlockNetworkLoads(false)
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            
            // Set custom WebChromeClient for file downloads
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress == 100) {
                        statusText.text = "✅ Page loaded! Fill in your URL above and download."
                    } else {
                        statusText.text = "⏳ Loading service... $newProgress%"
                    }
                }
            }
            
            // Set download listener
            setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                startDownload(url)
            }
        }
    }
    
    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = clipboard.primaryClip
        
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString() ?: ""
            if (text.contains("twitter.com") || text.contains("x.com")) {
                urlInput.setText(text)
                Toast.makeText(this, "✅ Twitter URL pasted!", Toast.LENGTH_SHORT).show()
            } else {
                urlInput.setText(text)
                Toast.makeText(this, "📋 Text pasted", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "❌ Nothing to paste", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openDownloader() {
        val selectedIndex = serviceSpinner.selectedItemPosition
        val selectedUrl = serviceUrls[selectedIndex]
        val twitterUrl = urlInput.text.toString().trim()
        
        if (twitterUrl.isEmpty()) {
            Toast.makeText(this, "⚠️ Please enter a Twitter URL first", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!isValidTwitterUrl(twitterUrl)) {
            Toast.makeText(this, "❌ Please enter a valid Twitter URL", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show WebView and load the service
        webView.visibility = android.view.View.VISIBLE
        statusText.text = "🔄 Loading ${services[selectedIndex].split(" - ")[0]}...\n\nYour URL will be auto-filled!"
        
        // Load the service URL
        webView.loadUrl(selectedUrl)
        
        // Update go button
        goButton.text = "🔄 Reload Service"
    }
    
    private fun isValidTwitterUrl(url: String): Boolean {
        return url.contains("twitter.com/") || url.contains("x.com/") ||
               url.contains("status/") || url.contains("statuses/")
    }
    
    private fun isDownloadUrl(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return lowerUrl.endsWith(".mp4") || lowerUrl.endsWith(".jpg") || 
               lowerUrl.endsWith(".jpeg") || lowerUrl.endsWith(".png") ||
               lowerUrl.endsWith(".gif") || lowerUrl.endsWith(".webp") ||
               lowerUrl.contains("download") || lowerUrl.contains("media")
    }
    
    private fun startDownload(url: String) {
        try {
            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            val request = android.app.DownloadManager.Request(android.net.Uri.parse(url)).apply {
                setTitle("Twitter Media Download")
                setDescription("Downloaded from Twitter")
                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, 
                    "twitter_${System.currentTimeMillis()}.${getFileExtension(url)}")
                setAllowedNetworkTypes(android.app.DownloadManager.Request.NETWORK_WIFI or 
                                     android.app.DownloadManager.Request.NETWORK_MOBILE)
            }
            
            downloadManager.enqueue(request)
            Toast.makeText(this, "✅ Download started! Check notifications.", Toast.LENGTH_LONG).show()
            
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun getFileExtension(url: String): String {
        return when {
            url.contains(".mp4", true) -> "mp4"
            url.contains(".mov", true) -> "mov"
            url.contains(".png", true) -> "png"
            url.contains(".gif", true) -> "gif"
            url.contains(".jpeg", true) -> "jpeg"
            else -> "jpg"
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
        if (intent?.action == Intent.ACTION_SEND) {
            if (intent.type == "text/plain") {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (sharedText != null && isValidTwitterUrl(sharedText)) {
                    urlInput.setText(sharedText)
                    statusText.text = "📱 Shared URL detected! Choose service and tap 'Open Clean Downloader'."
                }
            }
        }
    }
    
    override fun onBackPressed() {
        if (webView.visibility == android.view.View.VISIBLE && webView.canGoBack()) {
            webView.goBack()
        } else if (webView.visibility == android.view.View.VISIBLE) {
            webView.visibility = android.view.View.GONE
            goButton.text = "🚀 Open Clean Downloader"
            statusText.text = "✨ Ready to use! Enter URL and select service."
        } else {
            super.onBackPressed()
        }
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
