#!/usr/bin/env bash
# =============================================================================
# 같이가숲 — Mac(Apple Silicon) 개발 환경 준비 스크립트
#
# 무엇을 하나 (10단계)
#    1 macOS·arm64·디스크 확인          6 Sourcetree로 저장소 열기
#    2 Homebrew 확인(없으면 설치)        7 Android SDK (~/Library/Android/sdk)
#    3 앱·도구 설치 (brew)               8 Pleos Connect 에뮬레이터 이미지
#    4 GitHub 로그인 확인 (gh)           9 AVD "PleosConnect" 만들기·설정
#    5 저장소 클론 또는 갱신            10 요약과 다음 명령
#
# 무엇을 바꾸나
#   - Homebrew 패키지: sourcetree, android-studio, android-commandlinetools(cask), gh, git, openjdk@17
#   - 클론 폴더(기본 ~/Projects/familyjourney), 그 안의 .git/config(작성자 이름·이메일,
#     GitHub 인증 도우미)와 local.properties(git에 안 올라감)
#   - ~/Library/Android/sdk, ~/.android/repositories.cfg(백업 후 한 줄 추가),
#     ~/.android/avd/PleosConnect.avd/config.ini(백업 후 키 몇 개 변경)
#   - 전역 git 설정(~/.gitconfig)과 셸 설정 파일(~/.zshrc 등)은 바꾸지 않는다. sudo를 쓰지 않는다.
#
# 다시 실행해도 되나: 된다. 이미 된 단계는 건너뛴다.
#
# 실행
#   터미널:         bash tools/mac/setup.sh
#   Remote Control: bash tools/mac/setup.sh > ~/Library/Logs/familyjourney-setup.log 2>&1
#   터미널(TTY)이 아니면 비밀번호·브라우저 로그인·라이선스 동의가 필요한 일은 하지 않고 안내만 한다.
#   이 파일 하나만 따로 복사해서 실행해도 된다(저장소 클론 전에 쓰기 위해 다른 파일에 기대지 않는다).
#
# 설정: 환경 변수 또는 ~/.config/familyjourney/env (예시: tools/env.example)
#   GGF_REPO(필수)  GGF_DIR  GGF_BRANCH  GGF_GIT_NAME  GGF_GIT_EMAIL
#   GGF_ACCEPT_ANDROID_LICENSES  GGF_AVD  GGF_PLEOS_PKG  GGF_NONINTERACTIVE
#
# 종료 코드: 0 모두 완료 / 3 사람이 할 일이 남음 / 1 실패한 단계가 있음
#
# 검증 표시
#   [검증] 클라우드(Linux, cmdline-tools 12.0)에서 실제로 확인함 (2026-10-31)
#   [가정] Mac에서 아직 확인하지 못함
#   이 스크립트 전체는 Mac에서 아직 실행해 보지 않았다(bash -n, shellcheck만 통과).
#   macOS 기본 bash 3.2에서 돌도록 bash 4 문법(연관 배열, mapfile 등)을 쓰지 않는다.
# =============================================================================
set -euo pipefail

# ---- 고정값 ---------------------------------------------------------------
# [검증] Mac(arm64)용 Pleos 사이트. XML을 직접 받아 확인했다. 2026-10-31 기준 이 사이트에는
#        system-images;android-34;pleos_car_emulator;arm64-v8a (rev 2, 약 4.7GB) 하나가 있다.
#        그래도 경로는 하드코딩하지 않고 sdkmanager --list에서 찾는다(사이트가 바뀔 수 있음).
PLEOS_SITE_URL="https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml"
PLEOS_SITE_NAME="Pleos Connect System Image"
HOMEBREW_INSTALL_URL="https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh"

# AVD config.ini에 넣을 값. 기준: docs/pleos-dev-guide.md 2-3 (Automotive, Cold boot, RAM 4GB)
# [검증] avdmanager가 만든 config.ini 기본값은 fastboot.forceFastBoot=yes, hw.ramSize=1536M,
#        hw.gpu.enabled=no, hw.initialOrientation=portrait, disk.dataPartition.size=800M 였다.
#        그래서 Cold boot는 forceColdBoot=yes와 forceFastBoot=no를 함께 넣는다.
AVD_SETTINGS="hw.ramSize=4096
fastboot.forceColdBoot=yes
fastboot.forceFastBoot=no
hw.gpu.enabled=yes
hw.gpu.mode=host
hw.keyboard=yes
hw.initialOrientation=landscape"
# 새로 만든 AVD에만 넣는 값(이미 있는 AVD의 데이터 크기는 건드리지 않는다)
# [가정] 6GB·4코어는 Android Studio가 새 AVD에 주는 값과 비슷하게 맞춘 것이다.
AVD_SETTINGS_NEW_ONLY="disk.dataPartition.size=6144M
hw.cpu.ncore=4"

# ---- 실행 모드 -------------------------------------------------------------
# 터미널(TTY)에서 실행했는지 본다. Remote Control처럼 TTY가 없으면 사람에게 물어야 하는 일은 하지 않는다.
if [ -t 0 ] && [ -t 1 ] && [ "${GGF_NONINTERACTIVE:-0}" != "1" ]; then
  INTERACTIVE=1
else
  INTERACTIVE=0
fi
# git이 비밀번호를 물으며 멈추지 않게 한다(GitHub는 비밀번호 로그인을 받지 않는다).
export GIT_TERMINAL_PROMPT=0
export HOMEBREW_NO_ENV_HINTS=1

