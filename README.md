# Dutch Nation Events

> De centrale Dutch Nation-eventkalender in RuneLite — overzichtelijk plannen, samen leren en niets missen.

Dutch Nation Events brengt clan-events rechtstreeks naar RuneLite. Je ziet wat er speelt, wanneer het begint en wat je moet voorbereiden, zonder telkens tussen losse berichten, agenda's en links te hoeven wisselen.

## In één oogopslag

- **Actieve, 7-, 31- en 91-dagenweergave** voor alle aankomende clan-events.
- **Boss-, mass- en learner-events** met datum, tijd, wereld, host en voorbereiding.
- **Ingame herinneringen** vóór de start en wanneer een event begint.
- **Codewoord-overlay** die alleen tijdens het actieve tijdvak zichtbaar is.
- **Learner-sjablonen** met voorbereidingslijst, benodigde RuneLite-plugins, Wiki en YouTube.
- **Discord-koppeling** voor een helder eventbericht met beschrijving en voorbereiding.
- **Beheerrollen** voor owners, administrators, managers, learner hosts en teachers.

## Installeren en gebruiken

1. Zoek in de RuneLite Plugin Hub naar **Dutch Nation Events**.
2. Installeer de plugin en zet hem aan.
3. Log in en open de Dutch Nation clan-chat in Old School RuneScape.
4. Open het **Dutch Nation**-icoon in de RuneLite-zijbalk.

Alleen leden die in de Dutch Nation clan-chat aanwezig zijn, zien de kalender en events. Is de lijst leeg terwijl je wel lid bent? Open de clan-chat opnieuw en druk in de plugin op **Vernieuwen**.

## Kalender en meldingen

De kalender heeft vier duidelijke overzichten:

| Weergave | Waarvoor gebruik je hem? |
| --- | --- |
| **Actief** | Events die nu lopen of binnenkort starten. |
| **7 dagen** | De planning voor deze week. |
| **31 dagen** | De kalender voor de komende maand. |
| **91 dagen** | Verder vooruit plannen. |

Je kunt lokaal filteren op eventtype en eigen meldingen instellen. Herinneringen verschijnen alleen bij jou als RuneLite-gamebericht; de plugin stuurt nooit uit zichzelf clan-chatberichten.

## Codewoorden

Bij boss-events hoort altijd een codewoord. Bij mass-events bepaalt de organisator of een codewoord nodig is.

- Het codewoord wordt pas vrijgegeven tussen de ingestelde start- en eindtijd.
- De compacte overlay kun je zelf naar een fijne plek verslepen.
- De kalender wordt regelmatig ververst, zodat wijzigingen snel zichtbaar zijn.

## Learner-events

Learner-events zijn bedoeld om samen bosses en raids te leren. Een organizer kiest een sjabloon en vult zo in één keer de basis in:

- titel en korte uitleg in RuneLite;
- voorbereiding en vereisten;
- aanbevolen Plugin Hub-plugins;
- OSRS Wiki-link en optionele YouTube-guide;
- een uitgebreide Discord-beschrijving.

De uitgebreide Discord-beschrijving is alleen voor Discord. Het RuneLite-paneel blijft daardoor compact en prettig leesbaar.

## Discord

Wanneer de Dutch Nation-bot is ingesteld, wordt een eventbericht in het gekozen Discord-kanaal geplaatst. Een learner-event bevat achtereenvolgens:

1. titel, datum en tijd;
2. de Discord-beschrijving;
3. voorbereiding, benodigde plugins, Wiki en eventuele YouTube-video.

Bij wijzigen of verwijderen van een event wordt het bijbehorende Discord-bericht ook bijgewerkt of verwijderd.

## Rollen en beheer

De eerste owner is de RuneScape-naam `heavenskill`. Owners beheren in de plugin de event- en mededelingenkanalen, zichtbaarheid van mededelingen, learner-sjablonen, logs en rollen.

| Rol | Kan onder andere |
| --- | --- |
| **Owner** | Alle instellingen, rollen, logs en kanalen beheren. |
| **Administrator** | Events beheren en manager-, learner host- en teacherrollen beheren. |
| **Manager** | Events en planning beheren. |
| **Learner host / Teacher** | Learner-events begeleiden volgens de ingestelde rechten. |

Iedere beheerrol krijgt een persoonlijke managementcode. Bewaar die privé en deel hem nooit in Discord of clan-chat. De server controleert elke beheeractie; alleen het hebben van een zichtbare knop geeft dus geen extra toegang.

Na een normale login herkent de plugin de ingelogde speler automatisch opnieuw. Je hoeft de managementcode niet opnieuw in te voeren.

## Voor organizers

