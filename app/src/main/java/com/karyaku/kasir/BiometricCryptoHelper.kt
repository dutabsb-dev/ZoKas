package com.karyaku.kasir

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object BiometricCryptoHelper {

    private const val TAG = "ZOAKS_BIOMETRIC"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_SIZE = 256
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    // Fungsi untuk mengecek apakah perangkat support biometrik
    fun isBiometricSupported(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val result = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        Log.d(TAG, "Cek Sensor Support: $result")
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    // Fungsi untuk membuat kunci baru (saat pendaftaran)
    fun createKey(alias: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val builder = KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE)
                    .setUserAuthenticationRequired(true)
                    .setInvalidatedByBiometricEnrollment(true)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
                } else {
                    @Suppress("DEPRECATION")
                    builder.setUserAuthenticationValidityDurationSeconds(-1)
                }

                keyGenerator.init(builder.build())
                keyGenerator.generateKey()
                Log.d(TAG, "Kunci berhasil dibuat: $alias")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Gagal membuat kunci: ${e.message}", e)
                return false
            }
        }
        return false
    }

    private fun getKey(alias: String): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            keyStore.getKey(alias, null) as? SecretKey
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengambil kunci: ${e.message}", e)
            null
        }
    }

    fun deleteKey(alias: String) {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
                Log.d(TAG, "Kunci dihapus: $alias")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menghapus kunci: ${e.message}", e)
        }
    }

    fun getCipherForEncryption(alias: String): Cipher? {
        return try {
            val key = getKey(alias) ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            cipher
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menyiapkan Cipher Enkripsi: ${e.message}", e)
            null
        }
    }

    fun getCipherForDecryption(alias: String, iv: ByteArray): Cipher? {
        return try {
            val key = getKey(alias) ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            cipher
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menyiapkan Cipher Dekripsi: ${e.message}", e)
            null
        }
    }

    fun showBiometricPrompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButtonText: String,
        cipher: Cipher,
        onSuccess: (BiometricPrompt.AuthenticationResult) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val executor = ContextCompat.getMainExecutor(activity)
            val biometricPrompt = BiometricPrompt(activity, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        Log.d(TAG, "Autentikasi Biometrik SUKSES.")
                        onSuccess(result)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        Log.e(TAG, "Error Autentikasi Biometrik: [$errorCode] $errString")
                        if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                            onError(errString.toString())
                        }
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        Log.w(TAG, "Pemindaian Biometrik GAGAL (Wajah/Jari tidak cocok).")
                    }
                })

            val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                promptInfoBuilder.setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
            } else {
                promptInfoBuilder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                promptInfoBuilder.setNegativeButtonText(negativeButtonText)
            }

            val promptInfo = promptInfoBuilder.build()

            Log.d(TAG, "Menampilkan prompt biometrik...")
            biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
            
        } catch (e: Exception) {
            // INI ADALAH PENANGKAP ERROR UTAMA JIKA TERJADI CRASH SINKRON
            Log.e(TAG, "CRASH PREVENTED di authenticate(): ${e.message}", e)
            onError("Error Sistem: ${e.message}")
        }
    }
}