# ---- 설정 파일 읽기 ----------------------------------------------------------
GGF_CONFIG_FILE="${GGF_CONFIG_FILE:-$HOME/.config/familyjourney/env}"

# KEY=VALUE 파일을 읽는다. source 하지 않고 GGF_로 시작하는 이름만 받는다(파일 안의 명령이 실행되지 않게).
# 이미 환경 변수로 정해진 값은 덮어쓰지 않는다.
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
    # key는 위에서 대문자·숫자·밑줄만 허용했으므로 eval이 안전하다.
    eval "isset=\${$key+x}"
    if [ -z "$isset" ]; then
      printf -v "$key" '%s' "$val"
      export "${key?}"
    fi
  done <"$file"
}
load_config "$GGF_CONFIG_FILE"

GGF_DIR="${GGF_DIR:-$HOME/Projects/familyjourney}"
AVD="${GGF_AVD:-PleosConnect}"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export ANDROID_HOME
ANDROID_PREFS_DIR="${ANDROID_USER_HOME:-$HOME/.android}"
REPOS_CFG="$ANDROID_PREFS_DIR/repositories.cfg"
AVD_HOME="${ANDROID_AVD_HOME:-$ANDROID_PREFS_DIR/avd}"
LOG_DIR="$HOME/Library/Logs"
SDK_LOG="$LOG_DIR/familyjourney-sdkmanager.log"

TMPD="$(mktemp -d "${TMPDIR:-/tmp}/ggf-setup.XXXXXX")"
trap 'rm -rf "$TMPD"' EXIT

# ---- 출력 도우미 -------------------------------------------------------------
TOTAL=10
STEP=0
SUMMARY=""
FAILED=0
TODO=0
step() { STEP=$((STEP + 1)); printf '\n[%d/%d] %s\n' "$STEP" "$TOTAL" "$1"; }
info() { printf '    %s\n' "$*"; }
warn() { printf '    [주의] %s\n' "$*"; }
record() { SUMMARY="${SUMMARY}  ${1} [${STEP}] ${2}"$'\n'; }
ok() { info "완료: $*"; record "완료  " "$*"; }
skip() { info "건너뜀: $*"; record "건너뜀" "$*"; }
todo() { warn "직접 할 일: $*"; record "할 일 " "$*"; TODO=1; }
fail() { warn "실패: $*"; record "실패  " "$*"; FAILED=1; }

app_exists() { [ -d "/Applications/$1" ] || [ -d "$HOME/Applications/$1" ]; }

finish() {
  STEP=$TOTAL
  printf '\n[%d/%d] 요약\n' "$TOTAL" "$TOTAL"
  printf '%s' "$SUMMARY"
  printf '\n다음 명령\n'
  if [ -f "$GGF_DIR/tools/mac/emulator.sh" ]; then
    info "에뮬레이터 켜기(Cold boot): bash \"$GGF_DIR/tools/mac/emulator.sh\" start"
    info "Virtual Console 열기:       bash \"$GGF_DIR/tools/mac/emulator.sh\" console"
    info "AI 커밋 새로 받기:          bash \"$GGF_DIR/tools/mac/sync.sh\""
  elif [ -d "$GGF_DIR/.git" ]; then
    info "클론한 브랜치에 tools/mac/ 이 없습니다. GGF_BRANCH를 AI 작업 브랜치로 정하고 다시 실행하세요."
  fi
  if [ -f "$GGF_DIR/tools/mac/setup.sh" ]; then
    info "이 스크립트 다시 실행(안전): bash \"$GGF_DIR/tools/mac/setup.sh\""
  else
    info "이 스크립트 다시 실행(안전): bash setup.sh"
  fi
  if [ "$FAILED" = 1 ]; then
    exit 1
  elif [ "$TODO" = 1 ]; then
    exit 3
  fi
  exit 0
}

# ---- 1. Mac 확인 -------------------------------------------------------------
step "Mac 확인 (macOS, Apple Silicon, 디스크)"
if [ "$(uname -s)" != "Darwin" ]; then
  fail "macOS가 아닙니다. 이 스크립트는 Mac 전용입니다."
  finish
fi
if [ "$(sysctl -n hw.optional.arm64 2>/dev/null || echo 0)" != "1" ]; then
  fail "Apple Silicon(arm64) Mac이 아닙니다. Mac용 Pleos 이미지는 arm64 전용입니다."
  finish
fi
if [ "$(uname -m)" != "arm64" ]; then
  # Rosetta로 돌면 Homebrew가 /usr/local(Intel용)에 깔리는 등 꼬인다.
  fail "터미널이 Rosetta(x86_64)로 실행 중입니다. Rosetta 없이 연 터미널에서 다시 실행하세요."
  finish
fi
free_gb="$(df -Pk "$HOME" | awk 'NR == 2 { print int($4 / 1048576) }')"
info "macOS $(sw_vers -productVersion), arm64, 홈 폴더 여유 공간 약 ${free_gb}GB"
if [ "${free_gb:-0}" -lt 40 ]; then
  warn "여유 공간이 40GB보다 적습니다. Pleos 이미지는 내려받기 4.7GB, 풀면 약 19GB입니다."
fi
if [ "$INTERACTIVE" = 1 ]; then
  info "실행 모드: 터미널(대화형). 비밀번호·로그인·라이선스는 화면의 질문에 직접 답하세요."
else
  info "실행 모드: 비대화형(TTY 없음). 사람이 답해야 하는 일은 건너뛰고 안내만 합니다."
fi
ok "Mac 확인"

