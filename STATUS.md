# STATUS.md — Hermes Voice

## ÉTAT EN 5 LIGNES
- **Date :** 2026-11-01 · **Commit :** 9be79c7 (base fork hermes-relay) + commit Phase 0a (`git log -1`)
- **Ce qui compile :** rien — Phase 0a = audit/docs/scripts seulement ; `check.sh` ROUGE (attendu) : build échoue sur toolchain JDK 17.
- **Ce qui bloque :** (1) JDK 17 absent (seul Temurin 21 dans `~/.gradle/jdks` ; foojay n'offre pas d'URL de téléchargement) ; (2) Android SDK : seul `platform-tools`, sans `platforms;android-37`/build-tools, `ANDROID_HOME` non défini.
- **Appareil :** S21 FE (SM-S911W, Android 16 / API 36, One UI 8.0) connecté en ADB sans fil `192.168.3.4:5555` ; la cible S23 d'AGENTS.md n'est PAS connectée → à trancher avec Rodolphe.
- **Prochaine tâche :** Rodolphe provisionne JDK 17 + SDK (NEXT_STEPS §1-2), puis 1er build vert, puis Phase 0b (greffe wake word openWakeWord ONNX).

---
- 2026-11-01 : Phase 0a réalisée (audit base, licence MIT confirmée, endpoints 192.168.2.142 testés, ADB OK). Voir DECISIONS.md, docs/HERMES_API_SPEC.md, rapport 7 points.
- `./scripts/check.sh` : ROUGE (attendu) — BUILD/TESTS non exécutés à cause de l'environnement ; STATUS daté ; SECRETS: 0. Log : `build/logs/check-*.log`.
- Bug corrigé ce jour : `scripts/check.sh` sortait toujours 0 (FAIL dans un sous-shell de pipeline) — le code de sortie reflète maintenant le statut réel (vérifié : exit 1 sur ROUGE).
