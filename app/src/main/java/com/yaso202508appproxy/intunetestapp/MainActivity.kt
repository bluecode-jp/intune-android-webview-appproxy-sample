package com.yaso202508appproxy.intunetestapp

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    private lateinit var btnLoad: Button
    private lateinit var webView: WebView

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
    }

    private fun loadWebSite() {
        webView.loadUrl("https://testwebsite-blucodeinc.msappproxy.net")
    }
}