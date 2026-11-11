#!/usr/bin/env bash
# shellcheck shell=bash
# shellcheck disable=SC2034  # UI_TIMED_OUT, FOUND_TEXT, MOTION 등은 이 파일을 읽는 스크립트가 쓴다
# =============================================================================
# 같이가숲 — 점검 스크립트 공용 함수 (Mac)
#
# check.sh, demo-check.sh가 source로 읽는다. 직접 실행하지 않는다.
#
# 들어 있는 것
#   1. Virtual Console "살아 있음" 신호 루프 (vc_connect, vc_disconnect)
#      콘솔 웹 화면은 1초마다 POST /browser/session?client=이름 을 보낸다. 이 신호가 끊기면
#      쓰기(PUT)가 503으로 막힌다(docs/sdk-capabilities.md 11-3, docs/emulator-capabilities.md 8장).
#      그래서 점검 스크립트가 같은 신호를 배경에서 1초마다 보낸다. 끝날 때(정상 끝·실패·Ctrl+C)는
#      루프를 끄고 &closing=1 을 한 번 보낸다. 이 신호만 있으면 브라우저 탭 없이도 쓰기가 되고,
#      탭이 차속 값을 덮어쓰는 일도 없다.
#   2. Virtual Console API: vc_put(실패하면 다시 시도), vc_state, vc_get, vc_reset_ambient(무드등 흰색으로),
#      vc_close_body(문·프렁크·트렁크·창문 닫기: 에뮬레이터 왼쪽 안내의 "도어 열림" 경고를 없앤다)
#   3. 화면 글자 읽기(uiautomator dump): 한 번 읽어 두고 그 안에서 여러 번 찾는다
#      dump가 매우 느릴 수 있다(한 번에 수십 초, 길게는 약 17분, 2026-11-02 Mac 확인). 그래서
#      - refresh_ui가 읽은 결과를 변수(UI_XML, UI_LINES)에 둔다. has_text 등은 그 안에서만 찾는다.
#      - 화면을 바꾸는 동작(누르기, 스크롤, Virtual Console 쓰기, 앱 실행) 뒤에는 ui_stale로
#        "다시 읽어야 함" 표시만 한다. ensure_ui는 그 표시가 있을 때만 다시 읽는다.
#      - 한 번 읽기에 한도(GGF_UI_TIMEOUT, 기본 90초)를 둔다. 넘으면 "화면 읽기 시간 초과"로
#        적고 계속 간다(UI_OK=0). 에뮬레이터 안 timeout 명령이 있으면 그쪽에서도 끊는다.
#      - dump가 가끔 왼쪽 시스템 창(ai.umos.drivingview)만 읽고 우리 앱 노드는 0개다(2026-11-02 Mac 확인,
#        앱 영역을 한 번 누르면 다시 앱을 읽음). 그래서 읽은 결과가 drivingview 노드뿐이면, 앱이 맨 앞일 때만
#        앱 영역의 빈 곳(위쪽 여백 가운데, 버튼이 없는 점)을 한 번 누르고 한 번만 다시 읽는다. report에 적는다.
#        다시 읽어도 앱 노드가 없으면 그 화면은 "읽지 못함"(UI_OK=0)으로 둔다(없어야 하는 글자를 잘못 통과시키지 않게).
#        이때는 화면을 바꾸는 동작(ui_stale)이나 앱을 다시 읽기 전까지 더 누르지 않는다(기다리는 동안 계속 누르지 않게).
#   4. 화면 움직임 비교 (frames_start, frames_wait, frames_compare)
#      dump 없이 screencap(원시 픽셀)만 배경에서 빠르게 여러 장 찍고, 앱 영역만 비교한다.
#      앱 영역 좌표(APP_BOUNDS)는 앱이 보이는 첫 화면 읽기에서 한 번만 구해 둔다.
#
# 셸 규칙
#   - ensure_ui, refresh_ui, tap_text, wait_text, wait_any, tap_app_blank는 변수를 바꾸므로 $( ) 안에서 부르지 않는다.
#     has_text, ui_lines, node_of, center_of, chip_state_of, ui_fail_reason은 읽기만 하므로 $( ) 안에서 불러도 된다.
#   - macOS 기본 bash 3.2에서 돌도록 bash 4 문법(연관 배열, mapfile, EPOCHREALTIME 등)을 쓰지 않는다.
#   - set -e인 스크립트(check.sh)에서도 멈추지 않도록 실패할 수 있는 명령은 || true로 감싼다.
#
# 부르는 쪽이 먼저 정할 것: ADB, VC, OUT, REPORT, APP_ID, W, H, VC_CLIENT, 함수 note
# 무엇을 바꾸나: 이 파일 자체는 아무것도 바꾸지 않는다(함수만 정의).
# 검증: bash -n, shellcheck, 가짜 adb·curl로 흐름 확인(클라우드). demo-check.sh로 Mac에서 돎(2026-11-02, 통과 27).
#       왼쪽 시스템 창 대처(2026-11-02 추가)는 가짜 adb로만 확인. 실제 에뮬레이터에서는 Mac 담당 세션 확인 대기.
# =============================================================================

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  printf '이 파일은 직접 실행하지 않습니다. check.sh 또는 demo-check.sh를 실행해 주세요.\n' >&2
  exit 1
fi

UI_LIMIT="${GGF_UI_TIMEOUT:-90}"
case "$UI_LIMIT" in '' | *[!0-9]*) UI_LIMIT=90 ;; esac
SCROLL_UP_FROM=70   # 스크롤 시작·끝(화면 높이의 %). check.sh는 75/30을 쓴다
SCROLL_UP_TO=35

