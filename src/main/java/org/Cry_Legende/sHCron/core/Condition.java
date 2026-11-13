package org.Cry_Legende.sHCron.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Condition d'une directive "until N: gauche operateur droite".
 * Les deux cotes sont compares en nombres quand ils en sont, sinon en texte (== et != seulement).
 */
public record Condition(long timeoutSeconds, String left, String operator, String right) {

    /**
     * "until 300: %crazyenvoys_envoys_left% == 0". L'operateur est entoure d'espaces, la droite peut etre vide.
     */
    private static final Pattern UNTIL = Pattern.compile(
        "^until\\s+(\\d+)\\s*:\\s*(.*?)\\s+(==|!=|<=|>=|<|>)(?:\\s+(.*))?$", Pattern.CASE_INSENSITIVE);

    /**
     * @return la condition lue, ou null si la ligne n'est pas une directive "until"
     */
    public static Condition parse(String line, long maxSeconds) {
        Matcher m = UNTIL.matcher(line.trim());
        if (!m.matches()) {
            return null;
        }
        long timeout;
        try {
            timeout = Math.min(Long.parseLong(m.group(1)), maxSeconds);
        } catch (NumberFormatException e) {
            timeout = maxSeconds;
        }
        return new Condition(timeout, m.group(2).trim(), m.group(3), m.group(4) == null ? "" : m.group(4).trim());
    }

    /**
     * Compare les deux cotes une fois leurs placeholders resolus.
     */
    public boolean test(String resolvedLeft, String resolvedRight) {
        String a = resolvedLeft.trim();
        String b = resolvedRight.trim();
        Double x = number(a);
        Double y = number(b);
        if (x != null && y != null) {
            int order = Double.compare(x, y);
            return switch (operator) {
                case "==" -> order == 0;
                case "!=" -> order != 0;
                case "<" -> order < 0;
                case "<=" -> order <= 0;
                case ">" -> order > 0;
                default -> order >= 0;
            };
        }
        return switch (operator) {
            case "==" -> a.equalsIgnoreCase(b);
            case "!=" -> !a.equalsIgnoreCase(b);
            default -> false;
        };
    }

    private static Double number(String text) {
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(text.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return left + " " + operator + " " + right;
    }
}
