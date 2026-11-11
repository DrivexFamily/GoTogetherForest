package app.familyjourney.core.account

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 문자열 암호화기. 아이 별명을 저장하기 전에 거친다(decisions.md #11).
 * 단위 테스트에서는 가짜 구현을 넣는다(JVM에는 Android Keystore가 없다).
 */
interface TextCipher {
    /** 평문 → 저장용 문자열 */
    fun encrypt(plain: String): String

    /** 저장용 문자열 → 평문. 풀 수 없으면 예외 */
    fun decrypt(stored: String): String
}

/**
 * Android Keystore의 AES-256-GCM 키로 암호화한다.
 *
 * - 키는 Keystore 안에서 만들어지고 밖으로 나오지 않는다. 코드·저장소·APK에 키가 없다.
 * - 매번 새 IV(12바이트, Keystore가 만듦)를 쓰고, 인증 태그 128비트로 변조를 막는다.
 * - 저장 형식: `"g1:" + Base64(IV) + ":" + Base64(암호문+태그)`.
 * - 사용자 인증(잠금 화면)을 요구하지 않는다(차 화면에는 잠금이 없을 수 있고, 키가 무효화되면 별명을 잃는다).
 * - 앱 백업은 꺼져 있다(`android:allowBackup="false"`). 키는 백업되지 않으므로, 켜면 복원된 별명을 풀 수 없다.
 */
class KeystoreTextCipher(private val keyAlias: String = DEFAULT_ALIAS) : TextCipher {

    private val lock = Any()

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val b64 = Base64.getEncoder()
        return PREFIX + b64.encodeToString(cipher.iv) + ":" + b64.encodeToString(sealed)
    }

    override fun decrypt(stored: String): String {
        require(stored.startsWith(PREFIX)) { "알 수 없는 형식" }
        val parts = stored.removePrefix(PREFIX).split(':')
        require(parts.size == 2) { "알 수 없는 형식" }
        val b64 = Base64.getDecoder()
        val iv = b64.decode(parts[0])
        val sealed = b64.decode(parts[1])
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(sealed), Charsets.UTF_8)
    }

    private fun key(): SecretKey = synchronized(lock) {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(keyAlias, null) as? SecretKey) ?: generate()
    }

    private fun generate(): SecretKey {
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return gen.generateKey()
    }

    companion object {
        const val DEFAULT_ALIAS: String = "ggf_account_v1"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        private const val PREFIX = "g1:"
    }
}
