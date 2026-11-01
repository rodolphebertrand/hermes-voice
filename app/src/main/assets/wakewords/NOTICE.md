# Wake words — provenance et licence

## Modèles de pipeline (openWakeWord v0.5.1)
- `melspectrogram.onnx` et `embedding_model.onnx` proviennent de la release
  v0.5.1 de openWakeWord (https://github.com/dscripka/openWakeWord),
  licence Apache-2.0 (c) dscripka.
- Pipeline attendue par nos modèles de wake word (format 96-dim, pré-v0.6) :
  audio 16 kHz mono int16 → melspectrogram.onnx (1280 samples + 480 lookbehind
  → 8 trames × 32 mel) → transformation `v/10 + 2` → fenêtre de 76 trames →
  embedding_model.onnx (76×32 → embedding 96-dim) → fenêtre de 16 embeddings
  (16×96) → modèle de wake word (score logit sigmoidé ∈ [0,1]).

## Modèles de wake word (propriété Pi)
- `he_pixel.onnx`  — « Hé Pixel »            — entraîné 2026-05-14 (Nextcloud: Pi/)
- `dis_hermes.onnx` — « Dis Hermès »         — entraîné 2026-07-29 (Nextcloud: Hermes/)
- `alfraide.onnx`   — « Alfraide, s'il vous plaît » — entraîné 2026-10-07 (Nextcloud: Alfred/)
- Générés via le notebook de formation openWakeWord (features (N,16,96)),
  aucun code Gotcha (AGPL) n'est copié ici.

## Remplaçabilité (règle 14 AGENTS.md)
- Aucun modèle n'est compilé en dur dans le code : le chargement est 100 %
  piloté par `wakewords.json` (table de routage phrase → fichier .onnx →
  profil Hermes cible) + les fichiers .onnx du dossier.
- Après le premier démarrage, l'app copie ce dossier vers
  `filesDir/wakewords/` (dossier de l'app, éditable par l'utilisateur/adb) ;
  on peut ensuite ajouter/supprimer un .onnx et éditer wakewords.json sans
  recompiler. Supprimer `wakewords.json` réinitialise le dossier (recopie
  des assets). Le toggle UI de chaque wake word est stocké dans les
  préférences DataStore (clé `wake_word_asset_enabled_<id>`), indépendamment
  du champ `enabled` du JSON (qui sert de valeur par défaut).
- `wakewords.json` (schéma v1) :
  - `version` : entier du schéma.
  - `pipeline` : géométrie de la pipeline (paramètres ci-dessus) —
    `melspec_transform` = `divide` puis `add` appliqué aux trames mel.
  - `wake_words[]` : `id` (unique), `phrase` (affichée), `model` (fichier
    .onnx relatif à ce dossier), `target_profile` (profil Hermes : "pi",
    "alfred", ou "default" = profil actif), `threshold` (défaut 0.5),
    `confirmation_frames` (trames consécutives ≥ threshold, défaut 3),
    `enabled` (défaut de toggle, défaut true).
