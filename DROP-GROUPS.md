# Groepen voor dropscreenshots

De server vertaalt bekende groepsnamen uit de event-Sheet naar losse, officiële
itemnamen. De plugin krijgt de concrete namen via de bestaande droplijstroute.
Een groepswijziging vereist daardoor alleen een nieuwe serverversie.
De definities staan in `server/src/main/java/nl/dutchnations/server/DropGroups.java`.

Voorvoegsels zoals `2x` en `any`, hoofdletters en `unique`/`uniques` zijn toegestaan.
De schrijfwijze `cerb unqiue` uit de aangeleverde Sheet wordt ook ondersteund.
Een onbekende groep wordt niet gegokt en geeft geen extra items vrij.

## Barrows: 24 losse items

| Groep | Items |
| --- | --- |
| Ahrim | Ahrim's hood, Ahrim's robetop, Ahrim's robeskirt, Ahrim's staff |
| Dharok | Dharok's helm, Dharok's platebody, Dharok's platelegs, Dharok's greataxe |
| Guthan | Guthan's helm, Guthan's platebody, Guthan's chainskirt, Guthan's warspear |
| Karil | Karil's coif, Karil's leathertop, Karil's leatherskirt, Karil's crossbow |
| Torag | Torag's helm, Torag's platebody, Torag's platelegs, Torag's hammers |
| Verac | Verac's helm, Verac's brassard, Verac's plateskirt, Verac's flail |

`any barrows piece` bevat alle 24 items. `Full set Guthan` en `Guthan's set`
bevatten alleen de vier Guthan-items. Dit werkt ook voor de andere broeders.
`Guthan's spear`, `Guthan's platelegs` en `Guthans's set` uit de bestaande
bestanden zijn gekoppeld aan de correcte items of set. Barrows gloves en
versleten varianten horen niet bij de groep met nieuwe Barrows-drops.

Bron: [Barrows equipment](https://oldschool.runescape.wiki/w/Barrows_equipment).

## Moons: 12 losse items

| Groep | Items |
| --- | --- |
| Blood Moon | Blood moon helm, Blood moon chestplate, Blood moon tassets, Dual macuahuitl |
| Blue Moon | Blue moon helm, Blue moon chestplate, Blue moon tassets, Blue moon spear |
| Eclipse Moon | Eclipse moon helm, Eclipse moon chestplate, Eclipse moon tassets, Eclipse atlatl |

`piece moons`, `any moons piece`, `moons unique` en `moons of peril` bevatten alle
12 items. `blue moon set`, `blood moon set` en `eclipse moon set` bevatten ieder
de bijbehorende vier items. Gewone chestloot zoals Atlatl darts is uitgesloten.

Bron: [Moons-uitrusting](https://oldschool.runescape.wiki/w/RuneScape:Grand_Exchange_Market_Watch/Combat_minigames#Moons_of_Peril).

## Unique-groepen uit de eventbestanden

| Groep | Concrete drops |
| --- | --- |
| CoX | Dexterous prayer scroll, Arcane prayer scroll, Twisted buckler, Dragon hunter crossbow, Dinh's bulwark, Ancestral hat, Ancestral robe top, Ancestral robe bottom, Dragon claws, Elder maul, Kodai insignia, Twisted bow |
| ToB | Avernic defender hilt, Ghrazi rapier, Sanguinesti staff (uncharged), Justiciar faceguard, Justiciar chestguard, Justiciar legguards, Scythe of vitur (uncharged) |
| ToA | Osmumten's fang, Lightbearer, Elidinis' ward, Masori mask, Masori body, Masori chaps, Tumeken's shadow (uncharged) |
| Any raid unique | Alle bovenstaande CoX-, ToB- en ToA-drops samen |
| Nex | Ancient hilt, Nihil horn, Zaryte vambraces, Torva full helm (damaged), Torva platebody (damaged), Torva platelegs (damaged) |
| Nightmare | Inquisitor's great helm, Inquisitor's hauberk, Inquisitor's plateskirt, Inquisitor's mace, Nightmare staff, Volatile orb, Harmonised orb, Eldritch orb |
| Yama | Oathplate helm, Oathplate chest, Oathplate legs, Soulflame horn |
| Doom / Mokhaiotl | Avernic treads, Eye of ayak (uncharged), Mokhaiotl cloth |
| Tormented Demon | Tormented synapse, Burning claw |
| Cerb / Cerberus | Primordial crystal, Pegasian crystal, Eternal crystal, Smouldering stone |
| Zulrah | Tanzanite fang, Magic fang, Serpentine visage, Uncut onyx |
| Duke, geen quartz | Magus vestige, Chromium ingot, Eye of the duke, Virtus mask, Virtus robe top, Virtus robe bottom, Awakener's orb |
| Vardorvis, geen quartz | Ultor vestige, Chromium ingot, Executioner's axe head, Virtus mask, Virtus robe top, Virtus robe bottom, Awakener's orb |

De raidgroepen gebruiken de hoofdprijzen (purples). Pets, jars, gewone loot,
teleport-tablets en losse cosmetische/tertiaire beloningen zijn niet automatisch
inbegrepen. Duke en Vardorvis gebruiken de genoemde hoofdprijzen plus Awakener's
orb; quartz is uitgesloten zoals gevraagd in de event-Sheet.

Controlebronnen: [raids](https://oldschool.runescape.wiki/w/Tombs_of_Amascut#Rewards),
[ToB](https://oldschool.runescape.wiki/w/Theatre_of_Blood/Hard_Mode#Uniques),
[bossdrops](https://oldschool.runescape.wiki/w/Boss),
[Yama](https://oldschool.runescape.wiki/w/Module:Yama_rewards),
[Zulrah](https://oldschool.runescape.wiki/w/Zulrah#Uniques),
[Duke](https://oldschool.runescape.wiki/w/Duke_Sucellus#Uniques),
[Vardorvis](https://oldschool.runescape.wiki/w/Vardorvis#Uniques),
[officiële itemnamen via de Wiki-prijs-API](https://prices.runescape.wiki/api/v1/osrs/mapping).

## Voortgang en grenzen

Het vinkje bij de bingo-opdracht of de zeven ingevulde naamvelden bij PvM War
bepalen of die rij nog beschikbaar is. Een aangevinkte groepsrij levert geen
items meer op. Een item kan wel blijven meetellen voor een andere openstaande rij.
De plugin schrijft geen tellingen naar de Sheet.

Deze uitbreiding herkent itemnamen. De huidige screenshotmelding bevat nog geen
bossnaam of andere herkomstcontrole. Een gedeeld item zoals Virtus of Awakener's
orb kan dus op naam overeenkomen met meerdere bossgroepen. De eventgoedkeuring
blijft nodig om de juiste herkomst te controleren.

Andere samengestelde opdrachten, zoals specifieke killcounts, complete godswords,
ringgroepen en resterende spelfouten, vallen buiten deze eerste groepuitbreiding.
