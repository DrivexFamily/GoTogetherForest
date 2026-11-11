#!/usr/bin/env bash
# =============================================================================
# 같이가숲 — 한 번에 받아서 에뮬레이터에서 실행하기 (Mac)
#
# 사용법: bash ~/Projects/familyjourney/tools/mac/try.sh
#
# 하는 일 (순서대로, 이미 된 것은 건너뜀)
#   1. GitHub에서 최신 코드 받기 (git pull --ff-only)
#   2. 에뮬레이터가 꺼져 있으면 Cold boot로 켜고 부팅을 기다림 (emulator.sh start)
#   3. 개발용 앱 빌드·설치 (./gradlew installDebug, Java 17 사용)
#   4. 차속 권한 허용 (adb shell pm grant … CAR_SPEED) — 개발용 지름길
#   5. Virtual Console 열기 (emulator.sh console) — 사람이 손으로 바꿔 볼 때 쓰는 브라우저 탭
#   6. 앱 실행 (emulator.sh launch)
#
# 점검 스크립트(check.sh, demo-check.sh)는 Virtual Console "살아 있음" 신호를 스스로 보낸다(신호 루프).
# 그래서 점검 스크립트를 돌릴 때는 5번에서 연 브라우저 탭을 닫아도 된다(열려 있으면 탭이 차속 값을 덮어쓸 수 있음).
#
# 무엇을 바꾸나: 저장소 폴더의 파일(pull), 에뮬레이터 안의 앱 설치·권한, adb 포트 포워딩(8088).
# 다시 실행해도 되나: 된다. 코드가 바뀌었을 때마다 이것 하나만 실행하면 된다.
# 설정: ANDROID_HOME(기본 ~/Library/Android/sdk), GGF_SKIP_PULL=1(받기 건너뛰기)
# 검증: bash -n, shellcheck. Mac 실행은 팀원 확인 대기.
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
EMU_SH="$ROOT/tools/mac/emulator.sh"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"

say() { printf '\n▶ %s\n' "$*"; }
die() { printf '\n[멈춤] %s\n' "$*" >&2; exit 1; }

# ---- Java 17 찾기 (Android Studio 내장 Java 25는 Gradle 8.10.2가 지원하지 않음) ----
find_java17() {
  local c
  for c in \
    "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" \
    "$(/usr/libexec/java_home -v 17 2>/dev/null || true)"; do
    if [ -n "$c" ] && [ -x "$c/bin/java" ]; then
      printf '%s' "$c"
      return 0
    fi
  done
  return 1
}

APP_ID="$(awk -F= '$1 == "app.applicationId" { gsub(/[ \t\r]/, "", $2); print $2 }' "$ROOT/gradle.properties")"
[ -n "$APP_ID" ] || die "gradle.properties에서 app.applicationId를 찾지 못했습니다."

# 1. 최신 코드
if [ "${GGF_SKIP_PULL:-0}" != "1" ]; then
  say "1/6 최신 코드 받기"
  git -C "$ROOT" pull --ff-only || die "코드를 받지 못했습니다. 폴더에 직접 고친 파일이 있으면 Sourcetree에서 확인해 주세요."
fi

# 2. 에뮬레이터
say "2/6 에뮬레이터 확인 (꺼져 있으면 켜고 부팅을 기다립니다. 처음엔 1~3분)"
bash "$EMU_SH" start

# 3. 빌드·설치
say "3/6 앱 빌드·설치 (처음엔 몇 분 걸립니다)"
JAVA_HOME="$(find_java17)" || die "Java 17을 찾지 못했습니다. 터미널에서: brew install openjdk@17"
export JAVA_HOME
(cd "$ROOT" && ./gradlew --console=plain installDebug) || die "빌드·설치에 실패했습니다. 화면에 보이는 'What went wrong' 부분을 복사해 보내 주세요."

# 4. 차속 권한 (사용자 허용이 필요한 권한이라 개발 중에는 명령으로 허용)
say "4/6 차속 권한 허용"
# 차 안 Android는 사용자 0(시스템)과 실제 운전자 사용자(보통 10)가 나뉘어 있다.
# 앱은 현재 사용자로 돌기 때문에, 현재 사용자 번호를 물어 그 사용자에게 허용해야 한다.
# (2026-11-01 확인: 사용자 지정 없이 허용하면 사용자 0에만 들어가 앱 차속이 "모름"으로 나왔음)
CUR_USER="$("$ADB" shell am get-current-user 2>/dev/null | tr -dc '0-9')"
CUR_USER="${CUR_USER:-0}"
if "$ADB" shell pm grant --user "$CUR_USER" "$APP_ID" android.car.permission.CAR_SPEED 2>/dev/null; then
  echo "   차속 권한: 허용됨 (사용자 $CUR_USER)"
else
  echo "   차속 권한: 명령으로 허용하지 못했습니다. 앱의 '권한 요청' 버튼을 눌러 주세요."
fi

# 4-1. 음성 서비스 재시작 (2026-11-01 확인: 에뮬레이터의 음성 엔진이 처음엔 ON_DEVICE_ENGINE_ERROR를 내고,
#      이 서비스를 한 번 끄면 살아났다. 다음에 음성을 쓸 때 자동으로 다시 켜진다.)
"$ADB" shell am force-stop ai.pleos.playground.caas 2>/dev/null || true

# 5. Virtual Console
say "5/6 Virtual Console 열기 (브라우저)"
bash "$EMU_SH" console || echo "   Virtual Console을 열지 못했습니다. 나중에: bash $EMU_SH console"

# 6. 실행
say "6/6 앱 실행"
bash "$EMU_SH" launch

cat <<EOF

==========================================================
 준비 끝! 에뮬레이터 화면에서 같이가숲이 열렸습니다.
 - 신호 확인(개발용) 화면에서 값이 바뀌는지 보세요.
 - 브라우저의 Virtual Console에서 기어 D + ACCEL을 눌러 보세요.
 - 주행 제한은 Virtual Console 기어 D/P로 시험합니다(이 에뮬레이터에는 강제 명령 hmg_car_service가 없음).
 - 점검 스크립트(check.sh, demo-check.sh)를 돌릴 때는 브라우저의 Virtual Console 탭을 닫아도 됩니다
   (스크립트의 신호 루프가 탭 대신 신호를 보냅니다. 탭이 열려 있으면 차속 값을 덮어쓸 수 있습니다).
 코드가 새로 올라오면 이 명령 하나만 다시 실행하면 됩니다:
   bash $ROOT/tools/mac/try.sh
==========================================================
EOF
