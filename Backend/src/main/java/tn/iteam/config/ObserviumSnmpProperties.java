package tn.iteam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "observium.snmp")
public class ObserviumSnmpProperties {

    private int timeoutMs = 1500;
    private int retries = 0;
    private int maxWorkers = 32;
    private int defaultPort = 161;
    private String defaultCommunity = "public";
    private String defaultVersion = "2c";
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

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getRetries() {
        return retries;
    }

    public void setRetries(int retries) {
        this.retries = retries;
    }

    public int getMaxWorkers() {
        return maxWorkers;
    }

    public void setMaxWorkers(int maxWorkers) {
        this.maxWorkers = maxWorkers;
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
}
