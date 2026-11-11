#!/usr/bin/env bash
# =============================================================================
# 같이가숲 — Pleos Connect 에뮬레이터 도우미 (Mac)
#
# 사용법: bash tools/mac/emulator.sh <명령>
#   start [에뮬레이터 옵션...]  Cold boot로 켜고 부팅이 끝날 때까지 기다린다
#                                예) start -gpu swiftshader_indirect   (그래픽 문제 비교용)
#   stop                         끈다
#   console                      Virtual Console 포트(8088)를 잇고 브라우저로 연다
#   drive-on | drive-off         주행 규제 강제 켜기/끄기 (테스트용, 끝나면 drive-off)
#                                이 명령(hmg_car_service)이 없는 에뮬레이터에서는 안내만 하고 0으로 끝난다
#   drive-check                  주행 규제 강제 명령이 있는지 본다 (끝 코드 0 있음 / 3 없음 / 1 확인 못 함)
#   install [APK 경로]            APK 설치. 경로가 없으면 apk/ 와 app/build/outputs/apk/debug/ 에서 가장 최근 것
#   launch                       앱 실행 (앱 ID는 gradle.properties의 app.applicationId). 실패할 때만 monkey 출력을 보인다
#   status                       상태 보기
#
# 무엇을 바꾸나: 에뮬레이터 프로세스, adb 포트 포워딩(tcp:8088), 에뮬레이터 안의 앱 설치·주행 규제 상태.
#               로그는 ~/Library/Logs/familyjourney-emulator.log 에 쌓인다. 다른 파일은 바꾸지 않는다.
# 다시 실행해도 되나: 된다. 이미 켜져 있으면 start는 다시 켜지 않는다.
# 설정: GGF_AVD(기본 PleosConnect), GGF_BOOT_TIMEOUT(기본 600초), GGF_APP_ID, GGF_DIR
#       환경 변수 또는 ~/.config/familyjourney/env
# 주행 규제 강제 명령: Pleos Connect v2.1.0 에뮬레이터에는 hmg_car_service가 없다
#   (2026-11-02 Mac 실측: "cmd: Can't find service: hmg_car_service"). 주행 제한은 기어로만 바뀐다
#   (Virtual Console 기어 D → UX 제한 bits=80, P → 0). 그래서 drive-on/off는 먼저 서비스가 있는지 본다.
# 검증: bash -n, shellcheck, 가짜 adb로 drive-on/off·drive-check(서비스 있음/없음)·launch(성공/실패) 확인(클라우드).
#       start·console·launch·drive-off는 Mac 담당 세션이 try.sh·stage.sh로 돌려 봄(2026-11-02). [가정] 표시 참고.
# =============================================================================
set -euo pipefail

# ---- 설정 파일 읽기 (setup.sh와 같은 방식: source 하지 않고 GGF_ 이름만) ----
GGF_CONFIG_FILE="${GGF_CONFIG_FILE:-$HOME/.config/familyjourney/env}"
load_config() {
  local file="$1" line key val isset
  [ -f "$file" ] || return 0
  while IFS= read -r line || [ -n "$line" ]; do
    line="${line%$'\r'}"
    line="${line#"${line%%[![:space:]]*}"}"
    case "$line" in '' | '#'*) continue ;; esac
    line="${line#export }"
    case "$line" in *=*) ;; *) continue ;; esac
    key="${line%%=*}"
    val="${line#*=}"
    key="${key%"${key##*[![:space:]]}"}"
    case "$key" in GGF_*) ;; *) continue ;; esac
    case "$key" in *[!A-Z0-9_]*) continue ;; esac
    val="${val#"${val%%[![:space:]]*}"}"
    val="${val%"${val##*[![:space:]]}"}"
    case "$val" in
      \"*\") val="${val#\"}"; val="${val%\"}" ;;
      \'*\') val="${val#\'}"; val="${val%\'}" ;;
    esac
    # shellcheck disable=SC2016,SC2088  # 글자 그대로의 ~ 와 $HOME을 찾는 것이 목적이다
    case "$val" in
      '~') val="$HOME" ;;
      '~/'*) val="$HOME/${val#'~/'}" ;;
      '$HOME'*) val="$HOME${val#'$HOME'}" ;;
      '${HOME}'*) val="$HOME${val#'${HOME}'}" ;;
    esac
    eval "isset=\${$key+x}"
    if [ -z "$isset" ]; then
      printf -v "$key" '%s' "$val"
      export "${key?}"
    fi
  done <"$file"
}
load_config "$GGF_CONFIG_FILE"

ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"
EMU="$ANDROID_HOME/emulator/emulator"
AVD="${GGF_AVD:-PleosConnect}"
BOOT_TIMEOUT="${GGF_BOOT_TIMEOUT:-600}"
LOG="$HOME/Library/Logs/familyjourney-emulator.log"
VC_PORT=8088
VC_URL="http://127.0.0.1:$VC_PORT/"

