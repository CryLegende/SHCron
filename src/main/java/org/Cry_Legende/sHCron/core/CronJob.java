package org.Cry_Legende.sHCron.core;

import java.util.List;

/**
 * Une tache planifiee : un nom, une expression cron, des commandes.
 */
public record CronJob(String name, boolean enabled, CronExpression schedule, List<String> commands) {

    public CronJob {
        commands = List.copyOf(commands);
    }
}
