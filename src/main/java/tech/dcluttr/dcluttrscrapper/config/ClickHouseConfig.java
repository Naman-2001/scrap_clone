package tech.dcluttr.dcluttrscrapper.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
public class ClickHouseConfig {

    @Value("${clickhouse.jdbc.url}")
    private String jdbcUrl;

    @Value("${clickhouse.jdbc.username}")
    private String username;

    @Value("${clickhouse.jdbc.password}")
    private String password;

    @Value("${clickhouse.jdbc.driver-class-name}")
    private String driverClassName;

    @Value("${clickhouse.hikari.connection-timeout:20000}")
    private int connectionTimeout;

    @Value("${clickhouse.hikari.minimum-idle:5}")
    private int minimumIdle;

    @Value("${clickhouse.hikari.maximum-pool-size:20}")
    private int maximumPoolSize;

    @Value("${clickhouse.hikari.idle-timeout:300000}")
    private int idleTimeout;

    @Value("${clickhouse.hikari.max-lifetime:1200000}")
    private int maxLifetime;

    @Value("${clickhouse.hikari.auto-commit:true}")
    private boolean autoCommit;

    @Bean
    public DataSource clickHouseDataSource() {
        HikariConfig hikariConfig = new HikariConfig();
        
        // JDBC Properties
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setDriverClassName(driverClassName);
        
        // HikariCP Properties
        hikariConfig.setConnectionTimeout(connectionTimeout);
        hikariConfig.setMinimumIdle(minimumIdle);
        hikariConfig.setMaximumPoolSize(maximumPoolSize);
        hikariConfig.setIdleTimeout(idleTimeout);
        hikariConfig.setMaxLifetime(maxLifetime);
        hikariConfig.setAutoCommit(autoCommit);
        
        // ClickHouse JDBC Properties
        Properties props = new Properties();
        props.setProperty("socket_timeout", "300000"); // 5 minutes
        props.setProperty("compress", "true");
        props.setProperty("decompress", "true");
        props.setProperty("disable_frameworks_detection", "true");
        hikariConfig.setDataSourceProperties(props);
        
        return new HikariDataSource(hikariConfig);
    }

    @Bean
    public JdbcTemplate clickHouseJdbcTemplate(DataSource clickHouseDataSource) {
        return new JdbcTemplate(clickHouseDataSource);
    }
} 