package app.glass.tresor.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.glass.tresor.App
import javax.crypto.Cipher

object Auth {
    private const val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun available(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /** Systemsperre (Fingerabdruck oder Geräte-PIN) – entsperrt einen Schlüssel im Hardware-Chip. */
    fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String?,
        cipher: Cipher,
        onSuccess: (Cipher) -> Unit,
        onFail: () -> Unit
    ) {
        App.suppressLock = true
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    App.suppressLock = false
                    val c = result.cryptoObject?.cipher
                    if (c != null) onSuccess(c) else onFail()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    App.suppressLock = false
                    onFail()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { if (subtitle != null) setSubtitle(subtitle) }
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        try {
            prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        } catch (e: Exception) {
            App.suppressLock = false
            onFail()
        }
    }
}