# ---- 시간 ----
now_ms() {  # 지금 시각(밀리초). perl → python3 → date(초 단위) 순서로 쓴다
  perl -MTime::HiRes=time -e 'printf("%d\n", time() * 1000)' 2>/dev/null ||
    python3 -c 'import time; print(int(time.time() * 1000))' 2>/dev/null ||
    printf '%s000\n' "$(date +%s)"
}
ms_to_s() {  # 1234 → 1.2, -300 → -0.3
  local v="$1" sign=""
  if [ "$v" -lt 0 ]; then
    sign="-"
    v=$((-v))
  fi
  printf '%s%d.%d' "$sign" $((v / 1000)) $(((v % 1000) / 100))
}

# ---- 한도 있는 실행 (macOS에는 timeout 명령이 없어서 직접 만든다) ----
BG_PID=""
run_limited() {  # run_limited 초 출력파일 명령... → 명령의 끝 코드, 한도를 넘으면 끊고 124
  local limit="$1" out="$2" start rc=0
  shift 2
  "$@" >"$out" 2>/dev/null </dev/null &
  BG_PID=$!
  start=$SECONDS
  while kill -0 "$BG_PID" 2>/dev/null; do
    if [ $((SECONDS - start)) -ge "$limit" ]; then
      kill "$BG_PID" 2>/dev/null || true
      wait "$BG_PID" 2>/dev/null || true
      BG_PID=""
      return 124
    fi
    sleep 0.5
  done
  wait "$BG_PID" || rc=$?
  BG_PID=""
  return "$rc"
}

# ---- Virtual Console 신호 루프 ----
VC_SIGNAL_PID=""
VC_SIGNAL_FIRST=""
VC_CLIENTS_BEFORE=""
vc_signal_once() {  # vc_signal_once [closing] → HTTP 코드를 출력(연결 실패는 000)
  local q="client=$VC_CLIENT"
  if [ "${1:-}" = closing ]; then q="$q&closing=1"; fi
  curl -s -o /dev/null -w '%{http_code}' --noproxy '*' --max-time 2 -X POST "$VC/browser/session?$q" 2>/dev/null || true
}
vc_connect() {  # adb 포트(8088)를 잇고 신호 루프를 켠다. 첫 신호가 2xx면 0
  local stop="$OUT/.vc-signal-stop" errs="$OUT/vc-signal-errors.txt" hold="$OUT/.vc-hold-speed" parent=$$
  "$ADB" forward tcp:8088 tcp:8088 >/dev/null 2>&1 || true
  VC_CLIENTS_BEFORE="$(vc_get browser.clients)"   # 신호를 보내기 전에 센다(우리 신호도 클라이언트로 셀 수 있음)
  VC_SIGNAL_FIRST="$(vc_signal_once)"
  rm -f "$stop" "$hold"
  : >"$errs"
  (
    # 멈춤 파일이 생기거나, 점검 스크립트가 없어지면(강제 종료) 루프도 끝난다
    while [ ! -e "$stop" ] && kill -0 "$parent" 2>/dev/null; do
      code="$(vc_signal_once)"
      case "$code" in
        2[0-9][0-9]) ;;
        *) printf '%s %s\n' "$(date '+%H:%M:%S')" "${code:-000}" >>"$errs" 2>/dev/null || true ;;
      esac
      # 차속 유지(vc_hold_speed): 브라우저 Virtual Console 탭이 열려 있으면 탭이 차속을 조금씩 0으로 내린다
      # (2026-11-06 Mac 확인: 약 2km/h/초). 붙잡을 값이 있으면 1초마다 다시 쓴다.
      if [ -s "$hold" ]; then
        curl -s -o /dev/null --noproxy '*' --max-time 2 -X PUT "$VC/vehicle/controls?speed=$(cat "$hold" 2>/dev/null)&accelerator=0&brake=0" 2>/dev/null || true
      fi
      sleep 1
    done
  ) </dev/null >/dev/null 2>&1 &
  VC_SIGNAL_PID=$!
  note "Virtual Console 신호 루프 시작(client=$VC_CLIENT, 1초마다): 첫 응답 ${VC_SIGNAL_FIRST:-000}, 시작 전 브라우저 클라이언트 ${VC_CLIENTS_BEFORE:-모름}"
  case "$VC_SIGNAL_FIRST" in 2[0-9][0-9]) return 0 ;; esac
  return 1
}
vc_hold_speed() {  # vc_hold_speed 30 → 신호 루프가 1초마다 차속 30을 다시 쓴다(브라우저 탭이 차속을 내려도 유지)
  printf '%s' "$1" >"$OUT/.vc-hold-speed" 2>/dev/null || true
}
vc_release_speed() {  # 차속 붙잡기를 그만둔다(P로 바꾸기 전에 먼저 부른다)
  rm -f "$OUT/.vc-hold-speed"
}
vc_disconnect() {  # 신호 루프를 끄고 "닫음"(&closing=1) 신호를 한 번 보낸다. 여러 번 불러도 된다
  local errs
  vc_release_speed
  if [ -n "$VC_SIGNAL_PID" ]; then
    : >"$OUT/.vc-signal-stop" 2>/dev/null || true
    for _ in 1 2 3 4 5 6 7 8; do   # 루프가 스스로 끝나기를 최대 약 4초 기다린다(curl 2초 + sleep 1초)
      kill -0 "$VC_SIGNAL_PID" 2>/dev/null || break
      sleep 0.5
    done
    kill "$VC_SIGNAL_PID" 2>/dev/null || true
    wait "$VC_SIGNAL_PID" 2>/dev/null || true
    VC_SIGNAL_PID=""
    vc_signal_once closing >/dev/null
    errs="$(grep -c . "$OUT/vc-signal-errors.txt" 2>/dev/null || true)"
    note "Virtual Console 신호 루프 끝(closing=1 보냄). 루프 중 실패 ${errs:-0}번(vc-signal-errors.txt)"
  fi
  rm -f "$OUT/.vc-signal-stop"
}