# ---- 2. Homebrew -------------------------------------------------------------
step "Homebrew 확인"
BREW=""
if command -v brew >/dev/null 2>&1; then
  BREW="$(command -v brew)"
elif [ -x /opt/homebrew/bin/brew ]; then
  BREW=/opt/homebrew/bin/brew
fi
# 비대화형 셸은 PATH에 /opt/homebrew/bin이 없을 수 있어서 이 스크립트 안에서만 넣는다(파일은 바꾸지 않음).
# brew shellenv는 감지한 셸에 따라 출력이 달라서 쓰지 않는다.
use_brew_path() {
  local bin
  bin="$(dirname "$BREW")"
  PATH="$bin:${bin%/bin}/sbin:$PATH"
  export PATH
}
if [ -n "$BREW" ]; then
  use_brew_path
  skip "Homebrew가 이미 있습니다"
elif [ "$INTERACTIVE" = 1 ]; then
  info "Homebrew 공식 설치 스크립트를 실행합니다. Mac 로그인 비밀번호를 물으면 직접 입력하세요."
  if /bin/bash -c "$(curl -fsSL "$HOMEBREW_INSTALL_URL")" && [ -x /opt/homebrew/bin/brew ]; then
    BREW=/opt/homebrew/bin/brew
    use_brew_path
    ok "Homebrew 설치"
    info "새 터미널에서도 brew를 쓰려면 설치 끝에 나온 'Next steps' 안내를 따르세요. 이 스크립트는 ~/.zprofile을 바꾸지 않습니다."
  else
    fail "Homebrew 설치를 확인하지 못했습니다"
    finish
  fi
else
  todo "Homebrew가 없습니다. 비밀번호가 필요해서 사람이 터미널에서 한 번 실행해야 합니다:"
  info "/bin/bash -c \"\$(curl -fsSL $HOMEBREW_INSTALL_URL)\""
  info "그다음 이 스크립트를 다시 실행하세요."
  finish
fi

