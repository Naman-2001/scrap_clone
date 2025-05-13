package tech.dcluttr.dcluttrscrapper.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
@Slf4j
public class ClickHouseRepository {

    private final JdbcTemplate clickHouseJdbcTemplate;

    /**
     * Example method to execute a simple query
     */
    public List<Map<String, Object>> executeQuery(String sql) {
        log.info("Executing ClickHouse query: {}", sql);
        return clickHouseJdbcTemplate.queryForList(sql);
    }

    /**
     * Example method for batch insert
     */
    public int[] batchInsert(String sql, List<Object[]> batchArgs) {
        log.info("Executing ClickHouse batch insert with {} records", batchArgs.size());
        return clickHouseJdbcTemplate.batchUpdate(sql, batchArgs);
    }

    /**
     * Example method to check database connectivity
     */
    public boolean isConnected() {
        try {
            clickHouseJdbcTemplate.execute("SELECT 1");
            return true;
        } catch (Exception e) {
            log.error("Failed to connect to ClickHouse", e);
            return false;
        }
    }
} 