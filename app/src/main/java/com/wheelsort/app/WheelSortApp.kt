package com.wheelsort.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.wheelsort.app.data.MediaStoreThumbnailFetcher

class WheelSortApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            add(MediaStoreThumbnailFetcher.Factory())
        }
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.30) // generous - the wheel keeps several photos decoded at once
                .build()
        }
        .crossfade(120)
        .allowRgb565(true) // photos are display-only here, half the memory per bitmap is worth it
        .build()
}
