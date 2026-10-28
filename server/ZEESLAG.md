# Zeeslag via Google Sheets

De server verwerkt het spel; de openbare Sheet toont alleen uitslagen van afgevuurde schoten. De twee vlootbestanden blijven afzonderlijk privé. Er zijn geen openbare API-routes voor bootposities, schieten of configuratiewijzigingen.

## Eenmalige koppeling

Stel op de bestaande server onderstaande omgevingsvariabelen in. Gebruik losse document-ID's, geen URL's. Zet privébestands-ID's en sleutels niet in GitHub of chatberichten.

- `DN_BATTLESHIP_PUBLIC_SHEET`: het bestaande eventdocument.
- `DN_BATTLESHIP_TEAM_ONE_SHEET`: privévloot Team 1.
- `DN_BATTLESHIP_TEAM_TWO_SHEET`: privévloot Team 2.
- `DN_GOOGLE_SERVICE_ACCOUNT_JSON`: de Google-serviceaccountgegevens als geheime serverinstelling.

Schakel de Google Sheets API en Google Drive API voor dit account in. Deel alleen de drie betrokken documenten met het serviceaccount als bewerker. De server gebruikt de scopes `spreadsheets` en `drive.metadata.readonly`. Bewaar de bestaande serverstatus duurzaam in PostgreSQL of op een blijvende schijf; een vluchtige schijf is niet geschikt voor een echt event.

De twee privévloten mogen alleen met individuele Google-accounts gedeeld worden, niet met iedereen, een domein of een groep. Dezelfde eigenaar en het serveraccount mogen beide bestanden zien. Andere deelnemers mogen niet tot beide bestanden toegang hebben. Deze controle geldt ook voor kijkrechten. De server sluit het spel bij onveilige toegang.

Leiders worden later door de eigenaar uitgenodigd als bewerker van hun eigen vlootbestand en van de openbare Sheet. Het serveraccount stelt de afzonderlijke teamrechten voor de schotvelden in. Zonder leiders blijft alleen de eigenaar bevoegd. De openbare Sheet moet tijdzone `Europe/Amsterdam` gebruiken.

Gebruik bij Zeeslag geen geïnstalleerde eigenaarstriggers in het gedeelde, aan de Sheet gebonden Apps Script: bewerkers kunnen de scriptcode veranderen. De server verwerkt schoten en bevriezing. De bijgewerkte spelkeuze schakelt de drie eigen bewakingstriggers uit bij het kiezen van Zeeslag; activeer ze daarna niet opnieuw. Bestaande andere spellen blijven de bestaande bewaking gebruiken.

`GET /api/zeeslag/status` geeft alleen aan of de vier serverinstellingen aanwezig zijn. `configured: true` bewijst nog niet dat Google-toegang en het spel gereed zijn. Controleer daarvoor de actuele status en synchronisatietijd in de Sheet. Zonder koppeling blijft schieten gesloten.

## Voor ieder event

1. Kies **Zeeslag** op Instellingen. Vul vóór het event de teamnamen, deelnemers, dropdoelen en een toekomstige start- en einddatum met tijd in.
2. Wacht op de serverkoppeling in beide privébestanden. Plaats in elk bestand vijf rechte boten met lengtes **5, 4, 3, 3, 2** op een **8 × 8** bord. Kies beginvak en richting; geen overlap of vakken buiten het bord. Tussenstand toont beide teams naast elkaar.
3. Vink **Gereed** aan in beide privébestanden, ruim vóór de start. Controleer dat de server de voorbereiding bevestigt. Een spel kan niet voor het eerst na de start worden vrijgegeven.
4. Vanaf de start zijn de opgeslagen vloten, teamnamen, doelen en starttijd vast. Alleen de eigenaar kan via de kalender de eindtijd vervroegen of het event direct beëindigen; heropenen of verlengen na de start is niet toegestaan. Bestaande schoten en vloten blijven behouden. Betaald-vinkjes blijven alleen bewerkbaar als deze optie aanstaat. Het wijzigen van een privéblad na de start verplaatst geen opgeslagen boot.
5. Per volledig afgerond, uniek dropdoel verdient een team één schot. Een doel van drie items geeft dus één schot, niet drie. Extra items en opnieuw afvinken leveren geen extra schoten op.

   Zeeslag leest hiervoor rechtstreeks **Team 1** en **Team 2** (Drop-ID, Doel, Behaald en Actief), niet Screenshotlijst of Rekenbladen. Beide teams moeten dezelfde geselecteerde doelen hebben.
6. Vul op **Tussenstand** direct onder het eigen bord een coördinaat `A1` t/m `H8` in en vink **Schieten** aan. Team 1 gebruikt D65:E65 en H65; Team 2 gebruikt M65:N65 en Q65. De server controleert ongeveer iedere 15 seconden, afhankelijk van Google-responstijd. De velden worden daarna geleegd; uitslag en tellers worden teruggeschreven. Het losse voorbeeldtabblad Zeeslag is niet meer nodig.
7. Dubbele, ongeldige en ongeoorloofde schoten kosten niets. Correcties aan behaalde doelen kunnen beschikbaar krediet verminderen; reeds gebruikte schoten worden niet teruggegeven. Een team wint zodra alle 17 bootvakken zijn geraakt. Bij wederzijdse laatste treffers in dezelfde verwerkingsronde is het gelijkspel.
8. Na de eindtijd worden geen schoten meer geaccepteerd. Gebruik daarna de bestaande archief/resetfunctie. Eerst wordt het event gearchiveerd; vervolgens worden invoer en gekoppelde vlootposities gewist voor hergebruik. Gebruik niet alleen nieuwe datums als vervanging van de reset.

   De succesvolle reset plaatst een opdracht in de beschermde cel Zeeslagdata!A5. Alleen bij twee lege eventdatums verwerkt de server deze opdracht: oude spelstatus wordt buiten gebruik gesteld, beide privévloten worden geleegd en daarna wordt de opdracht gewist. Een onderbroken terugmelding kan veilig opnieuw worden verwerkt. Wacht op de resetbevestiging voordat nieuwe vloten worden ingevuld.

## Controle vóór echt gebruik

- Controleer vanuit elk leidersaccount dat alleen het eigen vlootbestand en de eigen schotvelden toegankelijk zijn.
- Test in een apart testevent een treffer, misser, gezonken boot, dubbele invoer, ontbrekend krediet en eindtijd.
- Controleer de terugmelding na een serverherstart en dat openbare bladen/feeds geen ongeprobeerde bootposities tonen.
- De automatische tests dekken spelregels, toegang, periodegrenzen, gelijktijdige schoten, herstel na een onderbroken terugmelding en herstart. Dit vervangt geen live Google-toegangstest met de uiteindelijk uitgenodigde leiders.
