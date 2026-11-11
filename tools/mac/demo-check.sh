#!/usr/bin/env bash
# shellcheck source-path=SCRIPTDIR
# =============================================================================
# 같이가숲 — 시연 한 번 자동 점검 (Mac, 사람 입력 없음)
#
# 사용법: bash ~/Projects/familyjourney/tools/mac/demo-check.sh
#   (먼저 try.sh로 에뮬레이터를 켜고 개발용 앱(debug)을 설치해 둔다)
#   Mac 담당 Claude 세션이 사람 없이 돌릴 수 있게 만들었다. 터미널 입력을 전혀 기다리지 않는다(stdin을 쓰지 않음).
#   브라우저의 Virtual Console 탭은 닫아도 된다(아래 "Virtual Console 신호"). 열려 있으면 탭이 차속을 조금씩 내리므로
#   주행 중에는 신호 루프가 1초마다 차속을 다시 쓴다(vc_hold_speed, 2026-11-06).
#
# 하는 일(데모 시연 대본 그대로, docs/architecture.md 10-4·14장)
#    1. 앱 데이터를 지운다(pm clear --user 현재 사용자) → 첫 실행 흐름부터 본다
#    2. 차속 권한을 현재 사용자에게 허용하고, 음성 서비스를 다시 켠 뒤 앱을 연다(처음 실행 안내가 나오면 "건너뛰기")
#    3. 보호자 확인 → 아이 "콩이" 만들기 → 선택됐는지 확인(칩 부모 노드의 checked)
#    4. "데모로 해 보기"
#    5. Virtual Console 기어 D + 차속 30 → 주행 중 화면(정지 그림, %, 한 줄, 포즈 문장) 기록
#    6. D 뒤 약 64초(1배속이면 약 45%)에 차속 0, 기어 P(휴게소) → P 직전부터 화면을 찍어 캐릭터 움직임 기록,
#       휴게소 문구와 진행률(35~92%) 확인. P 시각은 화면 읽기가 아니라 시간으로 정한다(읽기가 느리면 지나쳐 버림)
#    7. 발표자 패널 "도착 직전으로"
#    8. 다시 D + 차속 30 → 도착까지
#    9. 무드등을 흰색으로 초기화 → 기어 P → 도착 축하·도착 결과 화면, 음성 로그, 무드등이 바뀌었는지 기록
#   10. 숲 화면 기록
#   11. 앱을 강제 종료했다가 다시 켜서 별명 "콩이"가 남는지(암호화 저장) 확인
#   12. 끝 상태: 기어 P, 차속 0
#
# Virtual Console 신호(2026-11-02 Mac 세션 확인): 콘솔 웹 화면은 1초마다 POST /browser/session?client=이름 을
#   보내고, 이 신호가 끊기면 쓰기(PUT)가 503이 된다. 그래서 시작할 때 같은 신호를 1초마다 보내는 배경 루프를 켜고
#   (client=ggf-demo-check), 끝날 때(정상 끝·실패·Ctrl+C) 끄고 &closing=1 을 보낸다. 탭 없이도 쓰기가 되고,
#   탭이 차속 값을 덮어쓰는 일도 없다. 첫 신호가 실패하면 그때만 브라우저 탭 이야기를 report에 남긴다.
# 화면 읽기(uiautomator dump)는 매우 느릴 수 있다(수십 초~약 17분). 그래서
#   - 한 번 읽은 결과에서 여러 글자를 찾는다(화면을 바꾼 뒤에만 다시 읽음). 앱 영역 좌표는 처음 한 번만 구한다.
#   - 한 번 읽기가 GGF_UI_TIMEOUT(기본 90초)을 넘으면 "화면 읽기 시간 초과"로 적고 계속 간다.
#   - 읽은 결과가 왼쪽 시스템 창(ai.umos.drivingview)뿐이면 앱 빈 곳을 한 번 누르고 한 번만 다시 읽는다(report에 적음).
#     다시 읽어도 앱이 없으면 그 화면 글자 판정은 "확인 못 함"이 된다.
#     그때 화면 글자로 하는 판정은 실패가 아니라 "확인 못 함"이 된다.
#   - 움직임 확인은 dump 없이 screencap만 빠르게 찍는다. 휴게소는 P를 보내기 직전부터 9초 동안 찍는다
#     (캐릭터는 P 뒤 약 0.5~5초 사이에 움직이고 멈춘다).
#
# 결과: 저장소 안 captures/verification/demo-check/ggf-demo-check-날짜시각/ 폴더(git에 올라가지 않음)
#   report.txt   단계별 기대값·실제값·판정(통과/실패/확인 못 함/참고)과 요약, 화면 읽기 시간 통계
#   stepNN-*.png 화면 사진, stepNN-*.txt 화면 글자(uiautomator: text + content-desc)
#   logcat.txt   앱 로그(GgfApp 음성·무드등, GgfControl, GgfVoice, GgfVehicle)
#   vc-state-*.json Virtual Console 상태, vc-signal-errors.txt 신호 루프가 실패한 시각(보통 비어 있음)
# 끝 코드: 0 실패 없음 / 1 실패 있음 / 2 시작할 수 없음(에뮬레이터·앱 없음, Virtual Console 쓰기가 약 1분 동안 안 됨)
#          / 3 실패는 없지만 "확인 못 함"이 있음
#
# 무엇을 바꾸나: 앱 데이터(지움 → 아이 "콩이" 하나), Virtual Console 기어·차속·무드등 색, adb 포트 포워딩(8088),
#               바탕화면에 결과 폴더 하나.
# 다시 실행해도 되나: 된다. 실행할 때마다 앱 데이터를 지우고 새 폴더를 만든다.
# 설정(환경 변수): GGF_UI_TIMEOUT(화면 읽기 한도 초, 기본 90), GGF_VC_CLIENT(신호 이름, 기본 ggf-demo-check)
#
# 참고
#   - 별명 입력: `adb shell input text`는 한글을 넣지 못한다. 그래서 디버그 빌드에만 있는 인텐트로 넣는다
#     (MainActivity.handleDevIntent, 화면의 "만들기"와 같은 규칙). release 빌드에서는 이 단계가 실패로 나온다.
#   - 공용 함수(신호 루프, Virtual Console API, 화면 읽기, 움직임 비교)는 tools/mac/check-common.sh에 있다.
#   - 주행 중 포즈: 앱은 길 그림 전체 설명을 "목적지까지 N퍼센트 왔어요"로 합친다. 그 끝에 포즈 문장이 붙어 있으면
#     (예: "… 아이가 손을 흔들고 있어요") 확인하고, 없으면 "참고"로 둔다.
# 검증: bash -n, shellcheck, 가짜 adb·curl로 흐름 확인(클라우드). Mac 실행은 Mac 담당 세션 확인 대기.
# =============================================================================
set -uo pipefail
exec </dev/null   # 사람 입력을 기다리지 않는다(어떤 명령도 터미널 입력을 읽지 못하게)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"
VC="http://127.0.0.1:8088"
VC_CLIENT="${GGF_VC_CLIENT:-ggf-demo-check}"
# 결과는 저장소 안 captures/verification/(git에 올라가지 않음, .gitignore). 2026-11-04 바탕화면에서 옮김
OUT="$ROOT/captures/verification/demo-check/ggf-demo-check-$(date +%Y%m%d-%H%M%S)"
REPORT="$OUT/report.txt"
NICK="콩이"
ACTIVITY="app.familyjourney.MainActivity"   # 패키지명(namespace)은 고정. 앱 ID는 gradle.properties에서 읽는다
W=1920
H=1080
APP_ID=""

