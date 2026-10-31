package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.out.celula.ConsultarMiembrosCelulaPort;
import com.renaser.os.calendar.application.ports.out.celula.ConsultarPertenenciaAGrupoPort;
import com.renaser.os.calendar.application.ports.out.curso.ResolverAudienciaCursoPort;
import com.renaser.os.calendar.application.ports.out.elegibilidad.ConsultarElegibilidadEventoPort;
import com.renaser.os.calendar.application.ports.out.nivelmembresia.LoadNivelMembresiaPort;
import com.renaser.os.calendar.application.ports.out.participante.ConsultarProgresoParticipanteCalendarPort;
import com.renaser.os.calendar.application.ports.out.participante.ConsultarProgresoParticipanteCalendarPort.ProgresoParticipanteCalendar;
import com.renaser.os.calendar.application.ports.out.participante.ResolverAudienciaMasivaPort;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * D-293 por rol: la Mentoria del Alquimista le pide semaforo verde SOLO a un aprendiz. El personal (guia, mentor,
 * lider, admin, alquimista y Soporte TI, que en este modulo cuentan como MENTOR, MENTOR_LEAD y ADMIN) pasa por su
 * audiencia como siempre y el puerto de elegibilidad ni se le pregunta. Los otros tipos de evento no cambian.
 *
 * <p>Las dos puertas: el acceso a UN evento (lista, calendario, «Mi agenda», «Proximo evento», detalle, responder
 * «Voy») y la audiencia que recibe los avisos (generacion, anuncio al crear y la hoja «Quien respondio» del D-256).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MentoriaConSemaforoVerdePorRolTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-10-30T15:00:00Z"));
    private static final Instant INICIA_EN = Instant.parse("2026-11-04T01:00:00Z");
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Mock
    private ConsultarProgresoParticipanteCalendarPort progresoPort;
    @Mock
    private LoadNivelMembresiaPort nivelPort;
    @Mock
    private ResolverAudienciaCursoPort cursoPort;
    @Mock
    private ConsultarElegibilidadEventoPort elegibilidadPort;
    @Mock
    private ConsultarPertenenciaAGrupoPort pertenenciaAGrupoPort;
    @Mock
    private ResolverAudienciaMasivaPort audienciaMasivaPort;
    @Mock
    private ConsultarMiembrosCelulaPort celulaPort;

    private AccesoEventoService acceso;
    private AudienciaDelEventoService audiencia;
    private final UserId autor = UserId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        acceso = new AccesoEventoService(progresoPort, nivelPort, cursoPort, elegibilidadPort, pertenenciaAGrupoPort);
        audiencia = new AudienciaDelEventoService(nivelPort, progresoPort, audienciaMasivaPort, celulaPort, cursoPort,
                elegibilidadPort);
    }

    // ── El acceso a un evento ───────────────────────────────────────────────

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = RolUsuario.class, names = "TRAINEE", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("D-293: el personal ve la Mentoria del Alquimista aunque el puerto diga que no, y no se le pregunta")
    void elPersonalNoCambia(RolUsuario rol) {
        UserId persona = UserId.of(UUID.randomUUID());
        Evento mentoria = evento(TipoEvento.MENTORIA_ALQUIMISTA, TipoAudiencia.TODOS, Set.of());

        assertThat(puedeAcceder(persona, rol, mentoria)).isTrue();
        verifyNoInteractions(elegibilidadPort);
    }

    @Test
    @DisplayName("D-293: un aprendiz con semaforo verde ve la Mentoria; sin verde no, y el detalle la trata como a "
            + "cualquier evento que no es para el")
    void elAprendizDependeDelPuerto() {
        UserId verde = UserId.of(UUID.randomUUID());
        UserId otro = UserId.of(UUID.randomUUID());
        Evento mentoria = evento(TipoEvento.MENTORIA_ALQUIMISTA, TipoAudiencia.TODOS, Set.of());
        when(elegibilidadPort.esElegible(verde, TipoEvento.MENTORIA_ALQUIMISTA)).thenReturn(true);
        when(elegibilidadPort.esElegible(otro, TipoEvento.MENTORIA_ALQUIMISTA)).thenReturn(false);

        assertThat(puedeAcceder(verde, RolUsuario.TRAINEE, mentoria)).isTrue();
        assertThat(puedeAcceder(otro, RolUsuario.TRAINEE, mentoria)).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = TipoEvento.class, names = "MENTORIA_ALQUIMISTA", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("D-293: los demas tipos de evento no cambian: un aprendiz sin verde los sigue viendo y no se pregunta")
    void losDemasTiposNoCambian(TipoEvento tipo) {
        UserId aprendiz = UserId.of(UUID.randomUUID());

        assertThat(puedeAcceder(aprendiz, RolUsuario.TRAINEE, evento(tipo, TipoAudiencia.TODOS, Set.of()))).isTrue();
        verifyNoInteractions(elegibilidadPort);
    }

    @Test
    @DisplayName("D-293: primero la audiencia, que es barata; el semaforo solo se lee si la audiencia ya lo deja pasar")
    void sinAudienciaNoSeLeeElSemaforo() {
        UserId aprendiz = UserId.of(UUID.randomUUID());
        Evento soloParaAdmin = evento(TipoEvento.MENTORIA_ALQUIMISTA, TipoAudiencia.ROLES, Set.of(RolUsuario.ADMIN));

        assertThat(puedeAcceder(aprendiz, RolUsuario.TRAINEE, soloParaAdmin)).isFalse();
        verifyNoInteractions(elegibilidadPort);
    }

    // ── La audiencia que recibe los avisos ──────────────────────────────────

    @Test
    @DisplayName("D-293: a quien se le avisa — todo el personal de la audiencia y solo los aprendices en verde")
    void losAvisosLlegaAlPersonalYAlAprendizEnVerde() {
        List<UserId> personal = EnumSet.complementOf(EnumSet.of(RolUsuario.TRAINEE)).stream()
                .map(rol -> persona(rol)).toList();
        UserId verde = persona(RolUsuario.TRAINEE);
        UserId amarillo = persona(RolUsuario.TRAINEE);
        Evento mentoria = evento(TipoEvento.MENTORIA_ALQUIMISTA, TipoAudiencia.ROLES, EnumSet.allOf(RolUsuario.class));
        List<UserId> candidatos = new java.util.ArrayList<>(personal);
        candidatos.add(verde);
        candidatos.add(amarillo);
        when(audienciaMasivaPort.activosConRoles(any())).thenReturn(candidatos);
        when(elegibilidadPort.elegiblesEntre(anyCollection(), eq(TipoEvento.MENTORIA_ALQUIMISTA)))
                .thenReturn(Set.of(verde));

        List<UserId> destinatarios = audiencia.destinatarios(mentoria);

        assertThat(destinatarios).containsAll(personal).contains(verde).doesNotContain(amarillo)
                .hasSize(personal.size() + 1);
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<java.util.Collection<UserId>> preguntados = org.mockito.ArgumentCaptor
                .forClass(java.util.Collection.class);
        verify(elegibilidadPort).elegiblesEntre(preguntados.capture(), eq(TipoEvento.MENTORIA_ALQUIMISTA));
        assertThat(preguntados.getValue()).as("al puerto solo le llegan aprendices, y todos juntos")
                .containsExactlyInAnyOrder(verde, amarillo);
    }

    @Test
    @DisplayName("D-293: «Pasar lista» y «Quien respondio»: un aprendiz en amarillo no esta incluido; uno en verde si")
    void incluyeSegunElSemaforo() {
        UserId verde = persona(RolUsuario.TRAINEE);
        UserId amarillo = persona(RolUsuario.TRAINEE);
        Evento mentoria = evento(TipoEvento.MENTORIA_ALQUIMISTA, TipoAudiencia.TODOS, Set.of());
        when(audienciaMasivaPort.traineesActivos()).thenReturn(List.of(verde, amarillo));
        when(elegibilidadPort.elegiblesEntre(anyCollection(), eq(TipoEvento.MENTORIA_ALQUIMISTA)))
                .thenAnswer(inv -> ((java.util.Collection<UserId>) inv.getArgument(0)).contains(verde)
                        ? Set.of(verde) : Set.of());

        assertThat(audiencia.incluye(mentoria, verde)).isTrue();
        assertThat(audiencia.incluye(mentoria, amarillo)).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = TipoEvento.class, names = "MENTORIA_ALQUIMISTA", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("D-293: en los demas tipos de evento los avisos llegan a toda la audiencia, sin preguntar nada")
    void losDemasTiposAvisanATodos(TipoEvento tipo) {
        UserId uno = persona(RolUsuario.TRAINEE);
        UserId otro = persona(RolUsuario.TRAINEE);
        when(audienciaMasivaPort.traineesActivos()).thenReturn(List.of(uno, otro));

        assertThat(audiencia.destinatarios(evento(tipo, TipoAudiencia.TODOS, Set.of()))).containsExactly(uno, otro);
        verify(elegibilidadPort, never()).elegiblesEntre(anyCollection(), any());
    }

    @Test
    @DisplayName("D-293: quien ya no existe no pasa")
    void quienNoExisteNoPasa() {
        UserId fantasma = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(fantasma)).thenReturn(Optional.empty());

        assertThat(audiencia.quienesPasan(List.of(fantasma), TipoEvento.MENTORIA_ALQUIMISTA)).isEmpty();
        verifyNoInteractions(elegibilidadPort);
    }

    // ── Escenario ───────────────────────────────────────────────────────────

    private boolean puedeAcceder(UserId persona, RolUsuario rol, Evento evento) {
        ProgresoParticipanteCalendar progreso = new ProgresoParticipanteCalendar(12, LIMA, rol, false, null);
        return acceso.puedeAcceder(persona, progreso, acceso.buildVisor(progreso), evento);
    }

    private UserId persona(RolUsuario rol) {
        UserId id = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(id)).thenReturn(Optional.of(
                new ProgresoParticipanteCalendar(12, LIMA, rol, false, null)));
        return id;
    }

    private Evento evento(TipoEvento tipo, TipoAudiencia tipoAudiencia, Set<RolUsuario> roles) {
        return Evento.crear(EventoId.of(UUID.randomUUID()), "Mentoria", null, INICIA_EN, 60, LIMA, TipoUbicacion.MEET,
                "https://meet.google.com/abc-defg-hij", tipoAudiencia, null, null, null, tipo, true, false, false,
                null, roles, List.of(), autor, CLOCK);
    }
}
