package nl.dutchnations.events;

import java.util.Objects;
import java.awt.image.BufferedImage;

final class OnlineClanMember
{
    final String name;
    final int rank;
    final int world;
    final BufferedImage rankIcon;

    OnlineClanMember(String name, int rank, int world, BufferedImage rankIcon)
    {
        this.name = name;
        this.rank = rank;
        this.world = world;
        this.rankIcon = rankIcon;
    }

    @Override public boolean equals(Object other)
    {
        if (this == other) return true;
        if (!(other instanceof OnlineClanMember)) return false;
        OnlineClanMember member = (OnlineClanMember) other;
        return world == member.world && Objects.equals(name, member.name) && Objects.equals(rank, member.rank);
    }

    @Override public int hashCode() { return Objects.hash(name, rank, world); }
    @Override public String toString() { return name + ":" + rank + ":" + world + ":" + (rankIcon != null); }
}