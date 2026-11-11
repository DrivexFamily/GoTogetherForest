package app.familyjourney.core.account

/**
 * 동기화용 계정 JSON 창구(decisions.md #37, 2026-11-06 폰 앱·공유 서버 세션이 더함, 새 파일).
 *
 * 차 안 저장 형식([AccountCodec])을 그대로 쓰되 별명은 **평문**으로 둔다. 이 글자는 `core:sync`가
 * 가족 열쇠(AES-GCM)로 덩어리 전체를 암호화해서 서버에 보내므로, 서버도 별명을 읽지 못한다.
 * 기기 안에 저장할 때도 `core:sync`가 Keystore 열쇠로 다시 암호화한다. 이 글자를 로그에 쓰지 않는다.
 *
 * 기존 파일(AccountCodec·LocalAccountRepository)은 고치지 않았다.
 */
object AccountSnapshotCodec {
    /** 형식 버전(차 안 저장 형식과 같다) */
    const val VERSION: Int = AccountCodec.VERSION

    fun encode(account: GuardianAccount): String = AccountCodec.encode(account, PlainText)

    /** 읽을 수 없으면 예외 대신 null */
    fun decodeOrNull(text: String): GuardianAccount? = runCatching { AccountCodec.decode(text, PlainText) }.getOrNull()

    private object PlainText : TextCipher {
        override fun encrypt(plain: String): String = plain
        override fun decrypt(stored: String): String = stored
    }
}
