# Screenshot-droplijsten uit Google Sheets

Vul bij het event de **Screenshot-droplijst-link (Sheet)** in. Gebruik de link
naar het juiste tabblad, inclusief `gid`. De **Tussenstand**-link blijft apart
en kan naar het hoofdblad voor leden verwijzen. De server leest de Sheet en
schrijft er niets in. Een openbare Sheet met leesrechten heeft geen Google-account nodig.

Ondersteunde indelingen:

- `Drop (bewerkbaar)` naast `Doel`: de opgegeven itemnamen vormen de droplijst.
- `Item name` met `#1 obtained by` tot en met `#7 obtained by`: een item blijft
  beschikbaar totdat alle zeven plaatsen zijn ingevuld.
- Bingo met `Item` en een aangrenzend vinkje, of `Aantal` boven de genummerde
  tellers: niet-aangevinkte opdrachten leveren screenshotregels op.

Bij bingo wordt de volledige in-game itemnaam gezocht in de opdracht.
`3x occult necklace` herkent bijvoorbeeld `Occult necklace`.
Een omschrijving zoals `any inquisitor piece` zoekt naar `Inquisitor` in de
in-game naam. Alle items met die naam tellen mee, ook wapens.
Je kunt ook expliciet `bevat: Inquisitor` gebruiken.

Bekende groepen worden op de server vertaald naar concrete itemnamen. Zo wordt
`any nex unique` onder andere `Nihil horn`, en wordt `any barrows piece` een lijst
van alle 24 Barrows-items. `8x piece moons` wordt de lijst met alle 12 Moons-items.
Zie [de groepsindeling](DROP-GROUPS.md) voor de ondersteunde groepen en afspraken.
Andere afkortingen en spelfouten worden niet automatisch geraden.

Het vinkje bepaalt of een bingo-opdracht klaar is. Getallen zoals `3x` worden
niet door de plugin bijgehouden. De Sheet blijft de bron voor goedkeuring en
voortgang. Bij een herhaling kunnen de vinkjes of de zeven naamvelden worden
leeggemaakt; stel de juiste Sheet-link en eventperiode in bij het nieuwe event.
Wijzigingen worden bij de volgende verversing ingelezen.

De screenshotfunctie is een owner-test. Stel het kanaal in onder
**Instellingen → Event-dropscreenshots → Screenshotkanaal instellen** en zet
**Event-dropscreenshots delen** aan in de RuneLite-pluginconfiguratie. Deze optie
staat standaard uit. De normale plugin gebruikt de online server; een speciale
opstartstand is hiervoor niet nodig. Het lezen van een Sheet zet screenshots
niet automatisch aan. Voor de starttijd en vanaf de eindtijd worden geen
event-dropscreenshots gemaakt. Ook de server controleert de eventperiode.
