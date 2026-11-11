package app.familyjourney.core.account

import app.familyjourney.core.trip.ArrivalReason
import app.familyjourney.core.trip.DistanceSource
import app.familyjourney.core.trip.SafeDriveSummary
import app.familyjourney.core.trip.SafetyJudgement
import app.familyjourney.core.trip.TripResult
import app.familyjourney.core.vehicle.Powertrain
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Base64

/** 테스트 시간대(서울) */
internal val ZONE: ZoneId = ZoneId.of("Asia/Seoul")

internal fun epochMs(y: Int, m: Int, d: Int, h: Int = 12): Long =
    LocalDateTime.of(y, m, d, h, 0).atZone(ZONE).toInstant().toEpochMilli()

internal val NOV_1: Long = epochMs(2026, 11, 1)

/**
 * 여행 결과. 기본은 decisions.md #5의 예: 400km 중 360km 안전, 전기차.
 * @param safeKm 판정해서 안전했던 km(포인트 기준), [unjudgedKm]을 더하면 새싹 기준 km
 */
internal fun tripResult(
    tripId: String = "trip-1",
    safeKm: Int = 360,
    eventKm: Int = 40,
    unjudgedKm: Int = 0,
    powertrain: Powertrain = Powertrain.EV,
    demo: Boolean = false,
    judgement: SafetyJudgement = SafetyJudgement.JUDGED,
): TripResult = TripResult(
    tripId = tripId,
    totalKm = (safeKm + eventKm + unjudgedKm).toDouble(),
    distanceSource = DistanceSource.ODOMETER,
    safety = SafeDriveSummary(
        countedKm = safeKm + eventKm + unjudgedKm,
        safeKm = safeKm,
        eventKm = eventKm,
        unjudgedKm = unjudgedKm,
        eventCount = eventKm,
        judgement = judgement,
    ),
    powertrain = powertrain,
    demo = demo,
    arrivalReason = ArrivalReason.ROUTE_ARRIVED,
    startedAtMs = 0L,
    arrivedAtMs = 1L,
)

/** 가상의 별명만 쓴다(블라인드 원칙) */
internal fun account(vararg children: ChildProfile, wallet: StarWallet = StarWallet()): GuardianAccount =
    GuardianAccount(id = "guardian-1", confirmedAtEpochMs = 0L, wallet = wallet, children = children.toList())

/** 보유·누적 ★가 같은 지갑(쓴 적 없음) */
internal fun stars(balance: Int, lifetime: Int = balance) = StarWallet(balance = balance, lifetime = lifetime)

internal fun child(id: String, nickname: String = "별명$id", sprouts: Int = 0, treeKind: TreeKind = TreeKind.DEFAULT) =
    ChildProfile(id = id, nickname = nickname, totalSprouts = sprouts, treeKind = treeKind)

/**
 * 가짜 암호화기: 바이트를 뒤집어 Base64로 적는다. 저장 파일에 평문이 남지 않는지, 저장·읽기가 이것을 거치는지 센다.
 */
internal class FakeCipher : TextCipher {
    var encrypted = 0
    var decrypted = 0
    var failDecrypt = false

    override fun encrypt(plain: String): String {
        encrypted++
        return "fake:" + Base64.getEncoder().encodeToString(plain.toByteArray(Charsets.UTF_8).reversedArray())
    }

    override fun decrypt(stored: String): String {
        decrypted++
        check(!failDecrypt) { "키 없음" }
        require(stored.startsWith("fake:"))
        return String(Base64.getDecoder().decode(stored.removePrefix("fake:")).reversedArray(), Charsets.UTF_8)
    }
}
