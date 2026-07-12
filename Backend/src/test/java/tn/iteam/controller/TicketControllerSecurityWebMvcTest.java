package tn.iteam.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import tn.iteam.config.SecurityConfig;
import tn.iteam.domain.Ticket;
import tn.iteam.dto.TicketResponseDTO;
import tn.iteam.mapper.TicketMapper;
import tn.iteam.security.KeycloakJwtAuthenticationConverter;
import tn.iteam.security.KeycloakRolePermissionService;
import tn.iteam.security.EffectiveUserPermissionService;
import tn.iteam.security.PermissionService;
import tn.iteam.service.TicketService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketController.class)
@Import({
        SecurityConfig.class,
        KeycloakRolePermissionService.class,
        PermissionService.class,
        TicketMapper.class
})
class TicketControllerSecurityWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TicketService ticketService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter;

    @MockBean
    private EffectiveUserPermissionService effectiveUserPermissionService;

    @Test
    void endpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void viewerCannotCreateTicket() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .with(jwt().authorities(() -> "VIEW_TICKETS"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Unauthorized create",
                                  "description": "viewer should not create",
                                  "priority": "HIGH"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTicketIgnoresInjectedCreatorIdFromFrontend() throws Exception {
        Ticket created = Ticket.builder()
                .title("Secure ticket")
                .description("Created from authenticated identity")
                .build();
        created.setId(11L);
        when(ticketService.createManual(any())).thenReturn(created);

        mockMvc.perform(post("/api/tickets")
                        .with(jwt().authorities(() -> "CREATE_TICKET"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Secure ticket",
                                  "description": "Created from authenticated identity",
                                  "priority": "HIGH",
                                  "creatorId": 9999
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketService).createManual(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Secure ticket");
        assertThat(captor.getValue().getDescription()).isEqualTo("Created from authenticated identity");
        assertThat(captor.getValue().getCreatedBy()).isNull();
    }

    @Test
    void userWithViewTicketsPermissionCanListTickets() throws Exception {
        when(ticketService.search(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/tickets")
                        .with(jwt().authorities(() -> "VIEW_TICKETS")))
                .andExpect(status().isOk());
    }
}
