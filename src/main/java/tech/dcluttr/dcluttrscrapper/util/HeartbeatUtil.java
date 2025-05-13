package tech.dcluttr.dcluttrscrapper.util;

import io.temporal.activity.Activity;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public class HeartbeatUtil {
    
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    
    public static ScheduledFuture<?> startHeartbeat(Map<String, Object> details, int intervalSeconds) {
        return scheduler.scheduleAtFixedRate(() -> {
            try {
                if (details != null) {
                    Activity.getExecutionContext().heartbeat(details);
                    log.debug("Sent heartbeat with details: {}", details);
                } else {
                    // If details are null, send an empty map as fallback
                    Activity.getExecutionContext().heartbeat(Map.of("status", "heartbeat"));
                    log.debug("Sent heartbeat with default details");
                }
            } catch (Exception e) {
                log.warn("Failed to send heartbeat: {}", e.getMessage());
            }
        }, 0, intervalSeconds, TimeUnit.SECONDS);
    }
    
    public static void stopHeartbeat(ScheduledFuture<?> heartbeatTask) {
        if (heartbeatTask != null && !heartbeatTask.isCancelled()) {
            heartbeatTask.cancel(false);
            log.debug("Stopped heartbeat task");
        }
    }
} 