# 저장소 루트: 이 스크립트가 저장소 안에 있으면 그 루트, 아니면 GGF_DIR
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
ROOT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel 2>/dev/null || printf '%s' "${GGF_DIR:-$HOME/Projects/familyjourney}")"

say() { printf '%s\n' "$*"; }
warn() { printf '[주의] %s\n' "$*"; }
die() {
  printf '[오류] %s\n' "$*" >&2
  exit 1
}

need_adb() { [ -x "$ADB" ] || die "adb가 없습니다: $ADB (먼저 bash tools/mac/setup.sh)"; }
need_emu() { [ -x "$EMU" ] || die "emulator가 없습니다: $EMU (먼저 bash tools/mac/setup.sh)"; }

avd_exists() {
  local list
  list="$("$EMU" -list-avds 2>/dev/null | tr -d '\r' || true)"
  grep -qx -- "$AVD" <<<"$list"
}

# 이 AVD로 켠 에뮬레이터 프로세스가 있는지 (명령줄에 "-avd 이름"이 있는지)
emu_running() { pgrep -f -- "-avd $AVD" >/dev/null 2>&1; }

# adb에 보이는 에뮬레이터 중 이 AVD의 serial(emulator-5554 등)을 찾는다.
# [가정] 'adb -s <serial> emu avd name'은 첫 줄에 AVD 이름을 돌려준다(에뮬레이터 콘솔 명령).
find_serial() {
  local devs s name
  devs="$("$ADB" devices 2>/dev/null | awk 'NR > 1 && $1 ~ /^emulator-/ { print $1 }' || true)"
  for s in $devs; do
    name="$("$ADB" -s "$s" emu avd name 2>/dev/null | tr -d '\r' | sed -n 1p || true)"
    if [ "$name" = "$AVD" ]; then
      printf '%s' "$s"
      return 0
    fi
  done
  return 1
}

SERIAL=""
require_serial() {
  SERIAL="$(find_serial)" || die "에뮬레이터 '$AVD'가 켜져 있지 않습니다. 먼저: bash tools/mac/emulator.sh start"
}

getprop() { "$ADB" -s "$1" shell getprop "$2" 2>/dev/null | tr -d '\r' || true; }

# 새 세션으로 떼어 내 실행한다. 이 스크립트를 부른 셸(Remote Control 등)이 끝나도
# 에뮬레이터가 계속 돌게 하려는 것이다. [가정] macOS 기본 perl로 setsid. perl이 없으면 nohup만.
launch_detached() {
  if command -v perl >/dev/null 2>&1; then
    # shellcheck disable=SC2016  # $!는 perl 안에서 쓰는 값이다
    nohup perl -MPOSIX -e 'POSIX::setsid(); exec @ARGV or die "exec failed: $!\n"' "$@" \
      >>"$LOG" 2>&1 </dev/null &
  else
    nohup "$@" >>"$LOG" 2>&1 </dev/null &
  fi
  disown 2>/dev/null || true
}

