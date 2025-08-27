package com.yaso202508appproxy.intunetestapp

import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    private lateinit var btnLoad: Button
    private lateinit var btnLog: Button
    private lateinit var webView: WebView

    private val logHistory = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
    }

    private fun initViews() {
        webView = findViewById(R.id.webView)
        webView.apply {
            webViewClient = WebViewClient()

            settings.apply {
                javaScriptEnabled = true
            }
        }

        btnLoad = findViewById(R.id.btnLoad)
        btnLoad.setOnClickListener {
            loadWebSite()
        }

        btnLog = findViewById(R.id.btnLog)
        btnLog.setOnClickListener {
            logInfo("Log", "Test")
            showLog()
        }
    }

    private fun logInfo(tag: String, msg: String) {
        Log.d(tag, msg)
        logHistory.appendLine("[${tag}] ${msg}")
    }

    private fun showLog() {
        AlertDialog.Builder(this)
            .setTitle("Log")
            .setMessage(logHistory.toString())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun loadWebSite() {
        webView.loadUrl("https://testwebsite-blucodeinc.msappproxy.net")
    }
}