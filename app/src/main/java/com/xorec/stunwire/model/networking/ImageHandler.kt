package com.xorec.stunwire.model.networking

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.xorec.stunwire.bitmapToByteArray

class ImageHandler(private val width: Int, private val height: Int, private val density: Float) {
    fun scaleBitmapToFitSessionScreen(initialData: ByteArray): ByteArray {
        val bitmap = BitmapFactory.decodeByteArray(initialData, 0, initialData.size)

        val screenHeight = Integer.max(width, height)
        val screenWidth = Math.min(width, height)
        val requiredHeight = screenHeight * 0.45
        val requiredWidth = screenWidth * 0.8 - density * 8 // TODO: why 8?
        val heightScaleFactor: Double = bitmap.height / requiredHeight
        val widthScaleFactor: Double = bitmap.width / requiredWidth
        val bitmapScaleFactor = java.lang.Double.max(heightScaleFactor, widthScaleFactor)
        val bitmapFinalHeight = (bitmap.height / bitmapScaleFactor).toInt()
        val bitmapFinalWidth = (bitmap.width / bitmapScaleFactor).toInt()

        return bitmapToByteArray(Bitmap.createScaledBitmap(bitmap, bitmapFinalWidth, bitmapFinalHeight, false))
    }
}