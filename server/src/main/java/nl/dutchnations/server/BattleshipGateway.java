package nl.dutchnations.server;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

interface BattleshipGateway
{
    List<List<List<Object>>> batchValues(String file, String... ranges) throws IOException;
    void write(String file, Map<String, List<List<?>>> data) throws IOException;
    BattleshipSheets.Access privateAccess(String file) throws IOException;
    void protect(String file, Set<String> first, Set<String> second) throws IOException;
    void privateLocked(String file, boolean locked) throws IOException;
    void sourceLocked(String file, boolean locked, boolean payment) throws IOException;
}
