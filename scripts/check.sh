#!/usr/bin/env bash
# check.sh — технические проверки результата работы агента (до бизнес-приёмки accept.sh).
#
#   1. ./gradlew test проходит.
#   2. Охрана конституции: security/, ApiSecurityConfiguration.java и build.gradle не изменены
#      относительно lab-sdd/start; в build.gradle нет jmix-rest.
#   3. В specs/*/spec.md не осталось [NEEDS CLARIFICATION].
#   4. Сколько задач в specs/*/tasks.md ещё не закрыто (справочно).
#   5. Число тестов (@Test) не меньше, чем в последнем коммите: агент не удалял тесты.
#
# Использование:
#   scripts/check.sh              все проверки
#   scripts/check.sh --no-test    пропустить ./gradlew test (проверки 2-5 выполняются)
#   scripts/check.sh --help
#
# Строки вывода: PASS / FAIL / SKIP / INFO. Код возврата: 1, если есть хотя бы один FAIL.

set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 2

RUN_TESTS=1
for arg in "$@"; do
  case "$arg" in
    --no-test) RUN_TESTS=0 ;;
    -h|--help)
      sed -n '2,17p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *)
      echo "Неизвестный аргумент: $arg (см. --help)" >&2
      exit 2
      ;;
  esac
done

if [ -t 1 ]; then
  C_OK=$'\033[32m'; C_BAD=$'\033[31m'; C_SKIP=$'\033[33m'; C_INFO=$'\033[36m'; C_OFF=$'\033[0m'
else
  C_OK=''; C_BAD=''; C_SKIP=''; C_INFO=''; C_OFF=''
fi

N_PASS=0; N_FAIL=0; N_SKIP=0
pass() { printf '%s[PASS]%s %s\n' "$C_OK"   "$C_OFF" "$*"; N_PASS=$((N_PASS + 1)); }
fail() { printf '%s[FAIL]%s %s\n' "$C_BAD"  "$C_OFF" "$*"; N_FAIL=$((N_FAIL + 1)); }
skip() { printf '%s[SKIP]%s %s\n' "$C_SKIP" "$C_OFF" "$*"; N_SKIP=$((N_SKIP + 1)); }
info() { printf '%s[INFO]%s %s\n' "$C_INFO" "$C_OFF" "$*"; }
more() { sed 's/^/         /'; }

GUARDED_PATHS=(
  src/main/java/com/debugskills/equipment/security
  src/main/java/com/debugskills/equipment/api/ApiSecurityConfiguration.java
  build.gradle
)

TEST_ANN='@(Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)([^A-Za-z0-9_]|$)'

# ------------------------------------------------------------------ 1. тесты
if [ "$RUN_TESTS" = 0 ]; then
  skip "1. ./gradlew test (отключено флагом --no-test)"
elif [ ! -x ./gradlew ]; then
  fail "1. ./gradlew test: ./gradlew не найден или не исполняемый"
else
  LOG="$(mktemp "${TMPDIR:-/tmp}/check-gradle.XXXXXX")"
  if ./gradlew test >"$LOG" 2>&1; then
    pass "1. ./gradlew test"
  else
    fail "1. ./gradlew test (последние строки вывода):"
    tail -n 30 "$LOG" | more
  fi
  rm -f "$LOG"
fi

# ------------------------------------------------------------------ 2. охрана конституции
BASE_REF=""
for ref in origin/lab-sdd/start lab-sdd/start; do
  if git rev-parse --verify --quiet "$ref^{commit}" >/dev/null 2>&1; then
    BASE_REF="$ref"
    break
  fi
done

if [ -z "$BASE_REF" ]; then
  skip "2. Охрана конституции: нет ни origin/lab-sdd/start, ни lab-sdd/start (git fetch origin?)"
