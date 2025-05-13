package tech.dcluttr.dcluttrscrapper.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClickHouseHealthIndicator implements HealthIndicator {

    private final JdbcTemplate clickHouseJdbcTemplate;

    @Override
    public Health health() {
        try {
            clickHouseJdbcTemplate.execute("SELECT 1");
            return Health.up()
                    .withDetail("database", "ClickHouse")
                    .withDetail("status", "Connected")
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("database", "ClickHouse")
                    .withDetail("status", "Disconnected")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
} 