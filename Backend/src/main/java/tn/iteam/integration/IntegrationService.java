package tn.iteam.integration;

import tn.iteam.monitoring.MonitoringSourceType;

public interface IntegrationService {

    MonitoringSourceType getSourceType();

    void refresh();

    default void refreshHosts() {
        refresh();
    }

    default void refreshProblems() {
        refresh();
    }

    default void refreshMetrics() {
        refresh();
    }

    default void refreshAttendance() {
        refresh();
    }
    default void refreshAllAndPublish() {
        refresh();
    }

}
