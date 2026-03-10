package com.example.gallaryapp

import android.graphics.Bitmap
import android.util.LruCache

object ThumbnailCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun getBitmap(key: String): Bitmap? {
        return memoryCache.get(key)
    }

    fun addBitmap(key: String, bitmap: Bitmap) {
        if (getBitmap(key) == null) {
            memoryCache.put(key, bitmap)
        }
    }

    //Method for removing
    fun removeBitmap(key: String) {
        memoryCache.remove(key)
    }
}