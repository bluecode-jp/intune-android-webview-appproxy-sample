package com.yaso202508appproxy.intunetestapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.yaso202508appproxy.intunetestapp.auth.AuthResult
import com.yaso202508appproxy.intunetestapp.auth.AuthService
import com.yaso202508appproxy.intunetestapp.auth.CheckPermissionResult
import com.yaso202508appproxy.intunetestapp.ui0test.TestActivity
import com.yaso202508appproxy.intunetestapp.ui1auto.AutoLaunchActivity
import com.yaso202508appproxy.intunetestapp.web.WebViewWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var buttonUi0: Button
    private lateinit var buttonUi1: Button

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
        buttonUi0 = findViewById(R.id.buttonUi0)
        buttonUi0.setOnClickListener {
            val intent = Intent(this, TestActivity::class.java)
            startActivity(intent)
        }

        buttonUi1 = findViewById(R.id.buttonUi1)
        buttonUi1.setOnClickListener {
            val intent = Intent(this, AutoLaunchActivity::class.java)
            startActivity(intent)
        }
    }
}
