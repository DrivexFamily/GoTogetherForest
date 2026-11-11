package app.familyjourney.core.vehicle

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.transform

/**
 * 화면 갱신을 [periodMs]에 한 번으로 묶는다(docs/architecture.md 15장 "다시 그리기를 줄이는 규칙").
 *
 * - 첫 값은 바로 낸다(화면을 열자마자 보인다).
 * - 그 뒤로는 [periodMs]마다 **가장 최신 값 하나**만 낸다. 그 사이의 값은 버린다.
 * - 마지막 값은 버리지 않는다(멈춘 뒤의 값이 늦어도 [periodMs] 안에 보인다).
 *
 * 쓰는 곳: 0.5초마다 바뀌는 데모 신호를 그대로 보여 주는 개발용 신호 확인 화면, 발표자 패널의 설명 줄.
 * 아이 화면은 이것 대신 "보이는 값만 골라 같으면 내지 않기"(map + distinctUntilChanged)를 쓴다.
 */
fun <T> Flow<T>.throttleLatest(periodMs: Long): Flow<T> {
    require(periodMs > 0) { "periodMs는 0보다 커야 한다" }
    // conflate: 아래가 기다리는 동안 위에서 온 값은 마지막 것만 남긴다.
    return conflate().transform {
        emit(it)
        delay(periodMs)
    }
}