else
  GUARD_FAIL=0
  git diff --quiet "$BASE_REF" -- "${GUARDED_PATHS[@]}" 2>/dev/null
  rc=$?
  if [ "$rc" -eq 0 ]; then
    :
  elif [ "$rc" -eq 1 ]; then
    GUARD_FAIL=1
    fail "2. Охрана конституции: изменено относительно $BASE_REF (запрещено конституцией):"
    git diff --stat "$BASE_REF" -- "${GUARDED_PATHS[@]}" | more
  else
    GUARD_FAIL=1
    fail "2. Охрана конституции: git diff завершился с ошибкой (код $rc)"
  fi
  # git diff не видит новые неотслеживаемые файлы
  UNTRACKED="$(git ls-files --others --exclude-standard -- "${GUARDED_PATHS[@]}")"
  if [ -n "$UNTRACKED" ]; then
    GUARD_FAIL=1
    fail "2. Охрана конституции: появились новые файлы в защищённых путях:"
    printf '%s\n' "$UNTRACKED" | more
  fi
  if [ "$GUARD_FAIL" = 0 ]; then
    pass "2. Охрана конституции: security/, ApiSecurityConfiguration, build.gradle совпадают с $BASE_REF"
  fi
fi

if [ ! -f build.gradle ]; then
  fail "2b. В build.gradle нет jmix-rest: build.gradle не найден"
elif grep -q 'jmix-rest' build.gradle; then
  fail "2b. В build.gradle нет jmix-rest: найдено (конституция запрещает модуль REST):"
  grep -n 'jmix-rest' build.gradle | more
else
  pass "2b. В build.gradle нет jmix-rest"
fi

# ------------------------------------------------------------------ 3. [NEEDS CLARIFICATION]
SPECS=()
if [ -d specs ]; then
  for f in specs/*/spec.md; do
    [ -f "$f" ] && SPECS+=("$f")
  done
fi
if [ "${#SPECS[@]}" -eq 0 ]; then
  skip "3. [NEEDS CLARIFICATION] в spec.md: нет specs/*/spec.md (спека ещё не создана)"
else
  NC="$(grep -n -F '[NEEDS CLARIFICATION' "${SPECS[@]}" 2>/dev/null)"
  if [ -z "$NC" ]; then
    pass "3. В spec.md не осталось [NEEDS CLARIFICATION] (${#SPECS[@]} шт.)"
  else
    NC_COUNT="$(printf '%s\n' "$NC" | wc -l | tr -d ' ')"
    fail "3. В spec.md осталось [NEEDS CLARIFICATION]: $NC_COUNT (запустите /speckit.clarify):"
    printf '%s\n' "$NC" | head -n 10 | cut -c1-160 | more
  fi
fi

# ------------------------------------------------------------------ 4. открытые задачи
TASKS=()
if [ -d specs ]; then
  for f in specs/*/tasks.md; do
    [ -f "$f" ] && TASKS+=("$f")
  done
fi
if [ "${#TASKS[@]}" -eq 0 ]; then
  skip "4. Открытые задачи: нет specs/*/tasks.md (задачи ещё не созданы)"
else
  OPEN="$(cat "${TASKS[@]}" | grep -c -E '^[[:space:]]*- \[ \]')"
  DONE="$(cat "${TASKS[@]}" | grep -c -E '^[[:space:]]*- \[[xX]\]')"
  info "4. Открытых задач в tasks.md: $OPEN (закрыто: $DONE)"
fi

# ------------------------------------------------------------------ 5. число тестов
if ! git rev-parse --verify --quiet HEAD >/dev/null 2>&1; then
  skip "5. Число тестов: в репозитории нет коммитов"
else
  NOW=0
  if [ -d src/test ]; then
    NOW="$(grep -r -h -o -E --include='*.java' "$TEST_ANN" src/test 2>/dev/null | wc -l | tr -d ' ')"
  fi
  AT_HEAD="$(git grep -h -o -E "$TEST_ANN" HEAD -- 'src/test/*.java' 2>/dev/null | wc -l | tr -d ' ')"
  if [ "$NOW" -ge "$AT_HEAD" ]; then
    pass "5. Число тестов не уменьшилось: сейчас $NOW, в HEAD $AT_HEAD"
  else
    fail "5. Число тестов уменьшилось: сейчас $NOW, в HEAD $AT_HEAD (агент удалил тесты?)"
    git diff --stat HEAD -- src/test | more
  fi
fi

# ------------------------------------------------------------------ итог
printf '\nИТОГО: PASS=%s FAIL=%s SKIP=%s\n' "$N_PASS" "$N_FAIL" "$N_SKIP"
if [ "$N_FAIL" -gt 0 ]; then
  printf '%sFAIL%s\n' "$C_BAD" "$C_OFF"
  exit 1
fi
printf '%sOK%s\n' "$C_OK" "$C_OFF"
exit 0
