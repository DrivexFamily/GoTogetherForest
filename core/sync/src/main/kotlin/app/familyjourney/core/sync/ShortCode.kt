package app.familyjourney.core.sync

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 짧은 연결 코드(architecture.md 19-3 "QR을 못 찍을 때", 에뮬레이터 시연용). 화면에는 `K7QM-4RXP-2WDN`처럼 보인다.
 *
 * - 헷갈리는 글자(0·1·O·I)를 뺀 32글자 중 12글자(약 60비트), 5분·한 번만(QR과 같은 연결 코드에 붙는다).
 * - 서버에는 코드의 해시만 간다. 가족 열쇠는 **코드에서 만든 열쇠(PBKDF2)로 감싸서** 서버를 거쳐 건넨다.
 *   QR보다 약하다(코드가 짧아 서버가 마음먹으면 풀 수 있다) → 시연·에뮬레이터용으로 권한다.
 */
object ShortCode {
    private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    const val LENGTH: Int = 12
    private val random = SecureRandom()

    fun generate(): String = String(CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] })

    /** 사람이 친 글자 → 12글자(대문자, "-"·빈칸 빼기). 맞지 않으면 null */
    fun normalize(input: String): String? {
        val s = input.uppercase().filter { it.isLetterOrDigit() }
        return s.takeIf { it.length == LENGTH && it.all { c -> c in ALPHABET } }
    }

    /** 화면용: 4글자씩 끊기 */
    fun display(code: String): String = code.chunked(4).joinToString("-")

    /** 가족 열쇠를 코드로 감싼다(차) */
    fun wrapFamilyKey(code: String, familyKey: String): SealedBlob {
        val iv = ByteArray(12).also(random::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, derive(code), GCMParameterSpec(128, iv))
        c.updateAAD(AAD)
        return SealedBlob(Base64Url.encode(iv), Base64Url.encode(c.doFinal(familyKey.toByteArray(Charsets.UTF_8))))
    }

    /** 감싼 가족 열쇠를 푼다(폰). 코드가 다르면 null */
    fun unwrapFamilyKey(code: String, wrapped: SealedBlob): String? = runCatching {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, derive(code), GCMParameterSpec(128, Base64Url.decode(wrapped.iv)))
        c.updateAAD(AAD)
        String(c.doFinal(Base64Url.decode(wrapped.ct)), Charsets.UTF_8)
    }.getOrNull()

    /** 가족 열쇠를 받기 전 잠시 쓰는 이름 덩어리(서버는 모양만 본다) */
    internal fun sealPlaceholder(code: String): SealedBlob = FamilyCipher(Base64Url.encode(derive(code).encoded), "short").seal("가족 폰", BlobKind.NAME)

    private val AAD = "ggf1|short|key".toByteArray(Charsets.UTF_8)

    private fun derive(code: String): SecretKeySpec {
        val spec = PBEKeySpec(code.toCharArray(), "ggf1-short-v1".toByteArray(Charsets.UTF_8), 100_000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }
}
