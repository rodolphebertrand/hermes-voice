# HERMES_API_SPEC.md — endpoints testés le 2026-11-01 contre 192.168.2.142

Hôte : 192.168.2.142 (serveur Hermes de l'utilisateur). Aucun secret ci-dessous.
Toutes les lignes « VÉRIFIÉ » ont été exécutées aujourd'hui (curl, résultats observés).

## VÉRIFIÉ (exécuté, résultat observé)
| Endpoint | Résultat |
|---|---|
| `GET http://192.168.2.142:9119/` | HTTP 302 → `/login?next=%2F` (dashboard, uvicorn) |
| `GET :9119/health` | HTTP 302 → login (auth requise) |
| `GET :9119/api/profiles` | HTTP 401 `{"error":"unauthenticated","reason":"no_cookie","login_url":"/login"}` |
| `GET http://192.168.2.142:8767/` | HTTP 400 « No WebSocket UPGRADE hdr » → serveur WebSocket relay vivant |
| `GET http://192.168.2.142:8642/health` | HTTP 200 `{"status":"ok","platform":"hermes-agent","version":"0.22.5"}` |
| `POST :8642/p/{pi,alfred,apic,lea,alex3}/v1/chat/completions` | HTTP 401 `gateway_auth_failed` « Invalid gateway API key (API_SERVER_KEY) » — les 5 profils, endpoint existe mais clé requise |
| `GET :8642/p/{pi,alfred}/v1/models` | HTTP 401 `gateway_auth_failed` (API_SERVER_KEY) |
| `GET :8642/`, `GET :8642/openapi.json` | HTTP 404 |

## NON VÉRIFIÉ
- Routes dashboard authentifiées (chat `/api/ws`, audio `/api/audio/transcribe`, `/api/audio/speak-stream`) : nécessitent une session sign-in — non testées ici (pas de credential, et règle 6 : aucun secret).
- Toute route relay 8767 autre que le handshake WS (pairing `/pairing/register` est loopback-only côté serveur ; le pairage réel se fait via QR/code depuis l'app).
- Les 302 du dashboard vers `/login` : le code exact de la réponse 302 n'a pas été capté en clair (header Location), uniquement l'observation de la redirection.

## Contexte client (code du fork, à vérifier par le build)
- `network/upstream/DashboardApiClient.kt` — client natif du dashboard :9119.
- `network/upstream/StandardHermesVoiceClient.kt` — routes voix `/api/audio/*` sur le web server hermes (:9119).
- `network/relay/RelayVoiceClient.kt`, `RelayUrlDeriver.kt` (port par défaut 8767) — client relay WS.
- `network/shared/HermesLanDiscovery.kt`, `EndpointResolver.kt` — découverte LAN ; l'app teste la joignabilité réelle elle-même (règle 5).