- Gebruik een duidelijke titel: bijvoorbeeld `CoX Learner` of `Nex Mass`.
- Vermeld datum, tijd, wereld en host altijd volledig.
- Houd de tekst in RuneLite kort; plaats uitgebreide uitleg in de Discord-beschrijving van een learner-sjabloon.
- Voeg alleen plugins toe die deelnemers echt nodig hebben.
- Controleer vóór het opslaan of een event niet botst met een bestaande planning.

## Externe diensten, doel en veiligheid

De plugin gebruikt een klein aantal externe diensten. Hieronder staat precies waarom, welke gegevens daarbij betrokken zijn en hoe we dit beschermen.

| Dienst | Waarvoor gebruiken we het? | Welke gegevens gaan erheen? | Veiligheid |
| --- | --- | --- | --- |
| **Dutch Nation API** `dutch-nations-events.onrender.com` | Ophalen van de kalender, events, sjablonen en serverstatus. Beheerders gebruiken deze API om events, rollen en instellingen op te slaan. | Elke webverbinding bevat technisch gezien een IP-adres. Alleen bij een beheeractie gaan de handmatig ingevulde event- of rolgegevens plus de persoonlijke managementcode mee. | Alleen HTTPS in productie. Iedere beheeractie wordt op de server geautoriseerd; de managementcode wordt niet in de openbare kalenderfeed geplaatst. |
| **Discord** | De Dutch Nation-bot plaatst, wijzigt en verwijdert eventberichten in het door de owner gekozen Discord-kanaal. | Alleen de eventinhoud die de organizer heeft opgeslagen: titel, tijd, beschrijving, voorbereiding en links. | De bot gebruikt een servergeheime bot-token. Gebruikerscodes en managementrollen worden nooit naar Discord gestuurd. |
| **Wise Old Man API** `api.wiseoldman.net` | Tonen van de actuele Dutch Nation-competitie. | Een openbare aanvraag voor de vaste Dutch Nation-groep; geen persoonlijke RuneScape-naam uit de plugin. | Alleen openbare competitiegegevens worden gelezen. De informatie is uitsluitend zichtbaar in de plugin. |
| **OSRS Wiki** | Link naar strategie- en voorbereidingspagina's bij learner-events. | De plugin opent alleen een door de organizer opgeslagen link wanneer de gebruiker daarop klikt. | Geen automatische accountgegevens of managementcodes worden verstuurd. Controleer links altijd voordat je ze als organizer opslaat. |
| **YouTube** | Optionele video-guide bij learner-events. | De video wordt pas geopend of als Discord-preview geladen wanneer iemand de opgeslagen link bekijkt. | Geen managementcodes of spelergegevens worden gedeeld. YouTube hanteert zijn eigen privacyvoorwaarden. |
| **RuneLite Plugin Hub** | Installatie en verspreiding van de plugin. | RuneLite behandelt de installatie volgens zijn eigen Plugin Hub-proces. | De plugin wordt als open-source wijziging beoordeeld voordat een release beschikbaar komt. |

### Wat blijft lokaal?

- Online clanleden, werelden en clanrangen worden alleen uit jouw ingelogde RuneLite-client gelezen en uitsluitend lokaal in het zijpaneel getoond.
- De plugin verzendt nooit automatisch de naam van jouw ingelogde speler.
- Persoonlijke managementcodes worden lokaal door RuneLite bewaard en zijn verborgen in de instellingen.

### Wat kun je zelf doen?

- Deel een managementcode nooit in een openbaar kanaal, screenshot of stream.
- Gebruik alleen Wiki- en YouTube-links die je vertrouwt.
- Laat een owner een rol intrekken of de code vernieuwen zodra iemand geen beheerrechten meer nodig heeft.
- Meld een mogelijk beveiligingsprobleem direct via GitHub Issues en plaats geen code of geheim in het openbare bericht.

Lees de broncode in [`server/`](server/) als je wilt zien hoe de API, Discord-bot en rolcontrole werken.

## Hulp en feedback

Heb je een fout gevonden of een idee voor verbetering? Maak dan een melding aan op [GitHub Issues](https://github.com/WijheRP2023/dutch-nations-events/issues).

De actuele Plugin Hub-pagina vind je hier: [Dutch Nation Events](https://runelite.net/plugin-hub/show/dutch-nations-events).

## Ontwikkelen en testen

Voor ontwikkelaars:

```powershell
.\gradlew.bat test
.\gradlew.bat run
```

De API is een apart project in `server/`:

```powershell
.\gradlew.bat -p server test
.\gradlew.bat -p server runLocal
```

Gebruik voor een volledige lokale test uitsluitend de lokale ontwikkelomgeving. Productie gebruikt HTTPS; de plugin accepteert onversleuteld HTTP alleen voor `localhost`.
