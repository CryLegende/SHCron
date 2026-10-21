import org.Cry_Legende.shcron.core.CronExpression;

import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Verification rapide du parseur cron. Lancer avec le jar shade dans le classpath. */
public class CronTest {

    static int ok = 0;
    static int ko = 0;

    static ZonedDateTime t(String iso) {
        return ZonedDateTime.of(java.time.LocalDateTime.parse(iso), ZoneId.of("Europe/Paris"));
    }

    static void expect(String expr, String time, boolean want) {
        boolean got = CronExpression.parse(expr).matches(t(time));
        if (got == want) {
            ok++;
        } else {
            ko++;
            System.out.println("  ECHEC  \"" + expr + "\" @ " + time + " -> attendu " + want + ", obtenu " + got);
        }
    }

    static void expectInvalid(String expr) {
        try {
            CronExpression.parse(expr);
            ko++;
            System.out.println("  ECHEC  \"" + expr + "\" aurait du etre rejetee");
        } catch (IllegalArgumentException e) {
            ok++;
        }
    }

    public static void main(String[] args) {
        // La tache de redemarrage reprise de MC-Cron
        expect("0 3 */4 * *", "2026-08-01T03:00", true);
        expect("0 3 */4 * *", "2026-08-05T03:00", true);
        expect("0 3 */4 * *", "2026-08-09T03:00", true);
        expect("0 3 */4 * *", "2026-08-02T03:00", false);  // mauvais jour
        expect("0 3 */4 * *", "2026-08-01T04:00", false);  // mauvaise heure
        expect("0 3 */4 * *", "2026-08-01T03:01", false);  // mauvaise minute

        // Macros
        expect("@daily",  "2026-08-12T00:00", true);
        expect("@daily",  "2026-08-12T00:01", false);
        expect("@hourly", "2026-08-12T17:00", true);
        expect("@hourly", "2026-08-12T17:30", false);
        expect("@weekly", "2026-08-16T00:00", true);   // 16/08/2026 = dimanche
        expect("@weekly", "2026-08-17T00:00", false);  // lundi

        // Listes, plages, pas
        expect("0,30 * * * *", "2026-08-12T10:30", true);
        expect("0,30 * * * *", "2026-08-12T10:15", false);
        expect("*/15 * * * *", "2026-08-12T10:45", true);
        expect("*/15 * * * *", "2026-08-12T10:46", false);
        expect("0 9-17 * * *", "2026-08-12T09:00", true);
        expect("0 9-17 * * *", "2026-08-12T17:00", true);
        expect("0 9-17 * * *", "2026-08-12T18:00", false);
        expect("0 0-20/10 * * *", "2026-08-12T10:00", true);
        expect("0 0-20/10 * * *", "2026-08-12T15:00", false);

        // Jours de semaine : 0 et 7 = dimanche ; noms courts
        expect("0 0 * * 0",   "2026-08-16T00:00", true);
        expect("0 0 * * 7",   "2026-08-16T00:00", true);
        expect("0 0 * * SUN", "2026-08-16T00:00", true);
        expect("0 0 * * MON", "2026-08-17T00:00", true);
        expect("0 0 * * MON", "2026-08-16T00:00", false);
        expect("0 0 * * 1-5", "2026-08-14T00:00", true);   // vendredi
        expect("0 0 * * 1-5", "2026-08-15T00:00", false);  // samedi

        // Mois par nom
        expect("0 0 1 AUG *", "2026-08-01T00:00", true);
        expect("0 0 1 AUG *", "2026-09-01T00:00", false);

        // Semantique Vixie : jour-du-mois ET jour-semaine restreints -> OU logique
        expect("0 0 1 * MON", "2026-08-01T00:00", true);   // le 1er, un samedi
        expect("0 0 1 * MON", "2026-08-17T00:00", true);   // un lundi, pas le 1er
        expect("0 0 1 * MON", "2026-08-18T00:00", false);  // ni l'un ni l'autre

        // Un seul des deux restreint -> ET logique classique
        expect("0 0 15 * *", "2026-08-15T00:00", true);
        expect("0 0 15 * *", "2026-08-16T00:00", false);

        // Entrees invalides
        expectInvalid("0 3 * *");           // 4 champs
        expectInvalid("60 3 * * *");        // minute hors bornes
        expectInvalid("0 24 * * *");        // heure hors bornes
        expectInvalid("0 3 * * FOO");       // jour inconnu
        expectInvalid("0 3 * * */0");       // pas nul

        System.out.println(ok + " test(s) OK, " + ko + " echec(s)");
        if (ko > 0) {
            System.exit(1);
        }
    }
}
