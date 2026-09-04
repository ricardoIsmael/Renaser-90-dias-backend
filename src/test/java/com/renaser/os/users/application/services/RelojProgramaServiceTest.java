package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.participante.ActivateProgramUseCase.ActivateProgramCommand;
import com.renaser.os.users.application.ports.in.participante.ConsultarActivacionProgramaUseCase.ConsultarActivacionProgramaQuery;
import com.renaser.os.users.application.ports.out.participante.ListarParticipantesConProgramaActivoPort;
import com.renaser.os.users.application.ports.out.participante.LoadParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.participante.SaveParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelojProgramaServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Mock
    private LoadUserPort loadUserPort;
    @Mock
    private LoadParticipacionProgramaPort loadParticipacionProgramaPort;
    @Mock
    private SaveParticipacionProgramaPort saveParticipacionProgramaPort;
    @Mock
    private ListarParticipantesConProgramaActivoPort listarParticipantesConProgramaActivoPort;

    private RelojProgramaService service;

    @BeforeEach
    void setUp() {
        service = new RelojProgramaService(new RequireActiveUserGuard(loadUserPort), loadParticipacionProgramaPort,
                saveParticipacionProgramaPort, listarParticipantesConProgramaActivoPort, CLOCK);
    }

    private User usuarioActivo(UserId id) {
        return User.rehydrate(id, new Email(id + "@renaser.com"), UserRole.TRAINEE, UserStatus.ACTIVE,
                "Fixture " + id, null, null, null, null);
    }

    // ─── activarPrograma ────────────────────────────────────────────────────

    @Test
    void activarProgramaGuardaLaParticipacionActivada() {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(usuarioActivo(actorId)));
        ParticipacionPrograma participacion = ParticipacionPrograma.inscribirTraineeAprobado(actorId, CLOCK);
        when(loadParticipacionProgramaPort.byParticipanteId(actorId)).thenReturn(Optional.of(participacion));
        when(saveParticipacionProgramaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ParticipacionPrograma resultado = service.activarPrograma(
                new ActivateProgramCommand(actorId, CLOCK.today().plusDays(1)));

        assertThat(resultado.estaActivado()).isTrue();
        assertThat(resultado.diaPrograma()).isZero(); // el cron nocturno lo sube cuando llegue la fecha
        verify(saveParticipacionProgramaPort).save(participacion);
    }

    @Test
    void activarProgramaSinParticipacionPreviaDevuelveNoEncontrado() {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(usuarioActivo(actorId)));
        when(loadParticipacionProgramaPort.byParticipanteId(actorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activarPrograma(new ActivateProgramCommand(actorId, CLOCK.today().plusDays(1))))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void activarProgramaComoSuspendidoEsRechazado() {
        UserId actorId = UserId.of(UUID.randomUUID());
        User suspendido = User.rehydrate(actorId, new Email(actorId + "@renaser.com"), UserRole.TRAINEE,
                UserStatus.SUSPENDED, "Fixture", null, null, null, null);
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(suspendido));

        assertThatThrownBy(() -> service.activarPrograma(new ActivateProgramCommand(actorId, CLOCK.today().plusDays(1))))
                .isInstanceOf(NotAuthorizedException.class);
    }

    /** El fixture de staff ({@code activarSeguimientoPersonal}) activa con fecha=hoy;
     * pedir una fecha DISTINTA (mañana) sobre una participacion ya activada debe
     * rechazarse — reintentar con la MISMA fecha es un caso aparte (no-op, cubierto en
     * el dominio, {@code ParticipacionProgramaTest}). */
    @Test
    void activarProgramaYaActivadoConFechaDistintaPropagaElRechazoDelDominio() {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(usuarioActivo(actorId)));
        ParticipacionPrograma yaActivada = ParticipacionPrograma.activarSeguimientoPersonal(actorId, CLOCK);
        when(loadParticipacionProgramaPort.byParticipanteId(actorId)).thenReturn(Optional.of(yaActivada));

        assertThatThrownBy(() -> service.activarPrograma(new ActivateProgramCommand(actorId, CLOCK.today().plusDays(1))))
                .isInstanceOf(IllegalStateException.class);
        verify(saveParticipacionProgramaPort, never()).save(any());
    }

    // ─── consultarEstado ────────────────────────────────────────────────────

    @Test
    void consultarEstadoDevuelveLasTresFechasCuandoNoEstaActivado() {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(usuarioActivo(actorId)));
        ParticipacionPrograma pausada = ParticipacionPrograma.inscribirTraineeAprobado(actorId, CLOCK);
        when(loadParticipacionProgramaPort.byParticipanteId(actorId)).thenReturn(Optional.of(pausada));

        var estado = service.consultarEstado(new ConsultarActivacionProgramaQuery(actorId));

        assertThat(estado.activado()).isFalse();
        assertThat(estado.fechasValidas()).hasSize(3).contains(CLOCK.today().plusDays(1))
                .doesNotContain(CLOCK.today());
    }

    @Test
    void consultarEstadoDevuelveFechasVaciasCuandoYaEstaActivado() {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(usuarioActivo(actorId)));
        ParticipacionPrograma activada = ParticipacionPrograma.activarSeguimientoPersonal(actorId, CLOCK);
        when(loadParticipacionProgramaPort.byParticipanteId(actorId)).thenReturn(Optional.of(activada));

        var estado = service.consultarEstado(new ConsultarActivacionProgramaQuery(actorId));

        assertThat(estado.activado()).isTrue();
        assertThat(estado.fechasValidas()).isEmpty();
    }

    /**
     * D-84: Plan necesita distinguir "todavia no elegiste" de "ya elegiste, arrancas el X".
     * Hasta ahora las dos situaciones devolvian lo mismo y en pantalla se veian igual: un
     * plan vacio sin explicacion.
     */
    @Test
    void consultarEstadoActivadoDevuelveLaFechaDeInicioElegida() {
        UserId actorId = UserId.of(UUID.randomUUID());
        var participacion = ParticipacionPrograma.inscribirTraineeAprobado(actorId, CLOCK);
        participacion.activarPrograma(CLOCK.today().plusDays(2), CLOCK);
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(usuarioActivo(actorId)));
        when(loadParticipacionProgramaPort.byParticipanteId(actorId)).thenReturn(Optional.of(participacion));

        var estado = service.consultarEstado(new ConsultarActivacionProgramaQuery(actorId));

        assertThat(estado.activado()).isTrue();
        assertThat(estado.fechasValidas()).isEmpty();
        assertThat(estado.fechaInicio()).isEqualTo(CLOCK.today().plusDays(2));
    }

    @Test
    void consultarEstadoSinActivarNoDevuelveFechaDeInicio() {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(usuarioActivo(actorId)));
        when(loadParticipacionProgramaPort.byParticipanteId(actorId))
                .thenReturn(Optional.of(ParticipacionPrograma.inscribirTraineeAprobado(actorId, CLOCK)));

        var estado = service.consultarEstado(new ConsultarActivacionProgramaQuery(actorId));

        assertThat(estado.activado()).isFalse();
        assertThat(estado.fechaInicio()).isNull();
        assertThat(estado.fechasValidas()).hasSize(3);
    }

    // ─── avanzarParticipantesActivos (cron nocturno) ───────────────────────

    @Test
    void avanzarParticipantesActivosGuardaSoloLosQueEfectivamenteAvanzaron() {
        // Se lo "retrasa" a proposito para que SI haya algo que avanzar en esta corrida:
        ParticipacionPrograma pendienteDeAvance = ParticipacionPrograma.rehydrate(UserId.of(UUID.randomUUID()), null,
                null, 5, com.renaser.os.users.api.FasePrograma.PHASE_1_REBIRTH,
                CLOCK.today().minusDays(5), CLOCK.now(), java.time.ZoneId.of("America/Lima"), false, 0, CLOCK.now(),
                CLOCK.now(), null, null, null, CLOCK.today().minusDays(1));
        ParticipacionPrograma yaAvanzadaHoy = ParticipacionPrograma.rehydrate(UserId.of(UUID.randomUUID()), null,
                null, 10, com.renaser.os.users.api.FasePrograma.PHASE_2_DEVELOPMENT,
                CLOCK.today().minusDays(9), CLOCK.now(), java.time.ZoneId.of("America/Lima"), false, 0, CLOCK.now(),
                CLOCK.now(), null, null, null, CLOCK.today());

        List<ParticipacionPrograma> unicaPagina = new ArrayList<>(
                List.of(pendienteDeAvance, yaAvanzadaHoy));
        when(listarParticipantesConProgramaActivoPort.pagina(0, 500)).thenReturn(unicaPagina);
        when(saveParticipacionProgramaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var resultado = service.avanzarParticipantesActivos();

        assertThat(resultado.evaluados()).isEqualTo(2);
        assertThat(resultado.avanzados()).isEqualTo(1);
        verify(saveParticipacionProgramaPort, times(1)).save(any());
        assertThat(pendienteDeAvance.diaPrograma()).isEqualTo(6);
        assertThat(yaAvanzadaHoy.diaPrograma()).isEqualTo(10);
    }

    /**
     * Regresion del bug del 2026-09-03 (BITACORA E-91). Una cuenta de America/Lima con
     * `fecha_inicio` = HOY tiene que estar en el dia 1 durante TODO ese dia, corra el
     * barrido a la hora que corra.
     *
     * <p>Con el modelo incremental y el cron de las 04:50 UTC esto daba 0: para Lima
     * (UTC-5) esas son las 23:50 del dia ANTERIOR, asi que la guarda
     * "la fecha de inicio todavia no llego" era verdadera y el aprendiz pasaba su Dia 1
     * entero viendo "dia 0". El modelo derivado no depende de a que hora corrio nadie.
     */
    @Test
    void unParticipanteDeLimaEstaEnElDiaUnoDuranteTodoSuPrimerDia() {
        var inicio = java.time.LocalDate.of(2026, 9, 3);
        var lima = java.time.ZoneId.of("America/Lima");
        // 14:00 en Lima del propio dia de inicio (19:00 UTC): plena jornada del Dia 1.
        var relojEnPlenoDiaUno = FixedClock.at(Instant.parse("2026-09-03T19:00:00Z"));
        var service = new RelojProgramaService(new RequireActiveUserGuard(loadUserPort),
                loadParticipacionProgramaPort, saveParticipacionProgramaPort,
                listarParticipantesConProgramaActivoPort, relojEnPlenoDiaUno);
        ParticipacionPrograma recienActivado = ParticipacionPrograma.rehydrate(UserId.of(UUID.randomUUID()), null,
                null, 0, com.renaser.os.users.api.FasePrograma.PHASE_1_REBIRTH, inicio,
                Instant.parse("2026-09-03T04:07:00Z"), lima, false, 0,
                relojEnPlenoDiaUno.now(), relojEnPlenoDiaUno.now(), null, null, null, null);
        when(listarParticipantesConProgramaActivoPort.pagina(0, 500))
                .thenReturn(new ArrayList<>(List.of(recienActivado)));
        when(saveParticipacionProgramaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var resultado = service.avanzarParticipantesActivos();

        assertThat(resultado.avanzados()).isEqualTo(1);
        assertThat(recienActivado.diaPrograma()).isEqualTo(1);
        assertThat(recienActivado.diaProgramaAvanzadoEl()).isEqualTo(inicio);
    }

    /** Verifica que el barrido SI pida una segunda pagina cuando la primera viene llena
     * (500 filas) — la señal de que no esta cargando "todos" de una sola consulta. */
    @Test
    void avanzarParticipantesActivosPideLaSegundaPaginaCuandoLaPrimeraVieneLlena() {
        List<ParticipacionPrograma> paginaLlena = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            paginaLlena.add(ParticipacionPrograma.inscribirTraineeAprobado(UserId.of(UUID.randomUUID()), CLOCK));
        }
        when(listarParticipantesConProgramaActivoPort.pagina(0, 500)).thenReturn(paginaLlena);
        when(listarParticipantesConProgramaActivoPort.pagina(500, 500)).thenReturn(Collections.emptyList());

        var resultado = service.avanzarParticipantesActivos();

        assertThat(resultado.evaluados()).isEqualTo(500);
        assertThat(resultado.avanzados()).isZero(); // pausados: inscribirTraineeAprobado no activa
        verify(listarParticipantesConProgramaActivoPort).pagina(500, 500);
    }

    @Test
    void avanzarParticipantesActivosSinNingunoActivadoNoGuardaNada() {
        when(listarParticipantesConProgramaActivoPort.pagina(0, 500)).thenReturn(Collections.emptyList());

        var resultado = service.avanzarParticipantesActivos();

        assertThat(resultado.evaluados()).isZero();
        assertThat(resultado.avanzados()).isZero();
        verify(saveParticipacionProgramaPort, never()).save(any());
    }
}
