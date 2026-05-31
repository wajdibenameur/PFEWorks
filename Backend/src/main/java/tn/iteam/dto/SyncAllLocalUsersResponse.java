package tn.iteam.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SyncAllLocalUsersResponse {
    int totalKeycloakUsers;
    int synchronizedUsers;
    int createdUsers;
    int updatedUsers;
    int skippedUsers;
    int failedUsers;
}
