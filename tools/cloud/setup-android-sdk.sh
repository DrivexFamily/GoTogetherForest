#!/usr/bin/env bash
# =============================================================================
# 같이가숲 — 클라우드 컨테이너용 Android SDK 설치 (Linux)
#
# 클라우드 환경 설정의 Setup script에 등록한 것과 같은 내용이다. 새 세션이 시작될 때 실행된다.
#
# 무엇을 하나
#   1 Android command-line tools(빌드 11076708 = 12.0)를 $ANDROID_HOME/cmdline-tools/latest 에 설치
#   2 Java 17 이상인지 확인 (Gradle wrapper 8.10.2, AGP 8.8 빌드용)
#   3 Android SDK 라이선스 동의
#   4 platforms;android-35, build-tools;35.0.0, platform-tools 설치
#
# 무엇을 바꾸나: $ANDROID_HOME(기본 /opt/android-sdk) 아래만. sudo를 쓰지 않는다(클라우드는 root로 실행).
# 다시 실행해도 되나: 된다. 이미 있으면 건너뛴다.
# 참고: setup script는 환경에 저장된 변수·비밀값을 읽지 못한다. 그래서 경로 기본값을 여기서 정한다.
#       Gradle이 SDK를 찾으려면 환경 변수 ANDROID_HOME=/opt/android-sdk 가 필요하다(이미 등록됨, decisions.md #13).
#       에뮬레이터는 클라우드에서 돌릴 수 없어서(GPU·GUI) 설치하지 않는다.
# 검증: 2026-10-31 클라우드 컨테이너에서 실행해 확인 (docs/dev-environment.md 검증 상태)
# =============================================================================
set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
CLT_BUILD=11076708
# Google 공개 값과 같다. 2026-10-31 직접 내려받아 계산해 확인함.
CLT_SHA256=2d2d50857e4eb553af5a6dc3ad507a17adf43d115264b1afc116f95c92e5e258
CLT_URL="https://dl.google.com/android/repository/commandlinetools-linux-${CLT_BUILD}_latest.zip"
SDKM="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
PACKAGES="platforms;android-35 build-tools;35.0.0 platform-tools"

log() { printf '[%s] %s\n' "$1" "$2"; }
die() {
  printf '[오류] %s\n' "$*" >&2
  exit 1
}

# 패키지가 설치됐는지 폴더로 확인한다(라이선스를 거절해도 sdkmanager는 0으로 끝나기 때문).
pkg_present() {
  local rel
  rel="$(printf '%s' "$1" | tr ';' '/')"
  [ -f "$ANDROID_HOME/$rel/package.xml" ] || [ -f "$ANDROID_HOME/$rel/source.properties" ]
}

# sdkmanager의 질문(라이선스 y/N)에 y를 넣는다. yes가 SIGPIPE로 끝나도 pipefail에 걸리지 않게 감싼다.
sdkm_yes() { { yes 2>/dev/null || true; } | "$SDKM" --sdk_root="$ANDROID_HOME" "$@"; }

# ---- 1. command-line tools ---------------------------------------------------
if [ -x "$SDKM" ]; then
  log "1/4" "command-line tools 있음: $SDKM (건너뜀)"
else
  log "1/4" "command-line tools ${CLT_BUILD} 내려받기"
  command -v unzip >/dev/null 2>&1 || die "unzip이 없습니다"
  mkdir -p "$ANDROID_HOME/cmdline-tools" || die "$ANDROID_HOME 에 쓸 수 없습니다"
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' EXIT
  curl -fsSL --retry 3 -o "$tmp/clt.zip" "$CLT_URL"
  if command -v sha256sum >/dev/null 2>&1; then
    actual="$(sha256sum "$tmp/clt.zip" | awk '{ print $1 }')"
  else
    actual="$(shasum -a 256 "$tmp/clt.zip" | awk '{ print $1 }')"
  fi
  [ "$actual" = "$CLT_SHA256" ] || die "내려받은 파일의 SHA-256이 다릅니다 ($actual)"
  unzip -q "$tmp/clt.zip" -d "$tmp/x"
  # zip 안의 cmdline-tools/ 를 cmdline-tools/latest 로 옮긴다(sdkmanager가 자기 위치로 SDK를 찾는 규칙).
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$tmp/x/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  log "1/4" "설치함: $ANDROID_HOME/cmdline-tools/latest"
fi

# ---- 2. Java -------------------------------------------------------------------
command -v java >/dev/null 2>&1 || die "java가 없습니다 (JDK 17 이상 필요)"
jver="$(java -version 2>&1 || true)"
jver="$(awk -F'"' '/ version "/ { print $2 }' <<<"$jver" | sed -n 1p)"
jmajor="${jver%%.*}"
case "$jmajor" in '' | *[!0-9]*) die "Java 버전을 읽지 못했습니다" ;; esac
[ "$jmajor" -ge 17 ] || die "Java $jver 입니다. 17 이상이 필요합니다"
log "2/4" "Java $jver (17 이상, 통과)"

# ---- 3. 라이선스 -----------------------------------------------------------------
if [ -f "$ANDROID_HOME/licenses/android-sdk-license" ]; then
  log "3/4" "Android SDK 라이선스 동의 기록 있음 (건너뜀)"
else
  log "3/4" "Android SDK 라이선스 동의 (클라우드 빌드용, 등록한 사람이 동의함)"
  sdkm_yes --licenses >/dev/null
fi

# ---- 4. 패키지 ------------------------------------------------------------------
need=""
for p in $PACKAGES; do
  pkg_present "$p" || need="$need $p"
done
if [ -z "$need" ]; then
  log "4/4" "패키지 모두 있음 ($PACKAGES) (건너뜀)"
else
  log "4/4" "설치:$need"
  # shellcheck disable=SC2086  # 패키지 이름에는 공백이 없어서 일부러 나눈다
  sdkm_yes --install $need >/dev/null
  for p in $need; do
    pkg_present "$p" || die "설치되지 않았습니다: $p"
  done
  log "4/4" "설치함:$need"
fi

if [ "${ANDROID_HOME}" != "${ANDROID_SDK_ROOT:-$ANDROID_HOME}" ]; then
  log "참고" "ANDROID_SDK_ROOT가 ANDROID_HOME과 다릅니다. Gradle 오류가 나면 둘을 같게 맞추세요."
fi
log "완료" "Android SDK 준비됨: $ANDROID_HOME"
