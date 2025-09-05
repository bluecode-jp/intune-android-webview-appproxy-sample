package com.yaso202508appproxy.intunetestapp

import java.lang.Exception

interface Logger {
    fun info(msg: String)
    fun error(msg: String, exception: Exception? = null)
}