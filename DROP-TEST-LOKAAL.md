# Lokale drop-proef

Dit is een proefopstelling, geen live functie. Er wordt niets naar Render of Discord verstuurd. Gebruik een apart RuneLite-profiel voor deze proef, zodat het echte management-token niet wordt vervangen. De lokale testserver gebruikt alleen een apart testbestand en geen productiedatabase of bot-token.

1. Start de testserver met `gradlew.bat -p server runDropTestLocal`.
2. Start de plug-in met de Gradle-taak `runDropTestLocal`, of voeg in IntelliJ `-Ddutch.nations.local=true` toe aan de VM-opties van de lokale plug-instart.
3. Gebruik alleen in het aparte testprofiel het lokale testtoken `dutch-nations-local-owner`. Log in als de owner en stel bij de ownerinstellingen een test-dropkanaal in. Dit slaat alleen op in het lokale testbestand.
4. Maak alleen in de geïsoleerde lokale testomgeving een actief testevent en vul de PvM War-link als tussenstand in. Het handmatige dropveld en de testlijstknop zijn verwijderd.
5. Schakel de RuneLite Loot Tracker in en zet **Dropscreenshots delen (test)** aan. Een gekozen drop tijdens het event wordt verwerkt door de lokale testserver; de plug-in geeft aan dat er niets naar Discord is verstuurd. De screenshot wordt niet opgeslagen door deze proefserver.

De lokale testcode haalt de lijst uit de PvM War-pagina. Namen die op de website als groep, bijnaam of alternatieve schrijfwijze staan, zijn niet allemaal automatisch aan daadwerkelijke RuneLite-itemnamen gekoppeld. Pets en speedruns zijn niet ondersteund. Loot Tracker moet de drop daadwerkelijk melden; niet elke soort beloning is daarmee al gecontroleerd. Voor een privé-Google Sheet met de ondersteunde indeling is aparte serverauthenticatie nodig; zie DROP-TEST-ONLINE.md.

Wanneer de PvM War-link bij het event is ingevuld, leest de lokale server bij een drop de actuele voortgang van de openbare website. Drie goedgekeurde plekken betekenen dat het item voor de hele clan klaar is: de lokale server weigert de melding. Jars binnen dezelfde tier delen die drie plekken. Bij een onbekend item, een onbereikbare website of een onverwachte pagina-indeling wordt de melding uit voorzorg geweigerd. De website ontvangt alleen een gewone aanvraag voor de openbare pagina, geen screenshot of token.

Een lokale testmelding wordt niet automatisch een goedgekeurde plek op de website en telt niet zelfstandig op bij de websitevoortgang. Wachtende website-inzendingen tellen pas mee nadat zij zijn goedgekeurd. Een screenshot wordt vlak na de drop lokaal gemaakt, vóór de servercontrole, om het moment niet te missen; bij drie goedgekeurde plekken wordt die niet naar Discord doorgestuurd. De lokale testserver bewaart de screenshot niet.

De standaard testserver start bewust zonder bot. Voor een expliciet toegestane test naar Discord is er een aparte taak `runDropTestDiscord`: zet in de lokale startconfiguratie `DISCORD_BOT_TOKEN`. Het toegestane testkanaal komt uit `server-data/drop-discord-test/channel.txt` of uit `DN_DROP_TEST_CHANNEL` (de volledige kanaallink); gebruik exact die kanaallink in de lokale ownerinstelling of het testevent. De afzonderlijke testgegevens worden standaard in de tijdelijke map van de computer bewaard; die map kan door Windows worden opgeschoond. Gebruik geen productiedatabase en deel geen bot-token via chat. Alleen dit ene kanaal is toegestaan; de lokale bot luistert niet naar berichten en de eventberichten staan tijdens de proef uit. De bot moet toegang hebben tot het testkanaal en bestanden kunnen toevoegen. Een bevestiging van de wachtrij betekent nog niet dat het bericht daadwerkelijk is aangekomen: controleer het kanaal zelf.

Echte verzending, toegangsbeveiliging en testen met live spelers moeten apart worden gecontroleerd en goedgekeurd voordat dit online kan.
