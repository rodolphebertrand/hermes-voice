# DECISIONS.md — Hermes Voice

## D1 — Licence AGPL/Gotcha (2026-11-01)
- Base : hermes-relay = **MIT** (vérifié : `LICENSE` racine, « MIT License, Copyright (c) 2026 Axiom Labs »).
- Règle : Gotcha est AGPL-3.0 → **aucun code Gotcha copié** (le dépôt deviendrait AGPL). Réimplémentation uniquement via openWakeWord (Apache-2.0, Context7 ID `/dscripka/openwakeword`) + modèle ONNX entraîné par nous, chargé dynamiquement depuis `assets/wakewords/`. Toute exception = accord explicite de l'utilisateur.

## D2 — Point d'extension wake word (2026-11-01)
- Module existant `app/.../wake/` : interface `WakeWordDetector` (WakeWordDetector.kt) avec `SherpaWakeWordDetector` (sherpa-onnx KeywordSpotter, modèle zipformer téléchargé par WakeWordModelInstaller). **Non conforme** à la règle « assets ONNX remplaçables » : le point de greffe Phase 0b est `WakeWordDetectorFactory` + `WakeWordModelInstaller` pour charger des `.onnx` depuis `assets/wakewords/` + table de routage éditable, sans recompilation. Voir rapport Phase 0a §2.

## D3 — Environnement de build (2026-11-01)
- Build réel échoué le 2026-11-01 : `jvmToolchain(17)` (app/build.gradle.kts:323) ; seul Temurin 21 présent dans `~/.gradle/jdks/` ; foojay-resolver-convention 1.0.0 → « No defined toolchain download url for LINUX on x86_64 ».
- Android SDK : `ANDROID_HOME` non défini ; `/usr/lib/android-sdk/` contient uniquement `platform-tools` (ADB 1.0.41 OK). Manque : `platforms;android-37` (compileSdk 37), build-tools AGP 9.4.1, licences.
- Décision : ne rien installer sans accord de Rodolphe (Phase 0a = inventaire). Actions listées NEXT_STEPS.md §1-2.

## D4 — API 8642 (2026-11-01)
- `/p/<profil>/v1/chat/completions` répond 401 `gateway_auth_failed` pour pi/alfred/apic/lea/alex3 → l'endpoint existe mais exige une clé API serveur (jamais dans le dépôt). Voir docs/HERMES_API_SPEC.md.

## D5 — Appareil de test (2026-11-01)
- Connecté et vérifié : Samsung **S21 FE** SM-S911W (product dm1qcsx), Android 16 (API 36), One UI 8.0, build `S911WVLS8FZH3`, ADB sans fil `192.168.3.4:5555`.
- AGENTS.md cible un S23 ; aucun S23 connecté. → Question à Rodolphe : le S21 FE sert-il de référence de test, ou le S23 doit-il être branché ?
