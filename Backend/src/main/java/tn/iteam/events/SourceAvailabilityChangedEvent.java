package tn.iteam.events;

import tn.iteam.dto.SourceAvailabilityDTO;

public record SourceAvailabilityChangedEvent(SourceAvailabilityDTO payload) {
}
