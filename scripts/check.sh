#!/usr/bin/env bash
# check.sh — compile + tests + STATUS daté + scan de secrets. Résumé max 30 lignes,
# log complet dans build/logs/check-<date>.log. Peut échouer à la compilation si
# l'environnement (JDK/SDK) manque : le signale honnêtement, exit non nul.
set -u
FAIL=0
cd "$(dirname "$0")/.."
LOGDIR=build/logs; mkdir -p "$LOGDIR"
LOG="$LOGDIR/check-$(date +%Y%m%d-%H%M%S).log"
FAILFILE="${LOG%.log}.status"
{
echo "== check.sh $(date -Iseconds) =="
FAIL=0
# 1. Environnement
if command -v java >/dev/null 2>&1 || [ -x "$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2/bin/java" ]; then
  if command -v java >/dev/null 2>&1; then java -version 2>&1; else export JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2"; export PATH="$JAVA_HOME/bin:$PATH"; java -version 2>&1; fi
else
  echo "MANQUE: aucun JDK détecté (JDK 17+ requis, ex. Temurin 21 dans ~/.gradle/jdks)"; FAIL=1
fi
echo "ANDROID_HOME=${ANDROID_HOME:-unset}"
command -v adb >/dev/null && adb version | head -1 || { echo "MANQUE: adb"; FAIL=1; }
# 2. Compile + tests (si JDK dispo)
if [ "$FAIL" -eq 0 ]; then
  if [ -z "${ANDROID_HOME:-}" ] && [ -d /usr/lib/android-sdk ]; then export ANDROID_HOME=/usr/lib/android-sdk; fi
  ./scripts/build.sh assembleSideloadDebug && echo "BUILD: OK" || { echo "BUILD: ÉCHEC (voir $LOG)"; FAIL=1; }
  ./scripts/test.sh && echo "TESTS: OK" || { echo "TESTS: ÉCHEC (voir $LOG)"; FAIL=1; }
else
  echo "BUILD: non exécuté (environnement incomplet)"; echo "TESTS: non exécuté"
fi
# 3. STATUS daté du jour
grep -q "$(date +%Y-%m-%d)" STATUS.md 2>/dev/null && echo "STATUS: daté du jour" || { echo "STATUS: PAS daté du jour ($(date +%Y-%m-%d) absent)"; FAIL=1; }
# 4. Scan de secrets (patterns grossiers, hors .git)
if command -v rg >/dev/null; then
  N=$(rg -l -i --no-messages -g '!.git' -e 'AKIA[0-9A-Z]{16}' -e '-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----' . | wc -l)
else
  N=$(grep -rIl -E 'AKIA[0-9A-Z]{16}|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----' --exclude-dir=.git . 2>/dev/null | wc -l)
fi
echo "SECRETS: $N fichier(s) suspect(s)"
[ "$N" -gt 0 ] && FAIL=1
echo "== RÉSUMÉ: $([ "$FAIL" -eq 0 ] && echo VERT || echo ROUGE) =="
printf '%s' "$FAIL" > "$FAILFILE"
} 2>&1 | tee "$LOG" | tail -30
exit "$(cat "$FAILFILE" 2>/dev/null || echo 1)"
