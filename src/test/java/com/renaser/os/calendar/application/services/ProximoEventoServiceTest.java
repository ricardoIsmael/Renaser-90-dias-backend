package com.renaser.os.calendar.application.services;

import com.renaser.os.points.api.ProximoEventoFinder.ProximoEvento;
import com.renaser.os.calendar.application.ports.out.evento.LoadEventoPort;
import com.renaser.os.calendar.application.ports.out.evento.LoadExcepcionPort;
import com.renaser.os.calendar.application.ports.out.nivelmembresia.LoadNivelMembresiaPort;
import com.renaser.os.calendar.application.ports.out.participante.ConsultarProgresoParticipanteCalendarPort;
import com.renaser.os.calendar.application.ports.out.participante.ConsultarProgresoParticipanteCalendarPort.ProgresoParticipanteCalendar;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProximoEventoServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Mock
    private LoadEventoPort loadEventoPort;
    @Mock
    private LoadExcepcionPort loadExcepcionPort;
    @Mock
    private LoadNivelMembresiaPort nivelPort;
    @Mock
    private ConsultarProgresoParticipanteCalendarPort progresoPort;

    private ProximoEventoService service;

    private final UserId actorId = UserId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        AccesoEventoService accesoEventoService = new AccesoEventoService(progresoPort, nivelPort, cursoNoOp(),
                (u, t) -> false);
        service = new ProximoEventoService(loadEventoPort, loadExcepcionPort, accesoEventoService, CLOCK);
        lenient().when(nivelPort.listar()).thenReturn(List.of());
        lenient().when(loadExcepcionPort.porEventos(any())).thenReturn(Map.of());
    }

    private static com.renaser.os.calendar.application.ports.out.curso.ResolverAudienciaCursoPort cursoNoOp() {
        return new com.renaser.os.calendar.application.ports.out.curso.ResolverAudienciaCursoPort() {
            @Override
            public boolean tieneAcceso(UserId usuarioId, String cursoId) {
                return false;
            }

            @Override
            public Set<UserId> filtrarConAcceso(String cursoId, Set<UserId> candidatos) {
                return Set.of();
            }
        };
    }

    private ProgresoParticipanteCalendar progreso(RolUsuario rol) {
        return new ProgresoParticipanteCalendar(10, ZoneId.of("America/Lima"), rol, false, null);
    }

    private Evento eventoSuelto(String titulo, Instant iniciaEn) {
        return Evento.crear(titulo, null, iniciaEn, 60, ZoneId.of("America/Lima"), TipoUbicacion.MEET,
                "https://meet.google.com/abc", TipoAudiencia.TODOS, null, null, null, TipoEvento.ESPONTANEO, false,
                false, false, null, Set.of(), List.of(), actorId, CLOCK);
    }

    @Test
    void devuelveElEventoFuturoMasCercanoEntreVariosCandidatos() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolUsuario.TRAINEE)));
        Evento lejano = eventoSuelto("Sesion lejana", Instant.parse("2026-09-10T19:00:00Z"));
        Evento cercano = eventoSuelto("Sesion cercana", Instant.parse("2026-08-25T09:00:00Z"));
        // El orden de retorno del puerto no debe importar: el service busca el minimo real.
        when(loadEventoPort.candidatosParaVisor(any(), any())).thenReturn(List.of(lejano, cercano));

        Optional<ProximoEvento> resultado = service.proximoEventoDe(actorId);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().titulo()).isEqualTo("Sesion cercana");
        assertThat(resultado.get().iniciaEn()).isEqualTo(Instant.parse("2026-08-25T09:00:00Z"));
    }

    @Test
    void sinEventosVisiblesDevuelveVacio() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolUsuario.TRAINEE)));
        when(loadEventoPort.candidatosParaVisor(any(), any())).thenReturn(List.of());

        assertThat(service.proximoEventoDe(actorId)).isEmpty();
    }

    @Test
    void cuentaSuspendidaPropagaNotAuthorized() {
        ProgresoParticipanteCalendar suspendido = new ProgresoParticipanteCalendar(10, ZoneId.of("America/Lima"),
                RolUsuario.TRAINEE, true, null);
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(suspendido));

        assertThatThrownBy(() -> service.proximoEventoDe(actorId)).isInstanceOf(NotAuthorizedException.class);
    }
}
