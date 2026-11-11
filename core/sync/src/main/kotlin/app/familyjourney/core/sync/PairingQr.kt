package app.familyjourney.core.sync

/**
 * 가족 폰 연결 QR 내용(architecture.md 19-3): `ggf1.<가족ID 22>.<연결 코드 22>.<가족 열쇠 43>`.
 * 주소가 아니라서 브라우저로 열리지 않고, 가족 열쇠는 서버를 거치지 않는다.
 * 연결 코드는 5분·한 번만 쓸 수 있다(서버는 해시만 안다).
 */
data class PairingQr(val familyId: String, val code: String, val familyKey: String) {
    fun format(): String = "$PREFIX.$familyId.$code.$familyKey"

    /** 로그에 열쇠가 나가지 않게 가린다 */
    override fun toString(): String = "PairingQr(familyId=${familyId.take(4)}…, code=***, familyKey=***)"

    companion object {
        const val PREFIX: String = "ggf1"

        fun parse(text: String): PairingQr? {
            val p = text.trim().split('.')
            if (p.size != 4 || p[0] != PREFIX) return null
            if (!Base64Url.isValid(p[1], 22) || !Base64Url.isValid(p[2], 22) || !Base64Url.isValid(p[3], 43)) return null
            return PairingQr(p[1], p[2], p[3])
        }

        /** 차: 새 연결 코드로 QR 내용을 만든다 */
        fun newFor(familyId: String, familyKey: String): PairingQr = PairingQr(familyId, Base64Url.random(16), familyKey)
    }
}
