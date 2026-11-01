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

## D6 — Toolchain provisionnée (2026-11-01, par Pi avec accord Rodolphe)
- JDK 17 Temurin 17.0.21 → `~/.jdks/jdk17` ; Android SDK → `~/.android-sdk` (platforms android-36 + android-37.0, build-tools 34.0.0/35.0.0/37.0.0, platform-tools, licences OK). compileSdk=37 satisfait par android-37.0.
- Env requise pour build : `JAVA_HOME=~/.jdks/jdk17 ANDROID_HOME=~/.android-sdk`.

## D7 — Appareil de test = S21 FE (2026-11-01, décision Rodolphe)
- S21 FE (SM-S911W, 192.168.3.4:5555, Android 16/API 36) = référence de test ADB. Le S23 reste pour les tests physiques finaux (wake word écran verrouillé). Justification : conflit de micro potentiel avec le satellite HA du S23.

## D8 — Wake words de départ = modèles existants (2026-11-01, décision Rodolphe)
- Assets ONNX validés (layout openWakeWord [1,16,96]→[1,1]) depuis Nextcloud `Wake Word/` : `Dis_Hermès_20260729_141941.onnx`, `Hé_Pixel,_20260514_121337.onnx`, `Alfraide,_s'il_vous_plaît_20261007_133412.onnx`. Aucun entraînement « OK Hermes » immédiat (GPU occupé par qwen3.8-27b) ; custom = plus tard, asset remplaçable sans recompilation.

## D9 — Implémentation Phase 0b (2026-11-01)
- Branche : `hermes-voice` (cible de la mission). La branche `rebase-119` (rebase parallèle sur upstream 1.19.0, créée 14:32) n'a pas été modifiée ; à fusionner/re-porter ensuite si elle devient principale.
- WIP `738746d1` (poussé sur origin, écarté localement) repris comme base et complété : WakeTrigger/WakeWordAsset/WakeWordAssetRegistry/WakeWordFeatureWindow + intégration service + UI.
- Primaire = assets ONNX (ORT, runtime partagé sherpa 1.27.0) ; fallback = SherpaWakeWordDetector si aucun asset actif.
- Routage : détection porte `targetProfile` ; `default`/null → préférences ; sinon `WakeWordProfileRouting.Specific`.
- Validation hors build : signatures ONNX vérifiées (onnx 1.23.2 + onnxruntime 1.31.1, pipeline E2E, pas de faux positif sur sinus) ; AUCUN build Gradle lancé (contrainte serveur) → compile à valider sur PC .157.
