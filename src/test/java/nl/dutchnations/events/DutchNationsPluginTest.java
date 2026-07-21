package nl.dutchnations.events;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class DutchNationsPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(DutchNationsPlugin.class);
        RuneLite.main(args);
    }
}