PASS=0
FAIL=0
UNKNOWN=0
INFO=0
SUMMARY=""

mkdir -p "$OUT"
: >"$REPORT"
say() { printf '\n▶ %s\n' "$*"; }
note() { printf '%s\n' "$*" >>"$REPORT"; }
stop_now() {  # 더 진행할 수 없을 때: 이유를 적고 끝낸다
  note "[중단] $*"
  printf '\n[중단] %s\n결과: %s\n' "$*" "$REPORT" >&2
  exit 2
}

# shellcheck source=check-common.sh
. "$SCRIPT_DIR/check-common.sh"

[ -x "$ADB" ] || stop_now "adb가 없습니다($ADB). 먼저 try.sh를 실행해 주세요."
"$ADB" get-state >/dev/null 2>&1 || stop_now "에뮬레이터가 연결되어 있지 않습니다. 먼저 try.sh를 실행해 주세요."
APP_ID="$(awk -F= '$1 == "app.applicationId" { gsub(/[ \t\r]/, "", $2); print $2 }' "$ROOT/gradle.properties")"
[ -n "$APP_ID" ] || stop_now "gradle.properties에서 app.applicationId를 읽지 못했습니다."
"$ADB" shell pm path "$APP_ID" >/dev/null 2>&1 || stop_now "앱($APP_ID)이 설치되어 있지 않습니다. 먼저 try.sh를 실행해 주세요."

note "같이가숲 시연 자동 점검 $(date '+%Y-%m-%d %H:%M:%S')"
note "앱: $APP_ID"

# 화면 크기 (예: Physical size: 1920x1080)
SIZE="$("$ADB" shell wm size 2>/dev/null | awk -F': ' '/size/ { s = $2 } END { print s }' | tr -d '\r')"
W="${SIZE%x*}"
H="${SIZE#*x}"
case "$W$H" in *[!0-9]* | '') W=1920; H=1080 ;; esac
note "화면: ${W}x${H}"
ui_init

