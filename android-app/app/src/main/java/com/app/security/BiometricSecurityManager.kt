package com.app.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

enum class BiometricAvailability {
    AVAILABLE,
    NOT_ENROLLED,
    HARDWARE_UNAVAILABLE,
    UNSUPPORTED
}

/**
 * Hardware-backed Biometric Security Manager
 * Uses AndroidKeyStore with hardware-backed AES-256-GCM cipher
 * bound to BiometricPrompt.CryptoObject for silicon-level verification.
 */
@Singleton
class BiometricSecurityManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "BiometricSecurity"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "budgi_hardware_biometric_key"
        private const val TRANSFORMATION = "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_GCM}/${KeyProperties.ENCRYPTION_PADDING_NONE}"
        private val TEST_PAYLOAD = "budgi_hardware_auth_token".toByteArray(Charsets.UTF_8)
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun checkBiometricAvailability(): BiometricAvailability {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.HARDWARE_UNAVAILABLE
            else -> BiometricAvailability.UNSUPPORTED
        }
    }

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val builder = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setInvalidatedByBiometricEnrollment(true)
            }

            keyGenerator.init(builder.build())
            return keyGenerator.generateKey()
        }
        return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    /**
     * Initializes hardware Cipher and wraps it in a BiometricPrompt.CryptoObject
     * Returns null if biometric hardware is not available or if the key was invalidated.
     */
    fun createCryptoObject(): BiometricPrompt.CryptoObject? {
        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            BiometricPrompt.CryptoObject(cipher)
        } catch (e: KeyPermanentlyInvalidatedException) {
            Log.w(TAG, "Biometric key invalidated by new enrollment. Regenerating...", e)
            deleteKey()
            try {
                val newKey = getOrCreateSecretKey()
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, newKey)
                BiometricPrompt.CryptoObject(cipher)
            } catch (inner: Exception) {
                Log.e(TAG, "Failed to create crypto object after key regeneration", inner)
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create hardware BiometricPrompt.CryptoObject", e)
            null
        }
    }

    /**
     * Verifies that the hardware Cipher was successfully unlocked by the Secure Element / TEE
     */
    fun verifyAuthentication(cryptoObject: BiometricPrompt.CryptoObject?): Boolean {
        val cipher = cryptoObject?.cipher ?: return false
        return try {
            val encryptedBytes = cipher.doFinal(TEST_PAYLOAD)
            encryptedBytes.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Hardware cryptographic validation failed after authentication", e)
            false
        }
    }

    fun deleteKey() {
        try {
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete biometric key entry", e)
        }
    }
}
