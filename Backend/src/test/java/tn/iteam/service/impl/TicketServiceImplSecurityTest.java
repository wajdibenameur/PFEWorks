package tn.iteam.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tn.iteam.domain.Role;
import tn.iteam.domain.Ticket;
import tn.iteam.domain.User;
import tn.iteam.dto.TicketResponseDTO;
import tn.iteam.dto.ZabbixProblemDTO;
import tn.iteam.enums.RoleName;
import tn.iteam.repository.InterventionRepository;
import tn.iteam.repository.RoleRepository;
import tn.iteam.repository.TicketRepository;
import tn.iteam.repository.UserRepository;
import tn.iteam.mapper.TicketMapper;
import tn.iteam.security.AuthenticatedUserService;
import tn.iteam.service.support.TicketNotificationService;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceImplSecurityTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private InterventionRepository interventionRepository;

    @Mock
    private SimpMessagingTemplate ws;

    @Mock
    private AuthenticatedUserService authenticatedUserService;

    @Mock
    private TicketMapper ticketMapper;

    @Mock
    private TicketNotificationService ticketNotificationService;

    @InjectMocks
    private TicketServiceImpl ticketService;

    @Test
    void createManualUsesAuthenticatedUserAsCreator() {
        User currentUser = new User();
        currentUser.setUsername("current.user");
        when(authenticatedUserService.getCurrentUser()).thenReturn(currentUser);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ticketMapper.toResponse(any(Ticket.class))).thenReturn(TicketResponseDTO.builder().id(1L).build());

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

    @Test
    @SuppressWarnings("deprecation")
    void createFromProblemCreatesSystemUserWithoutPasswordPlaceholder() {
        doThrow(new RuntimeException("no authenticated user")).when(authenticatedUserService).getCurrentUser();
        Role systemRole = new Role();
        systemRole.setName(RoleName.SYSTEM);
        when(roleRepository.findByName(RoleName.SYSTEM)).thenReturn(Optional.of(systemRole));
        when(userRepository.findByUsername("SYSTEM")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ticketRepository.findByMonitoringSourceAndExternalProblemId("ZABBIX", "problem-1")).thenReturn(Optional.empty());
        when(ticketRepository.findFirstByMonitoringSourceAndResourceRefAndTitleAndArchivedFalseOrderByCreationDateDesc(
                "ZABBIX",
                null,
                "Critical alert"
        )).thenReturn(Optional.empty());
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ticketMapper.toResponse(any(Ticket.class))).thenReturn(TicketResponseDTO.builder().id(2L).build());

        ZabbixProblemDTO problem = new ZabbixProblemDTO();
        problem.setSource("ZABBIX");
        problem.setProblemId("problem-1");
        problem.setDescription("Critical alert");
        problem.setSeverity("5");

        Ticket created = ticketService.createFromProblem(problem);

        assertThat(created.getCreatedBy()).isNotNull();
        assertThat(created.getCreatedBy().getUsername()).isEqualTo("SYSTEM");
        assertThat(created.getCreatedBy().getEmail()).isEqualTo("system@monitoring.local");
        assertThat(created.getCreatedBy().getPassword()).isNull();
    }
}