# ---- 판정 기록 ----
STEP=0
STEP_TITLE=""
begin() {  # begin "제목"
  STEP=$((STEP + 1))
  STEP_TITLE="$1"
  say "$STEP. $1"
  note ""
  note "===== 단계 $STEP: $1 ($(date '+%H:%M:%S')) ====="
}
judge() {  # judge 통과|실패|확인못함|참고 "확인 항목" "기대" "실제"
  local verdict="$1" what="$2" want="$3" got="$4"
  note "- [$verdict] $what"
  note "    기대: $want"
  note "    실제: $got"
  case "$verdict" in
    통과) PASS=$((PASS + 1)) ;;
    실패)
      FAIL=$((FAIL + 1))
      SUMMARY="$SUMMARY
  실패: 단계 $STEP($STEP_TITLE) - $what" ;;
    확인못함)
      UNKNOWN=$((UNKNOWN + 1))
      SUMMARY="$SUMMARY
  확인 못 함: 단계 $STEP($STEP_TITLE) - $what ($(ui_fail_reason))" ;;
    *) INFO=$((INFO + 1)) ;;
  esac
  printf '   [%s] %s\n' "$verdict" "$what"
}
# check "확인 항목" "기대" "실제" 조건결과(0이면 통과) — 화면 글자로 판정하는 항목.
# 마지막 화면 읽기가 실패(시간 초과, 또는 다시 읽어도 왼쪽 시스템 창만 읽힘)했으면 통과·실패 대신 "확인못함"
# (없어야 하는 글자를 "없음"으로 잘못 통과시키지 않게).
check() {
  if [ "$UI_OK" != 1 ]; then
    judge 확인못함 "$1" "$2" "$3 [$(ui_fail_reason)]"
  elif [ "$4" = 0 ]; then
    judge 통과 "$1" "$2" "$3"
  else
    judge 실패 "$1" "$2" "$3"
  fi
}
# check_cmd: 명령·API·로그·screencap으로 판정하는 항목(화면 글자와 무관)
check_cmd() {
  if [ "$4" = 0 ]; then judge 통과 "$1" "$2" "$3"; else judge 실패 "$1" "$2" "$3"; fi
}

# ---- 이 앱의 화면 읽기 (읽어 둔 화면에서만 찾는다) ----
# 진행률 숫자: 큰 % 글자의 설명 "45퍼센트". 없으면 길 그림 설명 "목적지까지 45퍼센트 …"에서
percent_now() {
  local p
  p="$(ui_lines | sed -n 's/^\([0-9][0-9]*\)퍼센트$/\1/p' | head -n 1 || true)"
  if [ -z "$p" ]; then p="$(ui_lines | sed -n 's/^목적지까지 \([0-9][0-9]*\)퍼센트.*/\1/p' | head -n 1 || true)"; fi
  # 휴게소(주차) 화면은 "목적지까지 42% 왔어요"(% 글자)로 쓴다(2026-11-06)
  if [ -z "$p" ]; then p="$(ui_lines | sed -n 's/.*목적지까지 \([0-9][0-9]*\)% 왔어요.*/\1/p' | head -n 1 || true)"; fi
  printf '%s' "$p"
}
# 길 그림 전체 설명 한 줄: "목적지까지 N퍼센트 왔어요" (+ 포즈 문장이 붙을 수 있음)
road_line() { ui_lines | grep -m 1 -E '^목적지까지 [0-9]+퍼센트 왔어요' || true; }
# 길 그림 설명 끝에 붙은 포즈 문장(없으면 빈 값). 구분 글자(쉼표·마침표·…·· 등)는 떼어 낸다
road_pose() {
  road_line | sed -e 's/^목적지까지 [0-9][0-9]*퍼센트 왔어요//' -e 's/^[ ,.:;]*//' -e 's/^…[ ]*//' -e 's/^·[ ]*//' -e 's/^-[ ]*//' || true
}
# 따로 있는 캐릭터 설명(예전 방식: 캐릭터 노드에 포즈 설명이 있을 때)
pose_node() { ui_lines | grep -m 1 -E '^(출발! 아이가|아이가 |가족이 |거의 다 왔어요! 아이가)' || true; }
POSE=""
POSE_SRC=""
pose_now() {  # POSE, POSE_SRC를 채운다(읽어 둔 화면에서)
  POSE="$(road_pose)"
  POSE_SRC="길 그림 설명 끝"
  if [ -z "$POSE" ]; then
    POSE="$(pose_node)"
    POSE_SRC="캐릭터 설명"
  fi
  if [ -z "$POSE" ]; then POSE_SRC=""; fi
}

# ---- 기록: 화면 사진 + 글자 ----
txt_of() { printf '%s/step%02d-%s.txt' "$OUT" "$STEP" "$1"; }
shot() {  # shot 이름 → stepNN-이름.png / .txt, 화면 글자를 report에도 적는다. 화면이 바뀐 뒤에만 다시 읽는다
  local name="$1" base
  base="$OUT/$(printf 'step%02d' "$STEP")-$name"
  "$ADB" exec-out screencap -p >"$base.png" 2>/dev/null || true
  ensure_ui
  if [ "$UI_OK" = 1 ]; then
    ui_lines >"$base.txt"
  else
    printf '(%s — 글자를 읽지 못함. 사진 %s.png 참고)\n' "$(ui_fail_reason)" "$(basename "$base")" >"$base.txt"
  fi
  note "  화면 글자($name): $(tr '\n' '|' <"$base.txt")"
}

# ---- Virtual Console ----
drive() {  # drive 차속 → 기어 D + 차속. 차속은 신호 루프가 붙잡아 둔다(브라우저 탭이 열려 있어도 줄지 않게)
  vc_put "/vehicle/gear?gear=Drive" && vc_put "/vehicle/controls?speed=$1&accelerator=0&brake=0" && vc_hold_speed "$1"
}
park() {  # 차속 0 → 기어 P (차속을 먼저 내린다. 붙잡아 둔 차속도 먼저 놓는다)
  vc_release_speed
  vc_put "/vehicle/controls?speed=0&accelerator=0&brake=0" && vc_put "/vehicle/gear?gear=Park"
}

if vc_connect; then
  say "Virtual Console 신호 루프를 켰습니다(client=$VC_CLIENT). 브라우저 탭은 닫아도 됩니다."
