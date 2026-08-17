package com.wheelsort.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache

class WheelSortApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.30) // generous - the wheel keeps several photos decoded at once
                .build()
        }
        .crossfade(150)
        .allowRgb565(true) // photos are display-only here, half the memory per bitmap is worth it
        .build()
}
