#!/usr/bin/env bash
# adb-test.sh — journal des commandes adb autorisées (AGENTS.md). Usage:
#   scripts/adb-test.sh <serial> <commande...>   (ex. adb-test.sh 192.168.3.4:5555 shell getprop ro.product.model)
# Journal : build/logs/adb-test-<date>.log avec commande, objectif, observé.
# Autorisé : devices, infos appareil, install -r / uninstall de Hermes Voice seule,
# am start/force-stop de Hermes Voice, logcat, dumpsys battery|meminfo|cpuinfo|thermalservice.
set -uo pipefail
cd "$(dirname "$0")/.."
LOGDIR=build/logs; mkdir -p "$LOGDIR"
LOG="$LOGDIR/adb-test-$(date +%Y%m%d-%H%M%S).log"
SERIAL="${1:?usage: adb-test.sh <serial> <commande adb...>}"; shift
{
  echo "== $(date -Iseconds) serial=$SERIAL cmd: adb -s $SERIAL $* =="
  adb -s "$SERIAL" "$@"
  echo "== fin $(date -Iseconds) =="
} 2>&1 | tee -a "$LOG"