else
  note "[주의] Virtual Console 신호가 실패했습니다(첫 응답 ${VC_SIGNAL_FIRST:-000}). 쓰기가 503이 될 수 있습니다."
  note "       이때는 브라우저 Virtual Console 탭(bash tools/mac/emulator.sh console)을 열어 두면 탭이 신호를 대신 보냅니다."
  note "       단, 탭이 열려 있으면 차속 값을 덮어쓸 수 있습니다."
  printf '   [주의] Virtual Console 신호 실패(%s). 쓰기가 503이면 report.txt 첫 부분을 보세요.\n' "${VC_SIGNAL_FIRST:-000}"
fi
if [ -n "$VC_CLIENTS_BEFORE" ] && [ "$VC_CLIENTS_BEFORE" != "0" ]; then
  note "[참고] 시작 전 브라우저 Virtual Console 클라이언트 ${VC_CLIENTS_BEFORE}개(탭이 열려 있었을 수 있음). 탭은 차속을 덮어쓸 수 있다. 신호 루프가 대신하므로 점검 때는 탭이 필요 없다."
fi

# 끝날 때(중간에 멈춰도, Ctrl+C여도) 차를 P·차속 0으로 두고, 신호 루프를 끈다(P로 바꾸는 쓰기에 신호가 필요해서 이 순서)
# shellcheck disable=SC2317  # trap으로만 부른다
finish() {
  local rc=$?
  trap - EXIT INT TERM HUP
  note ""
  note "===== 끝 정리 ($(date '+%H:%M:%S')) ====="
  if [ -n "$FRAMES_PID" ]; then kill "$FRAMES_PID" 2>/dev/null || true; fi
  { vc_put "/vehicle/controls?speed=0&accelerator=0&brake=0" 2 && vc_put "/vehicle/gear?gear=Park" 2; } >/dev/null 2>&1 ||
    note "[주의] 끝 정리: 기어 P·차속 0으로 바꾸지 못함"
  common_cleanup
  exit "$rc"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP

CUR_USER="$("$ADB" shell am get-current-user 2>/dev/null | tr -dc '0-9')"
CUR_USER="${CUR_USER:-0}"
note "현재 사용자: $CUR_USER"
"$ADB" logcat -c >/dev/null 2>&1 || true

# Virtual Console 쓰기가 되는지 먼저 본다. 안 되면 시연 대본 대부분을 확인할 수 없으므로 바로 멈춘다(끝 코드 2).
# 신호를 보낸 직후 잠깐 503이 날 수 있어 최대 약 1분(12번) 다시 시도한다.
if ! vc_put "/vehicle/controls?speed=0&accelerator=0&brake=0" 12; then
  stop_now "Virtual Console 쓰기가 안 됩니다(신호 첫 응답 ${VC_SIGNAL_FIRST:-000}, 응답은 report.txt의 API PUT 줄). 에뮬레이터를 다시 켜거나(bash $SCRIPT_DIR/emulator.sh stop 후 try.sh), 브라우저 Virtual Console 탭을 열고 다시 실행해 주세요."
fi
# 시연 화면의 왼쪽 안내에서 "도어 열림"·"프렁크를 닫으세요"가 보이지 않게 문을 닫아 둔다(check-common.sh vc_close_body)
vc_close_body >/dev/null || note "  [주의] 문·프렁크 닫기를 쓰지 못함(왼쪽 안내에 '도어 열림'이 남을 수 있음, 앱과는 무관)"

# =============================================================================
begin "앱 데이터 지우기(첫 실행부터)"
park >/dev/null || note "  [주의] 시작할 때 기어 P·차속 0으로 바꾸지 못함"
"$ADB" shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
CLEAR="$("$ADB" shell pm clear --user "$CUR_USER" "$APP_ID" 2>&1 | tr -d '\r')"
case "$CLEAR" in *Success*) rc=0 ;; *) rc=1 ;; esac
check_cmd "pm clear --user $CUR_USER" "Success" "${CLEAR:-응답 없음}" "$rc"

# =============================================================================
begin "차속 권한 허용, 음성 서비스 다시 켜기, 앱 실행"
# 앱 데이터를 지우면 권한도 처음으로 돌아간다. 현재 사용자(보통 10)에게 다시 허용한다(try.sh와 같음).
if "$ADB" shell pm grant --user "$CUR_USER" "$APP_ID" android.car.permission.CAR_SPEED >/dev/null 2>&1; then rc=0; else rc=1; fi
check_cmd "차속 권한(CAR_SPEED) 허용" "허용됨(사용자 $CUR_USER)" "$([ "$rc" = 0 ] && echo 허용됨 || echo 실패)" "$rc"
# 음성 엔진이 처음엔 ON_DEVICE_ENGINE_ERROR를 내고, 서비스를 한 번 끄면 살아났다(2026-11-01). 다음에 말할 때 다시 켜진다.
"$ADB" shell am force-stop ai.pleos.playground.caas >/dev/null 2>&1 || true
"$ADB" shell am start --user "$CUR_USER" -n "$APP_ID/$ACTIVITY" >/dev/null 2>&1 || true
ui_stale
# 처음 실행 안내(GPT R32, 2026-11-05~): 앱 데이터를 지웠으니 다시 나온다 → "건너뛰기"
if wait_any 30 "건너뛰기" "보호자 확인"; then
  if [ "$FOUND_TEXT" = "건너뛰기" ]; then
    shot "처음-실행-안내"
    tap_text "건너뛰기" || note "  [주의] '건너뛰기'를 누르지 못함"
    ui_stale
  fi
