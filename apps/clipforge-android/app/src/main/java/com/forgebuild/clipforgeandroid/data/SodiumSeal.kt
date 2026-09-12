package com.forgebuild.clipforgeandroid.data

import android.util.Base64
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.Box

/**
 * GitHub Actions secret sealer (bot parity: bot/src/crypto.js sealForGitHub).
 *
 * The Zernio API key is stored as the repo's sealed Actions secret
 * ZERNIO_API_KEY. GitHub requires a genuine libsodium sealed box
 * (crypto_box_seal) over the repo's Actions public key — anything else is
 * undecryptable by the pipeline. We use lazysodium-android (bundled libsodium
 * native .so), which implements the real construction; no hand-rolled crypto.
 */
object SodiumSeal {

    @Volatile
    private var lazySodium: LazySodiumAndroid? = null

    private fun sodium(): LazySodiumAndroid =
        lazySodium ?: synchronized(this) {
            lazySodium ?: LazySodiumAndroid(SodiumAndroid()).also { lazySodium = it }
        }

    /**
     * Seal [plaintext] (UTF-8) for the repo's Actions public key.
     * [recipientPublicKeyBase64] is the base64 X25519 public key from
     * GET /repos/{owner}/{repo}/actions/secrets/public-key.
     * Returns base64(sealed_bytes) ready for the secret PUT body.
     */
    fun sealToBase64(plaintext: String, recipientPublicKeyBase64: String): String {
        val pk = Base64.decode(recipientPublicKeyBase64, Base64.DEFAULT)
        require(pk.size == Box.PUBLICKEYBYTES) { "GitHub Actions public key must be ${Box.PUBLICKEYBYTES} bytes" }
        val msg = plaintext.toByteArray(Charsets.UTF_8)
        val sealed = ByteArray(Box.SEALBYTES + msg.size)
        val ok = sodium().cryptoBoxSeal(sealed, msg, msg.size.toLong(), pk)
        require(ok) { "libsodium crypto_box_seal failed" }
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }
}
