# NEXT_STEPS.md

1. [ ] JDK 17 (Temurin) sur PATH ou dans `~/.gradle/jdks/` — le build est verrouillé `jvmToolchain(17)` (app/build.gradle.kts:323) ; seul JDK 21 présent ; foojay-resolver-convention 1.0.0 ne fournit pas d'URL de téléchargement (erreur Gradle observée le 2026-11-01).
2. [ ] Android SDK : `platforms;android-37` (compileSdk 37), build-tools AGP 9.4.1, licences acceptées, `ANDROID_HOME` défini — actuellement seulement `/usr/lib/android-sdk/platform-tools`.
3. [ ] Premier build vert : `./scripts/build.sh assembleSideloadDebug` puis `./scripts/check.sh` VERT (résumer dans le commit).
4. [ ] Trancher l'appareil de référence : S21 FE connecté (192.168.3.4:5555) vs S23 cible d'AGENTS.md (non connecté) — décision Rodolphe.
5. [ ] Phase 0b : greffe wake word openWakeWord (Apache-2.0) — point d'extension : paquet `app/.../relay/wake/` (`WakeWordDetectorFactory` + `WakeWordModelInstaller` + `assets/wakewords/`), voir DECISIONS.md D2.
