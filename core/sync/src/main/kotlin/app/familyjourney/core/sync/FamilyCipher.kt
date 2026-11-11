package app.familyjourney.core.sync

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

/** 암호문 덩어리 `{"v":1,"iv":…,"ct":…}`(docs/share-api.md 공통). 서버는 모양만 본다 */
data class SealedBlob(val iv: String, val ct: String) {
    val v: Int get() = 1
}

/** 덩어리 종류. AAD(추가 인증 데이터)에 들어가 다른 자리로 옮겨 붙이면 풀리지 않는다 */
enum class BlobKind(val wire: String) { STATE("state"), STATUS("status"), LOCATION("location"), NAME("name"), WRAPPED_KEY("key") }

/**
 * 가족 열쇠(AES-256-GCM)로 덩어리를 잠그고 푸는 일(architecture.md 19-1, 종단 간 암호화).
 * 가족 열쇠는 차가 만들고 QR로만 폰에 건넨다. 서버로 보내지 않는다.
 *
 * AAD = "ggf1|<가족ID>|<종류>". 그래서 다른 가족·다른 종류의 덩어리를 바꿔치기하면 [open]이 실패한다.
 */
class FamilyCipher(familyKey: String, private val familyId: String) {
    private val key = SecretKeySpec(Base64Url.decode(familyKey).also { require(it.size == 32) { "가족 열쇠는 32바이트" } }, "AES")

    fun seal(plain: String, kind: BlobKind): SealedBlob {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        c.updateAAD(aad(kind))
        return SealedBlob(Base64Url.encode(iv), Base64Url.encode(c.doFinal(plain.toByteArray(Charsets.UTF_8))))
    }

    /** 풀 수 없으면(열쇠·종류·가족이 다르거나 덩어리가 바뀜) null */
    fun open(blob: SealedBlob, kind: BlobKind): String? = runCatching {
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, Base64Url.decode(blob.iv)))
        c.updateAAD(aad(kind))
        String(c.doFinal(Base64Url.decode(blob.ct)), Charsets.UTF_8)
    }.getOrNull()

    private fun aad(kind: BlobKind) = "ggf1|$familyId|${kind.wire}".toByteArray(Charsets.UTF_8)

    companion object {
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private val random = SecureRandom()

        /** 새 가족 열쇠(차가 가족을 만들 때 한 번) */
        fun newFamilyKey(): String = Base64Url.random(32)
    }
}
