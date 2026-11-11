#!/usr/bin/env bash
# =============================================================================
# 같이가숲 — AI 커밋 새로 받기 (Sourcetree로 보기 전에)
#
# 사용법
#   bash tools/mac/sync.sh            한 번 받기
#   bash tools/mac/sync.sh watch 60   60초마다 계속 받기 (Ctrl+C로 멈춤)
#
# 무엇을 바꾸나: 원격 추적 브랜치(origin/...)만 갱신한다(git fetch).
#               내 작업 폴더의 파일과 로컬 브랜치는 바꾸지 않는다. Sourcetree는 이 변화를 보고 화면을 갱신한다.
# 다시 실행해도 되나: 된다.
# 설정: GGF_DIR (없으면 이 스크립트가 들어 있는 저장소, 그것도 아니면 ~/Projects/familyjourney)
# 검증: bash -n, shellcheck만 통과. Mac에서 아직 실행해 보지 않았다.
# =============================================================================
set -euo pipefail
export GIT_TERMINAL_PROMPT=0

# 설정 파일에서 GGF_DIR만 읽는다(source 하지 않음). 환경 변수가 있으면 그것이 이긴다.
CONFIG="${GGF_CONFIG_FILE:-$HOME/.config/familyjourney/env}"
if [ -z "${GGF_DIR:-}" ] && [ -f "$CONFIG" ]; then
  # 마지막 GGF_DIR= 줄의 값. 따옴표·줄끝 CR·뒤 공백을 지운다.
  GGF_DIR="$(sed -n 's/^[[:space:]]*GGF_DIR[[:space:]]*=[[:space:]]*//p' "$CONFIG" | tail -n 1 | tr -d "\r\"'")"
  GGF_DIR="${GGF_DIR%"${GGF_DIR##*[![:space:]]}"}"
  # shellcheck disable=SC2016,SC2088  # 글자 그대로의 ~ 와 $HOME을 찾는 것이 목적이다
  case "$GGF_DIR" in
    '~/'*) GGF_DIR="$HOME/${GGF_DIR#'~/'}" ;;
    '$HOME/'*) GGF_DIR="$HOME/${GGF_DIR#'$HOME/'}" ;;
  esac
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
if [ -n "${GGF_DIR:-}" ]; then
  DIR="$GGF_DIR"
elif ! DIR="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel 2>/dev/null)"; then
  DIR="$HOME/Projects/familyjourney"
fi
[ -d "$DIR/.git" ] || {
  echo "[오류] git 저장소가 아닙니다: $DIR (먼저 bash tools/mac/setup.sh)" >&2
  exit 1
}

sync_once() {
  local rc=0
  printf '== %s  %s\n' "$(date '+%H:%M:%S')" "$DIR"
  git -C "$DIR" fetch --all --prune || {
    echo "[주의] fetch 실패 (GitHub 로그인·네트워크 확인)"
    rc=1
  }
  git -C "$DIR" status -sb
  echo "-- 최근에 바뀐 원격 브랜치 5개"
  # origin/HEAD(기본 브랜치를 가리키는 별칭)는 빼고 보여 준다.
  git -C "$DIR" for-each-ref --sort=-committerdate \
    --format='%(if)%(symref)%(then)%(else)   %(refname:short)  (%(committerdate:relative))  %(subject)%(end)' \
    refs/remotes/origin | sed '/^$/d' | sed -n '1,5p'
  return "$rc"
}

case "${1:-once}" in
  once)
    sync_once
    ;;
  watch)
    interval="${2:-60}"
    case "$interval" in '' | *[!0-9]*)
      echo "[오류] 초는 숫자로 적으세요. 예: sync.sh watch 60" >&2
      exit 2
      ;;
    esac
    echo "${interval}초마다 받습니다. 멈추려면 Ctrl+C."
    while :; do
      sync_once || true
      sleep "$interval"
    done
    ;;
  *)
    sed -n '5,7p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
    exit 2
    ;;
esac
