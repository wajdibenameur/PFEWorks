package tn.iteam.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class HikariPoolMetricsLogger {

    private static final Logger log = LoggerFactory.getLogger(HikariPoolMetricsLogger.class);
    private final DataSource dataSource;

    public HikariPoolMetricsLogger(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Scheduled(fixedDelayString = "${app.hikari.metrics-log-interval-ms:30000}")
    public void logPoolMetrics() {
        if (!(dataSource instanceof HikariDataSource hikari)) {
            return;
        }
        var mxBean = hikari.getHikariPoolMXBean();
        if (mxBean == null) {
            return;
        }

        log.info(
                "Hikari pool metrics active={} idle={} pending={} total={} max={}",
                mxBean.getActiveConnections(),
                mxBean.getIdleConnections(),
                mxBean.getThreadsAwaitingConnection(),
                mxBean.getTotalConnections(),
                hikari.getMaximumPoolSize()
        );
    }
}
