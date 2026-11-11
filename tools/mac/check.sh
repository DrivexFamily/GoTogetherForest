#!/usr/bin/env bash
# shellcheck source-path=SCRIPTDIR
# =============================================================================
# 같이가숲 — 신호 확인 화면 자동 점검 (Mac)
#
# 사용법: bash ~/Projects/familyjourney/tools/mac/check.sh
#   (먼저 try.sh로 에뮬레이터를 켜고 앱을 설치해 둔다. 브라우저의 Virtual Console 탭은 닫아도 된다)
#
# 하는 일
#   - 앱의 "신호 확인 (개발용)" 화면을 자동으로 연다
#   - 단계마다 에뮬레이터 화면을 위에서부터 4번 나눠 찍고(PNG), 보이는 글자를 기록한다(TXT)
#   - Virtual Console 값(주행거리·배터리 에너지·브레이크·기어·차속)을 HTTP API로 직접 바꾼다
#     (API가 안 되면 사람에게 바꿔 달라고 묻는다)
#   - 기어가 P이면 무드등을 흰색(#ffffff)으로 초기화한 뒤 "무드등 시험"을 누르고 바뀌었는지 기록한다.
#     이어서 "음성 시험"을 누르고 결과를 기록한다
#   - Virtual Console 항목 목록(catalog)과 앱 로그도 함께 저장한다
#   - 결과는 저장소 안 captures/verification/check/ggf-check-날짜시각 폴더(git에 올라가지 않음). 그 안의 report.txt를 Claude에게 보낸다
#
# Virtual Console 신호(2026-11-02 Mac 세션 확인): 콘솔 웹 화면은 1초마다 POST /browser/session?client=이름 을
#   보내고, 이 신호가 끊기면 쓰기(PUT)가 503이 된다. 그래서 시작할 때 같은 신호를 1초마다 보내는 배경 루프를 켜고
#   (client=ggf-check), 끝날 때(정상 끝·실패·Ctrl+C) 끄고 &closing=1 을 보낸다. 탭 없이도 쓰기가 되고,
#   탭이 차속 값을 덮어쓰는 일도 없다. 첫 신호가 실패했을 때만 브라우저 탭을 열어 달라고 묻는다.
# 화면 읽기(uiautomator dump)는 매우 느릴 수 있다(수십 초~약 17분). 한 번 읽은 결과에서 여러 글자를 찾고,
#   한 번 읽기가 GGF_UI_TIMEOUT(기본 90초)을 넘으면 "화면 읽기 시간 초과"로 적고 계속 간다.
#   한 단계에서 시간 초과가 나면 그 단계의 나머지 위치는 사진만 찍는다.
#   읽은 결과가 왼쪽 시스템 창(ai.umos.drivingview)뿐이면 앱 빈 곳을 한 번 누르고 한 번만 다시 읽는다(report에 적음).
#
# 무엇을 바꾸나: 에뮬레이터 화면 터치·스크롤(앱 안), Virtual Console 값(주행거리 +10km, 배터리, 브레이크, 기어, 차속,
#               무드등 색), adb 포트 포워딩(8088), 바탕화면에 결과 폴더 하나.
#               중간에 멈추면(실패·Ctrl+C) 차속 0·기어 P로 되돌린다.
# 다시 실행해도 되나: 된다. 실행할 때마다 새 폴더를 만든다(주행거리는 줄일 수 없어서 실행마다 10km씩 늘어난다).
# 설정(환경 변수): GGF_UI_TIMEOUT(화면 읽기 한도 초, 기본 90), GGF_VC_CLIENT(신호 이름, 기본 ggf-check)
# Virtual Console API (2026-11-01 Mac 세션이 웹 화면 JS에서 확인): 모두 본문 없음, 200이면 전체 상태 JSON
#   PUT /vehicle/odometer?traveled_distance_km=N   (줄이기 불가, 400)
#   PUT /vehicle/ev-battery?field=battery_level&value=kWh
#   PUT /vehicle/controls?speed=&accelerator=&brake=   (%, 차속 km/h)
#   PUT /vehicle/gear?gear=Park|Reverse|Neutral|Drive
#   PUT /vehicle/body?ambient_color=%23ffffff        (무드등 색)
#   GET /vehicle/state, GET /vehicle/catalog
#   POST /browser/session?client=이름 [&closing=1]   (살아 있음 신호, 공용 함수 tools/mac/check-common.sh)
# 검증: bash -n, shellcheck, 가짜 adb·curl로 흐름 확인(클라우드). Mac 실행은 팀원·Mac 세션 확인 대기.
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"
VC="http://127.0.0.1:8088"
VC_CLIENT="${GGF_VC_CLIENT:-ggf-check}"
# 결과는 저장소 안 captures/verification/(git에 올라가지 않음, .gitignore). 2026-11-04 바탕화면에서 옮김
OUT="$ROOT/captures/verification/check/ggf-check-$(date +%Y%m%d-%H%M%S)"
REPORT="$OUT/report.txt"
W=1920
H=1080
APP_ID=""

