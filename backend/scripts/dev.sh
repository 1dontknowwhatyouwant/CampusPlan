#!/usr/bin/env bash
# 로컬 개발용: JDK 17 로 WAR 를 빌드하고 프로젝트 전용 Tomcat(10.1) 인스턴스에서 실행합니다.
# 사용법: backend/scripts/dev.sh [build|run]   (기본값: run = 빌드 후 Tomcat 실행)
# 사전 준비: brew install openjdk@17 tomcat@10 maven, 저장소 루트의 .env (.env.example 참고)
set -euo pipefail

BACKEND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT_DIR="$(cd "$BACKEND_DIR/.." && pwd)"
BREW_PREFIX="$(brew --prefix)"

export JAVA_HOME="${JAVA_HOME_17:-$BREW_PREFIX/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
export CATALINA_HOME="$BREW_PREFIX/opt/tomcat@10/libexec"
export CATALINA_BASE="$BACKEND_DIR/.tomcat"

if [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "JDK 17 을 찾을 수 없습니다: $JAVA_HOME (brew install openjdk@17)" >&2
  exit 1
fi
if [ ! -f "$ROOT_DIR/.env" ]; then
  echo "저장소 루트에 .env 가 없습니다. .env.example 을 복사해 값을 채우세요." >&2
  exit 1
fi

build() {
  (cd "$BACKEND_DIR" && mvn -q -B package)
}

run() {
  set -a
  # shellcheck disable=SC1091
  . "$ROOT_DIR/.env"
  set +a

  mkdir -p "$CATALINA_BASE"/{conf,logs,temp,webapps,work}
  if [ ! -f "$CATALINA_BASE/conf/server.xml" ]; then
    cp -R "$CATALINA_HOME/conf/." "$CATALINA_BASE/conf/"
  fi
  rm -rf "$CATALINA_BASE/webapps/ROOT" "$CATALINA_BASE/webapps/ROOT.war"
  cp "$BACKEND_DIR/target/campusplan-api.war" "$CATALINA_BASE/webapps/ROOT.war"

  echo "Tomcat 시작: http://localhost:8080/api/health (종료: Ctrl+C)"
  exec "$CATALINA_HOME/bin/catalina.sh" run
}

case "${1:-run}" in
  build) build ;;
  run) build; run ;;
  *) echo "사용법: $0 [build|run]" >&2; exit 1 ;;
esac
