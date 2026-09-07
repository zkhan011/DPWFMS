package com.dpworld.fms.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TrackItTelemetryRetentionScheduler {
  private final JdbcTemplate jdbc;
  private final int retentionDays;
  public TrackItTelemetryRetentionScheduler(JdbcTemplate jdbc,
      @Value("${trackit.telemetry.retention-days:90}") int retentionDays) {
    if (retentionDays < 1) throw new IllegalStateException("TrackIT telemetry retention must be at least one day");
    this.jdbc = jdbc; this.retentionDays = retentionDays;
  }
  @Scheduled(cron = "${trackit.telemetry.retention-cron:0 15 2 * * *}")
  @Transactional
  public void purgeHistory() {
    jdbc.update("""
        DELETE FROM trackit_telemetry_history h
        WHERE h.measurement_timestamp < now()-(?*interval '1 day')
          AND NOT EXISTS (SELECT 1 FROM trackit_telemetry_latest l WHERE l.telemetry_id=h.id)
        """, retentionDays);
  }
}
