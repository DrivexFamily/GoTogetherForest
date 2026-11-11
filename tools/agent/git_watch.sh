#!/usr/bin/env bash
# Claude 앱 PM용 git 감시(design/illustrations/coordination/PROTOCOL.md).
# 5분마다 main·작업 브랜치·chatgpt/* 를 받아 새 커밋을 한 줄로 알린다. 저장소 작업 트리는 건드리지 않는다.
# 쓰는 법(Claude Code의 Monitor 도구로, 30분마다 다시 켬):
#   bash tools/agent/git_watch.sh <상태 폴더> <내 세션 ID(커밋 본문의 Claude-Session 값, 내 커밋 빼기용)>
REPO=$(cd "$(dirname "$0")/../.." && pwd)
STATE=${1:?상태 폴더를 주세요(예: 스크래치패드/watch)}
ME=${2:-no-session}
mkdir -p "$STATE"
cd "$REPO" || exit 1
for b in main claude/lucid-babbage-a6quds; do
  f="$STATE/last-$(echo $b | tr '/' '_')"
  [ -s "$f" ] || git rev-parse "origin/$b" > "$f" 2>/dev/null
done
while true; do
  ok=0
  for i in 1 2 3; do git fetch -q origin main claude/lucid-babbage-a6quds 2>/dev/null && { ok=1; break; }; sleep $((i*4)); done
  if [ $ok = 0 ]; then echo "[git 확인 실패] $(date -u +%H:%M) fetch 3번 실패"; sleep 300; continue; fi
  for b in main claude/lucid-babbage-a6quds; do
    f="$STATE/last-$(echo $b | tr '/' '_')"
    old=$(cat "$f"); new=$(git rev-parse "origin/$b")
    if [ "$old" != "$new" ]; then
      list=""
      for c in $(git rev-list --no-merges "$old..$new" 2>/dev/null); do
        git log -1 --format=%B "$c" | grep -q "$ME" && continue
        list="$list$(git log -1 --format='%h %s' "$c")"$'\n'
      done
      n=$(printf '%s\n' "$list" | grep -c . )
      coord=$(git diff --name-only "$old" "$new" -- design/illustrations/coordination design/illustrations/COLLABORATION.md docs/team.md 2>/dev/null | tr '\n' ' ')
      if [ "$n" -gt 0 ] || [ -n "$coord" ]; then
        latest=$(printf '%s\n' "$list" | head -3 | tr '\n' '|')
        echo "[git ${b%%/*}] 새 커밋 ${n}개(내 것 제외) ${old:0:7}..${new:0:7} 최근: ${latest} ${coord:+소통 파일 변경: $coord}"
      fi
      echo "$new" > "$f"
    fi
  done
  # GPT 코드 브랜치(chatgpt/*): 새 브랜치나 새 커밋이 생기면 한 줄
  git fetch -q origin '+refs/heads/chatgpt/*:refs/remotes/origin/chatgpt/*' 2>/dev/null
  cur=$(git for-each-ref --format='%(refname:short) %(objectname:short)' 'refs/remotes/origin/chatgpt/*' | sort)
  prevf="$STATE/last-chatgpt-branches"
  if [ -f "$prevf" ]; then
    changed=$(comm -13 "$prevf" <(echo "$cur") | tr '\n' ' ')
    [ -n "$changed" ] && echo "[git chatgpt] 브랜치 새로 올라옴: $changed"
  fi
  echo "$cur" > "$prevf"
  sleep 300
done