say() { printf '\n▶ %s\n' "$*"; }
die() { printf '\n[멈춤] %s\n' "$*" >&2; exit 1; }
note() { printf '%s\n' "$*" >>"$REPORT"; }

# shellcheck source=check-common.sh
. "$SCRIPT_DIR/check-common.sh"
SCROLL_UP_FROM=75   # 신호 확인 화면은 목록이 길어서 조금 더 크게 넘긴다
SCROLL_UP_TO=30

[ -x "$ADB" ] || die "adb가 없습니다. 먼저 try.sh를 실행해 주세요."
"$ADB" get-state >/dev/null 2>&1 || die "에뮬레이터가 연결되어 있지 않습니다. 먼저 try.sh를 실행해 주세요."
APP_ID="$(awk -F= '$1 == "app.applicationId" { gsub(/[ \t\r]/, "", $2); print $2 }' "$ROOT/gradle.properties")"
mkdir -p "$OUT"
: >"$REPORT"
note "같이가숲 자동 점검 $(date '+%Y-%m-%d %H:%M:%S')"
note "앱: $APP_ID"

# 화면 크기 (예: Physical size: 1920x1080)
SIZE="$("$ADB" shell wm size 2>/dev/null | awk -F': ' '/size/ { s = $2 } END { print s }' | tr -d '\r')"
W="${SIZE%x*}"
H="${SIZE#*x}"
case "$W$H" in *[!0-9]* | '') W=1920; H=1080 ;; esac
note "화면: ${W}x${H}"
ui_init

# ---- 단계 기록: 화면 사진 + 글자 ----
STEP=0
capture() {
  # 신호 확인 화면은 목록(LazyColumn)이라 보이는 줄만 읽힌다. 위에서부터 4번 내려가며 찍고 읽는다.
  # 화면 읽기가 시간 초과면 그 단계의 나머지 위치는 사진만 찍는다(느린 읽기가 이어지지 않게).
  local title="$1" pos photo_only=0
  STEP=$((STEP + 1))
  sleep 3
  for _ in 1 2 3 4; do scroll_down; done   # 맨 위로
  note ""
  note "===== 단계 $STEP: $title ($(date '+%H:%M:%S')) ====="
  for pos in 1 2 3 4; do
    "$ADB" exec-out screencap -p >"$OUT/step$STEP-$pos.png" 2>/dev/null || true
    note "-- 화면 위치 $pos --"
    if [ "$photo_only" = 0 ]; then
      refresh_ui
      if [ "$UI_OK" = 1 ]; then
        ui_lines >>"$REPORT"
      else
        note "($(ui_fail_reason) — 사진 step$STEP-$pos.png 참고)"
        if [ "$UI_TIMED_OUT" = 1 ]; then photo_only=1; fi
      fi
    else
      note "(화면 읽기 시간 초과 뒤라 사진만: step$STEP-$pos.png)"
    fi
    scroll_up
  done
  for _ in 1 2 3 4; do scroll_down; done
  echo "   기록함: 단계 $STEP (사진 step$STEP-1~4.png)"
}
ask() {
  printf '\n👉 %s\n   다 했으면 Enter를 누르세요 (건너뛰려면 s 입력 후 Enter): ' "$1"
  local r
  read -r r || r="s"   # 입력이 없으면(터미널이 아님) 건너뛴 것으로 본다
  [ "$r" != "s" ]
}

# 바꾸고 기록. API가 안 되면 사람에게 묻는다.
change() {  # change "제목" "사람 안내" "API 경로"...
  local title="$1" human="$2" ok=1 path
  shift 2
  for path in "$@"; do vc_put "$path" || ok=0; done
  if [ "$ok" = 1 ]; then
    echo "   Virtual Console 변경: $title"
    capture "$title"
  elif ask "$human"; then
    capture "$title (사람이 변경)"
  else
    note "(건너뜀: $title)"
  fi
}

# ---- Virtual Console 신호 루프 ----
if vc_connect; then
  echo "   Virtual Console 신호 루프를 켰습니다(client=$VC_CLIENT). 브라우저 탭은 닫아도 됩니다."
  vc_close_body >/dev/null || note "[주의] 문·프렁크 닫기를 쓰지 못함(왼쪽 안내에 '도어 열림'이 남을 수 있음, 앱과는 무관)"
