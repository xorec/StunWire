package com.xorec.stunwire.model.crypto

import android.content.Context
import android.graphics.Bitmap
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import android.util.DisplayMetrics
import android.view.WindowManager
import com.xorec.stunwire.StunWireApp
import com.xorec.stunwire.fourBytesToIntBigEndian
import com.xorec.stunwire.refreshMyIdentityKeyBitmap
import com.google.crypto.tink.subtle.Hkdf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.lang.Integer.min
import java.nio.ByteBuffer
import java.security.*
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

fun doesMyIdentityKeyPairExist(): Boolean {
    val ks = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }
    val entry = ks.getEntry("MyIdentityKeyPair", null) as KeyStore.PrivateKeyEntry?

    return entry != null
}

fun refreshMyIdentityKeyPair(context: Context) {
    val kpg: KeyPairGenerator = KeyPairGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_RSA,
        "AndroidKeyStore"
    )

    val parameterSpec: KeyGenParameterSpec = KeyGenParameterSpec.Builder(
        "MyIdentityKeyPair",
        KeyProperties.PURPOSE_SIGN
    )
        .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
        .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PSS)
        .run {
            build()
        }

    kpg.initialize(parameterSpec)
    kpg.generateKeyPair()

    refreshMyIdentityKeyBitmap(context, "identity_key_bitmap", generateMyIdentityPublicKeyBitmap())
}

fun generateMyEphemeralKeyPair(): KeyPair {
    val keyPairGenerator = KeyPairGenerator.getInstance(
        "ECDH", "SC"
    )
    keyPairGenerator.initialize(ECGenParameterSpec("secp256r1"))
    return keyPairGenerator.generateKeyPair()
}

fun loadMyIdentityKeyPair(): KeyStore.PrivateKeyEntry {
    val ks = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }
    return ks.getEntry("MyIdentityKeyPair", null) as KeyStore.PrivateKeyEntry
}

fun importPartnerIdentityCertificate(buffer: ByteArray) {
    val parameterSpec: KeyProtection = KeyProtection.Builder(
        KeyProperties.PURPOSE_VERIFY
    )
        .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
        .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PSS)
        .run {
            build()
        }

    val partnerCert = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(buffer))

    val ks = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    ks.setEntry("PartnerIdentityCertificate", KeyStore.TrustedCertificateEntry(partnerCert), parameterSpec)
}

fun getPartnerEphemeralPublicKey(buffer: ByteArray): PublicKey {
    return KeyFactory.getInstance("ECDH", "SC").generatePublic(X509EncodedKeySpec(buffer))
}

fun loadPartnerIdentityCertificate(): Certificate {
    val ks = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    val entry = ks.getEntry("PartnerIdentityCertificate", null) as KeyStore.TrustedCertificateEntry
    return entry.trustedCertificate
}

fun signMyEphemeralCertificate(myEphemeralCert: ByteArray): ByteArray {
    val myIdentityKeyPair = loadMyIdentityKeyPair()

    val signature: Signature = Signature.getInstance("SHA256withRSA/PSS")
    signature.initSign(myIdentityKeyPair.privateKey)
    signature.update(myEphemeralCert)

    /*if (android.os.Build.VERSION.SDK_INT == 29) {
        log("CORRUPTING MY SIGNATURE !")
        sign[7] = (0xFE).toByte()
    }*/

    return signature.sign()
}

fun verifyPartnerEphemeralCertificate(partnerEphemeralPublicKeyBytes: ByteArray, sign: ByteArray): Boolean {
    val partnerEphemeralPublicKey = getPartnerEphemeralPublicKey(partnerEphemeralPublicKeyBytes)

    val partnerIdentityCertificate = loadPartnerIdentityCertificate()

    val signature: Signature = Signature.getInstance("SHA256withRSA/PSS")
    signature.initVerify(partnerIdentityCertificate)
    signature.update(partnerEphemeralPublicKey.encoded)

    return signature.verify(sign)
}

fun performKeyAgreement(myEphemeralKeyPair: KeyPair, partnerEphemeralPublicKeyBytes: ByteArray): SecretKeySpec {
    val partnerEphemeralPublicKey = getPartnerEphemeralPublicKey(partnerEphemeralPublicKeyBytes)

    val keyAgreement: KeyAgreement = KeyAgreement.getInstance("ECDH", "SC")
    keyAgreement.init(myEphemeralKeyPair.private)
    keyAgreement.doPhase(partnerEphemeralPublicKey, true)
    val sharedSecret: ByteArray = keyAgreement.generateSecret()

    val salt = byteArrayOf()
    val info = ByteArrayOutputStream()
    info.write("ECDH secp256r1 AES-256-GCM-SIV".toByteArray())

    val key = Hkdf.computeHkdf("HMACSHA256", sharedSecret, salt, info.toByteArray(), 32)

    return SecretKeySpec(key, "AES")
}

fun generateBitmapFromIdentityPublicKey(keyByteArray: ByteArray): Bitmap {
    var intArray = IntArray(64)

    for (i in intArray.indices) {
        intArray[i] = fourBytesToIntBigEndian(keyByteArray, i * 4)
    }

    var totalIntArray = intArray

    val displayMetrics = DisplayMetrics()

    (StunWireApp.instance.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getMetrics(
        displayMetrics
    )
    val metric = min(
        min(displayMetrics.heightPixels, displayMetrics.widthPixels),
        (500 * displayMetrics.density).toInt()
    )
    val bm = Bitmap.createBitmap(totalIntArray, 8, 8, Bitmap.Config.ARGB_8888)

    return Bitmap.createScaledBitmap(bm, metric / 2, metric / 2, false)
}

@OptIn(ExperimentalStdlibApi::class)
fun getPartnerIdentityPublicKeyString(): String {
    return loadPartnerIdentityCertificate().publicKey.encoded.toHexString(HexFormat.Default)
}

fun generateMyIdentityPublicKeyBitmap(): Bitmap {
    return generateBitmapFromIdentityPublicKey(loadMyIdentityKeyPair().certificate.publicKey.encoded.slice(IntRange(33, 288)).toByteArray())
}

fun getPartnerPublicKeyBitmap(): Bitmap {
    return generateBitmapFromIdentityPublicKey(loadPartnerIdentityCertificate().publicKey.encoded.slice(IntRange(33, 288)).toByteArray())
}

fun encrypt(
    plainText: ByteArray,
    secretKey: SecretKeySpec,
): ByteArray {
    val iv = ByteArray(12)
    SecureRandom().nextBytes(iv)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    val parameterSpec = GCMParameterSpec(128, iv)
    cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)
    val cipherText = cipher.doFinal(plainText)
    val byteBuffer: ByteBuffer = ByteBuffer.allocate(iv.size + cipherText.size)
    byteBuffer.put(iv)
    byteBuffer.put(cipherText)
    return byteBuffer.array()
}

fun decrypt(
    cipherMessage: ByteArray,
    secretKey: SecretKeySpec
): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    val gcmIv: AlgorithmParameterSpec = GCMParameterSpec(128, cipherMessage, 0, 12)
    cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmIv)
    return cipher.doFinal(cipherMessage, 12, cipherMessage.size - 12)
}


