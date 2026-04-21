package tn.iteam.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.iteam.service.ObserviumSummaryService;

import java.util.Map;

@RestController
@RequestMapping("/api/observium")
@RequiredArgsConstructor
/**
 * Temporary compatibility controller for Observium-specific API consumers.
 *
 * Keep this endpoint until frontend and external consumers are confirmed to be
 * fully migrated away from {@code /api/observium/*}. The summary is already
 * computed from the unified monitoring aggregation flow.
 *
 * Once compatibility is no longer needed, this controller can be moved to
 * {@code depl}.
 */
public class ObserviumController {

    private final ObserviumSummaryService observiumSummaryService;

    @GetMapping("/summary")
    public Map<String, Long> getSummary() {
        return observiumSummaryService.getSummary();
    }
}
