package nl.dutchnations.events;

import java.util.Objects;

final class OnlineClanMember
{
    final String name;
    final int rank;
    final int world;

    OnlineClanMember(String name, int rank, int world)
    {
        this.name = name;
        this.rank = rank;
        this.world = world;
    }

    @Override public boolean equals(Object other)
    {
        if (this == other) return true;
        if (!(other instanceof OnlineClanMember)) return false;
        OnlineClanMember member = (OnlineClanMember) other;
        return world == member.world && Objects.equals(name, member.name) && Objects.equals(rank, member.rank);
    }

    @Override public int hashCode() { return Objects.hash(name, rank, world); }
    @Override public String toString() { return name + ":" + rank + ":" + world; }
}