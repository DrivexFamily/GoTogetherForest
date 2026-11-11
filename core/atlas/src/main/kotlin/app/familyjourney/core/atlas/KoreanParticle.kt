package app.familyjourney.core.atlas

/**
 * "{이름}을/를 얻었어요"의 조사 고르기(U03 인계 8장).
 *
 * 마지막 글자가 한글이면 받침이 있으면 "을", 없으면 "를". 괄호 보조명은 괄호 앞 이름으로 판단한다
 * (예: "동대문디자인플라자(DDP)" → "플라자" → 를). 마지막이 한글이 아니면(로마자·숫자) null → 조사 없는 문장을 쓴다.
 */
object KoreanParticle {
    fun objectParticle(name: String): String? {
        val base = name.substringBefore('(').trim().ifEmpty { name.trim() }
        val c = base.lastOrNull() ?: return null
        if (c !in '가'..'힣') return null
        return if ((c - '가') % 28 != 0) "을" else "를"
    }

    /** 축하 문장. 조사를 정할 수 없으면 "{이름} 획득!" */
    fun acquiredSentence(name: String): String =
        objectParticle(name)?.let { "$name$it 얻었어요!" } ?: "$name 획득!"
}
