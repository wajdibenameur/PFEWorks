package tn.iteam.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tn.iteam.service.ZabbixCriticalHistorySyncResult;
import tn.iteam.service.ZabbixCriticalHistorySyncService;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminZabbixControllerWebMvcTest {

    @Mock
    private ZabbixCriticalHistorySyncService zabbixCriticalHistorySyncService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminZabbixController(zabbixCriticalHistorySyncService)).build();
    }

    @Test
    void syncCriticalHistoryReturnsFoundInsertedAndSkippedCounts() throws Exception {
        when(zabbixCriticalHistorySyncService.syncCriticalHistory())
                .thenReturn(new ZabbixCriticalHistorySyncResult(true, 7, 4, 2, 1, "critical history sync completed"));

        mockMvc.perform(post("/api/admin/zabbix/critical-history/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.source").value("ZABBIX"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.found").value(7))
                .andExpect(jsonPath("$.data.inserted").value(4))
                .andExpect(jsonPath("$.data.duplicatesIgnored").value(2))
                .andExpect(jsonPath("$.data.invalidIgnored").value(1));

        verify(zabbixCriticalHistorySyncService).syncCriticalHistory();
    }
}
