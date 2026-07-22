# Dutch Nations API

De API bewaart events, rollen en gehashte managementtokens. De openbare route
`GET /feed.json` bevat uitsluitend events: geen ledenlijst, rollen of tokenhashes.
Een boss-codewoord is in die feed leeg tot het event daadwerkelijk actief is.

## Lokaal testen

```powershell
.\gradlew.bat -p server test
.\gradlew.bat -p server runLocal
```

Lokale adressen:

- `http://127.0.0.1:8787/feed.json`
- `http://127.0.0.1:8787/api/events`
- `http://127.0.0.1:8787/api/roles`
- `http://127.0.0.1:8787/health`

De lokale ontwikkeltoken `dutch-nations-local-owner` werkt alleen wanneer de server
aan localhost bindt. Bij extern binden weigert de API zonder een expliciete
`DN_OWNER_TOKEN` te starten.

## Productie

Build vanaf de hoofdmap:

```powershell
docker build -f server/Dockerfile -t dutch-nations-api .
```

Vereiste instellingen:

- `DN_OWNER_TOKEN`: sterke willekeurige productietoken;
- `DN_BIND=0.0.0.0`;
- `DN_PORT`: luisterpoort, standaard 8787;
- `DATABASE_URL`: Koyeb PostgreSQL-verbindingsadres (als Secret);
- `DN_DATA_FILE`: alleen voor lokale bestandsopslag wanneer `DATABASE_URL` ontbreekt.

Op Koyeb verzorgt de publieke webservice automatisch HTTPS. De API maakt de tabel
`dutch_nations_state` bij de eerste start automatisch aan. Plaats `DATABASE_URL` en
`DN_OWNER_TOKEN` altijd in Koyeb Secrets.
Vul daarna de drie publieke `https://`-adressen en de persoonlijke managementtoken in
de RuneLite-configuratie in.

## Contract

- `GET /health`
- `GET /feed.json` — openbaar, alleen events, codewoord alleen indien actief
- `POST /api/events` — OWNER/ADMINISTRATOR/MANAGER/EVENT_HOST
- `DELETE /api/events/{id}` — OWNER/ADMINISTRATOR/MANAGER/EVENT_HOST
- `GET /api/roles` — toont uitsluitend de eigen rol bij een geldige token
- `POST /api/roles` — OWNER beheert alle niet-ownerrollen; ADMINISTRATOR mag alleen nieuwe MANAGER-rollen toevoegen

Requestbody's zijn begrensd tot 16 KiB. Eventteksten, wereldnummers en RSN's worden
server-side gevalideerd. Tokens worden met 256 bits entropie gemaakt en alleen als
SHA-256-hash opgeslagen. Verlopen events worden automatisch verwijderd.
