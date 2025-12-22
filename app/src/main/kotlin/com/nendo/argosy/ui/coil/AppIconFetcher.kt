package com.nendo.argosy.ui.coil

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options

private const val TAG = "AppIconFetcher"
private const val ICON_SIZE = 192

data class AppIconData(val packageName: String)

class AppIconFetcher(
    private val data: AppIconData,
    private val packageManager: PackageManager
) : Fetcher {

    @Suppress("SwallowedException")
    override suspend fun fetch(): FetchResult? {
        return try {
            val appInfo = packageManager.getApplicationInfo(data.packageName, 0)
            val drawable = packageManager.getApplicationIcon(appInfo)
            val safeDrawable = ensureValidDrawable(drawable)
            DrawableResult(
                drawable = safeDrawable,
                isSampled = false,
                dataSource = DataSource.DISK
            )
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Package not found: ${data.packageName}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load icon for ${data.packageName}", e)
            null
        }
    }

    private fun ensureValidDrawable(drawable: Drawable): Drawable {
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: ICON_SIZE
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: ICON_SIZE

        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable
        }

        return try {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
            BitmapDrawable(null, bitmap)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to convert drawable to bitmap for ${data.packageName}", e)
            drawable
        }
    }

    class Factory(
        private val packageManager: PackageManager
    ) : Fetcher.Factory<AppIconData> {

        override fun create(
            data: AppIconData,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher = AppIconFetcher(data, packageManager)
    }
}
