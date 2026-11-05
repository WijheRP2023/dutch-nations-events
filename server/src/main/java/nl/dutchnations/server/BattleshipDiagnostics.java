package nl.dutchnations.server;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Only fixed codes and operation labels may reach logs or public status cells. */
final class BattleshipDiagnostics
{
    enum Code { GOOGLE_AUTH, GOOGLE_HTTP, TIMEOUT, PRIVATE_ACCESS, SHEET_STRUCTURE, CONNECTION, UNEXPECTED }
    enum Stage { INITIALIZE, READ_CONTROLS, READ_EVENT, LOCK_PUBLIC, ACCESS_TEAM_1, ACCESS_TEAM_2,
        CHECK_SEPARATION, READ_FLEET_1, READ_FLEET_2, WRITE_PUBLIC, WRITE_FLEET_1, WRITE_FLEET_2,
        LOCK_FLEET_1, LOCK_FLEET_2, LOCK_SETTINGS, GAME_STATE }

    static final class Failure extends IOException
    {
        final Code code;
        final int status;
        Failure(Code code, String message) { this(code, 0, message); }
        Failure(Code code, int status, String message)
        { super(message); this.code = code; this.status = status >= 100 && status <= 599 ? status : 0; }
    }

    Stage stage = Stage.INITIALIZE;

    String summary(Exception error)
    {
        Code code = error instanceof Failure ? ((Failure) error).code
            : error instanceof HttpTimeoutException ? Code.TIMEOUT
            : error instanceof IOException ? Code.CONNECTION : Code.UNEXPECTED;
        int status = error instanceof Failure ? ((Failure) error).status : 0;
        String hint;
        switch (code)
        {
            case GOOGLE_AUTH: hint = "Controleer de Google-servertoegang."; break;
            case PRIVATE_ACCESS: hint = "Controleer de afscherming van de twee privevloten."; break;
            case SHEET_STRUCTURE: hint = "Controleer tijdzone, tabbladen en bestaande bladbeveiligingen."; break;
            case TIMEOUT: hint = "Google reageerde niet op tijd; de server probeert opnieuw."; break;
            case GOOGLE_HTTP:
                hint = status == 401 ? "Google accepteert de servertoegang niet."
                    : status == 403 ? "Google weigert de bewerking; controleer rechten en API-limieten."
                    : status == 404 ? "Een gekoppeld bestand of tabblad is niet gevonden."
                    : status == 429 ? "Google beperkt de aanvragen; de server probeert opnieuw."
                    : status >= 500 ? "Google meldt een tijdelijke storing; de server probeert opnieuw."
                    : "Google heeft de aanvraag afgewezen.";
                break;
            default: hint = "De controle is veilig gestopt. Controleer de serverkoppeling.";
        }
        return "Zeeslag [ZSL_" + code.name() + "; stap=" + stage.name()
            + (status == 0 ? "" : "; HTTP=" + status) + "]: " + hint;
    }

    BattleshipGateway track(BattleshipGateway delegate, String main, String first)
    {
        return new BattleshipGateway()
        {
            private Stage privateStage(String file, Stage one, Stage two) { return first.equals(file) ? one : two; }
            private void complete() { stage = Stage.GAME_STATE; }
            public List<List<List<Object>>> batchValues(String file, String... ranges) throws IOException
            {
                stage = main.equals(file) ? (ranges.length == 3 && "Borddata!Z2".equals(ranges[0]) ? Stage.READ_CONTROLS : Stage.READ_EVENT)
                    : privateStage(file, Stage.READ_FLEET_1, Stage.READ_FLEET_2);
                List<List<List<Object>>> result = delegate.batchValues(file, ranges); complete(); return result;
            }
            public void write(String file, Map<String,List<List<?>>> values) throws IOException
            { stage = main.equals(file) ? Stage.WRITE_PUBLIC : privateStage(file, Stage.WRITE_FLEET_1, Stage.WRITE_FLEET_2); delegate.write(file, values); complete(); }
            public BattleshipSheets.Access privateAccess(String file) throws IOException
            { stage = privateStage(file, Stage.ACCESS_TEAM_1, Stage.ACCESS_TEAM_2); BattleshipSheets.Access result = delegate.privateAccess(file); complete(); return result; }
            public void protect(String file, Set<String> one, Set<String> two) throws IOException
            { stage = Stage.LOCK_PUBLIC; delegate.protect(file, one, two); complete(); }
            public void privateLocked(String file, boolean locked) throws IOException
            { stage = privateStage(file, Stage.LOCK_FLEET_1, Stage.LOCK_FLEET_2); delegate.privateLocked(file, locked); complete(); }
            public void sourceLocked(String file, boolean locked, boolean payment) throws IOException
            { stage = Stage.LOCK_SETTINGS; delegate.sourceLocked(file, locked, payment); complete(); }
        };
    }
}
