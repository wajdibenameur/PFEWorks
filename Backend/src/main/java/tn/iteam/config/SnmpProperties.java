package tn.iteam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "snmp")
public class SnmpProperties {

    private boolean enabled = true;
    private Duration timeout = Duration.ofSeconds(2);
    private Duration executionTimeout = Duration.ofSeconds(7);
    private int retries = 0;
    private int batchSize = 20;
    private long pollingInterval = 60000L;
    private int defaultPort = 161;
    private String defaultCommunity = "public";
    private String defaultVersion = "2c";
    private int defaultPollingIntervalSeconds = 60;
    private Set<String> defaultMetricsToPoll = new LinkedHashSet<>(List.of("SYSTEM", "INTERFACES", "CATEGORY"));
    private List<String> seedAddresses = new ArrayList<>();
    private List<String> seedRanges = new ArrayList<>();
    private List<String> excludedSubnets = new ArrayList<>();
    private List<String> switchesSubnets = new ArrayList<>();
    private List<String> routersSubnets = new ArrayList<>();
    private List<String> firewallsSubnets = new ArrayList<>();
    private List<String> loadBalancersSubnets = new ArrayList<>();
    private List<String> wifiAccessPointsSubnets = new ArrayList<>();
    private List<String> networkControllersSubnets = new ArrayList<>();
    private List<String> printersSubnets = new ArrayList<>();
    private List<String> serverAddresses = new ArrayList<>();
    private List<String> firewallAddresses = new ArrayList<>();
    private List<String> upsAddresses = new ArrayList<>();
    private List<String> accessControlAddresses = new ArrayList<>();
    private ThreadPool thread = new ThreadPool();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeout = Duration.ofMillis(Math.max(1, timeoutMs));
    }

    public Duration getExecutionTimeout() {
        return executionTimeout;
    }

    public void setExecutionTimeout(Duration executionTimeout) {
        this.executionTimeout = executionTimeout;
    }

    public int getTimeoutMs() {
        return (int) Math.max(1L, timeout != null ? timeout.toMillis() : Duration.ofSeconds(2).toMillis());
    }

    public int getRetries() {
        return retries;
    }

    public void setRetries(int retries) {
        this.retries = retries;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getPollingInterval() {
        return pollingInterval;
    }

    public void setPollingInterval(long pollingInterval) {
        this.pollingInterval = pollingInterval;
    }

    public int getDefaultPort() {
        return defaultPort;
    }

    public void setDefaultPort(int defaultPort) {
        this.defaultPort = defaultPort;
    }

    public String getDefaultCommunity() {
        return defaultCommunity;
    }

    public void setDefaultCommunity(String defaultCommunity) {
        this.defaultCommunity = defaultCommunity;
    }

    public String getDefaultVersion() {
        return defaultVersion;
    }

    public void setDefaultVersion(String defaultVersion) {
        this.defaultVersion = defaultVersion;
    }

    public int getDefaultPollingIntervalSeconds() {
        return defaultPollingIntervalSeconds;
    }

    public void setDefaultPollingIntervalSeconds(int defaultPollingIntervalSeconds) {
        this.defaultPollingIntervalSeconds = defaultPollingIntervalSeconds;
    }

    public Set<String> getDefaultMetricsToPoll() {
        return defaultMetricsToPoll;
    }

    public void setDefaultMetricsToPoll(Set<String> defaultMetricsToPoll) {
        this.defaultMetricsToPoll = defaultMetricsToPoll;
    }

    public List<String> getSeedAddresses() {
        return seedAddresses;
    }

    public void setSeedAddresses(List<String> seedAddresses) {
        this.seedAddresses = seedAddresses;
    }

    public List<String> getSeedRanges() {
        return seedRanges;
    }

    public void setSeedRanges(List<String> seedRanges) {
        this.seedRanges = seedRanges;
    }

    public List<String> getExcludedSubnets() {
        return excludedSubnets;
    }

    public void setExcludedSubnets(List<String> excludedSubnets) {
        this.excludedSubnets = excludedSubnets;
    }

    public List<String> getSwitchesSubnets() {
        return switchesSubnets;
    }

    public void setSwitchesSubnets(List<String> switchesSubnets) {
        this.switchesSubnets = switchesSubnets;
    }

    public List<String> getRoutersSubnets() {
        return routersSubnets;
    }

    public void setRoutersSubnets(List<String> routersSubnets) {
        this.routersSubnets = routersSubnets;
    }

    public List<String> getFirewallsSubnets() {
        return firewallsSubnets;
    }

    public void setFirewallsSubnets(List<String> firewallsSubnets) {
        this.firewallsSubnets = firewallsSubnets;
    }

    public List<String> getLoadBalancersSubnets() {
        return loadBalancersSubnets;
    }

    public void setLoadBalancersSubnets(List<String> loadBalancersSubnets) {
        this.loadBalancersSubnets = loadBalancersSubnets;
    }

    public List<String> getWifiAccessPointsSubnets() {
        return wifiAccessPointsSubnets;
    }

    public void setWifiAccessPointsSubnets(List<String> wifiAccessPointsSubnets) {
        this.wifiAccessPointsSubnets = wifiAccessPointsSubnets;
    }

    public List<String> getNetworkControllersSubnets() {
        return networkControllersSubnets;
    }

    public void setNetworkControllersSubnets(List<String> networkControllersSubnets) {
        this.networkControllersSubnets = networkControllersSubnets;
    }

    public List<String> getPrintersSubnets() {
        return printersSubnets;
    }

    public void setPrintersSubnets(List<String> printersSubnets) {
        this.printersSubnets = printersSubnets;
    }

    public List<String> getServerAddresses() {
        return serverAddresses;
    }

    public void setServerAddresses(List<String> serverAddresses) {
        this.serverAddresses = serverAddresses;
    }

    public List<String> getFirewallAddresses() {
        return firewallAddresses;
    }

    public void setFirewallAddresses(List<String> firewallAddresses) {
        this.firewallAddresses = firewallAddresses;
    }

    public List<String> getUpsAddresses() {
        return upsAddresses;
    }

    public void setUpsAddresses(List<String> upsAddresses) {
        this.upsAddresses = upsAddresses;
    }

    public List<String> getAccessControlAddresses() {
        return accessControlAddresses;
    }

    public void setAccessControlAddresses(List<String> accessControlAddresses) {
        this.accessControlAddresses = accessControlAddresses;
    }

    public ThreadPool getThread() {
        return thread;
    }

    public void setThread(ThreadPool thread) {
        this.thread = thread;
    }

    public int getMaxWorkers() {
        return resolveMaxPoolSize();
    }

    public void setMaxWorkers(int maxWorkers) {
        int normalized = Math.max(1, maxWorkers);
        this.thread.setCorePoolSize(normalized);
        this.thread.setMaxPoolSize(normalized);
    }

    public int resolveCorePoolSize() {
        return Math.max(1, thread != null ? thread.getCorePoolSize() : 8);
    }

    public int resolveMaxPoolSize() {
        return Math.max(resolveCorePoolSize(), thread != null ? thread.getMaxPoolSize() : 16);
    }

    public int resolveQueueCapacity() {
        return Math.max(1, thread != null ? thread.getQueueCapacity() : 200);
    }

    public static class ThreadPool {
        private int corePoolSize = 8;
        private int maxPoolSize = 16;
        private int queueCapacity = 200;

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }
    }
}
