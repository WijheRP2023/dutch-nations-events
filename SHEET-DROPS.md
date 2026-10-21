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

Er wordt geen vertaling van afkortingen, spelfouten of bossnamen naar hun
droptabellen uitgevoerd. `any nex unique` herkent dus niet automatisch `Nihil horn`.
Gebruik concrete in-game namen of een herkenbaar naamfragment.

Het vinkje bepaalt of een bingo-opdracht klaar is. Getallen zoals `3x` worden
niet door de plugin bijgehouden. De Sheet blijft de bron voor goedkeuring en
voortgang. Bij een herhaling kunnen de vinkjes of de zeven naamvelden worden
leeggemaakt; stel de juiste Sheet-link en eventperiode in bij het nieuwe event.
Wijzigingen worden bij de volgende verversing ingelezen.

De screenshotfunctie gebruikt nog de bestaande owner-testinstelling en het
ingestelde Discord-screenshotkanaal. Het lezen van een Sheet activeert die
instelling niet automatisch.
