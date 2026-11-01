# STATUS.md — Hermes Voice

## ÉTAT EN 5 LIGNES
- **Date :** 2026-11-01 · **Commit :** `git log -1` (Phase 0b)
- **Ce qui compile :** NON VÉRIFIÉ ici — contrainte absolue : aucun build Gradle sur ce serveur. Compile à valider sur PC .157 via scripts/build-windows.ps1. Vérifié hors build : signatures ONNX (lib onnx + onnxruntime, pipeline E2E OK, sinus 1 kHz → score 0.0007), accolades/parenthèses équilibrées, scan secrets = 0.
- **Ce qui bloque :** rien côté code ; le build PC reste le jalon.
- **Appareil :** S21 FE (192.168.3.5:5555, décision D7).
- **Prochaine tâche :** build PC .157 → install S21 → test wake word réel écran verrouillé (Phase 0c).

---
- 2026-11-01 : **Phase 0b faite** — greffe wake word ONNX (openWakeWord, assets remplaçables, règle 14) :
  - assets `app/src/main/assets/wakewords/` (5 .onnx + wakewords.json + NOTICE.md), table phrase→onnx→profil.
  - `wake/` : WakeTrigger.kt (WakeDetection/WakeTrigger/OpenWakeWordAssetTrigger/SherpaWakeWordTrigger/WakeTriggerFactory), WakeWordAsset.kt, WakeWordAssetRegistry.kt, WakeWordFeatureWindow.kt (pipeline melspec→embedding→scorer via ORT).
  - Service branché sur WakeTriggerFactory (assets primaire, sherpa fallback) + routage par profil (WakeWordProfileRouting.Specific).
  - UI : liste des phrases + toggle par entrée dans WakeWordCard ; ViewModel : ensureWakeWordReady + entries.
  - Fix bd951fb1 : fenêtre streaming alignée sur position absolue (sans cela, lectures AudioRecord en gros blocs → trames mel dupliquées / scores faussés).
  - WIP 738746d1 (poussé sur origin mais écarté localement) repris et complété.
- Note : branche `rebase-119` créée en parallèle (rebase sur upstream 1.19.0, 14:32-14:33) — Phase 0b committée sur `hermes-voice` comme demandé dans la mission ; à re-porter sur rebase-119 si elle devient la branche principale.
- `./scripts/check.sh` : non exécuté (interdit sur ce serveur — pas de ./gradlew).
