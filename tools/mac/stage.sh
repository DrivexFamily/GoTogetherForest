#!/usr/bin/env bash
# shellcheck source-path=SCRIPTDIR
# =============================================================================
# 같이가숲 — 시연·녹화 바로 전 준비 (Mac)
#
# 사용법: bash ~/Projects/familyjourney/tools/mac/stage.sh
#         (먼저 try.sh로 앱을 설치하고 에뮬레이터를 켜 둔다)
#
# 하는 일 (30초 안쪽)
#   1. Virtual Console "살아 있음" 신호를 잠깐 켠다(브라우저 탭 없이도 값을 쓰기 위해)
#   2. 문·프렁크·트렁크·창문을 닫힘으로 쓴다 → 에뮬레이터 왼쪽 안내의 "도어 열림"·"프렁크를 닫으세요"가 사라진다
#   3. 기어 P, 차속·가속·브레이크 0, 무드등 흰색(도착 때 초록으로 바뀌는 것이 잘 보이게),
#      음성 엔진(ai.pleos.playground.caas) 다시 켜기: 엔진이 가끔 오류 상태(ON_DEVICE_ENGINE_ERROR)에 빠지고
#      앱이 다시 초기화해도 풀리지 않는다. 한 번 끄면 다음에 말할 때 자동으로 다시 켜진다(2026-11-02 Mac 확인)
#   4. 주행 규제 강제 설정을 끈다(drive-off). 이 명령(hmg_car_service)이 없는 에뮬레이터(Pleos Connect v2.1.0,
#      2026-11-02 확인)에서는 "(이 에뮬레이터에는 없음)" 한 줄만 내고 건너뛴다. 주행 제한은 기어 D/P로만 바뀐다
#   5. 신호를 끄고 앱을 앞으로 띄운다
#
# 남는 것: 기어 P에서 왼쪽 안내의 "안전 벨트를 매세요."는 끌 수 없다(Virtual Console에 벨트 항목이 없음).
#          출발해서 차속이 0보다 커지면 사라진다. 에뮬레이터 시스템 화면이고 우리 앱과는 상관없다.
# 무엇을 바꾸나: 에뮬레이터의 차량 값(문·창문·기어·차속·무드등), 주행 규제 강제 설정, adb 포트 포워딩(8088).
#               저장소 파일과 앱 데이터는 바꾸지 않는다(숲·포인트 기록은 그대로).
# 다시 실행해도 되나: 된다. 녹화를 다시 할 때마다 실행하면 된다.
#   (한 번 닫은 프렁크가 다시 열림(true)으로 돌아온 적이 있다. 원인은 모름 — pm clear 뒤이거나 기어 P를 여러 번
#    오간 뒤일 수 있음. 2026-11-02 Mac 확인. 이 스크립트를 다시 돌리면 다시 닫힌다.)
# 검증: bash -n, shellcheck, 가짜 adb·curl로 흐름 확인(클라우드). Mac 담당 세션이 2026-11-02에 돌려 봄
#       (그때 4단계가 hmg_car_service 없음으로 [주의] → 이번에 고침).
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"
VC="http://127.0.0.1:8088"
VC_CLIENT="${GGF_VC_CLIENT:-ggf-stage}"
OUT="$(mktemp -d "${TMPDIR:-/tmp}/ggf-stage.XXXXXX")"
REPORT="$OUT/report.txt"  # check-common.sh가 기대하는 이름(이 스크립트는 화면에만 적는다)
export REPORT
W=1920
H=1080
APP_ID="$(awk -F= '$1 == "app.applicationId" { gsub(/[ \t\r]/, "", $2); print $2 }' "$ROOT/gradle.properties")"

say() { printf '\n▶ %s\n' "$*"; }
die() { printf '\n[멈춤] %s\n' "$*" >&2; exit 1; }
note() { printf '   %s\n' "$*"; }

# shellcheck source=check-common.sh
. "$SCRIPT_DIR/check-common.sh"

finish() {
  common_cleanup
  rm -rf "$OUT"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

[ -x "$ADB" ] || die "adb가 없습니다. 먼저 try.sh를 실행해 주세요."
"$ADB" get-state >/dev/null 2>&1 || die "에뮬레이터가 연결되어 있지 않습니다. 먼저 try.sh를 실행해 주세요."
[ -n "$APP_ID" ] || die "gradle.properties에서 app.applicationId를 찾지 못했습니다."

say "1/5 Virtual Console 연결"
vc_connect || note "[주의] 첫 신호가 실패했습니다(응답 ${VC_SIGNAL_FIRST:-000}). 쓰기를 다시 시도합니다."
# 신호를 보낸 직후 잠깐 503이 날 수 있어 최대 약 1분 다시 시도한다
vc_put "/vehicle/controls?speed=0&accelerator=0&brake=0" 12 ||
  die "Virtual Console 쓰기가 안 됩니다. 에뮬레이터를 다시 켜거나(bash $SCRIPT_DIR/emulator.sh stop 후 try.sh) 브라우저 Virtual Console 탭을 열고 다시 실행해 주세요."

say "2/5 문·프렁크·트렁크·창문 닫기"
vc_close_body || note "[주의] 닫기를 쓰지 못했습니다. 왼쪽 안내에 '도어 열림'이 남을 수 있습니다."

say "3/5 기어 P, 차속 0, 무드등 흰색, 음성 엔진 다시 켜기"
vc_put "/vehicle/gear?gear=Park" 3 || note "[주의] 기어 P로 바꾸지 못했습니다."
vc_reset_ambient || note "[주의] 무드등을 흰색으로 바꾸지 못했습니다."
"$ADB" shell am force-stop ai.pleos.playground.caas >/dev/null 2>&1 || note "[주의] 음성 엔진을 다시 켜지 못했습니다."
note "음성 엔진을 껐습니다(다음 안내 때 자동으로 다시 켜짐)."

say "4/5 주행 규제 강제 설정 끄기"
# drive-check 끝 코드: 0 있음 / 3 없음 / 1 확인 못 함. 확인 못 함이면 drive-off가 다시 살펴본다
drive_rc=0
bash "$SCRIPT_DIR/emulator.sh" drive-check >/dev/null 2>&1 || drive_rc=$?
if [ "$drive_rc" = 3 ]; then
  note "(이 에뮬레이터에는 없음)"
else
  bash "$SCRIPT_DIR/emulator.sh" drive-off || note "[주의] 주행 규제를 끄지 못했습니다."
fi

say "5/5 신호 끄고 앱 띄우기"
vc_disconnect
bash "$SCRIPT_DIR/emulator.sh" launch

cat <<MSG

==========================================================
 시연 준비 끝!
 - 왼쪽 안내의 "도어 열림"·"프렁크를 닫으세요"는 사라졌습니다.
 - "안전 벨트를 매세요."는 끌 수 없습니다. 출발해서 차속이 오르면 사라집니다.
   영상에서는 오른쪽 앱 영역만 잘라 쓰거나, "에뮬레이터 시스템 화면"이라고 자막을 넣어 주세요.
 - 데모는 앱 홈의 "데모로 해 보기"에서 시작합니다(주차 중에만 보입니다).
==========================================================
MSG
