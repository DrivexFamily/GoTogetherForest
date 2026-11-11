#!/usr/bin/env bash
# 같이가숲 release 서명 키 만들기(마스터 Mac에서 한 번만). 키는 저장소 밖에 둔다.
# 키를 새로 바꾸기(예전 키는 .bak으로 남김): bash tools/mac/make-release-key.sh --replace
# - 키 파일: ~/.android-keys/ggf-release.jks
# - 비밀번호·별칭: ~/.gradle/gradle.properties (본인만 읽기, 600)
# - 이름(DN)은 블라인드 원칙대로 팀명만(CN=DriveXFamily). 실명·메일을 넣지 않는다. (11-11 이전에 만든 키는 CN=Acorn, 그대로 써도 됨)
# 키를 잃어버리면 같은 앱을 이어서 올릴 수 없으므로, 만든 뒤 키 파일과 비밀번호를 안전한 곳(암호 관리자 등)에 따로 보관하세요.
set -euo pipefail
KEY_DIR="$HOME/.android-keys"
KEY="$KEY_DIR/ggf-release.jks"
PROPS="$HOME/.gradle/gradle.properties"
if [ -f "$KEY" ]; then
  if [ "${1:-}" = "--replace" ]; then
    # 아직 앱을 배포한 적이 없을 때만 쓴다(배포 뒤 키를 바꾸면 같은 앱으로 업데이트할 수 없음)
    BAK="$KEY.$(date +%Y%m%d-%H%M%S).bak"
    mv "$KEY" "$BAK"
    echo "예전 키를 옮겨 두었습니다: $BAK"
  else
    echo "이미 키가 있습니다: $KEY (새로 만들지 않음. 바꾸려면 --replace)"; exit 0
  fi
fi
# macOS의 /usr/bin/keytool은 JDK가 연결되지 않으면 "Unable to locate a Java Runtime"으로 실패한다(11-11 마스터 Mac).
# try.sh와 같은 순서로 JDK 17을 찾아 그 안의 keytool을 쓴다.
KEYTOOL=""
for home in "${JAVA_HOME:-}" \
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
    "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" \
    "/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" \
    "$(/usr/libexec/java_home -v 17+ 2>/dev/null || true)"; do
  if [ -n "$home" ] && [ -x "$home/bin/keytool" ]; then KEYTOOL="$home/bin/keytool"; break; fi
done
[ -n "$KEYTOOL" ] || { echo "JDK 17의 keytool을 찾지 못했습니다. 터미널에서: brew install openjdk@17"; exit 1; }
echo "keytool: $KEYTOOL"
mkdir -p "$KEY_DIR" "$HOME/.gradle"
chmod 700 "$KEY_DIR"
# (예전 줄 `tr </dev/urandom | head`는 pipefail에서 tr이 SIGPIPE로 끝나 스크립트가 아무 말 없이 멈췄다. 11-11 고침)
PASS="$(openssl rand -hex 16)"
[ ${#PASS} -ge 24 ] || { echo "비밀번호를 만들지 못했습니다(openssl 확인)."; exit 1; }
echo "키를 만드는 중입니다…"
"$KEYTOOL" -genkeypair -v -keystore "$KEY" -alias ggf -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$PASS" -keypass "$PASS" -dname "CN=DriveXFamily, O=DriveXFamily, C=KR" >/dev/null \
  || { echo "keytool이 실패했습니다(위 메시지 확인)."; exit 1; }
chmod 600 "$KEY"
touch "$PROPS"; chmod 600 "$PROPS"
grep -v '^ggf\.release\.' "$PROPS" > "$PROPS.tmp" || true
{
  cat "$PROPS.tmp"
  echo "ggf.release.storeFile=$KEY"
  echo "ggf.release.storePassword=$PASS"
  echo "ggf.release.keyAlias=ggf"
  echo "ggf.release.keyPassword=$PASS"
} > "$PROPS"
rm -f "$PROPS.tmp"
echo "키를 만들었습니다: $KEY"
echo "비밀번호는 $PROPS 에 적었습니다(이 파일과 키 파일을 함께 안전한 곳에 백업하세요)."
"$KEYTOOL" -list -keystore "$KEY" -storepass "$PASS" | grep -i "fingerprint\|지문" || true