fi
if wait_text "보호자 확인" 30; then rc=0; else rc=1; fi
shot "첫-화면"
check "첫 실행 화면" "\"처음 오셨네요! 보호자 확인을 해 주세요\"" "$(head -n 3 "$(txt_of 첫-화면)" | tr '\n' '|')" "$rc"

# =============================================================================
begin "보호자 확인 → 아이 \"$NICK\" 만들기 → 선택"
tap_text "저는 아이의 보호자예요" || note "  [주의] '저는 아이의 보호자예요'를 찾지 못함"
if tap_text "확인"; then rc=0; else rc=1; fi
# 차속 권한 창이 뜨면(위에서 허용하지 못했을 때) 허용을 누른다. 화면을 한 번 읽고 네 글자를 모두 찾아본다
ensure_ui
for allow in "허용" "Allow" "앱 사용 중에만 허용" "While using the app"; do
  if has_text "$allow"; then
    tap_text "$allow" || true
    break
  fi
done
if wait_text "아이 별명을 적어 주세요" 15; then rc2=0; else rc2=1; fi
shot "보호자-확인-뒤"
check "보호자 확인 → 첫 아이 만들기 단계" "\"아이 별명을 적어 주세요\"" "$(grep -m 1 -F '별명' "$(txt_of 보호자-확인-뒤)" || echo 없음)" "$((rc + rc2 > 0 ? 1 : 0))"
# 한글 별명: adb input은 한글을 못 넣는다 → 디버그 빌드 전용 인텐트(UTF-8 → Base64)
B64="$(printf '%s' "$NICK" | base64 | tr -d '\n')"
"$ADB" shell am start --user "$CUR_USER" --activity-single-top -n "$APP_ID/$ACTIVITY" --es ggf.dev.addChildB64 "$B64" >/dev/null 2>&1 || true
ui_stale
if wait_text "오늘 누가 타나요?" 15; then rc=0; else rc=1; fi
shot "아이-고르기"
# 칩의 선택 상태는 글자 노드("콩이")가 아니라 그 부모(checkable="true") 노드의 checked에 있다
CHIP="$(chip_state_of "$NICK")"
case "$CHIP" in *'checked="true"'*) rc2=0 ;; *) rc2=1 ;; esac
check "아이 만들기 → \"오늘 누가 타나요?\"" "홈 \"오늘 누가 타나요?\"에 \"$NICK\"" "$(has_text "$NICK" && echo "$NICK 보임" || echo "$NICK 없음")" "$rc"
check "\"$NICK\" 선택됨(새로 만든 아이는 바로 선택)" "칩(부모 노드, checkable=\"true\")의 checked=\"true\"" "${CHIP:-checkable 노드 없음}" "$rc2"

# =============================================================================
begin "데모로 해 보기"
if tap_text "데모로 해 보기"; then rc=0; else rc=1; fi
# 데모는 가상 내비 목적지(서울역 → 부산역) 확인창부터 뜬다(결정 #45). [맞아요]를 눌러야 데모가 이어진다
wait_text "맞습니까" 10 || note "  [주의] 목적지 확인창을 찾지 못함"
tap_text "맞아요" || note "  [주의] '맞아요'를 누르지 못함"
wait_text "데모 주행" 15 || true
shot "데모-시작"
check "진행 화면 + \"데모 주행\" 배지" "\"데모 주행\" 보임" "$(has_text "데모 주행" && echo 보임 || echo 없음)" "$([ "$rc" = 0 ] && has_text "데모 주행" && echo 0 || echo 1)"
# 앱의 데모 기본은 16배속(2026-11-08). 이 점검의 시각표(휴게소 64초 등)는 1배속 기준이라 발표자 패널에서 1×로 바꾼다
tap_text "펼치기" || note "  [주의] '펼치기'를 찾지 못함(1× 바꾸기)"
tap_text "1×" || note "  [주의] '1×'를 누르지 못함 — 16배속이면 휴게소 전에 도착할 수 있음"
tap_text "접기" || true

# =============================================================================
begin "기어 D + 차속 30 → 주행 중 화면"
if drive 30; then rc=0; else rc=1; fi
DRIVE_T0=$SECONDS
check_cmd "Virtual Console 기어 D, 차속 30" "200" "$([ "$rc" = 0 ] && echo 200 || echo 실패)" "$rc"
sleep 20
# 움직임: dump 없이 4초 동안 screencap만(앱 영역 비교). 그다음 화면을 한 번 읽는다
frames_start "$OUT/.frames-drive" 4 6    # 약 0.7초 간격
frames_wait
frames_compare "$OUT/.frames-drive"
shot "주행-중"
P1="$(percent_now)"
pose_now
ROAD="$(road_line)"
note "  길 그림 설명: ${ROAD:-없음}"
if [ -n "$APP_BOUNDS" ]; then
  check_cmd "주행 중 그림이 움직이지 않음(앱 영역 screencap 비교)" "같은 쌍이 있음(계속 움직이지 않음)" "같은 쌍 $SAME, 다른 쌍 $DIFF" "$([ "$SAME" -gt 0 ] && echo 0 || echo 1)"
