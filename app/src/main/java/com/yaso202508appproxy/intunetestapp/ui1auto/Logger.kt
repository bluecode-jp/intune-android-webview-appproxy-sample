package com.yaso202508appproxy.intunetestapp.ui1auto

import android.util.Log
import com.yaso202508appproxy.intunetestapp.AppLogger
import java.lang.Exception

fun createLogger(tag: String): AppLogger = object : AppLogger {
    override fun info(msg: String) {
        Log.d(tag, msg)
    }

    override fun error(msg: String, exception: Exception?) {
        Log.e(tag, msg, exception)
    }
}