# ---- Virtual Console API ----
vc_put() {  # vc_put "/vehicle/gear?gear=Park" [횟수] → 200이면 0. 아니면 5초 간격으로 다시(기본 6번)
  local tries="${2:-6}" code body try
  for try in $(seq 1 "$tries"); do
    body="$(curl -s -w '\n%{http_code}' --noproxy '*' --max-time 5 -X PUT "$VC$1" 2>/dev/null || true)"
    code="$(printf '%s' "$body" | tail -n 1)"
    if [ "$code" = "200" ]; then
      note "  API PUT $1 → 200 (시도 $try)"
      ui_stale
      return 0
    fi
    note "  API PUT $1 → ${code:-실패} (시도 $try) 응답: $(printf '%s' "$body" | sed '$d' | head -c 200)"
    if [ "$try" -lt "$tries" ]; then sleep 5; fi
  done
  return 1
}
vc_state() { curl -s --noproxy '*' --max-time 5 "$VC/vehicle/state" 2>/dev/null || true; }
vc_get() {  # vc_get body.ambient_light → 값(객체·배열은 JSON 한 줄). 없으면 빈 값
  vc_state | python3 -c 'import json,sys
d=json.load(sys.stdin)
for k in sys.argv[1].split("."):
    d=d[k]
print(json.dumps(d, ensure_ascii=False) if isinstance(d, (dict, list)) else d)' "$1" 2>/dev/null || true
}
vc_reset_ambient() {  # 무드등 색을 흰색(#ffffff)으로 되돌린다. 시험 뒤 "바뀌었나"를 보기 위해 시험 전에 부른다
  vc_put "/vehicle/body?ambient_color=%23ffffff" 3
}
# 문·프렁크·트렁크·창문을 모두 닫힘(0)으로 쓴다.
# 에뮬레이터 왼쪽 패널(시스템 앱 ai.umos.drivingview의 주행 전 안내)은 기어 P가 될 때마다 처음부터 다시 돌며
# "프렁크를 닫으세요", "도어 열림"을 띄운다. /vehicle/state에는 문이 false로 보여도 한 번도 쓰인 적이 없으면
# 에뮬레이터는 열린 상태로 안다. 그래서 한 번 직접 "닫힘"을 써 준다(2026-11-02 Mac 담당 세션 확인: 바로 사라짐).
# "안전 벨트를 매세요."는 Virtual Console에 벨트 항목이 없어 끌 수 없다. 기어 D에서 차속이 0보다 크면 사라진다.
VC_CLOSE_BODY="/vehicle/body?door_fl=0&door_fr=0&door_rl=0&door_rr=0&frunk=0&trunk=0&window_fl=0&window_fr=0&window_rl=0&window_rr=0"
vc_close_body() { vc_put "$VC_CLOSE_BODY" 3; }

# ---- 화면 글자 읽기 (uiautomator dump, 한 번 읽어 두기) ----
UI_XML=""           # 마지막으로 읽은 화면(노드 하나가 한 줄)
UI_LINES=""         # 그 화면의 글자(text)와 설명(content-desc), 위에서부터 한 줄씩
UI_OK=0             # 마지막 읽기가 성공했으면 1
UI_TIMED_OUT=0      # 마지막 읽기가 시간 초과였으면 1
UI_STALE=1          # 화면을 바꾼 뒤 아직 다시 읽지 않았으면 1
UI_DEVICE_TIMEOUT=""
UI_DUMPS=0
UI_DUMP_SECS=0
UI_DUMP_MAX=0
UI_TIMEOUTS=0
APP_BOUNDS=""       # 앱 창 "x1 y1 x2 y2" (처음 한 번만 구함)
UI_NO_APP=0         # 마지막 읽기가 왼쪽 시스템 창만 읽혀(다시 읽어도) 앱 노드가 없었으면 1
UI_DV_ONLY=0        # 왼쪽 시스템 창만 읽힌 횟수(다시 읽기 전 기준)
UI_DV_TAPS=0        # 그때 앱 빈 곳을 누른 횟수
UI_DV_FIXED=0       # 누르고 다시 읽어 앱 노드를 읽은 횟수
UI_DV_GAVE_UP=0     # 누르고 다시 읽어도 안 풀렸으면 1 → 화면이 바뀌거나(ui_stale) 앱을 다시 읽을 때까지 더 누르지 않는다
UI_DV_SKIPPED=0     # 같은 화면에서 이미 시도해서(누름 또는 누르지 못함) 다시 시도하지 않고 "읽지 못함"으로 둔 횟수
LAST_APP_XML=""     # 앱 노드가 있던 마지막 화면(빈 곳을 고를 때 버튼 자리를 피하려고 둔다)
DV_PKG="ai.umos.drivingview"   # 에뮬레이터 왼쪽 시스템 창(차량 3D 패널·주행 전 안내)
APP_W_X1=853        # 앱 영역을 모를 때 쓰는 앱 창 가로 범위(2560 너비 화면 기준, 2026-11-02 Mac 확인)
APP_W_X2=2535

