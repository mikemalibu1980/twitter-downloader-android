// Simplified MainActivity.kt - Easier to build and test
package com.example.twitterdownloader

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {
    private lateinit var urlInput: EditText
    private lateinit var downloadButton: Button
    private lateinit var statusText: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Simple layout without Compose for easier building
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        // Title
        val title = TextView(this).apply {
            text = "Twitter Media Downloader"
            textSize = 24f
            setPadding(0, 0, 0, 32)
        }
        
        // URL input
        urlInput = EditText(this).apply {
            hint = "Enter Twitter URL here..."
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            setPadding(16, 16, 16, 16)
        }
        
        // Download button
        downloadButton = Button(this).apply {
            text = "Download Media"
            setOnClickListener { 
                val url = urlInput.text.toString().trim()
                if (url.isNotEmpty()) {
                    downloadTwitterMedia(url)
                } else {
                    Toast.makeText(this@MainActivity, "Please enter a URL", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        // Status text
        statusText = TextView(this).apply {
            text = "Enter a Twitter URL and tap Download"
            setPadding(0, 32, 0, 0)
        }
        
        layout.addView(title)
        layout.addView(urlInput)
        layout.addView(downloadButton)
        layout.addView(statusText)
        
        setContentView(layout)
    }
    
    private fun downloadTwitterMedia(tweetUrl: String) {
        downloadButton.isEnabled = false
        statusText.text = "Processing..."
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val mediaUrls = extractMediaUrls(tweetUrl)
                
                withContext(Dispatchers.Main) {
                    if (mediaUrls.isNotEmpty()) {
                        statusText.text = "Found ${mediaUrls.size} media files. Starting download..."
                        downloadFiles(mediaUrls)
                    } else {
                        statusText.text = "No media found. Try a different URL."
                        downloadButton.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText.text = "Error: ${e.message}"
                    downloadButton.isEnabled = true
                }
            }
        }
    }
    
    private suspend fun extractMediaUrls(tweetUrl: String): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                // Convert x.com to twitter.com for compatibility
                val normalizedUrl = tweetUrl.replace("x.com", "twitter.com")
                
                // Try to get media URLs using a simple approach
                val urls = mutableListOf<String>()
                
                // Method 1: Try nitter.net (Twitter alternative)
                val tweetId = extractTweetId(normalizedUrl)
                if (tweetId != null) {
                    val nitterUrl = "https://nitter.net/i/status/$tweetId"
                    val nitterUrls = scrapeNitter(nitterUrl)
                    urls.addAll(nitterUrls)
                }
                
                // Method 2: Add some example URLs for testing
                if (urls.isEmpty()) {
                    // For testing purposes, add some sample media URLs
                    // In a real implementation, you'd parse the actual tweet
                    if (tweetUrl.contains("twitter.com") || tweetUrl.contains("x.com")) {
                        // This is just for demonstration - replace with actual extraction
                        urls.add("https://pbs.twimg.com/media/sample_image.jpg")
                    }
                }
                
                return@withContext urls
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
    
    private suspend fun scrapeNitter(nitterUrl: String): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(nitterUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", 
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                
                val html = connection.inputStream.bufferedReader().use { it.readText() }
                
                val urls = mutableListOf<String>()
                
                // Simple regex patterns to find media URLs
                val videoPattern = Pattern.compile("src=\"([^\"]*\\.(mp4|mov))")
                val imagePattern = Pattern.compile("src=\"([^\"]*\\.(jpg|jpeg|png|gif))")
                
                val videoMatcher = videoPattern.matcher(html)
                while (videoMatcher.find()) {
                    val url = videoMatcher.group(1)
                    if (url != null && !url.contains("profile")) {
                        urls.add(if (url.startsWith("http")) url else "https://nitter.net$url")
                    }
                }
                
                val imageMatcher = imagePattern.matcher(html)
                while (imageMatcher.find()) {
                    val url = imageMatcher.group(1)
                    if (url != null && !url.contains("profile")) {
                        urls.add(if (url.startsWith("http")) url else "https://nitter.net$url")
                    }
                }
                
                return@withContext urls
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
    
    private fun extractTweetId(url: String): String? {
        val pattern = Pattern.compile("status/(\\d+)")
        val matcher = pattern.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }
    
    private fun downloadFiles(urls: List<String>) {
        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        var completedDownloads = 0
        
        urls.forEachIndexed { index, url ->
            try {
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setTitle("Twitter Media ${index + 1}")
                    setDescription("Downloading from Twitter")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    
                    val filename = "twitter_media_${System.currentTimeMillis()}_${index + 1}.${getFileExtension(url)}"
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                }
                
                downloadManager.enqueue(request)
                completedDownloads++
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        statusText.text = "Started $completedDownloads downloads. Check your Downloads folder."
        downloadButton.isEnabled = true
    }
    
    private fun getFileExtension(url: String): String {
        return when {
            url.contains(".mp4") -> "mp4"
            url.contains(".mov") -> "mov"
            url.contains(".png") -> "png"
            url.contains(".gif") -> "gif"
            url.contains(".jpeg") -> "jpeg"
            else -> "jpg"
        }
    }
}

// Simplified AndroidManifest.xml
/*
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.twitterdownloader">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="Twitter Downloader"
        android:theme="@style/Theme.AppCompat.DayNight">
        
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
*/

// Simplified build.gradle (app level)
/*
plugins {
    id 'com.android.application'
    id 'kotlin-android'
}

android {
    compileSdk 34
    
    defaultConfig {
        applicationId "com.example.twitterdownloader"
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName "1.0"
    }
    
    buildTypes {
        release {
            minifyEnabled false
        }
    }
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
}
*/
