package com.yaso202508appproxy.intunetestapp.web

import android.content.Context

object TextAssetLoader {
    private val cache = mutableMapOf<String, String>()

    fun loadContent(context: Context, assetPath: String): String? {
        if (cache.containsKey(assetPath)) {
            return cache[assetPath]
        }

        val content = readAssetContent(context, assetPath)

        if (content != null) {
            cache[assetPath] = content
        }

        return content
    }

    private fun readAssetContent(context: Context, assetPath: String): String? {
        return try {
            context.assets.open(assetPath).bufferedReader().use { reader ->
                val content = reader.readText()
                content
            }
        } catch (exception: Exception) {
            null
        }
    }
}