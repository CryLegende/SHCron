package org.Cry_Legende.sHCron.core;

import java.util.List;

/**
 * Une tache planifiee : un nom, une expression cron, des commandes.
 * Sans expression (cron: manual), la tache ne part que par /shcron run.
 */
public record CronJob(String name, boolean enabled, CronExpression schedule, List<String> commands) {

    public CronJob {
        commands = List.copyOf(commands);
    }

    public boolean manual() {
        return schedule == null;
    }

    /**
     * L'expression cron telle qu'elle s'affiche, "manual" pour une tache sans horaire.
     */
    public String scheduleText() {
        return schedule == null ? "manual" : String.valueOf(schedule);
    }
}
