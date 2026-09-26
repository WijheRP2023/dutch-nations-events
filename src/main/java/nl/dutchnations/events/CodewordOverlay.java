package nl.dutchnations.events;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

final class CodewordOverlay extends OverlayPanel
{
    private static final ZoneId DUTCH_TIME_ZONE = ZoneId.of("Europe/Amsterdam");
    private static final DateTimeFormatter DUTCH_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm z");
    private static final Color CODEWORD_COLOR = new Color(40, 255, 80);
    private final DutchNationsPlugin plugin;
    private final DutchNationsConfig config;

    @Inject
    CodewordOverlay(DutchNationsPlugin plugin, DutchNationsConfig config)
    {
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        OffsetDateTime currentMoment = OffsetDateTime.now();
        ClanFeed.ClanEvent event = plugin.activeEvent(currentMoment);
        if (!config.showOverlay() || event == null || event.codeword == null || event.codeword.trim().isEmpty()) return null;

        String timestamp = DUTCH_TIME.format(currentMoment.atZoneSameInstant(DUTCH_TIME_ZONE));
        int detailWidth = graphics.getFontMetrics().stringWidth(event.codeword + "  " + timestamp);
        int requiredWidth = Math.max(140, Math.min(250, detailWidth + 12));
        panelComponent.setPreferredSize(new Dimension(requiredWidth, 0));

        panelComponent.getChildren().add(LineComponent.builder()
            .left(event.codeword)
            .leftColor(CODEWORD_COLOR)
            .right(timestamp)
            .rightColor(Color.WHITE)
            .build());
        return super.render(graphics);
    }

}
