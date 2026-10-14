package dev.securebank.scheduled;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;

@Component
@ConditionalOnProperty(name="securebank.jobs-enabled",havingValue="true",matchIfMissing=true)
public class ScheduleJob {
    private static final Logger log=LoggerFactory.getLogger(ScheduleJob.class);
    private final JdbcTemplate jdbc;
    private final ScheduleExecutor executor;
    public ScheduleJob(JdbcTemplate jdbc,ScheduleExecutor executor) { this.jdbc=jdbc; this.executor=executor; }
    @Scheduled(fixedDelayString="${securebank.schedule-interval-ms:15000}")
    public void run() {
        var ids=jdbc.queryForList("SELECT id FROM scheduled_transfers WHERE status='ACTIVE' AND next_run_at<=now() AND (next_attempt_at IS NULL OR next_attempt_at<=now()) ORDER BY next_run_at LIMIT 20",UUID.class);
        for(UUID id:ids) { try { executor.execute(id); } catch(Exception error) { log.warn("Schedule worker failed for {}: {}",id,error.getClass().getSimpleName()); } }
    }
}