# ---- 3. 앱과 도구 ------------------------------------------------------------
step "앱과 도구 설치 (Homebrew)"
install_cask() { # $1 = cask 이름, $2 = 앱 폴더 이름(.app, 없으면 빈 값)
  local cask="$1" app="${2:-}"
  if "$BREW" list --cask "$cask" >/dev/null 2>&1; then
    skip "$cask (이미 설치됨)"
    return 0
  fi
  if [ -n "$app" ] && app_exists "$app"; then
    skip "$cask ($app 이 이미 있음, Homebrew 밖에서 설치됨)"
    return 0
  fi
  info "brew install --cask $cask"
  if "$BREW" install --cask "$cask"; then ok "$cask 설치"; else fail "$cask 설치"; fi
}
install_formula() { # $1 = formula 이름
  if "$BREW" list --formula "$1" >/dev/null 2>&1; then
    skip "$1 (이미 설치됨)"
    return 0
  fi
  info "brew install $1"
  if "$BREW" install "$1"; then ok "$1 설치"; else fail "$1 설치"; fi
}
install_cask sourcetree "Sourcetree.app"
install_cask android-studio "Android Studio.app"
if [ -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  skip "android-commandlinetools (SDK 안에 이미 cmdline-tools가 있음)"
else
  # SDK를 처음 채울 때만 쓴다. 이후에는 SDK 안의 cmdline-tools;latest를 쓴다(7단계).
  install_cask android-commandlinetools ""
fi
install_formula gh
install_formula git
install_formula openjdk@17

# ---- 4. GitHub 로그인 --------------------------------------------------------
step "GitHub 로그인 확인 (gh)"
GH="$(command -v gh 2>/dev/null || true)"
GH_OK=0
if [ -z "$GH" ]; then
  fail "gh가 없습니다 (3단계 결과 확인)"
elif "$GH" auth status --hostname github.com >/dev/null 2>&1; then
  # 계정 이름이 로그에 남지 않게 출력은 버린다(블라인드 원칙).
  GH_OK=1
  skip "이미 로그인되어 있습니다"
elif [ "$INTERACTIVE" = 1 ]; then
  info "브라우저로 GitHub에 로그인합니다. 실명이 드러나지 않는 중립 계정으로 로그인하세요."
  info "'Authenticate Git with your GitHub credentials?' 질문은 어느 쪽을 골라도 이 저장소는 동작합니다."
  if "$GH" auth login --hostname github.com --web --git-protocol https &&
    "$GH" auth status --hostname github.com >/dev/null 2>&1; then
    GH_OK=1
    ok "GitHub 로그인"
  else
    fail "GitHub 로그인"
  fi
else
  todo "GitHub 로그인이 필요합니다(브라우저). 터미널에서: gh auth login --hostname github.com --web --git-protocol https"
fi

# ---- 5. 저장소 클론 또는 갱신 -----------------------------------------------
step "저장소 클론 또는 갱신 ($GGF_DIR)"
REPO_OK=0

# GGF_REPO(owner/name 또는 주소)를 클론 주소로 바꾼다.
repo_url() {
  case "$1" in
    https://* | http://* | ssh://* | git@* | file://*) printf '%s' "$1" ;;
    */*) printf 'https://github.com/%s.git' "${1%.git}" ;;
    *) return 1 ;;
  esac
}
is_github_https() { case "$1" in https://github.com/*) return 0 ;; *) return 1 ;; esac; }

# 이 저장소 안에만 설정한다. 전역 git 설정은 건드리지 않는다.
configure_repo() {
  local origin
  origin="$(git -C "$GGF_DIR" remote get-url origin 2>/dev/null || true)"
  if [ -n "$GH" ] && is_github_https "$origin"; then
    # gh auth setup-git과 같은 방식(빈 값으로 목록을 비운 뒤 gh 추가)을 저장소 범위로만 한다.
    # GUI 앱(Sourcetree)은 PATH가 짧아서 gh를 절대 경로로 적는다. [가정] Sourcetree도 이 설정을 따른다.
    git -C "$GGF_DIR" config --local --unset-all credential.https://github.com.helper >/dev/null 2>&1 || true
    git -C "$GGF_DIR" config --local --add credential.https://github.com.helper ""
    git -C "$GGF_DIR" config --local --add credential.https://github.com.helper "!\"$GH\" auth git-credential"
    info "GitHub 인증 도우미(gh)를 이 저장소에만 설정했습니다"
  fi
  # 블라인드 원칙: 이 Mac에서 커밋해도 실명이 들어가지 않게 저장소 범위 작성자를 정한다.
  git -C "$GGF_DIR" config --local user.name "${GGF_GIT_NAME:-DriveXFamily}"
  info "커밋 작성자 이름(이 저장소만): ${GGF_GIT_NAME:-DriveXFamily}"
  if [ -n "${GGF_GIT_EMAIL:-}" ]; then
    git -C "$GGF_DIR" config --local user.email "$GGF_GIT_EMAIL"
    info "커밋 작성자 이메일(이 저장소만): 설정함"
  elif ! git -C "$GGF_DIR" config --local --get user.email >/dev/null 2>&1; then
    warn "GGF_GIT_EMAIL이 없어 이메일은 설정하지 않았습니다. 이 Mac에서 커밋하면 전역 이메일(실명일 수 있음)이 들어갑니다."
    warn "중립 계정의 noreply 주소를 $GGF_CONFIG_FILE 에 GGF_GIT_EMAIL=... 로 넣고 다시 실행하세요."
  fi
}

# 원하는 브랜치로 옮기고 fast-forward만 한다. 갈라졌으면 덮어쓰지 않는다.
checkout_branch() {
  local def target current
  def="$(git -C "$GGF_DIR" symbolic-ref --quiet --short refs/remotes/origin/HEAD 2>/dev/null || true)"
  if [ -z "$def" ]; then
    git -C "$GGF_DIR" remote set-head origin --auto >/dev/null 2>&1 || true
    def="$(git -C "$GGF_DIR" symbolic-ref --quiet --short refs/remotes/origin/HEAD 2>/dev/null || true)"
  fi
  def="${def#origin/}"
  target="${GGF_BRANCH:-$def}"
  if [ -z "$target" ]; then
    warn "기본 브랜치를 알 수 없어 브랜치를 바꾸지 않았습니다 (GGF_BRANCH로 지정 가능)"
    return 0
  fi
  if ! git -C "$GGF_DIR" rev-parse --verify --quiet "refs/remotes/origin/$target" >/dev/null; then
    fail "원격에 '$target' 브랜치가 없습니다 (GGF_BRANCH 확인)"
    return 0
  fi
  current="$(git -C "$GGF_DIR" rev-parse --abbrev-ref HEAD 2>/dev/null || true)"
  if [ "$current" != "$target" ]; then
    if [ -n "$(git -C "$GGF_DIR" status --porcelain)" ]; then
      warn "작업 중인 변경이 있어 '$target' 브랜치로 바꾸지 않았습니다 (현재: $current)"
      return 0
    fi
    if git -C "$GGF_DIR" show-ref --verify --quiet "refs/heads/$target"; then
      git -C "$GGF_DIR" checkout --quiet "$target"
    else
      git -C "$GGF_DIR" checkout --quiet -b "$target" --track "origin/$target"
    fi
    info "브랜치: $target"
  fi
  if git -C "$GGF_DIR" merge --ff-only --quiet "origin/$target" >/dev/null 2>&1; then
    ok "브랜치 '$target'를 원격과 같게 맞춤"
  else
    warn "로컬 '$target'가 원격과 갈라졌습니다(AI가 히스토리를 고쳤을 수 있음). 자동으로 덮어쓰지 않았습니다."
    warn "Sourcetree에서 origin/$target 을 보면 AI 커밋을 볼 수 있습니다."
  fi
}

if [ -z "${GGF_REPO:-}" ] && [ ! -d "$GGF_DIR/.git" ] && [ "$INTERACTIVE" = 1 ]; then
  printf '    저장소 주소(owner/name 또는 https 주소)를 입력하세요: '
  read -r GGF_REPO || GGF_REPO=""
  if [ -n "$GGF_REPO" ]; then
    mkdir -p "$(dirname "$GGF_CONFIG_FILE")"
    printf 'GGF_REPO=%s\n' "$GGF_REPO" >>"$GGF_CONFIG_FILE"
    chmod 600 "$GGF_CONFIG_FILE"
    info "주소를 $GGF_CONFIG_FILE 에 저장했습니다(저장소에는 올라가지 않음)"
  fi
fi

if [ -d "$GGF_DIR/.git" ]; then
  REPO_OK=1
  info "이미 클론되어 있습니다"
elif [ -z "${GGF_REPO:-}" ]; then
  todo "저장소 주소가 없습니다. $GGF_CONFIG_FILE 에 GGF_REPO=owner/name 을 적으세요 (예시: tools/env.example)"
elif ! CLONE_URL="$(repo_url "$GGF_REPO")"; then
  fail "GGF_REPO 형식을 알 수 없습니다 (owner/name 또는 https 주소)"
elif [ -e "$GGF_DIR" ] && [ -n "$(ls -A "$GGF_DIR" 2>/dev/null)" ]; then
  fail "$GGF_DIR 가 비어 있지 않고 git 저장소도 아닙니다. 건드리지 않았습니다. GGF_DIR로 다른 폴더를 정하세요."
else
  mkdir -p "$(dirname "$GGF_DIR")"
  info "클론합니다 (주소는 설정 파일에서 읽음)"
  if [ -n "$GH" ] && is_github_https "$CLONE_URL"; then
    # 클론하는 동안만 gh 인증을 쓴다. 이후에는 configure_repo가 저장소 안에 설정한다.
    clone_ok=0
    git -c credential.helper= -c "credential.helper=!\"$GH\" auth git-credential" \
      clone "$CLONE_URL" "$GGF_DIR" && clone_ok=1
  else
    clone_ok=0
    git clone "$CLONE_URL" "$GGF_DIR" && clone_ok=1
  fi
  if [ "$clone_ok" = 1 ]; then
    REPO_OK=1
    ok "클론"
  elif [ "$GH_OK" != 1 ]; then
    todo "클론하지 못했습니다. 비공개 저장소면 GitHub 로그인(4단계) 후 다시 실행하세요."
  else
    fail "클론 (주소와 저장소 접근 권한 확인)"
  fi
fi

if [ "$REPO_OK" = 1 ]; then
  configure_repo
  if git -C "$GGF_DIR" fetch --all --prune; then
    ok "원격 변경 받기(fetch)"
    checkout_branch
  elif [ "$GH_OK" != 1 ]; then
    todo "fetch 실패. GitHub 로그인(4단계) 후 다시 실행하세요."
  else
    fail "fetch (네트워크·접근 권한 확인)"
  fi
fi

# ---- 6. Sourcetree ----------------------------------------------------------
step "Sourcetree로 저장소 열기"
if [ "$REPO_OK" != 1 ]; then
  skip "저장소가 준비되지 않아 건너뜀"
elif ! app_exists "Sourcetree.app"; then
  fail "Sourcetree.app이 없습니다 (3단계 결과 확인)"
elif open -a Sourcetree "$GGF_DIR"; then
  # [가정] 'open -a Sourcetree <폴더>'로 저장소 탭이 열리고 목록에 추가된다.
  ok "Sourcetree 열기 요청 (첫 실행이면 동의·계정 연결 화면이 먼저 뜹니다)"
else
  fail "Sourcetree 열기 (화면이 잠겨 있으면 실패할 수 있음)"
fi
# Sourcetree 자동 fetch는 설정 키를 확인하지 못해서 자동으로 바꾸지 않는다.
info "자동 갱신은 Sourcetree 설정(Settings…)에서 원격 저장소를 주기적으로 확인하는 항목을 직접 켜세요."
info "(항목 이름과 위치는 버전마다 달라 확인하지 못했습니다. 확실한 방법: bash tools/mac/sync.sh)"

# ---- 7. Android SDK ---------------------------------------------------------
step "Android SDK ($ANDROID_HOME)"
SDK_OK=0
SDKM=""
SDK_MISSING=""
mkdir -p "$LOG_DIR"

# JDK 17을 찾는다. 셸 설정 파일은 바꾸지 않고 이 스크립트 안에서만 JAVA_HOME을 정한다.
setup_java() {
  local p=""
  if [ -n "$BREW" ] && p="$("$BREW" --prefix openjdk@17 2>/dev/null)" &&
    [ -x "$p/libexec/openjdk.jdk/Contents/Home/bin/java" ]; then
    JAVA_HOME="$p/libexec/openjdk.jdk/Contents/Home"
  elif [ -x "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java" ]; then
    JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  elif p="$(/usr/libexec/java_home -v 17+ 2>/dev/null)" && [ -n "$p" ]; then
    JAVA_HOME="$p"
  else
    return 1
  fi
  export JAVA_HOME
  PATH="$JAVA_HOME/bin:$PATH"
  export PATH
}

# 패키지가 설치됐는지 폴더로 확인한다.
# [검증] TTY가 없을 때 라이선스를 거절하면 sdkmanager는 설치를 건너뛰고도 0으로 끝난다.
#        그래서 종료 코드가 아니라 폴더(package.xml 또는 source.properties)로 판단한다.
pkg_present() {
  local rel
  rel="$(printf '%s' "$1" | tr ';' '/')"
  [ -f "$ANDROID_HOME/$rel/package.xml" ] || [ -f "$ANDROID_HOME/$rel/source.properties" ]
}

# 라이선스 동의 방식
#   터미널: sdkmanager가 라이선스 전문을 보여 주고 사람이 y를 직접 친다.
#   비대화형 + GGF_ACCEPT_ANDROID_LICENSES=yes: 사람이 미리 읽고 동의한 것으로 보고 y를 넣는다.
#   비대화형 + 그 외: 동의하지 않는다(설치가 건너뛰어짐 → 할 일로 안내).
run_sdkmanager() {
  local call_log="$TMPD/sdk-call.log" rc=0
  : >"$call_log"
  if [ "$INTERACTIVE" = 1 ]; then
    "$SDKM" --sdk_root="$ANDROID_HOME" "$@" || rc=$?
  elif [ "${GGF_ACCEPT_ANDROID_LICENSES:-no}" = "yes" ]; then
    { yes 2>/dev/null || true; } | "$SDKM" --sdk_root="$ANDROID_HOME" "$@" >"$call_log" 2>&1 || rc=$?
  else
    "$SDKM" --sdk_root="$ANDROID_HOME" "$@" </dev/null >"$call_log" 2>&1 || rc=$?
  fi
  {
    printf '\n===== %s sdkmanager %s =====\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
    cat "$call_log"
  } >>"$SDK_LOG"
  return "$rc"
}

# 없는 패키지만 한 번에 설치하고, 끝나면 폴더로 다시 확인한다.
sdk_install() {
  local p n=0
  local -a need
  need=()
  SDK_MISSING=""
  for p in "$@"; do
    if pkg_present "$p"; then
      info "있음: $p"
    else
      need[n]="$p"
      n=$((n + 1))
    fi
  done
  [ "$n" -gt 0 ] || return 0
  info "설치: ${need[*]}"
  [ "$INTERACTIVE" = 1 ] || info "진행 기록: $SDK_LOG"
  run_sdkmanager --install "${need[@]}" || true
  for p in "${need[@]}"; do
    pkg_present "$p" || SDK_MISSING="$SDK_MISSING $p"
  done
  [ -z "$SDK_MISSING" ]
}

# 설치가 덜 됐을 때 이유에 맞게 안내한다.
report_sdk_missing() {
  if [ "$INTERACTIVE" != 1 ] && grep -q "not accepted" "$TMPD/sdk-call.log" 2>/dev/null; then
    todo "라이선스 동의가 필요합니다:$SDK_MISSING"
    info "방법 1: 터미널에서 bash tools/mac/setup.sh 를 한 번 실행하고 라이선스에 y로 답하기"
    info "방법 2: 라이선스를 직접 읽은 뒤 $GGF_CONFIG_FILE 에 GGF_ACCEPT_ANDROID_LICENSES=yes 를 넣고 다시 실행"
  elif [ "$INTERACTIVE" = 1 ]; then
    fail "설치되지 않음:$SDK_MISSING (라이선스에 y로 답했는지, 네트워크·디스크 확인)"
  else
    fail "설치되지 않음:$SDK_MISSING (네트워크·디스크 확인, 기록: $SDK_LOG)"
  fi
}

if ! setup_java; then
  fail "JDK 17을 찾지 못했습니다 (3단계의 openjdk@17 확인)"
else
  info "Java: $JAVA_HOME"
  mkdir -p "$ANDROID_HOME"
  if [ -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
    SDKM="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
  elif command -v sdkmanager >/dev/null 2>&1; then
    # Homebrew의 android-commandlinetools. --sdk_root로 우리 SDK 폴더에 설치하게 한다. [검증: Linux에서 --sdk_root 설치 동작]
    SDKM="$(command -v sdkmanager)"
  fi
  if [ -z "$SDKM" ]; then
    fail "sdkmanager가 없습니다 (3단계의 android-commandlinetools 확인)"
  else
    # cmdline-tools;latest를 SDK 안에 둔다. avdmanager는 자기 위치로 SDK를 찾기 때문이다. [검증]
    if sdk_install "cmdline-tools;latest" "platform-tools" "emulator" "platforms;android-35" "build-tools;35.0.0"; then
      SDK_OK=1
      ok "Android SDK 준비 (platform-tools, emulator, platforms;android-35, build-tools;35.0.0)"
    else
      report_sdk_missing
    fi
    if [ -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
      SDKM="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
    fi
  fi
fi
if [ "$REPO_OK" = 1 ] && [ ! -f "$GGF_DIR/local.properties" ] && [ -d "$ANDROID_HOME" ]; then
  # Gradle이 SDK 위치를 알게 한다. local.properties는 .gitignore에 있어 저장소에 안 올라간다.
  printf 'sdk.dir=%s\n' "$ANDROID_HOME" >"$GGF_DIR/local.properties"
  info "local.properties에 sdk.dir을 적었습니다"
fi

# ---- 8. Pleos Connect 이미지 ------------------------------------------------
step "Pleos Connect 에뮬레이터 이미지"
PLEOS_PKG=""

# repositories.cfg에 사이트를 더한다. 기존 줄은 그대로 두고 번호를 이어 붙인다.
# [검증] cmdline-tools 12.0의 sdkmanager는 이 파일(count, srcNN, dispNN, enabledNN)을 읽어
#        Pleos 사이트의 이미지를 목록에 보여 줬다. 파일이 없을 때는 보이지 않았다.
# [가정] Android Studio의 SDK Update Sites도 같은 파일을 쓴다(Studio에서 보이는지는 Mac에서 확인).
register_site() {
  local cfg="$1" url="$2" name="$3" next idx tmp
  mkdir -p "$(dirname "$cfg")"
  if [ -f "$cfg" ]; then
    if awk -v u="$url" '/^src[0-9]+=/ { v = substr($0, index($0, "=") + 1); gsub(/\\/, "", v); if (v == u) f = 1 }
         END { exit f ? 0 : 1 }' "$cfg"; then
      info "사이트가 이미 등록되어 있습니다 ($cfg)"
      if awk -v u="$url" '
           /^src[0-9]+=/ { v = substr($0, index($0, "=") + 1); gsub(/\\/, "", v); if (v == u) k = substr($0, 4, index($0, "=") - 4) }
           /^enabled[0-9]+=/ { e[substr($0, 8, index($0, "=") - 8)] = substr($0, index($0, "=") + 1) }
           END { exit (k != "" && e[k] == "false") ? 0 : 1 }' "$cfg"; then
        warn "등록된 사이트가 꺼져 있습니다. Android Studio의 SDK Update Sites에서 체크를 켜세요."
      fi
      return 0
    fi
    # 다음 번호 = max(count, 가장 큰 번호 + 1)
    next="$(awk '/^count=/ { c = substr($0, 7) + 0 }
                 /^src[0-9]+=/ { i = substr($0, 4, index($0, "=") - 4) + 0; if (i + 1 > m) m = i + 1 }
                 END { print (c > m ? c : m) + 0 }' "$cfg")"
    cp -p "$cfg" "$cfg.bak-$(date +%Y%m%d-%H%M%S)"
    info "기존 repositories.cfg를 백업했습니다"
  else
    next=0
  fi
  idx="$(printf '%02d' "$next")"
  tmp="$(mktemp "$cfg.tmp.XXXXXX")"
  {
    if [ -f "$cfg" ]; then
      awk '!/^count=/' "$cfg"
    else
      printf '### User Sources for Android SDK Manager\n'
    fi
    printf 'count=%d\n' "$((next + 1))"
    printf 'disp%s=%s\n' "$idx" "$name"
    printf 'enabled%s=true\n' "$idx"
    printf 'src%s=%s\n' "$idx" "$url"
  } >"$tmp"
  mv "$tmp" "$cfg"
  info "사이트를 등록했습니다: $cfg (번호 $idx)"
}

# 이미 설치된 Pleos 이미지를 SDK 폴더에서 찾는다(Android Studio로 설치한 경우 포함).
# [확인 2026-10-31] Mac의 최신 cmdline-tools는 repositories.cfg에 등록한 사이트를
#   --list에 보여 주지 않았다. 그래서 Android Studio로 설치한 뒤에도 원격 목록만 보면
#   "못 찾음"이 되어 9단계가 건너뛰어졌다. 설치된 폴더를 먼저 본다.
find_installed_pleos() {
  local d rel low best=""
  for d in "$ANDROID_HOME"/system-images/android-*/*/*; do
    [ -f "$d/package.xml" ] || [ -f "$d/source.properties" ] || continue
    rel="${d#"$ANDROID_HOME"/}"
    low="$(printf '%s' "$rel" | tr '[:upper:]' '[:lower:]')"
    case "$low" in *pleos*|*crp*) ;; *) continue ;; esac
    rel="$(printf '%s' "$rel" | tr '/' ';')"
    case "$rel" in *android-34*arm64-v8a*) printf '%s' "$rel"; return 0 ;; esac
    [ -n "$best" ] || best="$rel"
  done
  printf '%s' "$best"
}

# Pleos 이미지 경로를 찾는다(짐작해서 하드코딩하지 않음): 설치된 폴더 → sdkmanager 목록 순.
# 화면 출력 없이 경로만 돌려준다.
find_pleos_pkg() {
  local list="$TMPD/sdk-list.txt" cands c best=""
  if [ -n "${GGF_PLEOS_PKG:-}" ]; then
    printf '%s' "$GGF_PLEOS_PKG"
    return 0
  fi
  best="$(find_installed_pleos)"
  if [ -n "$best" ]; then
    printf '%s' "$best"
    return 0
  fi
  "$SDKM" --sdk_root="$ANDROID_HOME" --list </dev/null >"$list" 2>/dev/null || true
  cands="$(tr '\r' '\n' <"$list" |
    awk -F'|' '{ gsub(/^[ \t]+|[ \t]+$/, "", $1) } $1 ~ /^system-images;/ { print $1 }' |
    grep -i -E 'pleos|crp' | sort -u || true)"
  for c in $cands; do
    case "$c" in *android-34*arm64-v8a*) best="$c"; break ;; esac
  done
  if [ -z "$best" ]; then
    for c in $cands; do
      case "$c" in *arm64-v8a*) best="$c"; break ;; esac
    done
  fi
  printf '%s' "$best"
}

print_studio_fallback() {
  info "Android Studio로 직접 설치하는 방법 (docs/pleos-dev-guide.md 2-3):"
  info "  1) Android Studio → Settings → Languages & Frameworks → Android SDK"
  info "  2) SDK Update Sites 탭 → + → Name: $PLEOS_SITE_NAME"
  info "     URL: $PLEOS_SITE_URL"
  info "  3) SDK Platforms 탭 → Show Package Details → Android 14 (API 34) 아래"
  info "     'Pleos Connect v2.1.0 APIs arm64 System Image' 체크 → Apply → Android Studio 재시작"
  info "  4) 설치가 끝나면 이 스크립트를 다시 실행하세요(9단계 AVD 만들기)."
}

if [ "$SDK_OK" != 1 ] || [ -z "$SDKM" ]; then
  skip "Android SDK가 준비되지 않아 건너뜀"
else
  if pgrep -f "Android Studio.app/Contents/MacOS/studio" >/dev/null 2>&1; then
    warn "Android Studio가 켜져 있습니다. Studio가 닫힐 때 repositories.cfg를 다시 쓸 수 있습니다."
  fi
  register_site "$REPOS_CFG" "$PLEOS_SITE_URL" "$PLEOS_SITE_NAME"
  PLEOS_PKG="$(find_pleos_pkg)"
  if [ -z "$PLEOS_PKG" ]; then
    todo "sdkmanager 목록에서 Pleos 이미지를 찾지 못했습니다. Android Studio로 설치하세요."
    print_studio_fallback
  elif pkg_present "$PLEOS_PKG"; then
    skip "이미 설치됨: $PLEOS_PKG"
  else
    info "찾은 패키지: $PLEOS_PKG"
    info "내려받기 약 4.7GB, 풀면 약 19GB입니다. 시간이 오래 걸립니다."
    info "Pleos Connect SDK 라이선스(42dot)와 Android 에뮬레이터 라이선스 동의가 필요합니다."
    if sdk_install "$PLEOS_PKG"; then
      ok "Pleos 이미지 설치: $PLEOS_PKG"
    else
      report_sdk_missing
    fi
  fi
fi

# ---- 9. AVD -------------------------------------------------------------------
step "AVD \"$AVD\" 만들기와 설정"
AVDM="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"
AVD_DIR="$AVD_HOME/$AVD.avd"
AVD_INI="$AVD_DIR/config.ini"
AVD_OK=0
AVD_NEW=0

ini_get() { # $1 = 파일, $2 = 키
  awk -v k="$2" '{ i = index($0, "="); if (i == 0) next
                   kk = substr($0, 1, i - 1); gsub(/^[ \t]+|[ \t]+$/, "", kk)
                   if (kk == k) { v = substr($0, i + 1); gsub(/^[ \t]+|[ \t]+$/, "", v); val = v; f = 1 } }
                 END { if (f) print val }' "$1"
}
ini_set() { # $1 = 파일, $2 = 키, $3 = 값 (있으면 바꾸고 없으면 더함)
  local tmp
  tmp="$(mktemp "$1.tmp.XXXXXX")"
  awk -v k="$2" '{ i = index($0, "="); kk = (i > 0) ? substr($0, 1, i - 1) : $0
                   gsub(/^[ \t]+|[ \t]+$/, "", kk); if (kk != k) print }' "$1" >"$tmp"
  printf '%s=%s\n' "$2" "$3" >>"$tmp"
  mv "$tmp" "$1"
}
apply_avd_settings() { # $1 = "키=값" 줄 목록
  local kv key val cur
  while IFS= read -r kv; do
    [ -n "$kv" ] || continue
    key="${kv%%=*}"
    val="${kv#*=}"
    cur="$(ini_get "$AVD_INI" "$key")"
    if [ "$cur" != "$val" ]; then
      if [ "$AVD_BACKED_UP" = 0 ]; then
        cp -p "$AVD_INI" "$AVD_INI.bak-$(date +%Y%m%d-%H%M%S)"
        AVD_BACKED_UP=1
        info "config.ini를 백업했습니다"
      fi
      ini_set "$AVD_INI" "$key" "$val"
      info "$key: ${cur:-(없음)} → $val"
      AVD_CHANGED=$((AVD_CHANGED + 1))
    fi
  done <<EOF
$1
EOF
}

if [ -f "$AVD_INI" ]; then
  AVD_OK=1
  skip "AVD가 이미 있습니다: $AVD_DIR (새로 만들지 않음)"
elif [ -z "$PLEOS_PKG" ] || ! pkg_present "$PLEOS_PKG"; then
  skip "Pleos 이미지가 없어 AVD를 만들지 않음"
elif [ ! -x "$AVDM" ]; then
  fail "avdmanager가 없습니다: $AVDM"
else
  # 장치 프로필 고르기
  # [검증] Pleos 이미지 안의 devices.xml에 id "Pleos_Connect_v2_0"(2560x1440, 가로, android-automotive)이 있고,
  #        이미지가 SDK에 있으면 avdmanager list device에 이 id가 나온다(Linux에서 이미지 파일을 흉내 내 확인).
  # 없으면 Automotive 기본 프로필(가로)로 대신한다.
  devices="$("$AVDM" list device -c 2>/dev/null | tr -d '\r' || true)"
  cands="$(printf '%s\n' "$devices" | grep -i 'pleos' || true)
$(printf '%s\n' "$devices" | grep -x 'automotive_1080p_landscape' || true)
$(printf '%s\n' "$devices" | grep -x 'automotive_1024p_landscape' || true)
$(printf '%s\n' "$devices" | grep -i 'automotive' || true)"
  tried=""
  for dev in $cands; do
    case " $tried " in *" $dev "*) continue ;; esac
    tried="$tried $dev"
    info "장치 프로필 $dev 로 만듭니다"
    # 'custom hardware profile?' 질문에 no로 답한다.
    if printf 'no\n' | "$AVDM" create avd -n "$AVD" -k "$PLEOS_PKG" -d "$dev" >"$TMPD/avd.log" 2>&1 &&
      [ -f "$AVD_INI" ]; then
      AVD_OK=1
      AVD_NEW=1
      ok "AVD 생성: $AVD (장치 $dev, 이미지 $PLEOS_PKG)"
      break
    fi
    warn "$dev 로 만들지 못했습니다: $(sed -n '/[Ee]rror/{p;q;}' "$TMPD/avd.log")"
  done
  if [ "$AVD_OK" != 1 ]; then
    fail "AVD를 만들지 못했습니다. Android Studio Device Manager → + → Automotive → Pleos Connect v2.1.0 으로 만드세요(이름 $AVD, Cold boot, RAM 4GB)."
  fi
fi

if [ "$AVD_OK" = 1 ]; then
  AVD_BACKED_UP=0
  AVD_CHANGED=0
  apply_avd_settings "$AVD_SETTINGS"
  if [ "$AVD_NEW" = 1 ]; then
    apply_avd_settings "$AVD_SETTINGS_NEW_ONLY"
  fi
  if [ "$AVD_CHANGED" -gt 0 ]; then
    ok "config.ini 설정 ${AVD_CHANGED}개 반영 (Cold boot, RAM 4GB, GPU host)"
  else
    skip "config.ini가 이미 원하는 값입니다"
  fi
fi

# ---- 10. 요약 -----------------------------------------------------------------
finish