ui_init() {  # 에뮬레이터 안에 timeout 명령이 있는지 본다(있으면 dump를 그쪽에서도 끊는다)
  if "$ADB" shell 'timeout 5 true' >/dev/null 2>&1; then
    UI_DEVICE_TIMEOUT="timeout $UI_LIMIT"
    note "화면 읽기(uiautomator dump) 한도 ${UI_LIMIT}초 (에뮬레이터 안 timeout 명령 있음)"
  else
    UI_DEVICE_TIMEOUT=""
    note "화면 읽기(uiautomator dump) 한도 ${UI_LIMIT}초 (에뮬레이터 안 timeout 명령 없음 → Mac 쪽에서만 끊음)"
  fi
}
ui_stale() { UI_STALE=1; UI_DV_GAVE_UP=0; }   # 화면이 바뀌었으니 왼쪽 시스템 창 대처(누르기)도 다시 해 본다
ensure_ui() { if [ "$UI_STALE" = 1 ]; then refresh_ui; fi; }
bounds_of_line() {  # 노드 한 줄 → "x1 y1 x2 y2" (없으면 빈 값)
  printf '%s' "$1" | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p'
}
_UI_RAW=""
_ui_dump() {  # dump를 한 번(빈 결과면 3번까지) 받아 _UI_RAW에 둔다. UI_OK·UI_TIMED_OUT·통계를 채운다
  local start_ms took_ms took=0 rc raw="$OUT/.ui.xml" host_limit
  UI_OK=0
  UI_TIMED_OUT=0
  _UI_RAW=""
  # 에뮬레이터 안 timeout이 있으면 그쪽이 먼저 끊고, Mac 쪽은 15초 더 기다린 뒤 끊는다(예비). 없으면 Mac 쪽이 한도에서 끊는다
  if [ -n "$UI_DEVICE_TIMEOUT" ]; then host_limit=$((UI_LIMIT + 15)); else host_limit="$UI_LIMIT"; fi
  for _ in 1 2 3; do   # 가끔 금방 빈 결과가 나온다 → 3번까지. 시간 초과면 다시 하지 않는다
    start_ms="$(now_ms)"
    rc=0
    # 지난번 파일을 먼저 지운다(이번 읽기가 실패했을 때 옛 화면을 읽지 않도록)
    run_limited "$host_limit" "$raw" "$ADB" exec-out \
      "rm -f /sdcard/ggf-ui.xml; $UI_DEVICE_TIMEOUT uiautomator dump /sdcard/ggf-ui.xml >/dev/null 2>&1; cat /sdcard/ggf-ui.xml 2>/dev/null" || rc=$?
    took_ms=$(($(now_ms) - start_ms))
    took=$(((took_ms + 500) / 1000))
    UI_DUMPS=$((UI_DUMPS + 1))
    UI_DUMP_SECS=$((UI_DUMP_SECS + took))
    if [ "$took" -gt "$UI_DUMP_MAX" ]; then UI_DUMP_MAX="$took"; fi
    _UI_RAW="$(cat "$raw" 2>/dev/null || true)"
    case "$_UI_RAW" in *bounds=*) UI_OK=1; break ;; esac
    _UI_RAW=""
    # 한도 가까이(1.5초 이내)까지 걸렸으면 시간 초과로 본다(에뮬레이터 안 timeout이 끊은 경우)
    if [ "$rc" = 124 ] || [ "$took_ms" -ge $((UI_LIMIT * 1000 - 1500)) ]; then
      UI_TIMED_OUT=1
      UI_TIMEOUTS=$((UI_TIMEOUTS + 1))
      note "  [화면 읽기 시간 초과] ${took}초(한도 ${UI_LIMIT}초). 이 화면의 글자는 읽지 못하고 계속 진행"
      printf '   (화면 읽기 시간 초과 %s초 — 계속 진행)\n' "$took"
      "$ADB" shell pkill -f com.android.commands.uiautomator >/dev/null 2>&1 || true
      break
    fi
    sleep 1
  done
  rm -f "$raw"
  if [ "$took" -ge 10 ]; then note "  (화면 읽기 ${took}초 걸림)"; fi
  return 0
}
UI_APP_NODES=0
UI_DV_NODES=0
UI_OTHER_NODES=0
_ui_count() {  # _ui_count XML → UI_APP_NODES(우리 앱), UI_DV_NODES(왼쪽 시스템 창), UI_OTHER_NODES(그 밖) 노드 수
  local nodes total
  nodes="$(printf '%s' "$1" | tr '<' '\n' | grep '^node ' || true)"
  total="$(printf '%s\n' "$nodes" | grep -c '^node ' || true)"
  UI_APP_NODES="$(printf '%s\n' "$nodes" | grep -cF "package=\"$APP_ID\"" || true)"
  UI_DV_NODES="$(printf '%s\n' "$nodes" | grep -cF "package=\"$DV_PKG\"" || true)"
  UI_OTHER_NODES=$((${total:-0} - ${UI_APP_NODES:-0} - ${UI_DV_NODES:-0}))
}
point_on_button() {  # point_on_button x y → 마지막 앱 화면에서 그 점이 누를 수 있는 노드(clickable="true") 안이면 0
  [ -n "$LAST_APP_XML" ] || return 1
  printf '%s\n' "$LAST_APP_XML" | awk -v x="$1" -v y="$2" '
    / clickable="true"/ && match($0, /bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"/) {
      s = substr($0, RSTART + 8, RLENGTH - 9); gsub(/\]\[/, ",", s); gsub(/\[/, "", s); gsub(/\]/, "", s)
      split(s, b, ",")
      if (x >= b[1] + 0 && x < b[3] + 0 && y >= b[2] + 0 && y < b[4] + 0) { found = 1; exit }
    }
    END { exit (found ? 0 : 1) }'
}
app_window_frame() {  # dumpsys window에서 앱 창 좌표 "x1 y1 x2 y2" (못 찾으면 빈 값)
  # [가정] Android 12 이상 형식: "Window #N Window{… 앱ID/…}:" 블록 안의 "Frames: … frame=[l,t][r,b] …"
  "$ADB" shell dumpsys window windows 2>/dev/null | tr -d '\r' | awk -v id="$APP_ID/" '
    index($0, "Window #") && index($0, "Window{") { inwin = (index($0, id) > 0); next }
    inwin && match($0, / frame=\[-?[0-9]+,-?[0-9]+\]\[-?[0-9]+,-?[0-9]+\]/) {
      s = substr($0, RSTART + 7, RLENGTH - 7); gsub(/\]\[/, ",", s); gsub(/\[/, "", s); gsub(/\]/, "", s)
      split(s, b, ","); print b[1], b[2], b[3], b[4]; exit
    }' || true
}
TAP_NOTE=""
tap_app_blank() {  # 앱이 맨 앞이면 앱 영역의 빈 곳을 한 번 누른다. 누르면 0. TAP_NOTE에 좌표와 근거(또는 안 누른 이유)
  # 빈 곳: 앱 창 위쪽 여백(창 맨 위에서 6px 아래). 앱 화면은 모두 바깥 여백(8dp 이상)이 있어 이 줄에는 버튼이 없다.
  # 가운데 → 왼쪽 1/4 → 오른쪽 1/4 순서로, 마지막으로 읽은 앱 화면의 버튼 자리를 피해 고른다.
  local front b src x1 y1 x2 y2 y x cand
  front="$("$ADB" shell dumpsys activity activities 2>/dev/null | tr -d '\r' | grep -i 'ResumedActivity' || true)"
  if ! grep -qF "$APP_ID/" <<<"$front"; then
    TAP_NOTE="앱이 맨 앞 화면이 아니라 누르지 않음(맨 앞: $(printf '%s\n' "$front" | head -n 1 | tr -s ' ' | cut -c 1-160))"
    return 1
  fi
  b="$APP_BOUNDS"
  src="앱 영역 $APP_BOUNDS(화면 읽기)"
  if [ -z "$b" ]; then
    b="$(app_window_frame)"
    src="앱 창 $b(dumpsys window)"
  fi
  if [ -n "$b" ]; then
    read -r x1 y1 x2 y2 <<<"$b"
    y=$((y1 + 6))
    if [ "$y" -ge "$y2" ]; then y=$(((y1 + y2) / 2)); fi
  else
    # 앱 영역을 모를 때: 앱 창 가로 범위(853~2535/2560 비율), 위쪽 약 7%(맨 위 시스템 상태 줄 아래)
    x1=$((W * APP_W_X1 / 2560))
    x2=$((W * APP_W_X2 / 2560))
    y=$((H * 7 / 100))
    src="앱 영역 모름 → 기본값(가로 ${x1}~${x2}, 화면 높이의 7%)"
  fi
  [ "$y" -lt "$H" ] || y=$((H - 1))
  x=""
  for cand in $(((x1 + x2) / 2)) $((x1 + (x2 - x1) / 4)) $((x2 - (x2 - x1) / 4)); do
    if ! point_on_button "$cand" "$y"; then
      x="$cand"
      break
    fi
  done
  if [ -z "$x" ]; then
    TAP_NOTE="빈 곳을 찾지 못해 누르지 않음(후보가 모두 버튼 자리, $src)"
    return 1
  fi
  "$ADB" shell input tap "$x" "$y" >/dev/null 2>&1 || true
  UI_DV_TAPS=$((UI_DV_TAPS + 1))
  TAP_NOTE="($x,$y), 근거: $src 위쪽 여백"
  return 0
}
refresh_ui() {  # 화면을 한 번 읽어 UI_XML·UI_LINES에 둔다. 늘 0으로 끝난다(결과는 UI_OK)
  local xml="" app_line dv_n other_n
  UI_XML=""
  UI_LINES=""
  UI_STALE=0
  UI_NO_APP=0
  _ui_dump
  xml="$_UI_RAW"
  if [ "$UI_OK" = 1 ]; then
    _ui_count "$xml"
    # 앱이 아닌 창만 읽힘(앱 노드 0, 왼쪽 시스템 창이나 다른 앱 창의 노드만 있음) → 앱 빈 곳을 한 번 누르고 한 번만 다시 읽는다.
    # 3분할(차량 | 내비 | 앱)에서는 내비 창만 읽히기도 한다(2026-11-05 Mac 제안). 앱이 맨 앞이 아니면 tap_app_blank가 누르지 않는다.
    if [ "$UI_APP_NODES" = 0 ] && { [ "$UI_DV_NODES" -gt 0 ] || [ "$UI_OTHER_NODES" -gt 0 ]; }; then
      UI_DV_ONLY=$((UI_DV_ONLY + 1))
      dv_n="$UI_DV_NODES"
      other_n="$UI_OTHER_NODES"
      if [ "$UI_DV_GAVE_UP" = 1 ]; then
        UI_DV_SKIPPED=$((UI_DV_SKIPPED + 1))   # 이 화면에서 이미 시도했다 → 조용히 "읽지 못함"
      elif tap_app_blank; then
        printf '   (화면 읽기: 앱이 아닌 창만 읽혀 앱 빈 곳을 한 번 누르고 다시 읽음)\n'
        sleep 1
        _ui_dump
        xml="$_UI_RAW"
        if [ "$UI_OK" = 1 ]; then _ui_count "$xml"; else UI_APP_NODES=0; fi
        if [ "$UI_APP_NODES" -gt 0 ]; then UI_DV_FIXED=$((UI_DV_FIXED + 1)); fi
        note "  [화면 다시 읽기] 앱 노드 0개, $DV_PKG 노드 ${dv_n}개, 다른 앱 노드 ${other_n}개 → 앱 빈 곳 $TAP_NOTE 한 번 누름 → 다시 읽음: $(
          if [ "$UI_OK" = 1 ]; then printf '앱 노드 %s개, %s 노드 %s개' "$UI_APP_NODES" "$DV_PKG" "$UI_DV_NODES"; else printf '읽지 못함'; fi)"
        if [ "$UI_APP_NODES" = 0 ]; then
          UI_DV_GAVE_UP=1
          note "  [화면 읽기] 다시 읽어도 앱 노드가 없음 → 화면이 바뀔 때까지(또는 앱을 다시 읽을 때까지) 더 누르지 않음"
        fi
      else
        UI_DV_GAVE_UP=1
        note "  [화면 다시 읽기 안 함] 앱 노드 0개, $DV_PKG 노드 ${dv_n}개. $TAP_NOTE → 이 화면의 글자 판정은 '확인 못 함'"
      fi
      if [ "$UI_OK" = 1 ] && [ "$UI_APP_NODES" = 0 ] && [ "$UI_OTHER_NODES" = 0 ]; then
        UI_OK=0
        UI_NO_APP=1
      fi
    fi
  fi
  if [ "$UI_OK" = 1 ]; then
    UI_XML="$(printf '%s' "$xml" | tr '<' '\n')"
    UI_LINES="$(printf '%s\n' "$UI_XML" | awk '
      function dec(s) {
        gsub(/&quot;/, "\"", s); gsub(/&apos;/, "\047", s); gsub(/&lt;/, "<", s); gsub(/&gt;/, ">", s)
        gsub(/&#10;/, " ", s); gsub(/&amp;/, "\\&", s)
        return s
      }
      {
        t = ""; d = ""
        if (match($0, / text="[^"]*"/)) t = dec(substr($0, RSTART + 7, RLENGTH - 8))
        if (match($0, / content-desc="[^"]*"/)) d = dec(substr($0, RSTART + 15, RLENGTH - 16))
        if (t != "") print t
        if (d != "" && d != t) print d
      }' || true)"
    app_line="$(printf '%s\n' "$UI_XML" | grep -F "package=\"$APP_ID\"" | head -n 1 || true)"
    if [ -n "$app_line" ]; then
      LAST_APP_XML="$UI_XML"
      UI_DV_GAVE_UP=0
      if [ -z "$APP_BOUNDS" ]; then
        APP_BOUNDS="$(bounds_of_line "$app_line")"
        if [ -n "$APP_BOUNDS" ]; then note "  앱 영역(처음 한 번만 구함): $APP_BOUNDS"; fi
      fi
    fi
  fi
  return 0
}
ui_fail_reason() {  # 마지막 읽기가 왜 실패했나(UI_OK=0일 때 report 문구)
  if [ "$UI_TIMED_OUT" = 1 ]; then
    printf '화면 읽기 시간 초과'
  elif [ "$UI_NO_APP" = 1 ]; then
    printf '왼쪽 시스템 창만 읽혀 앱 글자를 읽지 못함'
  else
    printf '화면을 읽지 못함'
  fi
}
ui_lines() { if [ -n "$UI_LINES" ]; then printf '%s\n' "$UI_LINES"; fi; }
has_text() {  # 읽어 둔 화면에 글자가 있나(부분 일치). 있으면 0. 다시 읽지 않는다
  case "$UI_LINES" in *"$1"*) return 0 ;; esac
  return 1
}
node_of() {  # 글자 노드 한 줄. 정확히 같은 글자(text 또는 content-desc)를 먼저, 없으면 그 글자로 시작하는 것
  local want="$1" line
  line="$(printf '%s\n' "$UI_XML" | grep -F -e " text=\"$want\"" -e " content-desc=\"$want\"" | head -n 1 || true)"
  if [ -z "$line" ]; then
    line="$(printf '%s\n' "$UI_XML" | grep -F -e " text=\"$want" -e " content-desc=\"$want" | head -n 1 || true)"
  fi
  printf '%s' "$line"
}
center_of() {  # "x y" (없으면 빈 줄)
  local b x1 y1 x2 y2
  b="$(bounds_of_line "$(node_of "$1")")"
  [ -n "$b" ] || return 0
  read -r x1 y1 x2 y2 <<<"$b"
  printf '%s %s\n' $(((x1 + x2) / 2)) $(((y1 + y2) / 2))
}
# 고르기 칩의 상태. Compose 칩은 글자 노드의 **부모**가 checkable="true"이고 거기에 checked가 있다
# (자식 글자 노드는 늘 checked="false"). 그 글자 노드부터 위로 올라가 가장 가까운 checkable 노드의
# checked="…" selected="…"를 출력한다. 정확히 같은 글자를 먼저, 없으면 그 글자로 시작하는 노드. 못 찾으면 빈 값.
chip_state_of() {
  local r
  r="$(_chip_state "$1" exact)"
  if [ -z "$r" ]; then r="$(_chip_state "$1" prefix)"; fi
  printf '%s' "$r"
}
_chip_state() {
  printf '%s\n' "$UI_XML" | awk -v want="$1" -v mode="$2" '
    function hit(line) {
      if (mode == "exact") return index(line, " text=\"" want "\"") || index(line, " content-desc=\"" want "\"")
      return index(line, " text=\"" want) || index(line, " content-desc=\"" want)
    }
    /^node / {
      depth++
      stack[depth] = $0
      if (hit($0)) {
        for (i = depth; i >= 1; i--) {
          if (index(stack[i], "checkable=\"true\"")) {
            c = ""; s = ""
            if (match(stack[i], / checked="[a-z]*"/)) c = substr(stack[i], RSTART + 1, RLENGTH - 1)
            if (match(stack[i], / selected="[a-z]*"/)) s = substr(stack[i], RSTART + 1, RLENGTH - 1)
            print c " " s
            exit
          }
        }
      }
      if ($0 ~ /\/>[ \t\r]*$/) depth--
      next
    }
    /^\/node>/ { if (depth > 0) depth-- }
  ' || true
}