else
  judge 참고 "주행 중 그림이 움직이지 않음(앱 영역을 몰라 화면 전체 비교)" "같은 쌍이 있음" "같은 쌍 $SAME, 다른 쌍 $DIFF"
fi
check "큰 진행률 숫자" "N퍼센트" "${P1:-없음}%" "$([ -n "$P1" ] && echo 0 || echo 1)"
# 포즈 문장: 길 그림 설명 끝에 붙은 문장(새 방식) 또는 캐릭터 설명(예전 방식). 둘 다 없으면 "참고"
if [ "$UI_OK" != 1 ]; then
  check "정지 포즈 문장" "주행 포즈 문장" "없음" 1
elif [ -n "$POSE" ]; then
  case "$POSE" in
    *아이가* | *가족이* | *출발!* | *"거의 다 왔어요"*)
      check "정지 포즈 문장($POSE_SRC)" "주행 포즈 문장(출발!/창밖 보기/손 흔들기/거의 다 왔어요)" "$POSE" 0 ;;
    *)
      judge 참고 "정지 포즈 문장($POSE_SRC)" "주행 포즈 문장" "$POSE (모르는 문장 — 앱 문구가 바뀌었으면 스크립트 기대값을 고친다)" ;;
  esac
else
  judge 참고 "정지 포즈 문장" "길 그림 설명 끝에 포즈 문장(예: \"… 아이가 손을 흔들고 있어요\"). 앱에 아직 없으면 참고" "포즈 문장 없음. 길 그림 설명: ${ROAD:-없음}"
fi
check "주행 중 발표자 패널은 접힘" "\"주행 중에는 접혀 있어요\"" "$(has_text "주행 중에는 접혀" && echo 보임 || echo 없음)" "$(has_text "주행 중에는 접혀" && echo 0 || echo 1)"
check "주행 중 홈·숲 버튼 없음" "\"숲 보기\"·\"숲 보러 가기\" 없음" "$(has_text "숲 보" && echo 있음 || echo 없음)" "$(has_text "숲 보" && echo 1 || echo 0)"
note "  주행 중 문장(한 줄): $(grep -E '만화|노래|가는 중|가고 있어요|출발 준비' "$(txt_of 주행-중)" | head -n 1)"
note "  맨 앞 화면: $("$ADB" shell dumpsys activity activities 2>/dev/null | grep -m 1 -i 'ResumedActivity' | tr -s ' ')"

# =============================================================================
begin "약 45%에서 차속 0, 기어 P(휴게소)"
# P를 보낼 때를 화면 읽기가 아니라 **시간**으로 정한다. 1배속에서 0→45%는 약 66초(진행률은 15초마다 한 번에 바뀜, 결정 #25).
# 화면 읽기가 느리면(시간 초과 한 번이 90초) 그동안 차가 계속 달려 휴게소를 지나쳐 도착해 버리기 때문이다.
# P 뒤에는 차가 서 있으므로 진행률은 그다음에 천천히 읽어도 된다.
REST_AT=64   # D 뒤 이 초에 P
WAIT_S=$((REST_AT - (SECONDS - DRIVE_T0)))
if [ "$WAIT_S" -gt 0 ]; then
  sleep "$WAIT_S"
else
  note "  [참고] 앞 단계의 화면 읽기가 오래 걸려 D 뒤 $((SECONDS - DRIVE_T0))초에 P를 보냄(목표 ${REST_AT}초)"
fi
REST_ELAPSED=$((SECONDS - DRIVE_T0))
# 움직임: P를 보내기 **직전**부터 9초 동안 screencap만 찍는다(캐릭터는 P 뒤 약 0.5~5초 사이에 움직이고 멈춤)
frames_start "$OUT/.frames-rest" 9 20   # 약 0.45초 간격, 최대 20장
sleep 0.5
if park; then rc=0; else rc=1; fi
P_MS="$(now_ms)"
[ "$rc" = 0 ] || note "  [주의] P로 바꾸지 못함"
frames_wait
frames_compare "$OUT/.frames-rest" "$P_MS"
wait_text "휴게소에서 쉬는 중" 15 || true
shot "휴게소"
PR="$(percent_now)"
check "휴게소 진행률(여행 중간)" "35~92%(D 뒤 약 ${REST_AT}초에 P, 1배속이면 약 45%)" "${PR:-없음}% (D 뒤 ${REST_ELAPSED}초에 P)" \
  "$([ -n "$PR" ] && [ "$PR" -ge 35 ] && [ "$PR" -le 92 ] && echo 0 || echo 1)"
check "휴게소 문구" "\"휴게소에서 쉬는 중\"" "$(has_text "휴게소에서 쉬는 중" && echo 보임 || echo 없음)" "$(has_text "휴게소에서 쉬는 중" && echo 0 || echo 1)"
if [ "$FRAMES_AFTER" -gt 0 ]; then
  judge 참고 "주차 중 캐릭터 움직임(P 직전부터 9초, 앱 영역 screencap)" "P 뒤 잠깐 움직이고 멈춤(다른 쌍이 있고, 끝에는 같은 쌍)" \
    "$MOTION (같은 쌍 $SAME, 다른 쌍 $DIFF, 바뀐 장의 시각: P 뒤 ${MOTION_FIRST:-없음}~${MOTION_LAST:-없음}초, P 뒤에 찍은 장 ${FRAMES_AFTER}장)"
