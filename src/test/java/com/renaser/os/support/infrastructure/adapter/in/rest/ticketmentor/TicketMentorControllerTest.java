package com.renaser.os.support.infrastructure.adapter.in.rest.ticketmentor;

import com.renaser.os.RelojDePrueba;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.application.ports.out.ticketmentor.AprendicesDelAcompanantePort;
import com.renaser.os.support.application.ports.out.ticketmentor.BuscarBibliotecaPort;
import com.renaser.os.support.application.ports.out.ticketmentor.LoadTicketMentorPort;
import com.renaser.os.support.application.ports.out.ticketmentor.SaveTicketMentorPort;
import com.renaser.os.support.application.services.TicketMentorService;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentor;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentorId;
import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los tickets de mentoría por HTTP, con el servicio REAL detrás (con el servicio simulado no se vería a quién se le consulta qué): el
 * aprendiz ve los suyos, un mentor o una guía los de los aprendices que acompaña hoy, y ADMIN, ALCHEMIST y MENTOR_LEAD todos por la vista
 * de plataforma. Quien manda es el servicio; el permiso del endpoint lo satisface más de un rol.
 */
@WebMvcTest({TicketMentorController.class, TicketMentorAdminController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import({TicketMentorService.class, RelojDePrueba.class})
class TicketMentorControllerTest {

    private static final Instant AHORA = Instant.parse("2026-11-12T15:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserSummaryFinder userSummaryFinder;
    @MockitoBean
    private LoadTicketMentorPort cargaTickets;
    @MockitoBean
    private SaveTicketMentorPort guardaTickets;
    @MockitoBean
    private BuscarBibliotecaPort biblioteca;
    @MockitoBean
    private ParticipacionProgramaFinder participaciones;
    @MockitoBean
    private AprendicesDelAcompanantePort acompanamiento;
    @MockitoBean
    private AlcanceDeFormacionFinder formaciones;
    @Autowired
    private RelojDePrueba reloj;
    @MockitoBean
    private IdGenerator ids;

    private final UserId ana = UserId.of(UUID.randomUUID());
    private final UserId luis = UserId.of(UUID.randomUUID());
    private TicketMentor deAna;
    private TicketMentor deLuis;

    @BeforeEach
    void preparar() {
        reloj.fijar(AHORA);
        deAna = TicketMentor.abrir(TicketMentorId.of(UUID.randomUUID()), ana, "Bloqueo de Ana", "Intento", "Impacto", FixedClock.at(AHORA));
        deLuis = TicketMentor.abrir(TicketMentorId.of(UUID.randomUUID()), luis, "Bloqueo de Luis", "Intento", "Impacto", FixedClock.at(AHORA));
        // la base tiene los dos; lo que cambia es a quién se le pregunta
        when(cargaTickets.todos(any(), anyInt())).thenReturn(List.of(deAna, deLuis));
        when(cargaTickets.porParticipante(eq(ana), any(), anyInt())).thenReturn(List.of(deAna));
    }

    private UUID cuenta(UserRole rol, UserStatus estado) {
        UUID id = UUID.randomUUID();
        when(userSummaryFinder.findById(UserId.of(id))).thenReturn(Optional.of(new UserSummary(UserId.of(id), "Alguien", null, rol, estado)));
        return id;
    }

    @ParameterizedTest(name = "{0} ve solo los tickets de los aprendices que acompaña")
    @EnumSource(value = UserRole.class, names = {"MENTOR", "TRAINEE_GUIDE"})
    @DisplayName("D19: GET /api/v1/tickets de un mentor o una guía trae solo los tickets de los aprendices que acompaña hoy")
    void mentorYGuiaVenSoloLosDeSusAprendices(UserRole rol) throws Exception {
        UUID acompanante = cuenta(rol, UserStatus.ACTIVE);
        when(acompanamiento.aprendicesQueAcompana(UserId.of(acompanante), AHORA)).thenReturn(Set.of(ana));
        when(cargaTickets.deParticipantes(eq(Set.of(ana)), any(), anyInt())).thenReturn(List.of(deAna));

        mockMvc.perform(get("/api/v1/tickets").header("X-Actor-Id", acompanante.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickets.length()").value(1))
                .andExpect(jsonPath("$.tickets[0].traineeProfileId").value(ana.value().toString()))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
        verify(cargaTickets, never()).todos(any(), anyInt());
    }

    @Test
    @DisplayName("D19: un mentor que no acompaña a nadie recibe una lista vacía, no los tickets de la plataforma")
    void mentorSinAprendicesRecibeVacio() throws Exception {
        UUID mentor = cuenta(UserRole.MENTOR, UserStatus.ACTIVE);
        when(acompanamiento.aprendicesQueAcompana(UserId.of(mentor), AHORA)).thenReturn(Set.of());

        mockMvc.perform(get("/api/v1/tickets").header("X-Actor-Id", mentor.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickets.length()").value(0));
        verify(cargaTickets, never()).todos(any(), anyInt());
    }

    @Test
    @DisplayName("el aprendiz sigue viendo los suyos")
    void aprendizVeLosSuyos() throws Exception {
        when(userSummaryFinder.findById(ana)).thenReturn(Optional.of(new UserSummary(ana, "Ana", null, UserRole.TRAINEE, UserStatus.ACTIVE)));

        mockMvc.perform(get("/api/v1/tickets").header("X-Actor-Id", ana.value().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickets.length()").value(1))
                .andExpect(jsonPath("$.tickets[0].blockDescription").value("Bloqueo de Ana"));
        verifyNoInteractions(acompanamiento);
    }

    @ParameterizedTest(name = "{0} ve todos los tickets por la vista de plataforma")
    @EnumSource(value = UserRole.class, names = {"ADMIN", "ALCHEMIST", "MENTOR_LEAD"})
    @DisplayName("ADMIN, ALCHEMIST y MENTOR_LEAD ven todos los tickets en GET /api/v1/admin/tickets")
    void plataformaVeTodos(UserRole rol) throws Exception {
        UUID quien = cuenta(rol, UserStatus.ACTIVE);
        // el alcance por formación se prueba en TicketMentorServiceTest; acá la vista ve todas
        when(formaciones.de(any())).thenReturn(com.renaser.os.shared.domain.AlcanceDeFormacion.todas());

        mockMvc.perform(get("/api/v1/admin/tickets").header("X-Actor-Id", quien.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickets.length()").value(2));
    }

    @ParameterizedTest(name = "{0} recibe 403 en la vista de plataforma")
    @EnumSource(value = UserRole.class, names = {"MENTOR", "TRAINEE_GUIDE"})
    @DisplayName("un mentor o una guía no usan la vista de plataforma: 403")
    void mentorYGuiaNoUsanLaVistaDePlataforma(UserRole rol) throws Exception {
        UUID quien = cuenta(rol, UserStatus.ACTIVE);
        // el alcance por formación se prueba en TicketMentorServiceTest; acá la vista ve todas
        when(formaciones.de(any())).thenReturn(com.renaser.os.shared.domain.AlcanceDeFormacion.todas());

        mockMvc.perform(get("/api/v1/admin/tickets").header("X-Actor-Id", quien.toString()))
                .andExpect(status().isForbidden());
        verify(cargaTickets, never()).todos(any(), anyInt());
    }

    @Test
    @DisplayName("un mentor suspendido recibe 403 en GET /api/v1/tickets aunque acompañe a alguien")
    void mentorSuspendidoRecibe403() throws Exception {
        UUID mentor = cuenta(UserRole.MENTOR, UserStatus.SUSPENDED);
        when(acompanamiento.aprendicesQueAcompana(UserId.of(mentor), AHORA)).thenReturn(Set.of(ana));

        mockMvc.perform(get("/api/v1/tickets").header("X-Actor-Id", mentor.toString()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(acompanamiento);
    }

    @ParameterizedTest(name = "{0}: responder el ticket de un aprendiz ajeno responde 403")
    @EnumSource(value = UserRole.class, names = {"MENTOR", "TRAINEE_GUIDE"})
    @DisplayName("E-38 y D19: el ticket de un aprendiz solo lo responde su mentor asignado: otro mentor o guía recibe 403")
    void soloElAsignadoResponde(UserRole rol) throws Exception {
        UUID otro = cuenta(rol, UserStatus.ACTIVE);
        when(cargaTickets.byId(deAna.id())).thenReturn(Optional.of(deAna));
        when(participaciones.deParticipante(ana)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/tickets/{id}/answer", deAna.id().value())
                        .header("X-Actor-Id", otro.toString())
                        .contentType("application/json").content("{\"mentorAnswer\":\"Una respuesta\"}"))
                .andExpect(status().isForbidden());
        verify(guardaTickets, never()).save(any());
    }

    // spec 056 FR-030
    @Test
    @DisplayName("spec 056 FR-030: el líder de la formación responde con POST /answer y la respuesta trae respondidoPor {id, nombre}")
    void elLiderRespondeYSeVeQuienRespondio() throws Exception {
        UUID lider = cuenta(UserRole.MENTOR_LEAD, UserStatus.ACTIVE);
        UserId liderId = UserId.of(lider);
        when(cargaTickets.byId(deAna.id())).thenReturn(Optional.of(deAna));
        when(formaciones.de(liderId)).thenReturn(AlcanceDeFormacion.de(Formacion.ESCUELA));
        when(formaciones.formacionesDe(ana)).thenReturn(Set.of(Formacion.ESCUELA));
        when(guardaTickets.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(userSummaryFinder.findByIds(any())).thenReturn(java.util.Map.of(liderId, new UserSummary(liderId, "Lucía Líder", null, UserRole.MENTOR_LEAD, UserStatus.ACTIVE)));

        mockMvc.perform(post("/api/v1/tickets/{id}/answer", deAna.id().value()).header("X-Actor-Id", lider.toString())
                        .contentType("application/json").content("{\"mentorAnswer\":\"Prueba con una sola cosa\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANSWERED"))
                .andExpect(jsonPath("$.respondidoPor.id").value(lider.toString()))
                .andExpect(jsonPath("$.respondidoPor.nombre").value("Lucía Líder"));
    }

    // spec 056 FR-030
    @Test
    @DisplayName("spec 056 FR-030: fuera de su formación el líder recibe 403 con codigo FUERA_DE_SU_ALCANCE")
    void elLiderFueraDeSuFormacionRecibe403ConCodigo() throws Exception {
        UUID lider = cuenta(UserRole.MENTOR_LEAD, UserStatus.ACTIVE);
        when(cargaTickets.byId(deAna.id())).thenReturn(Optional.of(deAna));
        when(formaciones.de(UserId.of(lider))).thenReturn(AlcanceDeFormacion.de(Formacion.ESCUELA));
        when(formaciones.formacionesDe(ana)).thenReturn(Set.of(Formacion.DIPLOMADO));

        mockMvc.perform(post("/api/v1/tickets/{id}/answer", deAna.id().value()).header("X-Actor-Id", lider.toString())
                        .contentType("application/json").content("{\"mentorAnswer\":\"No me toca\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("FUERA_DE_SU_ALCANCE"));
        verify(guardaTickets, never()).save(any());
    }

    // spec 056 FR-030
    @Test
    @DisplayName("spec 056 FR-030: la lista de la plataforma trae respondidoPor con el nombre en las respondidas y null en las abiertas")
    void laListaTraeRespondidoPor() throws Exception {
        UUID quien = cuenta(UserRole.MENTOR_LEAD, UserStatus.ACTIVE);
        when(formaciones.de(any())).thenReturn(com.renaser.os.shared.domain.AlcanceDeFormacion.todas());
        UserId mentora = UserId.of(UUID.randomUUID());
        deAna.responder("Respuesta", mentora, FixedClock.at(AHORA));
        when(userSummaryFinder.findByIds(any())).thenReturn(java.util.Map.of(mentora, new UserSummary(mentora, "Marta Mentora", null, UserRole.MENTOR, UserStatus.ACTIVE)));

        mockMvc.perform(get("/api/v1/admin/tickets").header("X-Actor-Id", quien.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickets[0].respondidoPor.id").value(mentora.value().toString()))
                .andExpect(jsonPath("$.tickets[0].respondidoPor.nombre").value("Marta Mentora"))
                .andExpect(jsonPath("$.tickets[1].respondidoPor").doesNotExist());
    }
}