# ---- 누르기·스크롤 ----
tap_xy() { "$ADB" shell input tap "$1" "$2" >/dev/null 2>&1 || true; ui_stale; }
scroll_up() {
  "$ADB" shell input swipe $((W * 3 / 4)) $((H * SCROLL_UP_FROM / 100)) $((W * 3 / 4)) $((H * SCROLL_UP_TO / 100)) 300 >/dev/null 2>&1 || true
  ui_stale
  sleep 1
}
scroll_down() {
  "$ADB" shell input swipe $((W * 3 / 4)) $((H * 30 / 100)) $((W * 3 / 4)) $((H * 80 / 100)) 300 >/dev/null 2>&1 || true
  ui_stale
  sleep 1
}
tap_text() {  # 글자를 찾아 누른다. 화면 아래에 있으면 스크롤하며 찾는다(최대 5번). 찾으면 0
  local want="$1" n xy x y
  ensure_ui
  for n in 1 2 3 4 5; do
    xy="$(center_of "$want")"
    if [ -n "$xy" ]; then
      read -r x y <<<"$xy"
      if [ "$y" -gt 0 ] && [ "$y" -lt $((H - 20)) ]; then
        tap_xy "$x" "$y"
        sleep 1
        return 0
      fi
    fi
    [ "$UI_OK" = 1 ] || return 1   # 화면을 못 읽었으면 더 찾지 않는다(시간 초과가 이어지지 않게)
    [ "$n" -lt 5 ] || break
    scroll_up
    refresh_ui
  done
  return 1
}
FOUND_TEXT=""
wait_any() {  # wait_any 초 글자1 [글자2 …] → 하나라도 보이면 0(FOUND_TEXT에 그 글자)
  # 한 번 읽을 때마다 글자를 모두 찾아본다. 화면 읽기가 느리면 한도보다 늦게 끝날 수 있다(최소 한 번은 읽음).
  local limit="$1" start=$SECONDS t
  shift
  FOUND_TEXT=""
  ensure_ui
  while :; do
    for t in "$@"; do
      if has_text "$t"; then
        FOUND_TEXT="$t"
        return 0
      fi
    done
    [ $((SECONDS - start)) -lt "$limit" ] || return 1
    sleep 2
    refresh_ui
  done
}
wait_text() { wait_any "${2:-20}" "$1"; }
ui_summary() {
  note "화면 읽기(uiautomator dump): ${UI_DUMPS}번, 합계 ${UI_DUMP_SECS}초, 가장 오래 ${UI_DUMP_MAX}초, 시간 초과 ${UI_TIMEOUTS}번(한도 ${UI_LIMIT}초)"
  note "왼쪽 시스템 창($DV_PKG)만 읽힘 ${UI_DV_ONLY}번 → 앱 빈 곳 누름 ${UI_DV_TAPS}번, 다시 읽어 앱을 읽음 ${UI_DV_FIXED}번, 같은 화면이라 다시 시도하지 않고 넘김 ${UI_DV_SKIPPED}번"
}

