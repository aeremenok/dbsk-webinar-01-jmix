#!/usr/bin/env bash
# doctor.sh — проверка рабочего места перед лабой «SDD».
#
# Проверяет: версии java (21), node (22), opencode, git, curl, jq; наличие ключа
# DEEPSEEK_API_KEY (печатается только ***); короткий ответ обеих моделей с замером времени;
# компиляцию проекта (./gradlew -q compileJava).
#
# Использование:
#   scripts/doctor.sh              полная проверка
#   scripts/doctor.sh --no-llm     без запросов к моделям (быстро, без токенов)
#   scripts/doctor.sh --help
#
# Переменные окружения:
#   DOCTOR_PRO_MODEL    модель «Pro»   (по умолчанию из .opencode/commands, иначе deepseek/deepseek-v4-pro)
#   DOCTOR_FLASH_MODEL  модель «Flash» (по умолчанию из opencode.json, иначе deepseek/deepseek-v4-flash)
#   DOCTOR_LLM_TIMEOUT  таймаут одного запроса к модели, секунды (по умолчанию 120)
#
# Вывод скрипта участник присылает в чат подготовки. Код возврата: 0 — всё зелёное, 1 — есть красное.

set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 2

WANT_JAVA=21
WANT_NODE=22
WANT_OPENCODE=1.19.4
LLM_TIMEOUT="${DOCTOR_LLM_TIMEOUT:-120}"

RUN_LLM=1
for arg in "$@"; do
  case "$arg" in
    --no-llm) RUN_LLM=0 ;;
    -h|--help)
      sed -n '2,19p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *)
      echo "Неизвестный аргумент: $arg (см. --help)" >&2
      exit 2
      ;;
  esac
done

if [ -t 1 ]; then
  C_OK=$'\033[32m'; C_BAD=$'\033[31m'; C_WARN=$'\033[33m'; C_OFF=$'\033[0m'
else
  C_OK=''; C_BAD=''; C_WARN=''; C_OFF=''
fi

FAILS=0
WARNS=0
ok()   { printf '%s[ OK ]%s %s\n'   "$C_OK"   "$C_OFF" "$*"; }
bad()  { printf '%s[FAIL]%s %s\n'   "$C_BAD"  "$C_OFF" "$*"; FAILS=$((FAILS + 1)); }
warn() { printf '%s[WARN]%s %s\n'   "$C_WARN" "$C_OFF" "$*"; WARNS=$((WARNS + 1)); }
info() { printf '       %s\n' "$*"; }
head_() { printf '\n== %s ==\n' "$*"; }

# Миллисекунды (GNU date); на macOS %N не поддерживается — откатываемся к секундам.
now_ms() {
  local t
  t="$(date +%s%N 2>/dev/null)"
  case "$t" in
    *N|'') echo $(($(date +%s) * 1000)) ;;
    *)     echo $((t / 1000000)) ;;
  esac
}

# Запуск с таймаутом, если есть timeout/gtimeout.
with_timeout() {
  local secs="$1"; shift
  if command -v timeout >/dev/null 2>&1; then
    timeout "$secs" "$@"
  elif command -v gtimeout >/dev/null 2>&1; then
    gtimeout "$secs" "$@"
  else
    "$@"
  fi
}

# Убираем значение ключа из любого вывода, который собираемся печатать.
mask() {
  local line
  while IFS= read -r line || [ -n "$line" ]; do
    if [ -n "${DEEPSEEK_API_KEY:-}" ]; then
      line="${line//"$DEEPSEEK_API_KEY"/***}"
    fi
    printf '%s\n' "$line"
  done
}

# ---------------------------------------------------------------- инструменты
head_ "Инструменты"