else
  judge 참고 "주차 중 캐릭터 움직임" "P 뒤에 찍은 장이 있음" "P 뒤에 찍은 장 없음(P 쓰기가 늦었거나 screencap 실패)"
fi
note "  새싹 문구: $(grep -F '새싹' "$(txt_of 휴게소)" | head -n 1)"
pose_now
note "  포즈: ${POSE:-없음}${POSE_SRC:+ ($POSE_SRC)}"

# =============================================================================
begin "발표자 패널 \"도착 직전으로\""
tap_text "펼치기" || note "  [주의] '펼치기'를 찾지 못함"
if tap_text "도착 직전으로"; then rc=0; else rc=1; fi
sleep 3
shot "도착-직전"
P2="$(percent_now)"
check "\"도착 직전으로\" 누름" "버튼 있음" "$([ "$rc" = 0 ] && echo 누름 || echo 못 찾음)" "$rc"
check "진행률 90% 이상" "90% 이상" "${P2:-없음}%" "$([ -n "$P2" ] && [ "$P2" -ge 90 ] && echo 0 || echo 1)"
tap_text "접기" || true

# =============================================================================
begin "다시 D + 차속 30 → 도착"
drive 30 || note "  [주의] D로 바꾸지 못함"
# 아직 기어 D: 100%에 "거의 다 왔어요" 정지 포즈. "도착했어요!" 화면은 기어 P 뒤에만 나온다(2026-11-08 Mac 확인)
# 100%만 기다린다("거의 다 왔어요"는 93%부터 보여서 기다림 조건으로 쓰면 도착 전에 넘어감, 11-08 Mac 확인)
if wait_text "목적지까지 100퍼센트" 120; then ARRIVED=0; else ARRIVED=1; fi   # 93→100%는 약 20초 + 도착 신호
shot "도착-주행-중"
check "도착(아직 D): 100% 정지 그림 + \"거의 다 왔어요\"" "100퍼센트, \"거의 다 왔어요\"(도착 화면은 P 뒤)" "$(percent_now)%, $(has_text "거의 다 왔어요" && echo "거의 다 왔어요 보임" || echo 없음)" \
  "$([ "$ARRIVED" = 0 ] && [ "$(percent_now)" = 100 ] && echo 0 || echo 1)"

# =============================================================================
begin "무드등 초기화 → 기어 P → 도착 축하, 도착 결과, 음성, 무드등"
# 무드등을 흰색으로 되돌려 두고, 도착 축하 뒤 바뀌었는지 본다(지난 실행의 초록이 남아 있어도 판정할 수 있게)
if vc_reset_ambient; then MOOD_RESET=0; else MOOD_RESET=1; fi
MOOD_BEFORE="$(vc_get body.ambient_light)"
note "  무드등 초기화(#ffffff): $([ "$MOOD_RESET" = 0 ] && echo 200 || echo 실패), 초기화 뒤 값: ${MOOD_BEFORE:-없음}"
park || note "  [주의] P로 바꾸지 못함"
# 도착해 주차하면 랜드마크 축하(결정 #43: 데모는 "랜드마크 5곳을 획득했어요!")가 먼저 덮는다 → [좋아요!]로 닫는다
if wait_any 15 "획득했어요" "얻었어요"; then
  shot "랜드마크-축하"
  check "랜드마크 축하(주차)" "\"랜드마크 N곳을 획득했어요!\" 또는 \"○○을 얻었어요!\"" "$(grep -m 1 -E '획득했어요|얻었어요' "$(txt_of 랜드마크-축하)" || echo 없음)" 0
  tap_text "좋아요!" || note "  [주의] '좋아요!'를 누르지 못함"
  ui_stale
else
  note "  [참고] 랜드마크 축하가 보이지 않음(이미 다 얻었거나 도감 꺼짐)"
fi
wait_text "운전해 줘서 고마워요" 15 || true
shot "도착-축하"
check "도착 축하(주차)" "\"도착했어요!\" + \"운전해 줘서 고마워요\" + \"숲 보러 가기\"" \
  "$(has_text "운전해 줘서 고마워요" && echo 고마워요 || echo 고마워요-없음), $(has_text "숲 보러 가기" && echo 숲버튼 || echo 숲버튼-없음)" \
  "$(has_text "운전해 줘서 고마워요" && has_text "숲 보러 가기" && echo 0 || echo 1)"