# 부팅 완료(sys.boot_completed=1)까지 기다린다.
# 'adb wait-for-device'는 에뮬레이터가 죽어도 끝없이 기다리고 macOS에는 timeout 명령이 없어서,
# 5초마다 직접 확인하고 프로세스가 사라지면 바로 멈춘다.
wait_boot() {
  local start=$SECONDS serial=""
  say "부팅을 기다립니다 (최대 ${BOOT_TIMEOUT}초). 첫 Cold boot는 몇 분 걸릴 수 있습니다."
  while :; do
    if [ -z "$serial" ]; then
      serial="$(find_serial || true)"
    fi
    if [ -n "$serial" ] && [ "$(getprop "$serial" sys.boot_completed)" = "1" ]; then
      break
    fi
    if ! emu_running; then
      say "[오류] 에뮬레이터가 꺼졌습니다. 로그 끝부분 ($LOG):"
      tail -n 30 "$LOG" 2>/dev/null || true
      say "도움말: docs/dev-environment.md 문제 해결 → 그래픽 문제면 'emulator.sh start -gpu swiftshader_indirect'로 비교"
      exit 1
    fi
    if [ $((SECONDS - start)) -ge "$BOOT_TIMEOUT" ]; then
      die "${BOOT_TIMEOUT}초 안에 부팅이 끝나지 않았습니다. 에뮬레이터는 켜 둔 채입니다. 로그: $LOG"
    fi
    sleep 5
  done
  say "부팅 완료 ($serial, 약 $((SECONDS - start))초)."
  say "다음: bash tools/mac/emulator.sh console   (Virtual Console)"
}

cmd_start() {
  local serial
  need_emu
  need_adb
  avd_exists || die "AVD '$AVD'가 없습니다. 먼저: bash tools/mac/setup.sh"
  "$ADB" start-server >/dev/null 2>&1 || true
  if serial="$(find_serial)"; then
    say "이미 켜져 있습니다 ($serial)."
  elif emu_running; then
    say "에뮬레이터 프로세스가 이미 있습니다. adb 연결과 부팅을 기다립니다."
  else
    mkdir -p "$(dirname "$LOG")"
    printf '\n===== %s start %s =====\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$AVD" >>"$LOG"
    # Cold boot: 스냅샷을 읽지 않는다. Quick boot면 VHAL을 쓸 수 없다(docs/pleos-dev-guide.md 1장 9번).
    say "Cold boot로 켭니다: emulator -avd $AVD -no-snapshot-load ${*:-}"
    launch_detached "$EMU" -avd "$AVD" -no-snapshot-load ${1+"$@"}
    sleep 2
  fi
  wait_boot
}

cmd_stop() {
  local serial i
  need_adb
  if serial="$(find_serial)"; then
    say "끕니다 ($serial)."
    "$ADB" -s "$serial" emu kill >/dev/null 2>&1 || true
  elif emu_running; then
    say "adb에 보이지 않아 프로세스에 종료 신호를 보냅니다."
    pkill -f -- "-avd $AVD" || true
  else
    say "켜져 있지 않습니다."
    return 0
  fi
  for i in $(seq 1 30); do
    emu_running || {
      say "꺼졌습니다."
      return 0
    }
    sleep 2
  done
  die "아직 꺼지지 않았습니다(${i}회 확인). 에뮬레이터 창을 직접 닫으세요."
}

# Virtual Console 연결 확인 순서 (docs/pleos-dev-guide.md 2-4)
print_vc_checklist() {
  say "연결이 안 되면 차례로 확인하세요:"
  say "  1) 에뮬레이터가 켜져 있는지:  bash tools/mac/emulator.sh status"
  say "  2) 이미지가 Pleos Connect 2.1.0 이상인지"
  say "  3) adb에 보이는지:            \"$ADB\" devices"
  say "  4) 포워딩 다시 하기:          bash tools/mac/emulator.sh console"
  say "  5) $VC_PORT 포트를 다른 프로그램이 쓰는지: lsof -nP -iTCP:$VC_PORT -sTCP:LISTEN"
}

cmd_console() {
  local listener code
  need_adb
  require_serial
  # 8088을 adb가 아닌 다른 프로그램이 쓰고 있으면 포워딩이 실패한다.
  listener="$(lsof -nP -iTCP:"$VC_PORT" -sTCP:LISTEN 2>/dev/null | awk 'NR > 1 { print $1 }' | sort -u || true)"
  if [ -n "$listener" ] && ! grep -qx 'adb' <<<"$listener"; then
    die "$VC_PORT 포트를 다른 프로그램($listener)이 쓰고 있습니다. 그 프로그램을 끄고 다시 실행하세요."
  fi
  "$ADB" -s "$SERIAL" forward "tcp:$VC_PORT" "tcp:$VC_PORT" >/dev/null
  say "포워딩: 127.0.0.1:$VC_PORT → 에뮬레이터 $VC_PORT"
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 --noproxy '*' "$VC_URL" 2>/dev/null || true)"
  if [ -z "$code" ] || [ "$code" = "000" ]; then
    warn "Virtual Console이 아직 응답하지 않습니다(부팅 직후면 잠시 뒤 새로 고침)."
    print_vc_checklist
  else
    say "Virtual Console 응답: HTTP $code"
  fi
  open "$VC_URL" 2>/dev/null || say "브라우저에서 $VC_URL 을 여세요."
}

