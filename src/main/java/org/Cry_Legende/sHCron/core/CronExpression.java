package org.Cry_Legende.sHCron.core;

import java.time.ZonedDateTime;
import java.util.BitSet;
import java.util.Locale;

/**
 * Expression cron a 5 champs : minute heure jour-du-mois mois jour-semaine.
 * <p>
 * Syntaxe acceptee par champ : *, n, a-b, a-b/s, *&#47;s, et listes separees par
 * des virgules. Les macros @hourly @daily @midnight @weekly @monthly @yearly
 * sont acceptees.
 * <p>
 * Jour de semaine : 0 ou 7 = dimanche. Les noms courts (MON, TUE, ...) et les
 * noms de mois (JAN, FEB, ...) sont acceptes.
 * <p>
 * Semantique Vixie pour les jours : si jour-du-mois ET jour-semaine sont tous
 * deux restreints, l'expression correspond des que l'UN des deux correspond.
 */
public final class CronExpression {

    private static final String[] MONTHS = {
        "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
        "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"
    };
    private static final String[] DAYS = {
        "SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"
    };

    private final String source;
    private final BitSet minutes = new BitSet(60);
    private final BitSet hours = new BitSet(24);
    private final BitSet daysOfMonth = new BitSet(32);
    private final BitSet months = new BitSet(13);
    private final BitSet daysOfWeek = new BitSet(8);
    private final boolean domRestricted;
    private final boolean dowRestricted;

    private CronExpression(String source, String[] f) {
        this.source = source;
        parseField(f[0], 0, 59, minutes, null);
        parseField(f[1], 0, 23, hours, null);
        parseField(f[2], 1, 31, daysOfMonth, null);
        parseField(f[3], 1, 12, months, MONTHS);
        parseField(f[4], 0, 7, daysOfWeek, DAYS);
        // dimanche accepte sous les deux formes
        if (daysOfWeek.get(7)) {
            daysOfWeek.set(0);
        }
        this.domRestricted = !"*".equals(f[2].trim());
        this.dowRestricted = !"*".equals(f[4].trim());
    }

    public static CronExpression parse(String expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cron vide");
        }
        String expr = expression.trim();

        String macro = switch (expr.toLowerCase(Locale.ROOT)) {
            case "@yearly", "@annually" -> "0 0 1 1 *";
            case "@monthly" -> "0 0 1 * *";
            case "@weekly" -> "0 0 * * 0";
            case "@daily", "@midnight" -> "0 0 * * *";
            case "@hourly" -> "0 * * * *";
            default -> null;
        };
        if (macro != null) {
            expr = macro;
        }

        String[] fields = expr.split("\\s+");
        if (fields.length != 5) {
            throw new IllegalArgumentException(
                "il faut 5 champs (minute heure jour-du-mois mois jour-semaine), recu "
                    + fields.length + " dans \"" + expression + "\"");
        }
        return new CronExpression(expression, fields);
    }

    private static void parseField(String field, int min, int max, BitSet out, String[] names) {
        for (String part : field.split(",")) {
            parsePart(part.trim(), min, max, out, names, field);
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException("champ cron sans valeur : \"" + field + "\"");
        }
    }

    private static void parsePart(String part, int min, int max, BitSet out, String[] names, String field) {
        int step = 1;
        int slash = part.indexOf('/');
        if (slash >= 0) {
            step = parseInt(part.substring(slash + 1), field);
            if (step < 1) {
                throw new IllegalArgumentException("pas invalide dans \"" + field + "\"");
            }
            part = part.substring(0, slash);
        }

        int from;
        int to;
        if ("*".equals(part)) {
            from = min;
            to = max;
        } else {
            int dash = part.indexOf('-');
            if (dash > 0) {
                from = parseValue(part.substring(0, dash), names, field);
                to = parseValue(part.substring(dash + 1), names, field);
            } else {
                from = parseValue(part, names, field);
                // "5/2" sans borne haute veut dire "de 5 jusqu'au maximum"
                to = (slash >= 0) ? max : from;
            }
        }

        if (from < min || to > max || from > to) {
            throw new IllegalArgumentException(
                "valeur hors bornes dans \"" + field + "\" (attendu " + min + "-" + max + ")");
        }
        for (int i = from; i <= to; i += step) {
            out.set(i);
        }
    }

    private static int parseValue(String token, String[] names, String field) {
        String t = token.trim();
        if (names != null) {
            String upper = t.toUpperCase(Locale.ROOT);
            for (int i = 0; i < names.length; i++) {
                if (names[i].equals(upper)) {
                    // les mois commencent a 1, les jours de semaine a 0
                    return (names == MONTHS) ? i + 1 : i;
                }
            }
        }
        return parseInt(t, field);
    }

    private static int parseInt(String token, String field) {
        try {
            return Integer.parseInt(token.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("valeur illisible \"" + token + "\" dans \"" + field + "\"");
        }
    }

    /**
     * Vrai si l'instant donne (a la minute pres) correspond a l'expression.
     */
    public boolean matches(ZonedDateTime time) {
        if (!minutes.get(time.getMinute())) return false;
        if (!hours.get(time.getHour())) return false;
        if (!months.get(time.getMonthValue())) return false;

        boolean domHit = daysOfMonth.get(time.getDayOfMonth());
        // DayOfWeek.getValue() : lundi=1 .. dimanche=7 ; cron veut dimanche=0
        boolean dowHit = daysOfWeek.get(time.getDayOfWeek().getValue() % 7);

        if (domRestricted && dowRestricted) {
            return domHit || dowHit;
        }
        return domHit && dowHit;
    }

    @Override
    public String toString() {
        return source;
    }
}