sleep 12   # 음성(최대 8초 기다림)과 무드등(쓰기 → 0.5초 → 다시 읽기)이 끝날 시간
ui_stale   # 그동안 화면이 바뀌었을 수 있다(다음에 누르기 전에 다시 읽는다)
"$ADB" logcat -d -s GgfApp GgfControl GgfVoice GgfVehicle >"$OUT/logcat.txt" 2>/dev/null || true
VOICE="$(grep -F '부드럽게 달려 줘서 고마워요' "$OUT/logcat.txt" | tail -n 1)"
case "$VOICE" in *Spoken*) rc=0 ;; *) rc=1 ;; esac
check_cmd "음성 \"부드럽게 달려 줘서 고마워요\"" "GgfApp 로그에 Spoken(말함)" "${VOICE:-로그 없음}" "$rc"
MOOD_LOG="$(grep -F '도착 축하 무드등' "$OUT/logcat.txt" | tail -n 1)"
case "$MOOD_LOG" in *확인됨*) rc=0 ;; *) rc=1 ;; esac
check_cmd "무드등(앱 결과)" "\"도착 축하 무드등: 확인됨: 다시 읽은 색 #2B774A\"" "${MOOD_LOG:-로그 없음}" "$rc"
MOOD_AFTER="$(vc_get body.ambient_light)"
vc_state >"$OUT/vc-state-arrival.json"
if [ "$MOOD_RESET" = 0 ]; then
  check_cmd "무드등(Virtual Console /vehicle/state body.ambient_light)" "흰색(#ffffff)으로 초기화한 값에서 바뀜(숲 초록)" "전: ${MOOD_BEFORE:-없음} / 뒤: ${MOOD_AFTER:-없음}" \
    "$([ -n "$MOOD_AFTER" ] && [ "$MOOD_AFTER" != "$MOOD_BEFORE" ] && echo 0 || echo 1)"
else
  judge 참고 "무드등(Virtual Console /vehicle/state body.ambient_light)" "전과 달라짐(숲 초록)" "초기화 실패. 전: ${MOOD_BEFORE:-없음} / 뒤: ${MOOD_AFTER:-없음}"
fi
if tap_text "숲 보러 가기"; then rc=0; else rc=1; fi
wait_any 15 "부드럽게 달린" "다음 여행에서 숲이 자라요" || true
shot "도착-결과"
check "도착 결과 화면" "\"부드럽게 달린 …km만큼 숲이 자랐어요\"" "$(grep -m 1 -E '숲이 자랐어요|다음 여행에서' "$(txt_of 도착-결과)" || echo 없음)" \
  "$([ "$rc" = 0 ] && has_text "숲이 자랐어요" && echo 0 || echo 1)"
check "데모는 포인트 0P" "\"데모 주행이라 포인트는 쌓이지 않아요\"" "$(has_text "데모 주행이라 포인트" && echo 보임 || echo 없음)" "$(has_text "데모 주행이라 포인트" && echo 0 || echo 1)"

# =============================================================================
begin "숲 화면"
if tap_text "숲 보러 가기"; then rc=0; else rc=1; fi
wait_text "${NICK}의 숲" 10 || true
shot "숲"
check "\"${NICK}의 숲\"" "\"${NICK}의 숲\" + 나무·새싹 수" "$(grep -m 1 -F '의 숲' "$(txt_of 숲)" || echo 없음), $(grep -m 1 -F '그루' "$(txt_of 숲)" || echo 없음)" \
  "$([ "$rc" = 0 ] && has_text "${NICK}의 숲" && echo 0 || echo 1)"

# =============================================================================
begin "앱 강제 종료 → 다시 켜기: 별명 \"$NICK\"이 남나(암호화 저장)"
"$ADB" shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
sleep 2
"$ADB" shell am start --user "$CUR_USER" -n "$APP_ID/$ACTIVITY" >/dev/null 2>&1 || true
ui_stale
wait_text "오늘 누가 타나요?" 30 || true
shot "다시-켬"
check "다시 켠 뒤 \"$NICK\"" "홈 \"오늘 누가 타나요?\"에 \"$NICK\"" "$(has_text "$NICK" && echo "$NICK 보임" || echo "$NICK 없음")" "$(has_text "$NICK" && echo 0 || echo 1)"

# =============================================================================
begin "끝 상태: 기어 P, 차속 0"
if park; then rc=0; else rc=1; fi
GEAR_NOW="$(vc_get gear)"
vc_state >"$OUT/vc-state-end.json"
check_cmd "Virtual Console P, 차속 0" "200" "$([ "$rc" = 0 ] && echo "200 (gear=${GEAR_NOW:-?})" || echo 실패)" "$rc"
"$ADB" logcat -d -s GgfApp GgfControl GgfVoice GgfVehicle >"$OUT/logcat.txt" 2>/dev/null || true

# ---- 요약 ----
note ""
note "===== 요약 ====="
note "통과 $PASS · 실패 $FAIL · 확인 못 함 $UNKNOWN · 참고 $INFO"
if [ -n "$SUMMARY" ]; then note "$SUMMARY"; fi
ui_summary
note ""
note "===== 앱 로그 (마지막 60줄) ====="
tail -n 60 "$OUT/logcat.txt" >>"$REPORT" 2>/dev/null || true

open "$OUT" 2>/dev/null || true
cat <<EOF

==========================================================
 시연 점검 끝: 통과 $PASS · 실패 $FAIL · 확인 못 함 $UNKNOWN · 참고 $INFO
 화면 읽기: ${UI_DUMPS}번, 가장 오래 ${UI_DUMP_MAX}초, 시간 초과 ${UI_TIMEOUTS}번, 왼쪽 시스템 창만 읽힘 ${UI_DV_ONLY}번(다시 읽어 해결 ${UI_DV_FIXED}번)
 결과 폴더: $OUT
 Claude에게 보낼 것: report.txt (실패·확인 못 함이 있으면 그 단계의 stepNN-*.png도)
==========================================================
EOF
if [ "$FAIL" -gt 0 ]; then
  exit 1
elif [ "$UNKNOWN" -gt 0 ]; then
  exit 3
fi
exit 0
