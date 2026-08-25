package com.renaser.os.rocks.application.services;

import com.renaser.os.evidence.api.RegistrarEvidenciaPort;
import com.renaser.os.evidence.api.RegistrarEvidenciaPort.EvidenciaRegistrada;
import com.renaser.os.evidence.api.EstadoValidacion;
import com.renaser.os.points.api.AjustarPuntosPort;
import com.renaser.os.points.api.MotivoPuntos;
import com.renaser.os.points.api.ResumenAjustePuntos;
import com.renaser.os.rocks.api.RocaCompletadaEvent;
import com.renaser.os.rocks.application.ports.in.rocadiaria.CompletarRocaDiariaUseCase.CompletarRocaDiariaCommand;
import com.renaser.os.rocks.application.ports.in.rocadiaria.SolicitarUrlAdjuntoRocaUseCase.SolicitarUrlAdjuntoRocaCommand;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.rocks.application.ports.out.rocadiaria.LoadRocaDiariaPort;
import com.renaser.os.rocks.domain.model.rocadiaria.TipoEvidenciaRoca;
import com.renaser.os.rocks.application.ports.out.rocadiaria.SaveRocaDiariaPort;
import com.renaser.os.rocks.application.ports.out.rocamaestra.LoadRocaMaestraPort;
import com.renaser.os.rocks.application.ports.out.rocasemanal.LoadRocaSemanalPort;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiariaId;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RocaDiariaServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T20:05:00Z"));

    @Mock
    private LoadRocaMaestraPort loadRocaMaestraPort;
    @Mock
    private LoadRocaSemanalPort loadRocaSemanalPort;
    @Mock
    private LoadRocaDiariaPort loadRocaDiariaPort;
    @Mock
    private SaveRocaDiariaPort saveRocaDiariaPort;
    @Mock
    private RegistrarEvidenciaPort registrarEvidenciaPort;
    @Mock
    private ConsultarProgresoParticipanteRocksPort progresoPort;
    @Mock
    private AlmacenamientoPort almacenamientoPort;
    @Mock
    private AjustarPuntosPort ajustarPuntosPort;
    @Mock
    private ApplicationEventPublisher events;

    private RocaDiariaService service;
    private UserId actorId;

    @BeforeEach
    void setUp() {
        service = new RocaDiariaService(loadRocaMaestraPort, loadRocaSemanalPort, loadRocaDiariaPort,
                saveRocaDiariaPort, registrarEvidenciaPort, progresoPort, almacenamientoPort, ajustarPuntosPort,
                events, CLOCK);
        actorId = UserId.of(UUID.randomUUID());
        lenient().when(saveRocaDiariaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(registrarEvidenciaPort.registrar(any()))
                .thenReturn(new EvidenciaRegistrada(UUID.randomUUID(), EstadoValidacion.PENDIENTE));
    }

    private static ProgresoParticipanteRocks progreso(RolParticipante rol, boolean suspendido) {
        return new ProgresoParticipanteRocks(20, LocalDate.of(2026, 1, 5), ZoneOffset.UTC, rol, suspendido);
    }

    private RocaDiaria rocaVerde(LocalTime horaFin) {
        return RocaDiaria.planificar(actorId, LocalDate.of(2026, 8, 24), 1, "verde", null, 5, false,
                EjeObjetivo.CUERPO, null, null, horaFin, CLOCK);
    }

    private RocaDiaria rocaAmarilla() {
        return RocaDiaria.planificar(actorId, LocalDate.of(2026, 8, 24), 2, "amarilla", null, 5, false,
                EjeObjetivo.CUERPO, null, null, null, CLOCK);
    }

    private CompletarRocaDiariaCommand comandoTexto(RocaDiariaId id) {
        return new CompletarRocaDiariaCommand(actorId, id, TipoEvidenciaRoca.TEXTO, null, null, "hecho", null, null,
                null);
    }

    @Test
    @DisplayName("CLAUDE.MD §0.3: rol sin permiso (no TRAINEE) -> NotAuthorizedException")
    void rolSinPermisoRechazado() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.MENTOR, false)));

        assertThatThrownBy(() -> service.completar(comandoTexto(RocaDiariaId.newId())))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("CLAUDE.MD §0.3: actor SUSPENDIDO -> NotAuthorizedException")
    void actorSuspendidoRechazado() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, true)));

        assertThatThrownBy(() -> service.completar(comandoTexto(RocaDiariaId.newId())))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void completarUnaRocaYaCompletadaEsConflicto() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, false)));
        RocaDiaria completada = rocaVerde(null);
        completada.completar(CLOCK.now(), CLOCK);
        when(loadRocaDiariaPort.byId(completada.id())).thenReturn(Optional.of(completada));

        assertThatThrownBy(() -> service.completar(comandoTexto(completada.id())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Ley IV: AMARILLA/ROJA bloqueadas mientras la VERDE del eje no tenga evidencia")
    void amarillaBloqueadaSinVerdeCompletada() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, false)));
        RocaDiaria amarilla = rocaAmarilla();
        RocaDiaria verdeSinCompletar = rocaVerde(null);
        when(loadRocaDiariaPort.byId(amarilla.id())).thenReturn(Optional.of(amarilla));
        when(loadRocaDiariaPort.deParticipanteYFecha(actorId, amarilla.fecha()))
                .thenReturn(List.of(amarilla, verdeSinCompletar));

        assertThatThrownBy(() -> service.completar(comandoTexto(amarilla.id())))
                .isInstanceOf(NotAuthorizedException.class)
                .hasMessageContaining("GREEN_NOT_EVIDENCED");
    }

    @Test
    @DisplayName("Ley VI: EXIF de una FOTO a mas de 15 min del instante de subida se rechaza")
    void exifFueraDeMargenRechazado() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, false)));
        RocaDiaria verde = rocaVerde(null);
        when(loadRocaDiariaPort.byId(verde.id())).thenReturn(Optional.of(verde));

        Instant exifMuyViejo = CLOCK.now().minus(Duration.ofMinutes(20));
        var command = new CompletarRocaDiariaCommand(actorId, verde.id(), TipoEvidenciaRoca.FOTO, "bucket", "ruta",
                null, exifMuyViejo, null, null);

        assertThatThrownBy(() -> service.completar(command)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EXIF_MISMATCH");
    }

    @Test
    @DisplayName("completar paga puntos sincronicamente via AjustarPuntosPort, ROCK_COMPLETED a tiempo")
    void completarATiempoPagaRockCompleted() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, false)));
        RocaDiaria verde = rocaVerde(LocalTime.of(21, 0)); // 21:00 UTC, completando a las 20:05 -> a tiempo
        when(loadRocaDiariaPort.byId(verde.id())).thenReturn(Optional.of(verde));
        when(ajustarPuntosPort.ajustar(eq(actorId), eq(MotivoPuntos.ROCK_COMPLETED), eq(10), anyString()))
                .thenReturn(new ResumenAjustePuntos(actorId, 10, 110));

        RocaDiaria resultado = service.completar(comandoTexto(verde.id()));

        assertThat(resultado.completada()).isTrue();
        assertThat(resultado.puntosOtorgados()).isEqualTo(10);
        verify(ajustarPuntosPort).ajustar(eq(actorId), eq(MotivoPuntos.ROCK_COMPLETED), eq(10), anyString());
        // any() sin tipo es ambiguo: ApplicationEventPublisher esta sobrecargado
        // (ApplicationEvent vs Object) y RocaCompletadaEvent no extiende ApplicationEvent,
        // asi que la llamada real resuelve al overload Object — hay que forzar el mismo
        // overload aca o Mockito verifica sobre la sobrecarga equivocada (ver BITACORA_ERRORES).
        verify(events).publishEvent(any(RocaCompletadaEvent.class));
    }

    @Test
    @DisplayName("D-P6/idempotencia: si ya tiene puntos otorgados, no se vuelve a llamar a AjustarPuntosPort")
    void noVuelveAPagarSiYaTienePuntos() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, false)));
        RocaDiaria verde = rocaVerde(null);
        verde.otorgarPuntos(10); // simula que un intento anterior ya pago (defensivo, no deberia pasar con completada=false)
        when(loadRocaDiariaPort.byId(verde.id())).thenReturn(Optional.of(verde));

        service.completar(comandoTexto(verde.id()));

        verify(ajustarPuntosPort, never()).ajustar(any(), any(), anyInt(), anyString());
    }

    @Test
    void solicitarUrlDevuelveUrlFirmadaDeAlmacenamientoPort() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, false)));
        RocaDiaria verde = rocaVerde(null);
        when(loadRocaDiariaPort.byId(verde.id())).thenReturn(Optional.of(verde));
        when(almacenamientoPort.firmarSubida(anyString(), anyString(), any()))
                .thenReturn(URI.create("https://s3.example/rocas/x"));

        var url = service.solicitarUrl(new SolicitarUrlAdjuntoRocaCommand(actorId, verde.id(), "image/jpeg"));

        assertThat(url.bucket()).isEqualTo("renaser-files");
        assertThat(url.url().toString()).isEqualTo("https://s3.example/rocas/x");
    }
}
