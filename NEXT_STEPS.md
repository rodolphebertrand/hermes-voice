# NEXT_STEPS.md

1. [x] Phase 0b code (wake word ONNX dynamique) — committé sur hermes-voice.
2. [ ] **Build PC .157** : `git pull`, `.\scripts\build-windows.ps1` → APK sideload debug.
3. [ ] Install sur S21 FE (192.168.3.5:5555) + premier lancement (copie assets → filesDir/wakewords).
4. [ ] Phase 0c : test physique — wake word réel, écran verrouillé, logcat, métriques (batterie, température).
5. [ ] Phase 1 : routage multi-profils E2E (he_pixel→pi, alfraide→alfred, dis_hermes→profil actif).
6. [ ] Phase 2 : mode hors ligne.
7. [ ] Si `rebase-119` devient la branche principale : re-porter Phase 0b dessus (conflits attendus sur wake/ refactorisé upstream).