else
  note "[주의] Virtual Console 신호 실패(첫 응답 ${VC_SIGNAL_FIRST:-000}). 쓰기가 503이 될 수 있음"
  # 신호 루프가 안 될 때만: 브라우저 탭이 대신 신호를 보내게 한다(대신 탭이 차속 값을 덮어쓸 수 있음)
  ask "Virtual Console 신호를 보내지 못했습니다(응답 ${VC_SIGNAL_FIRST:-000}). 브라우저에서 Virtual Console을 열어 두세요(bash $SCRIPT_DIR/emulator.sh console). 그 탭이 차속 값을 덮어쓸 수 있습니다" || true
fi
if [ -n "$VC_CLIENTS_BEFORE" ] && [ "$VC_CLIENTS_BEFORE" != "0" ]; then
  note "[참고] 시작 전 브라우저 Virtual Console 클라이언트 ${VC_CLIENTS_BEFORE}개(탭이 열려 있었을 수 있음). 탭은 차속 값을 덮어쓸 수 있다. 점검 때는 탭이 필요 없다."
fi

# 끝날 때 신호 루프를 끈다. 중간에 멈췄으면(실패·Ctrl+C) 먼저 차속 0·기어 P로 되돌린다(쓰기에 신호가 필요해서 이 순서)
finish() {
  local rc=$?
  trap - EXIT INT TERM HUP
  set +e
  if [ "$rc" != 0 ]; then
    { vc_put "/vehicle/controls?speed=0&accelerator=0&brake=0" 2 && vc_put "/vehicle/gear?gear=Park" 2; } >/dev/null 2>&1 ||
      note "[주의] 끝 정리: 기어 P·차속 0으로 바꾸지 못함"
  fi
  common_cleanup
  exit "$rc"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP

# 차속 권한은 현재 사용자(보통 10)에게 허용해야 앱에 차속이 나온다
CUR_USER="$("$ADB" shell am get-current-user 2>/dev/null | tr -dc '0-9' || true)"
"$ADB" shell pm grant --user "${CUR_USER:-0}" "$APP_ID" android.car.permission.CAR_SPEED >/dev/null 2>&1 || true
note "현재 사용자: ${CUR_USER:-알 수 없음} (차속 권한 허용 시도)"

if ! vc_put "/vehicle/gear?gear=Park"; then
  note "[주의] 시작할 때 기어를 P로 바꾸지 못함"
  ask "Virtual Console에서 기어를 P로 바꿔 주세요(무드등·음성 시험은 P에서만 합니다)" || true
fi
vc_put "/vehicle/controls?speed=0&accelerator=0&brake=0" || true

# ---- 0. 앱 열기 → 신호 확인 화면 ----
say "앱을 앞으로 가져와 '신호 확인' 화면을 엽니다"
"$ADB" shell monkey -p "$APP_ID" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
ui_stale
sleep 3
ensure_ui   # 화면을 한 번 읽고 두 글자를 그 안에서 찾는다
if [ -z "$(center_of '누적 주행거리')" ] && [ -z "$(center_of '신호 (앱이')" ]; then
  if ! tap_text "신호 확인"; then
    note "[주의] '신호 확인' 버튼을 찾지 못함"
    ask "에뮬레이터에서 앱의 '신호 확인 (개발용)'을 직접 눌러 주세요" || true
  fi
  sleep 2
fi

# ---- 1. 처음 상태 ----
say "1. 처음 상태를 기록합니다"
capture "처음 상태"

# ---- 2. Virtual Console 값 바꾸기 (자동) ----
say "2. Virtual Console 값을 하나씩 바꾸며 앱이 따라 바뀌는지 기록합니다"
ODO="$(vc_get odometer.traveled_distance_km | cut -d. -f1)"
if [ -n "$ODO" ]; then
  change "주행거리 +10km ($ODO → $((ODO + 10)))" "Emulation Controls에서 주행거리를 10km 늘려 주세요" "/vehicle/odometer?traveled_distance_km=$((ODO + 10))"
else
  change "주행거리 +10km" "Emulation Controls에서 주행거리를 10km 늘려 주세요" "/vehicle/odometer?traveled_distance_km=__모름__"
fi
change "배터리 에너지 60kWh" "배터리 에너지를 60 kWh로 바꿔 주세요" "/vehicle/ev-battery?field=battery_level&value=60"
change "브레이크 50" "BRAKE를 50으로 올려 주세요" "/vehicle/controls?speed=0&accelerator=0&brake=50"
change "브레이크 0" "BRAKE를 0으로 내려 주세요" "/vehicle/controls?speed=0&accelerator=0&brake=0"

# ---- 3. 무드등·음성 시험 (기어 P일 때 자동) ----
say "3. 무드등·음성 시험 (기어가 P일 때만)"
ensure_ui
if has_text "PARK"; then
  # 무드등을 흰색으로 되돌려 두고, 시험 뒤 바뀌었는지 본다(지난 시험의 초록이 남아 있어도 판정할 수 있게)
  if vc_reset_ambient; then MOOD_RESET="200"; else MOOD_RESET="실패"; fi
  MOOD_BEFORE="$(vc_get body.ambient_light)"
  note "무드등 초기화(#ffffff): $MOOD_RESET"
  note "무드등 시험 전 Virtual Console 무드등: $MOOD_BEFORE"
  if tap_text "무드등 시험"; then sleep 3; capture "무드등 시험 누른 뒤"; else note "[주의] '무드등 시험' 버튼을 찾지 못함"; fi
  MOOD_AFTER="$(vc_get body.ambient_light)"
  if [ -n "$MOOD_AFTER" ] && [ "$MOOD_AFTER" != "$MOOD_BEFORE" ]; then MOOD_CHANGED="바뀜"; else MOOD_CHANGED="안 바뀜"; fi
  note "무드등 시험 뒤 Virtual Console 무드등: $MOOD_AFTER ($MOOD_CHANGED, 초기화 $MOOD_RESET)"
  echo "   무드등: $MOOD_CHANGED"
  echo "   음성 시험을 누릅니다. 에뮬레이터에서 소리가 나는지 들어 보세요."
  if tap_text "음성 시험"; then sleep 20; capture "음성 시험 누른 뒤"; else note "[주의] '음성 시험' 버튼을 찾지 못함"; fi
  printf '   소리가 들렸나요? (y/n): '
  read -r heard || heard=""
  note "음성 들림(사람 확인): ${heard:-응답 없음}"
else
  note "[건너뜀] 기어가 P가 아니어서(또는 화면을 읽지 못해서) 무드등·음성 시험을 하지 않음"
  echo "   기어가 P가 아니라서(또는 화면을 읽지 못해서) 건너뜁니다."
fi

# ---- 4. 주행 (자동) ----
say "4. 주행 확인 (기어 D, 차속 30km/h) — 앱이 가려지지 않는지, 차속이 나오는지"
change "기어 D, 차속 30km/h" "Virtual Console에서 기어를 D로 바꾸고 ACCEL을 눌러 주세요" "/vehicle/gear?gear=Drive" "/vehicle/controls?speed=30&accelerator=0&brake=0"
note "주행 중 맨 앞 화면: $("$ADB" shell dumpsys activity activities 2>/dev/null | grep -m 1 -i 'ResumedActivity' | tr -s ' ' || true)"
change "다시 P, 차속 0" "기어를 다시 P로 돌려 주세요" "/vehicle/controls?speed=0&accelerator=0&brake=0" "/vehicle/gear?gear=Park" "/vehicle/controls?speed=0&accelerator=0&brake=0"

# ---- 5. 부가 자료 ----
say "5. Virtual Console 항목 목록과 앱 로그를 저장합니다"
curl -s --noproxy '*' --max-time 5 "$VC/vehicle/catalog" -o "$OUT/vc-catalog.json" || echo "   (catalog를 받지 못함)"
vc_state >"$OUT/vc-state.json"
curl -s --noproxy '*' --max-time 5 "$VC/" -o "$OUT/vc-index.html" || true
"$ADB" logcat -d -s GgfVehicle GgfNavi GgfControl GgfVoice >"$OUT/logcat.txt" 2>/dev/null || true
note ""
note "===== 앱 로그 (마지막 80줄) ====="
tail -n 80 "$OUT/logcat.txt" >>"$REPORT" 2>/dev/null || true
note ""
note "vc-catalog.json 크기: $(wc -c <"$OUT/vc-catalog.json" 2>/dev/null || echo 0) 바이트"
ui_summary

open "$OUT" 2>/dev/null || true
cat <<EOF

==========================================================
 점검 끝! 결과 폴더가 열렸습니다:
   $OUT
 화면 읽기: ${UI_DUMPS}번, 가장 오래 ${UI_DUMP_MAX}초, 시간 초과 ${UI_TIMEOUTS}번, 왼쪽 시스템 창만 읽힘 ${UI_DV_ONLY}번(다시 읽어 해결 ${UI_DV_FIXED}번)
 Claude에게 보낼 것: report.txt (가능하면 vc-catalog.json도)
 사진(step*.png)은 Claude가 요청하는 것만 보내시면 됩니다.
==========================================================
EOF
