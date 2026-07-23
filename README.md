# Dutch Nations Events

RuneLite-plugin voor de centrale Dutch Nations-eventkalender.

## Rechten

De vaste eerste owner is de RuneScape-naam `heavenskill`. Deze naam bepaalt alleen
welke beheerknoppen zichtbaar zijn. Iedere wijziging wordt daarnaast door de API
gecontroleerd met een persoonlijke Bearer-token; de client is nooit de bron van
waarheid voor rechten.

Een owner kan via **Rollen** een `MANAGER`, `EVENT_HOST` of extra `OWNER` toevoegen.
De API geeft daarbij een nieuwe persoonlijke token terug. Die token wordt eenmalig
getoond en moet privé aan het betreffende lid worden gegeven. Ingetrokken of opnieuw
uitgedeelde rollen maken de vorige token ongeldig.

Managementrollen en RuneScape-namen staan niet in de openbare kalenderfeed.

## Codewoorden en chatbox

- Learner- en mass-events hebben geen codewoord.
- Een boss-codewoord wordt door de API pas tijdens de start- en eindtijd vrijgegeven.
- De plugin ververst de feed ongeveer iedere 30 seconden.
- Meldingen 30 minuten voor aanvang en bij de start verschijnen uitsluitend lokaal
  als RuneLite-gamebericht; de plugin verzendt geen clan-chat.

## Testen

RuneLite-plugin:

```powershell
.\gradlew.bat test
.\gradlew.bat run
```

Losse API:

```powershell
.\gradlew.bat -p server test
.\gradlew.bat -p server runLocal
```

Voor lokaal beheer vul je in RuneLite bij **Management-token** uitsluitend voor de
lokale API `dutch-nations-local-owner` in. Deze testtoken mag nooit online gebruikt
worden.

## Publicatie

De rootbuild bevat alleen de RuneLite-plugin. De API in `server/` is bewust een apart
Gradle-project en wordt apart gehost. Productie-adressen moeten HTTPS gebruiken;
onversleuteld HTTP wordt door de plugin alleen voor `localhost` toegestaan.

## Kalenderuitbreidingen

- Lijst-, 7-dagen- en 31-dagenweergave
- Lokale filters en meldingsvoorkeuren per eventtype
- Werkelijke resterende minuten in lokale chatboxmeldingen
- Centrale conflictcontrole met expliciete bevestiging
- Learner-checklists voor voorbereiding
- Apart overzicht van benodigde RuneLite-plugins per learner-event
- Live/offline serverstatus
- Owner-only rollenoverzicht, tokenrotatie en rol intrekken
