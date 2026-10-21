package com.prism.gallery

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.video.VideoFrameDecoder
import com.prism.gallery.core.database.PrismDatabase
import com.prism.gallery.core.media.MediaRepository
import com.prism.gallery.core.security.VaultRepository
import com.prism.gallery.feature.settings.SettingsRepository

class PrismApplication : Application(), SingletonImageLoader.Factory {
    val database by lazy { PrismDatabase.open(this) }
    val media by lazy { MediaRepository(this) }
    val settings by lazy { SettingsRepository(this) }
    val vault by lazy { VaultRepository(this, database.dao()) }
    override fun newImageLoader(context: android.content.Context): ImageLoader = ImageLoader.Builder(context).components { add(VideoFrameDecoder.Factory()) }.build()
}