# ---- 화면 움직임 비교 (dump 없이 screencap만) ----
FRAMES_PID=""
frames_start() {  # frames_start 폴더 초 최대장수 — 배경에서 화면을 찍는다(원시 픽셀, 장마다 찍기 시작 시각)
  # 장 사이 간격을 (초 ÷ 최대장수) 이상으로 벌려 창 전체에 고르게 찍는다(screencap이 빨라도 앞쪽에 몰리지 않게).
  # screencap 한 장이 그보다 오래 걸리면 쉬지 않고 바로 다음 장을 찍는다.
  local dir="$1" secs="$2" max="$3"
  rm -rf "$dir"
  mkdir -p "$dir"
  (
    n=0
    t0="$(now_ms)"
    gap=$((secs * 1000 / max))
    end=$((t0 + secs * 1000))
    while [ "$n" -lt "$max" ]; do
      t="$(now_ms)"
      [ "$t" -lt "$end" ] || break
      n=$((n + 1))
      "$ADB" exec-out screencap >"$dir/$(printf '%02d' "$n").raw" 2>/dev/null || true
      printf '%02d %s\n' "$n" "$t" >>"$dir/times.txt"
      wait_ms=$((t0 + n * gap - $(now_ms)))
      if [ "$wait_ms" -gt 20 ]; then sleep "$(printf '%d.%03d' $((wait_ms / 1000)) $((wait_ms % 1000)))"; fi
    done
  ) </dev/null >/dev/null 2>&1 &
  FRAMES_PID=$!
}
frames_wait() {
  if [ -n "$FRAMES_PID" ]; then
    wait "$FRAMES_PID" 2>/dev/null || true
    FRAMES_PID=""
  fi
}
frame_sig() {  # frame_sig 파일 x1 y1 x2 y2 → 앱 영역 가로줄 48개의 체크섬(원시 형식을 모르면 파일 전체)
  local f="$1" x1="$2" y1="$3" x2="$4" y2="$5" w h size hdr k y
  [ -s "$f" ] || return 1
  w="$(od -An -tu4 -N4 "$f" 2>/dev/null | tr -d ' ' || true)"
  h="$(od -An -tu4 -j4 -N4 "$f" 2>/dev/null | tr -d ' ' || true)"
  size="$(wc -c <"$f" | tr -d ' ')"
  case "$w$h" in *[!0-9]* | '') cksum <"$f"; return 0 ;; esac
  hdr=$((size - w * h * 4))
  if [ "$hdr" != 12 ] && [ "$hdr" != 16 ]; then
    cksum <"$f"
    return 0
  fi
  [ "$x2" -le "$w" ] || x2="$w"
  [ "$y2" -le "$h" ] || y2="$h"
  if [ "$x2" -le "$x1" ] || [ "$y2" -le "$y1" ]; then x1=0; y1=0; x2="$w"; y2="$h"; fi
  for k in $(seq 1 48); do
    y=$((y1 + (y2 - y1) * k / 49))
    dd if="$f" bs=4 skip=$((hdr / 4 + y * w + x1)) count=$((x2 - x1)) 2>/dev/null || true
  done | cksum
}
SAME=0
DIFF=0
MOTION="정지"
MOTION_LOG=""
MOTION_FIRST=""
MOTION_LAST=""
FRAMES_AFTER=0
frames_compare() {  # frames_compare 폴더 [기준 시각 ms] → SAME·DIFF(이웃한 두 장이 같은/다른 쌍 수), MOTION
  # MOTION_LOG: 장마다 "기준 뒤 초:같음/다름", MOTION_FIRST·LAST: 처음·마지막으로 바뀐 장의 시각(초)
  # FRAMES_AFTER: 기준 시각 뒤에 찍기 시작한 장 수. 기준이 없으면 첫 장이 0초
  local dir="$1" base="${2:-}" n t sig prev="" rel rel_ms x1 y1 x2 y2 mark
  SAME=0
  DIFF=0
  MOTION_LOG=""
  MOTION_FIRST=""
  MOTION_LAST=""
  FRAMES_AFTER=0
  read -r x1 y1 x2 y2 <<<"${APP_BOUNDS:-0 0 0 0}"
  if [ -z "$base" ]; then base="$(awk 'NR == 1 { print $2 }' "$dir/times.txt" 2>/dev/null || true)"; fi
  base="${base:-0}"
  if [ -f "$dir/times.txt" ]; then
    while read -r n t; do
      rel_ms=$((t - base))
      rel="$(ms_to_s "$rel_ms")"
      if [ "$rel_ms" -ge 0 ]; then FRAMES_AFTER=$((FRAMES_AFTER + 1)); fi
      if ! sig="$(frame_sig "$dir/$n.raw" "$x1" "$y1" "$x2" "$y2")"; then
        MOTION_LOG="$MOTION_LOG ${rel}초:못찍음"
        continue
      fi
      if [ -z "$prev" ]; then
        mark="첫장"
      elif [ "$prev" = "$sig" ]; then
        SAME=$((SAME + 1))
        mark="같음"
      else
        DIFF=$((DIFF + 1))
        mark="다름"
        if [ -z "$MOTION_FIRST" ]; then MOTION_FIRST="$rel"; fi
        MOTION_LAST="$rel"
      fi
      MOTION_LOG="$MOTION_LOG ${rel}초:$mark"
      prev="$sig"
    done <"$dir/times.txt"
  fi
  rm -rf "$dir"
  if [ "$DIFF" -gt 0 ]; then MOTION="움직임"; else MOTION="정지"; fi
  note "  화면 비교(앱 영역 ${APP_BOUNDS:-모름 → 화면 전체}): 같은 쌍 $SAME, 다른 쌍 $DIFF"
  note "   장별(기준 뒤 초):${MOTION_LOG:- 찍은 장 없음}"
}

# ---- 끝 정리 ----
common_cleanup() {  # 배경 작업을 정리하고 신호 루프를 끈다. 각 스크립트의 끝 trap에서 부른다
  if [ -n "$BG_PID" ]; then kill "$BG_PID" 2>/dev/null || true; fi
  if [ -n "$FRAMES_PID" ]; then kill "$FRAMES_PID" 2>/dev/null || true; fi
  vc_disconnect
  rm -f "$OUT/.ui.xml"
}