# Java: так же, как gradlew — JAVA_HOME, если задан, иначе java из PATH.
JAVA_BIN=java
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA_BIN="$JAVA_HOME/bin/java"
fi
if command -v "$JAVA_BIN" >/dev/null 2>&1; then
  JAVA_VER_LINE="$("$JAVA_BIN" -version 2>&1 | head -n1)"
  JAVA_VER="$(printf '%s' "$JAVA_VER_LINE" | sed -n 's/.*version "\([^"]*\)".*/\1/p')"
  case "$JAVA_VER" in
    1.*) JAVA_MAJOR="$(printf '%s' "$JAVA_VER" | cut -d. -f2)" ;;
    *)   JAVA_MAJOR="$(printf '%s' "$JAVA_VER" | cut -d. -f1)" ;;
  esac
  if [ "$JAVA_MAJOR" = "$WANT_JAVA" ]; then
    ok "java $JAVA_VER (JAVA_HOME=${JAVA_HOME:-не задан})"
  else
    bad "java ${JAVA_VER:-?}: нужен JDK $WANT_JAVA. Укажите JAVA_HOME, например: export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64"
  fi
else
  bad "java не найден: установите JDK $WANT_JAVA и задайте JAVA_HOME"
fi

# Node
if command -v node >/dev/null 2>&1; then
  NODE_VER="$(node --version 2>/dev/null)"
  NODE_MAJOR="${NODE_VER#v}"; NODE_MAJOR="${NODE_MAJOR%%.*}"
  if [ "$NODE_MAJOR" -ge "$WANT_NODE" ] 2>/dev/null; then
    ok "node $NODE_VER"
  elif [ "$NODE_MAJOR" -ge 18 ] 2>/dev/null; then
    warn "node $NODE_VER: на стенде Node $WANT_NODE LTS (с $NODE_VER Vaadin, скорее всего, соберётся, но не проверялось). nvm install $WANT_NODE"
  else
    bad "node $NODE_VER: нужен Node $WANT_NODE LTS. nvm install $WANT_NODE"
  fi
else
  bad "node не найден: установите Node $WANT_NODE LTS (nvm install $WANT_NODE)"
fi

# opencode
if command -v opencode >/dev/null 2>&1; then
  OC_VER="$(opencode --version 2>/dev/null | head -n1)"
  if [ "$OC_VER" = "$WANT_OPENCODE" ]; then
    ok "opencode $OC_VER"
  else
    warn "opencode ${OC_VER:-?}: на стенде $WANT_OPENCODE (npm i -g opencode-ai@$WANT_OPENCODE)"
  fi
else
  bad "opencode не найден: npm i -g opencode-ai@$WANT_OPENCODE"
fi

# git, curl, jq
for tool in git curl jq; do
  if command -v "$tool" >/dev/null 2>&1; then
    case "$tool" in
      git)  v="$(git --version 2>/dev/null | head -n1)" ;;
      curl) v="$(curl --version 2>/dev/null | head -n1 | cut -d' ' -f1-2)" ;;
      jq)   v="$(jq --version 2>/dev/null | head -n1)" ;;
    esac
    ok "$v"
  else
    bad "$tool не найден: sudo apt install -y $tool (Linux/WSL) или brew install $tool (macOS)"
  fi
done

# ---------------------------------------------------------------- модели и ключ
# Какие модели проверяем.
FLASH_MODEL="${DOCTOR_FLASH_MODEL:-}"
PRO_MODEL="${DOCTOR_PRO_MODEL:-}"
if [ -z "$FLASH_MODEL" ] && [ -f opencode.json ] && command -v jq >/dev/null 2>&1; then
  FLASH_MODEL="$(jq -r '.model // empty' opencode.json 2>/dev/null)"
fi
if [ -z "$PRO_MODEL" ]; then
  # model: в frontmatter команды plan — это Pro
  for f in .opencode/commands/speckit.plan.md .opencode/commands/speckit.specify.md; do
    if [ -f "$f" ]; then
      PRO_MODEL="$(awk '
        NR == 1 && $0 !~ /^---[[:space:]]*$/ { exit }
        /^---[[:space:]]*$/ { n++; if (n == 2) exit; next }
        n == 1 && /^model:/ { sub(/^model:[[:space:]]*/, ""); gsub(/["'"'"'[:space:]]/, ""); print; exit }
      ' "$f")"
      [ -n "$PRO_MODEL" ] && break
    fi
  done
