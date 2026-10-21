# Dropscreenshots: lokale plugintest met de online server

> Dit document beschrijft de eerdere owner-test. Voor de nieuwe vrijwillige test voor alle
> clanleden, zonder management-token of extra instellingen, zie [DROP-PARTICIPANTS.md](DROP-PARTICIPANTS.md).

Deze test draait alleen in een lokaal gestarte plugin. De online server gebruikt de bestaande Discord-bot; de afbeelding wordt niet op de server opgeslagen. Ze wordt wel als bijlage in het ingestelde Discord-kanaal geplaatst.

1. Start in IntelliJ de normale **Dutch Nations RuneLite**-configuratie of de Gradle-taak **run** en log in als owner. De gebruikelijke management-token moet zijn ingesteld; deel die niet.
2. Open **Ownerinstellingen → Screenshotkanaal instellen**. Voor de test staat de eerder gekozen kanaallink klaar. Controleer die en sla de instelling op.
3. Zet in de pluginconfiguratie **Dropscreenshots delen (test)** aan. Deze optie staat standaard uit.
4. Zodra de nieuwe serverversie gecontroleerd online staat, kan een afzonderlijk testevent de PvM War- of ondersteunde Google Sheets-link als tussenstand gebruiken. Het handmatige dropveld en de testlijstknop zijn uit het eventvenster verwijderd. Laat het lopende event met codewoord ongemoeid.
5. Tijdens het actieve testevent wordt alleen een geselecteerde drop doorgestuurd wanneer die op de PvM War-pagina minder dan drie goedgekeurde vermeldingen heeft. Bij onbereikbare of onduidelijke voortgang wordt niets verstuurd.

Alleen de owner kan deze test gebruiken. De server of bot moet toegang hebben tot het gekozen kanaal en daar berichten met bijlagen mogen plaatsen. Als Discord niet op tijd antwoordt, controleer dan het kanaal voordat je opnieuw test; een bericht kan alsnog zijn aangekomen.

De PvM War-pagina bevat ook verzamelgroepen en namen die niet altijd overeenkomen met de RuneScape-itemnaam. De nieuwe lokale code haalt de actuele namen en goedgekeurde aantallen automatisch op; de online server gebruikt voorlopig nog de bestaande momentopname. Bepaalde groepen, waaronder pets, zijn niet volledig gekoppeld en worden daarom niet automatisch herkend. Jar-groepen zijn gekoppeld aan bekende individuele jars. De lijst is geen volledige garantie dat iedere mogelijke drop wordt doorgestuurd.

## Google Sheets als droplijst (nog niet online gepubliceerd)

Voor een Sheet met dezelfde indeling als het voorbeeld wordt een `https://docs.google.com/spreadsheets/d/...`-link in de tussenstand van het event herkend. De server leest alleen `Instellingen!G3:H1000`, met `Drop (bewerkbaar)` en `Doel` als kolomkoppen; alleen de dropnamen worden gebruikt. Andere indelingen zijn niet automatisch ondersteund. Het werkblad wordt nooit door de koppeling gewijzigd.

Een aparte Google Cloud-serviceaccount moet alleen kijkrechten op de gewenste Sheet krijgen en de Sheets API moet in diens project ingeschakeld zijn. Configureer de serviceaccount-JSON uitsluitend als geheime serveromgeving `DN_GOOGLE_SERVICE_ACCOUNT_JSON`; zet de sleutel nooit in de repository, RuneLite-instellingen, Discord of chat. Verleen toegang per bestand, niet aan alle bestanden. De server gebruikt de Google Sheets-lezen-scope en ververst de toegangstoken zelf. Zonder correcte leesrechten wordt niets doorgestuurd. De Google Drive-koppeling van Codex is geen authenticatie voor Render.

De plugin haalt de lijst tijdens een actief event ongeveer elke 30 seconden opnieuw op. Een drop wordt ook bij ontvangst opnieuw door de server gecontroleerd. Voor Google Sheets worden doelen en teamstanden nog **niet** gebruikt om capped drops te stoppen; daarvoor moeten de teamregels eerst bepaald en geïmplementeerd worden. De bestaande PvM War-test gebruikt zijn oude regel van drie goedgekeurde drops.

Deze wijziging is lokaal voorbereid en getest, maar vereist nog een gecontroleerde serverupdate en configuratie van het leesaccount voordat de normale RuneLite-test met de online server kan werken. Controleer eerst verschillen tussen de lokaal gewijzigde server en de daadwerkelijk online versie; overschrijf de bestaande productiecode of database niet. Ook de lokaal draaiende plugin moet na het bouwen opnieuw worden gestart. Plaats geen live testbericht zonder dat dit is afgesproken.
