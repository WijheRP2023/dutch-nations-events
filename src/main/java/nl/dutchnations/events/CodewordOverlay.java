package nl.dutchnations.events;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

final class CodewordOverlay extends OverlayPanel
{
    private static final DateTimeFormatter UTC_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm 'UTC'");
    private static final Color TITLE_COLOR = new Color(255, 190, 70);
    private static final Color CODEWORD_COLOR = new Color(40, 255, 80);
    private final DutchNationsPlugin plugin;
    private final DutchNationsConfig config;

    @Inject
    CodewordOverlay(DutchNationsPlugin plugin, DutchNationsConfig config)
    {
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_CENTER);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        OffsetDateTime currentMoment = OffsetDateTime.now();
        ClanFeed.ClanEvent event = plugin.activeEvent(currentMoment);
        if (!config.showOverlay() || event == null) return null;

        String title = "DUTCH NATIONS - " + event.title;
        String timestamp = UTC_TIME.format(currentMoment.withOffsetSameInstant(ZoneOffset.UTC));
        int titleWidth = graphics.getFontMetrics().stringWidth(title);
        int detailWidth = graphics.getFontMetrics().stringWidth(event.codeword + "    " + timestamp);
        int requiredWidth = Math.max(250, Math.min(500, Math.max(titleWidth, detailWidth) + 28));
        panelComponent.setPreferredSize(new Dimension(requiredWidth, 0));

        panelComponent.getChildren().add(TitleComponent.builder()
            .text(title)
            .color(TITLE_COLOR)
            .build());
        panelComponent.getChildren().add(LineComponent.builder()
            .left(event.codeword)
            .leftColor(CODEWORD_COLOR)
            .right(timestamp)
            .rightColor(Color.WHITE)
            .build());
        return super.render(graphics);
    }
}
