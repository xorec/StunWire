package com.example.stunwire

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import android.util.TypedValue
import androidx.core.content.FileProvider
import com.example.stunwire.model.networking.NetworkAddress
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.*
import kotlin.experimental.xor

fun log(message: String) {
    Log.d("STUNWIRE-APP-DEBUG", message)
}

fun getRandomString(length: Int) : String {
    val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
    return (1..length)
        .map { allowedChars.random() }
        .joinToString("")
}

fun extractMappedAddress(buffer: ByteArray): NetworkAddress {
    var shouldXOR = true

    val XOR_MAPPED_ADDRESS = 0x0020
    val MAPPED_ADDRESS = 0x0001

    var mappedAddressOffset = findAttributeOffset(buffer, XOR_MAPPED_ADDRESS)

    if (mappedAddressOffset == -1) {
        mappedAddressOffset = findAttributeOffset(buffer, MAPPED_ADDRESS)
        shouldXOR = false
    }

    val address = ByteArray(6)

    address[0] = buffer[mappedAddressOffset]
    address[1] = buffer[mappedAddressOffset + 1]
    address[2] = buffer[mappedAddressOffset + 2]
    address[3] = buffer[mappedAddressOffset + 3]
    address[4] = buffer[mappedAddressOffset + 4]
    address[5] = buffer[mappedAddressOffset + 5]

    address[0] = if (shouldXOR) buffer[mappedAddressOffset].xor(0x21) else buffer[mappedAddressOffset]
    address[1] = if (shouldXOR) buffer[mappedAddressOffset + 1].xor(0x12) else buffer[mappedAddressOffset + 1]
    address[2] = if (shouldXOR) buffer[mappedAddressOffset + 2].xor(0x21) else buffer[mappedAddressOffset + 2]
    address[3] = if (shouldXOR) buffer[mappedAddressOffset + 3].xor(0x12) else buffer[mappedAddressOffset + 3]
    address[4] = if (shouldXOR) buffer[mappedAddressOffset + 4].xor(0xA4.toByte()) else buffer[mappedAddressOffset + 4]
    address[5] = if (shouldXOR) buffer[mappedAddressOffset + 5].xor(0x42) else buffer[mappedAddressOffset + 5]

    val port: Int = twoBytesToIntBigEndian(address, 0)
    val addressBytes: ByteArray = byteArrayOf(address[2], address[3], address[4], address[5])
    val inetAddress = InetAddress.getByAddress(addressBytes)

    return NetworkAddress(inetAddress, port)
}

fun findAttributeOffset(buffer: ByteArray, requestedAttribute: Int): Int {
    var offset = 20

    var attribute: Int
    var length: Int

    while (offset < buffer.size) {
        attribute = twoBytesToIntBigEndian(buffer, offset)
        offset += 2
        length = twoBytesToIntBigEndian(buffer, offset)
        offset += 2

        if (attribute != requestedAttribute) {
            offset += length
            continue
        } else {
            return (offset + 2)
        }
    }

    return -1
}

fun twoBytesToIntBigEndian(buffer: ByteArray, offset: Int): Int {
    val bytes = buffer.slice(IntRange(offset, offset + 1)).toByteArray()
    var result = 0
    result = result or ((bytes[0].toInt() shl 8) and 0x0000FF00)
    result = result or (bytes[1].toInt() and 0x000000FF)
    result = result and 0x0000FFFF

    return result
}

fun fourBytesToIntBigEndian(buffer: ByteArray, offset: Int): Int {
    val bytes = buffer.slice(IntRange(offset, offset + 3)).toByteArray()
    var result = 0
    result = result or ((bytes[0].toInt() shl 24))
    result = result or ((bytes[1].toInt() shl 16) and 0x00FF0000)
    result = result or ((bytes[2].toInt() shl 8) and 0x0000FF00)
    result = result or ((bytes[3].toInt() shl 0) and 0x000000FF)

    return result
}

fun intToBytes(data: Int): ByteArray {
    return byteArrayOf((data shr 24).toByte(), (data shr 16).toByte(), (data shr 8).toByte(), (data).toByte())
}

fun eightBytesToLongBigEndian(buffer: ByteArray, offset: Int): Long {
    val bytes = buffer.slice(IntRange(offset, offset + 7)).toByteArray()

    var result = 0L
    result = result or ((bytes[0].toLong() shl 56))
    result = result or ((bytes[1].toLong() shl 48) and 0x00FF000000000000)
    result = result or ((bytes[2].toLong() shl 40) and 0x0000FF0000000000)
    result = result or ((bytes[3].toLong() shl 32) and 0x000000FF00000000)
    result = result or ((bytes[4].toLong() shl 24) and 0x00000000FF000000)
    result = result or ((bytes[5].toLong() shl 16) and 0x0000000000FF0000)
    result = result or ((bytes[6].toLong() shl 8) and 0x0000000000000FF00)
    result = result or ((bytes[7].toLong() shl 0) and 0x000000000000000FF)

    return result
}

fun longToBytes(data: Long): ByteArray {
    return byteArrayOf(
        (data shr 56).toByte(),
        (data shr 48).toByte(),
        (data shr 40).toByte(),
        (data shr 32).toByte(),
        (data shr 24).toByte(),
        (data shr 16).toByte(),
        (data shr 8).toByte(),
        (data).toByte())
}

fun twoBytesToBufferBigEndian(data: Int): ByteArray {
    return byteArrayOf((data shr 8).toByte(),
        (data).toByte())
}

fun refreshMyIdentityKeyBitmap(context: Context, filename: String, bitmap: Bitmap) {
    val imagesFolder = File(context.filesDir, "images")
    imagesFolder.mkdir()

    val file = File(imagesFolder, filename)
    val fileOutputStream = FileOutputStream(file)

    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
    fileOutputStream.flush()
    fileOutputStream.close()
}

fun openBitmap(filename: String): Bitmap {
    return BitmapFactory.decodeFile(filename)
}

fun getUriByFilename(context: Context, filename: String): Uri? {
    val file = File(context.filesDir.absolutePath, filename)
    return FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", file)
}

fun createSessionDatabaseName(): String {
    val cal: Calendar = Calendar.getInstance()
    val dateFormat = SimpleDateFormat("dd MMMM y HH:mm:ss")
    return dateFormat.format(cal.time)
}

fun rotateImage(source: Bitmap, angle: Int): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(angle.toFloat())
    return Bitmap.createBitmap(
        source, 0, 0, source.width, source.height,
        matrix, true
    )
}

fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
    val imageOutputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, imageOutputStream)
    return imageOutputStream.toByteArray()
}

fun getColorFromTheme(context: Context, resId: Int): Int {
    val typedValue = TypedValue()
    context.theme.resolveAttribute(resId, typedValue, true)
    return typedValue.data
}