fi
FLASH_MODEL="${FLASH_MODEL:-deepseek/deepseek-v4-flash}"
PRO_MODEL="${PRO_MODEL:-deepseek/deepseek-v4-pro}"

head_ "Ключ"
case "$PRO_MODEL $FLASH_MODEL" in
  *deepseek/*) KEY_REQUIRED=1 ;;
  *)           KEY_REQUIRED=0 ;;
esac
if [ -n "${DEEPSEEK_API_KEY:-}" ]; then
  ok "DEEPSEEK_API_KEY задан: ***"
elif [ "$KEY_REQUIRED" = 1 ]; then
  bad "DEEPSEEK_API_KEY не задан: export DEEPSEEK_API_KEY=... (ключ выдаёт ведущий лично)"
else
  warn "DEEPSEEK_API_KEY не задан (для моделей $PRO_MODEL и $FLASH_MODEL он не нужен)"
fi

head_ "Модели"
if [ "$RUN_LLM" = 0 ]; then
  info "пропущено (--no-llm)"
elif ! command -v opencode >/dev/null 2>&1; then
  bad "модели не проверены: нет opencode"
else
  ping_model() {
    local label="$1" model="$2" out rc t0 t1 secs answer ESC
    ESC="$(printf '\033')"
    t0="$(now_ms)"
    out="$(with_timeout "$LLM_TIMEOUT" opencode run --model "$model" "ответь ok" </dev/null 2>&1)"
    rc=$?
    t1="$(now_ms)"
    secs="$(LC_ALL=C awk -v ms=$((t1 - t0)) 'BEGIN { printf "%.1f", ms / 1000 }')"
    # убираем ANSI-раскраску и значение ключа
    answer="$(printf '%s' "$out" | sed "s/${ESC}\[[0-9;]*[a-zA-Z]//g" | mask | grep -v '^[[:space:]]*$' | tail -n 3 | tr '\n' ' ' | cut -c1-120)"
    if [ "$rc" -eq 0 ] && [ -n "$answer" ]; then
      ok "$label $model: ответил за ${secs} с — «${answer% }»"
    elif [ "$rc" -eq 124 ]; then
      bad "$label $model: нет ответа за ${LLM_TIMEOUT} с (таймаут)"
    else
      bad "$label $model: ошибка (код $rc, ${secs} с): ${answer:-пустой вывод}"
      info "проверьте ключ и ID модели: opencode models deepseek"
    fi
  }
  ping_model "Pro  " "$PRO_MODEL"
  ping_model "Flash" "$FLASH_MODEL"
fi

# ---------------------------------------------------------------- сборка
head_ "Сборка"
if [ ! -x ./gradlew ]; then
  bad "./gradlew не найден или не исполняемый (chmod +x gradlew)"
else
  t0="$(now_ms)"
  GRADLE_OUT="$(./gradlew -q compileJava 2>&1)"
  rc=$?
  t1="$(now_ms)"
  secs="$(LC_ALL=C awk -v ms=$((t1 - t0)) 'BEGIN { printf "%.1f", ms / 1000 }')"
  if [ "$rc" -eq 0 ]; then
    ok "./gradlew -q compileJava: успешно за ${secs} с"
  else
    bad "./gradlew -q compileJava: ошибка (код $rc, ${secs} с). Последние строки:"
    printf '%s\n' "$GRADLE_OUT" | tail -n 15 | mask | sed 's/^/         /'
  fi
fi

# ---------------------------------------------------------------- итог
head_ "Итог"
if [ "$FAILS" -eq 0 ]; then
  printf '%sGREEN%s: всё готово к лабе' "$C_OK" "$C_OFF"
  [ "$WARNS" -gt 0 ] && printf ' (предупреждений: %s)' "$WARNS"
  printf '\n'
  exit 0
else
  printf '%sRED%s: проблем: %s' "$C_BAD" "$C_OFF" "$FAILS"
  [ "$WARNS" -gt 0 ] && printf ', предупреждений: %s' "$WARNS"
  printf '. Пришлите этот вывод в чат подготовки.\n'
  exit 1
fi
