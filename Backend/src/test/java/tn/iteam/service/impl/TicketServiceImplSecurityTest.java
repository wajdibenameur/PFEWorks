package tn.iteam.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tn.iteam.domain.Ticket;
import tn.iteam.domain.User;
import tn.iteam.repository.InterventionRepository;
import tn.iteam.repository.TicketRepository;
import tn.iteam.repository.UserRepository;
import tn.iteam.security.AuthenticatedUserService;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceImplSecurityTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private InterventionRepository interventionRepository;

    @Mock
    private SimpMessagingTemplate ws;

    @Mock
    private AuthenticatedUserService authenticatedUserService;

    @InjectMocks
    private TicketServiceImpl ticketService;

    @Test
    void createManualUsesAuthenticatedUserAsCreator() {
        User currentUser = new User();
        currentUser.setUsername("current.user");
        when(authenticatedUserService.getCurrentUser()).thenReturn(currentUser);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Ticket ticket = Ticket.builder()
                .title("Ticket")
                .description("Secure create")
                .interventions(new ArrayList<>())
                .build();

        Ticket created = ticketService.createManual(ticket);

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedBy()).isSameAs(currentUser);
        assertThat(created.getCreatedBy()).isSameAs(currentUser);
    }
}