# ---- 주행 규제 강제 명령 (hmg_car_service) ----
DRIVE_SVC=hmg_car_service
# 이 에뮬레이터에 서비스가 있는지: 0 있음 / 1 없음 / 2 모름 (SERIAL이 정해져 있어야 한다)
#   1) "service check 이름" → "Service 이름: found" 또는 ": not found"
#   2) 답이 이상하면 "cmd -l"(돌고 있는 서비스 목록, 첫 줄 "Currently running services:")에서 찾는다
drive_service_state() {
  local out
  out="$("$ADB" -s "$SERIAL" shell service check "$DRIVE_SVC" 2>&1 | tr -d '\r' || true)"
  case "$out" in
    *": not found"*) return 1 ;;
    *": found"*) return 0 ;;
  esac
  out="$("$ADB" -s "$SERIAL" shell cmd -l 2>/dev/null | tr -d '\r' || true)"
  case "$out" in
    *"Currently running services"*)
      if grep -qx -- "$DRIVE_SVC" <<<"$(printf '%s\n' "$out" | tr -d ' \t')"; then return 0; fi
      return 1
      ;;
  esac
  return 2
}

print_no_drive_cmd() { # $1 = on | off
  say "[안내] 이 에뮬레이터에는 주행 규제 강제 명령이 없습니다($DRIVE_SVC 서비스 없음)."
  say "       Pleos Connect v2.1.0 에뮬레이터에서 확인했습니다(2026-11-02). 주행 제한은 기어로 시험합니다:"
  say "       Virtual Console에서 기어 D → 주행 제한(UX 제한 bits=80), 기어 P → 해제(0)."
  say "       Virtual Console 열기: bash tools/mac/emulator.sh console"
  if [ "$1" = "off" ]; then
    say "       강제로 켠 규제가 없으니 되돌릴 것도 없습니다."
  fi
}

cmd_drive() { # $1 = on | off
  local state=0 out rc=0
  need_adb
  require_serial
  drive_service_state || state=$?
  if [ "$state" = 1 ]; then
    print_no_drive_cmd "$1"
    return 0
  fi
  # state 2(모름)이면 그냥 실행해 보고, "Can't find service"가 나오면 없는 것으로 본다
  out="$("$ADB" -s "$SERIAL" shell cmd "$DRIVE_SVC" --service drivingstate driving_regulation "$1" 2>&1)" || rc=$?
  out="$(printf '%s' "$out" | tr -d '\r')"
  case "$out" in
    *"Can't find service"*)
      print_no_drive_cmd "$1"
      return 0
      ;;
  esac
  if [ -n "$out" ]; then printf '%s\n' "$out"; fi
  [ "$rc" = 0 ] || die "주행 규제 명령이 실패했습니다(끝 코드 $rc)."
  if [ "$1" = "on" ]; then
    say "주행 규제를 강제로 켰습니다(주행 중 화면 테스트)."
    say "테스트가 끝나면 꼭 되돌리세요: bash tools/mac/emulator.sh drive-off"
  else
    say "강제 주행 규제를 껐습니다."
  fi
}

cmd_drive_check() {
  local state=0
  need_adb
  require_serial
  drive_service_state || state=$?
  case "$state" in
    0) say "주행 규제 강제 명령($DRIVE_SVC): 있음" ;;
    1)
      say "주행 규제 강제 명령($DRIVE_SVC): 없음 → 기어 D/P로 시험"
      exit 3
      ;;
    *)
      say "주행 규제 강제 명령($DRIVE_SVC): 확인 못 함(service check, cmd -l 모두 답이 이상함)"
      exit 1
      ;;
  esac
}

