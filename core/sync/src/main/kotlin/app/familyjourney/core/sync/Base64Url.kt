package app.familyjourney.core.sync

import java.security.SecureRandom
import java.util.Base64

/** base64url(패딩 없음). 서버(server/src/util.ts)와 같은 표기 */
internal object Base64Url {
    private val enc = Base64.getUrlEncoder().withoutPadding()
    private val dec = Base64.getUrlDecoder()
    private val random = SecureRandom()

    fun encode(bytes: ByteArray): String = enc.encodeToString(bytes)

    fun decode(text: String): ByteArray = dec.decode(text)

    /** [bytes]바이트 안전한 난수. 16바이트 → 22글자, 32바이트 → 43글자 */
    fun random(bytes: Int): String = encode(ByteArray(bytes).also(random::nextBytes))

    fun isValid(text: String, length: Int): Boolean = text.length == length && text.all { (it in 'A'..'Z') || (it in 'a'..'z') || (it in '0'..'9') || it == '-' || it == '_' }

    fun sha256(text: String): String =
        encode(java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)))
}
