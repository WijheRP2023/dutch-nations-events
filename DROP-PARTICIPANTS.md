# Dropscreenshottest voor deelnemers

Na verspreiding van de nieuwe plug-inversie en de bijbehorende serverupdate kan ieder
Dutch Nation-clanlid vrijwillig deelnemen. Er komt geen extra instelling of beheerrol bij.

## Voor leden

1. Gebruik de bijgewerkte plug-in en RuneLite Loot Tracker en log in bij de clan.
2. Zet het bestaande vinkje **Event-dropscreenshots delen** aan. Het staat standaard uit.
3. Tijdens een actief event maakt de plug-in alleen bij een herkende eventdrop een screenshot
   en verstuurt deze via de bestaande server en Discord-bot. Buiten de eventtijd niet.
4. Zet het vinkje uit om te stoppen. Er is geen management-token nodig.

Het spelbeeld, de spelersnaam, het item en aantal gaan naar Discord. Chat kan zichtbaar zijn.
De server bewaart de afbeelding niet op schijf. Het Discord-bericht blijft daar wel staan.
Dit is een test, geen gegarandeerde bewijsregistratie: controleer het kanaal en volg de eventregels.

## Alleen de owner beheert

- Het Discord-screenshotkanaal via de bestaande ownerinstellingen.
- Het event, de looptijd en de screenshot-droplijst-link.
- Rollen, sjablonen en overige beheerinstellingen.

De persoonlijke deelvoorkeur van een lid geeft geen rechten om deze gegevens te veranderen.
Een geweigerde, onleesbare of al afgeronde drop levert geen nieuw Discord-bericht op.
De test schrijft geen score, goedkeuring of nieuwe drop terug naar de Sheet of website.

## Afzonderlijke deelnemersroutes

De plug-in gebruikt `GET /api/event-drop-list?eventId=...` en `POST /api/event-drops`.
Deze routes hebben bewust geen management-token: zo hoeven leden geen beheercredentials
te ontvangen. Bestaande managementroutes en de oude owner-testroutes blijven beveiligd.

De server controleert eventtijden, itemnamen, de actuele centrale droplijst, een beperkte
PNG-bijlage en het ingestelde kanaal. Er zijn limieten per verbinding en spelersnaam,
een globale uploadlimiet, maximaal vier gelijktijdige uploads en tijdelijke duplicaatdetectie.
Aanvragen namens meerdere gebruikers achter dezelfde proxy delen de verbindingslimiet.
De server vertrouwt geen door de aanvrager opgegeven forwardingheaders.

Droplijsten worden dertig seconden gedeeld uit geheugen gelezen. Maximaal twee bronnen
worden tegelijk geladen. Een mislukte bron wordt kort afgekoeld; geen verouderde lijst wordt
als terugval gebruikt. Bij een inzending wordt de actuele bron opnieuw gecontroleerd.

Belangrijk: de normale plug-in controleert het clankanaal lokaal. Deze publieke deelnemersroutes
bewijzen niet dat de opgegeven RuneScape-naam bij de inzender hoort. Berichten zijn daarom
testinzendingen en blijven onder menselijke controle, niet automatisch goedgekeurd bewijs.
De beschermingen beperken misbruik maar zijn geen identiteitsverificatie of garantie tegen spam.
Voor sterke identiteit is een afzonderlijke persoonlijke aanmelding nodig.

Er is geen databasemigratie of wijziging van bestaande events, rollen of kanaalinstellingen nodig.