cmd_install() {
  local apk="${1:-}" f
  need_adb
  require_serial
  if [ -z "$apk" ]; then
    shopt -s nullglob
    for f in "$ROOT"/apk/*.apk "$ROOT"/app/build/outputs/apk/debug/*.apk; do
      if [ -z "$apk" ] || [ "$f" -nt "$apk" ]; then
        apk="$f"
      fi
    done
    shopt -u nullglob
  fi
  if [ -z "$apk" ] || [ ! -f "$apk" ]; then
    die "설치할 APK가 없습니다. sync.sh로 최신 apk/를 받았는지, 또는 Android Studio에서 빌드했는지 확인하세요."
  fi
  say "설치: $apk"
  "$ADB" -s "$SERIAL" install -r "$apk"
}

cmd_launch() {
  local app_id="${GGF_APP_ID:-}" out rc=0
  need_adb
  require_serial
  if [ -z "$app_id" ] && [ -f "$ROOT/gradle.properties" ]; then
    app_id="$(awk -F= '$1 == "app.applicationId" { gsub(/[ \t\r]/, "", $2); print $2 }' "$ROOT/gradle.properties")"
  fi
  [ -n "$app_id" ] || die "앱 ID를 찾지 못했습니다 (gradle.properties의 app.applicationId 또는 GGF_APP_ID)"
  say "실행: $app_id"
  # monkey로 런처 Activity를 연다(2026-11-02 Mac 확인: AAOS에서도 열림).
  # monkey는 성공해도 args/arg/data 줄을 찍는다 → 모아 두었다가 실패할 때만 보인다.
  out="$("$ADB" -s "$SERIAL" shell monkey -p "$app_id" -c android.intent.category.LAUNCHER 1 2>&1)" || rc=$?
  out="$(printf '%s\n' "$out" | tr -d '\r')"
  if [ "$rc" != 0 ] || grep -qE '^\*\*|aborted|No activities found|Exception' <<<"$out"; then
    say "[오류] 앱을 열지 못했습니다. monkey 출력:"
    printf '%s\n' "$out" | sed 's/^/    /'
    die "앱 실행 실패(끝 코드 $rc). 앱이 설치되어 있는지 확인하세요: bash tools/mac/emulator.sh install"
  fi
}

cmd_status() {
  local serial fwd boot
  say "Android SDK : $ANDROID_HOME"
  if [ -x "$EMU" ]; then say "emulator    : 있음"; else say "emulator    : 없음"; fi
  if [ -x "$ADB" ]; then say "adb         : 있음"; else say "adb         : 없음"; fi
  if [ -x "$EMU" ] && avd_exists; then say "AVD         : $AVD (있음)"; else say "AVD         : $AVD (없음)"; fi
  if [ -x "$ADB" ] && serial="$(find_serial)"; then
    boot="$(getprop "$serial" sys.boot_completed)"
    if [ "$boot" = "1" ]; then say "상태        : 켜짐 ($serial), 부팅 완료"; else say "상태        : 켜짐 ($serial), 부팅 중"; fi
    fwd="$("$ADB" -s "$serial" forward --list 2>/dev/null | awk -v p="tcp:$VC_PORT" '$2 == p { print "있음" }' | sed -n 1p || true)"
    say "8088 포워딩 : ${fwd:-없음}"
  elif emu_running; then
    say "상태        : 프로세스는 있음, adb 연결 대기"
  else
    say "상태        : 꺼짐"
  fi
  say "로그        : $LOG"
  say "저장소      : $ROOT"
}

usage() { # 맨 위 주석의 "사용법:"부터 빈 주석 줄 앞까지
  awk '/^# 사용법:/ { on = 1 } on && /^#$/ { exit } on { sub(/^# ?/, ""); print }' "${BASH_SOURCE[0]}"
}

cmd="${1:-help}"
if [ $# -gt 0 ]; then shift; fi
case "$cmd" in
  start) cmd_start ${1+"$@"} ;;
  stop) cmd_stop ;;
  console) cmd_console ;;
  drive-on) cmd_drive on ;;
  drive-off) cmd_drive off ;;
  drive-check) cmd_drive_check ;;
  install) cmd_install "${1:-}" ;;
  launch) cmd_launch ;;
  status) cmd_status ;;
  help | -h | --help) usage ;;
  *)
    usage
    exit 2
    ;;
esac
