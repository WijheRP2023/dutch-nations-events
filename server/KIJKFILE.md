# Automatisch kijkbestand

De bestaande basis blijft het beheerdocument, inclusief Tussenstand en beide teampagina's. Het aanvullende kijkbestand bevat uitsluitend Tussenstand, Team 1 en Team 2. Teamleiders blijven registreren en schieten in de basis.

De bestaande Google-servertoegang wordt gebruikt. Geef hetzelfde serviceaccount alleen op het nieuwe kijkbestand bewerkerrechten. Vul de document-ID in de beschermde broncel `Zeeslagdata!A6` in. Reset wist deze koppeling niet. Het kijkbestand moet documentmetadata `DN_EVENT_VIEW_SOURCE` met de bron-ID hebben en precies de drie genoemde tabbladen bevatten. Bron en bestemming moeten verschillen. Privévloten worden nooit door de kijkbestandcode gelezen.

De achtergrondtaak controleert iedere 45 seconden, onafhankelijk van Discord en Zeeslag. Alleen gewijzigde gegevens worden geschreven; opmaak wordt uiterlijk na vijf minuten opnieuw overgenomen. De laatst geslaagde synchronisatie is zichtbaar op Tussenstand en via `GET /api/event-view/status`. Een slapende Render-server kan pas na wakker worden verder synchroniseren; een oude tijdstempel is geen actuele stand.

De taak kopieert berekende waarden en effectieve opmaak, kolombreedtes, rijhoogten en samengevoegde koppen. Alleen actieve dropregels worden in de teampagina's getoond, maximaal 500. Itemafbeeldingen worden zelfstandige IMAGE-formules met gecontroleerde openbare itemhosts. Bronformules, instellingen, betaaloverzichten en privévloten worden niet gepubliceerd. Onverwerkte schotvelden worden niet gespiegeld. Het geselecteerde spel bepaalt welke bordsectie zichtbaar is.

Het kijkbestand is eenrichtingsverkeer. Wijzigingen door de eigenaar in dit kijkbestand worden bij een volgende synchronisatie overschreven. Andere deelnemers krijgen alleen leesrechten. Het apart delen van een kijkbestand verandert de bestaande deelrechten van het basisbestand niet automatisch.

Bij Google-fouten of rekenfouten in de bron blijft de vorige geldige weergave behouden. Controleer bij een probleem altijd de synchronisatietijd. De synchronisatietaak kan geen bootlocaties of instellingen via een openbare API uitlezen of wijzigen.
