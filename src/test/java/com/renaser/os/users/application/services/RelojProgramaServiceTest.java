package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.participante.ActivateProgramUseCase.ActivateProgramCommand;
import com.renaser.os.users.application.ports.in.participante.ConsultarActivacionProgramaUseCase.ConsultarActivacionProgramaQuery;
import com.renaser.os.users.application.ports.out.participante.GuardarAvanceDelRelojPort;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
    @Mock
    private GuardarAvanceDelRelojPort guardarAvanceDelRelojPort;

    private RelojProgramaService service;

    @BeforeEach
    void setUp() {
        service = new RelojProgramaService(new RequireActiveUserGuard(loadUserPort), loadParticipacionProgramaPort,
                saveParticipacionProgramaPort, listarParticipantesConProgramaActivoPort, guardarAvanceDelRelojPort, CLOCK);
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

    /**
     * D-260: el personal con la fila sin activar elige su Día 1 por este mismo caso de uso, con la
     * misma regla que el aprendiz. Reloj en la madrugada UTC (regla 02): 03:00 UTC del 7 = 22:00 del 6
     * en Lima, así que «mañana» es el 7 de Lima y no el 8 del servidor.
     */
    @ParameterizedTest(name = "{0} con la fila sin activar elige su Día 1")
    @EnumSource(value = UserRole.class, names = {"MENTOR", "MENTOR_LEAD", "ADMIN", "ALCHEMIST"})
    void elPersonalConLaFilaSinActivarEligeSuDiaUnoEnLaMadrugadaUtc(UserRole rol) {
        FixedClock madrugadaUtc = FixedClock.at(Instant.parse("2026-10-07T03:00:00Z"));
        RelojProgramaService enLaMadrugada = new RelojProgramaService(new RequireActiveUserGuard(loadUserPort),
                loadParticipacionProgramaPort, saveParticipacionProgramaPort, listarParticipantesConProgramaActivoPort,
                guardarAvanceDelRelojPort, madrugadaUtc);
        UserId actorId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(User.rehydrate(actorId,
                new Email(actorId + "@renaser.com"), rol, UserStatus.ACTIVE, "Fixture", null, null, null, null)));
        ParticipacionPrograma sinActivar = ParticipacionPrograma.inscribirTraineeAprobado(actorId, madrugadaUtc);
        when(loadParticipacionProgramaPort.byParticipanteId(actorId)).thenReturn(Optional.of(sinActivar));
        when(saveParticipacionProgramaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var estado = enLaMadrugada.consultarEstado(new ConsultarActivacionProgramaQuery(actorId));
        ParticipacionPrograma activada = enLaMadrugada.activarPrograma(
                new ActivateProgramCommand(actorId, estado.fechasValidas().get(0)));

        assertThat(estado.fechasValidas()).first().isEqualTo(java.time.LocalDate.parse("2026-10-07"));
        assertThat(activada.estaActivado()).isTrue();
        assertThat(activada.fechaInicio()).isEqualTo(java.time.LocalDate.parse("2026-10-07"));
        assertThat(activada.diasAjuste()).isZero();
    }

    /**
     * D-261 (decisión del dueño del 2026-10-07: «que elija el día como los demás»): el personal SIN
     * fila ve las mismas fechas que el aprendiz y, al elegir, se le crea la fila ya activada con esa
     * fecha. Consultar no guarda nada. Madrugada UTC (regla 02): las fechas se cuentan desde el 6 de Lima.
     */
    @ParameterizedTest(name = "{0} sin fila elige su Día 1 y se le crea la fila con esa fecha")
    @EnumSource(value = UserRole.class, names = {"MENTOR", "MENTOR_LEAD", "ADMIN", "ALCHEMIST"})
    void elPersonalSinFilaEligeSuDiaUnoYSeLeCreaLaFila(UserRole rol) {
        FixedClock madrugadaUtc = FixedClock.at(Instant.parse("2026-10-07T03:00:00Z"));
        RelojProgramaService enLaMadrugada = new RelojProgramaService(new RequireActiveUserGuard(loadUserPort),
                loadParticipacionProgramaPort, saveParticipacionProgramaPort, listarParticipantesConProgramaActivoPort,
                guardarAvanceDelRelojPort, madrugadaUtc);
        UserId actorId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(User.rehydrate(actorId,
                new Email(actorId + "@renaser.com"), rol, UserStatus.ACTIVE, "Fixture", null, null, null, null)));
        when(loadParticipacionProgramaPort.byParticipanteId(actorId)).thenReturn(Optional.empty());
        when(saveParticipacionProgramaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var estado = enLaMadrugada.consultarEstado(new ConsultarActivacionProgramaQuery(actorId));
        verify(saveParticipacionProgramaPort, never()).save(any());
        ParticipacionPrograma creada = enLaMadrugada.activarPrograma(
                new ActivateProgramCommand(actorId, estado.fechasValidas().get(1)));

        assertThat(estado.activado()).isFalse();
        assertThat(estado.fechasValidas()).containsExactly(java.time.LocalDate.parse("2026-10-07"),
                java.time.LocalDate.parse("2026-10-08"), java.time.LocalDate.parse("2026-10-09"));
        assertThat(creada.participanteId()).isEqualTo(actorId);
        assertThat(creada.estaActivado()).isTrue();
        assertThat(creada.fechaInicio()).isEqualTo(java.time.LocalDate.parse("2026-10-08"));
        assertThat(creada.diasAjuste()).isZero();
        assertThat(creada.diaPrograma()).isZero();
        verify(saveParticipacionProgramaPort).save(creada);
    }

    @Test
    void elAprendizSinFilaSigueSinPoderConsultarNiActivar() {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(usuarioActivo(actorId)));
        when(loadParticipacionProgramaPort.byParticipanteId(actorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultarEstado(new ConsultarActivacionProgramaQuery(actorId)))
                .isInstanceOf(NoSuchElementException.class);
        verify(saveParticipacionProgramaPort, never()).save(any());
    }

    @ParameterizedTest(name = "{0} suspendido no elige su Día 1")
    @EnumSource(value = UserRole.class, names = {"MENTOR", "MENTOR_LEAD", "ADMIN", "ALCHEMIST"})
    void elPersonalSuspendidoNoEligeSuDiaUno(UserRole rol) {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(User.rehydrate(actorId,
                new Email(actorId + "@renaser.com"), rol, UserStatus.SUSPENDED, "Fixture", null, null, null, null)));

        assertThatThrownBy(() -> service.activarPrograma(new ActivateProgramCommand(actorId, CLOCK.today().plusDays(1))))
                .isInstanceOf(NotAuthorizedException.class);
        verify(saveParticipacionProgramaPort, never()).save(any());
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
        when(guardarAvanceDelRelojPort.guardarSiNoSeAjusto(any())).thenReturn(true);

        var resultado = service.avanzarParticipantesActivos();

        assertThat(resultado.evaluados()).isEqualTo(2);
        assertThat(resultado.avanzados()).isEqualTo(1);
        verify(guardarAvanceDelRelojPort, times(1)).guardarSiNoSeAjusto(any());
        assertThat(pendienteDeAvance.diaPrograma()).isEqualTo(6);
        assertThat(yaAvanzadaHoy.diaPrograma()).isEqualTo(10);
    }

    /**
     * A-2 (2026-09-08). Un participante que falla NO puede detener el barrido
     * (`.claude/rules/02-tiempo-zonas-y-schedulers.md` §4).
     *
     * <p>Contra el codigo viejo esto ni siquiera llegaba a la aserccion: la excepcion subia
     * por {@code avanzarParticipantesActivos} y se llevaba puesta la corrida entera. En
     * produccion eso significa que UNA fila rota deja a TODO el padron sin avanzar de dia,
     * cada hora, con el unico rastro de un stacktrace en el log del scheduler — el mismo
     * sintoma que "sigo en el dia 0" pero con causa distinta a E-91.
     */
    @Test
    void unParticipanteQueFallaNoDetieneElBarrido() {
        ParticipacionPrograma rompe = participacionPendienteDeAvance();
        ParticipacionPrograma sigueDespues = participacionPendienteDeAvance();

        when(listarParticipantesConProgramaActivoPort.pagina(0, 500))
                .thenReturn(new ArrayList<>(List.of(rompe, sigueDespues)));
        when(guardarAvanceDelRelojPort.guardarSiNoSeAjusto(rompe))
                .thenThrow(new IllegalStateException("fila corrupta en la base"));
        when(guardarAvanceDelRelojPort.guardarSiNoSeAjusto(sigueDespues)).thenReturn(true);

        var resultado = service.avanzarParticipantesActivos();

        assertThat(resultado.evaluados()).isEqualTo(2);
        assertThat(resultado.avanzados()).isEqualTo(1);
        assertThat(sigueDespues.diaPrograma()).isEqualTo(6);
    }

    /** Activado hace 5 dias, sincronizado por ultima vez ayer: en esta corrida tiene que avanzar. */
    private static ParticipacionPrograma participacionPendienteDeAvance() {
        return ParticipacionPrograma.rehydrate(UserId.of(UUID.randomUUID()), null, null, 5,
                com.renaser.os.users.api.FasePrograma.PHASE_1_REBIRTH, CLOCK.today().minusDays(5), CLOCK.now(),
                java.time.ZoneId.of("America/Lima"), false, 0, CLOCK.now(), CLOCK.now(), null, null, null,
                CLOCK.today().minusDays(1));
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
                listarParticipantesConProgramaActivoPort, guardarAvanceDelRelojPort, relojEnPlenoDiaUno);
        ParticipacionPrograma recienActivado = ParticipacionPrograma.rehydrate(UserId.of(UUID.randomUUID()), null,
                null, 0, com.renaser.os.users.api.FasePrograma.PHASE_1_REBIRTH, inicio,
                Instant.parse("2026-09-03T04:07:00Z"), lima, false, 0,
                relojEnPlenoDiaUno.now(), relojEnPlenoDiaUno.now(), null, null, null, null);
        when(listarParticipantesConProgramaActivoPort.pagina(0, 500))
                .thenReturn(new ArrayList<>(List.of(recienActivado)));
        when(guardarAvanceDelRelojPort.guardarSiNoSeAjusto(any())).thenReturn(true);

        var resultado = service.avanzarParticipantesActivos();

        assertThat(resultado.avanzados()).isEqualTo(1);
        assertThat(recienActivado.diaPrograma()).isEqualTo(1);
        assertThat(recienActivado.diaProgramaAvanzadoEl()).isEqualTo(inicio);
    }

    /**
     * La graduacion es del PARTICIPANTE, no del servidor (regla 02 §1 y §3, misma familia que
     * E-91). Este aprendiz de Lima cumple su dia 90 el 2026-09-15 en SU zona. A las 03:00 UTC de
     * ese dia todavia son las 22:00 del 14 en Lima: le falta un dia, y graduarlo ahi seria
     * graduarlo antes de tiempo —exactamente lo que haria un barrido que mirara {@code
     * clock.today()}, la fecha del proceso—. Veinticuatro horas mas tarde, si.
     *
     * <p>El reloj se fija a proposito entre las 00:00 y las 05:00 UTC: es la franja donde la
     * fecha del servidor y la del participante NO coinciden, y la unica que distingue las dos
     * implementaciones. Con el reloj a las 10:00 UTC, como el resto de esta clase, un barrido
     * zona-ciego pasaria el test igual.
     */
    @Test
    void graduaPorElDiaDelParticipanteYNoPorLaFechaDelServidor() {
        var lima = java.time.ZoneId.of("America/Lima");
        var diaNoventaEnLima = java.time.LocalDate.of(2026, 9, 15);
        var inicio = diaNoventaEnLima.minusDays(89);
        var vispera = FixedClock.at(Instant.parse("2026-09-15T03:00:00Z"));
        ParticipacionPrograma p = ParticipacionPrograma.rehydrate(UserId.of(UUID.randomUUID()), null, null, 88,
                com.renaser.os.users.api.FasePrograma.PHASE_4_ASCENSION, inicio,
                inicio.atStartOfDay(lima).toInstant(), lima, false, 0, vispera.now(), vispera.now(), null, null,
                null, diaNoventaEnLima.minusDays(2));
        when(listarParticipantesConProgramaActivoPort.pagina(0, 500)).thenReturn(new ArrayList<>(List.of(p)));
        when(guardarAvanceDelRelojPort.guardarSiNoSeAjusto(any())).thenReturn(true);

        servicioCon(vispera).avanzarParticipantesActivos();

        assertThat(p.diaPrograma()).isEqualTo(89);
        assertThat(p.programaCompletado()).isFalse();

        servicioCon(FixedClock.at(Instant.parse("2026-09-16T03:00:00Z"))).avanzarParticipantesActivos();

        assertThat(p.diaPrograma()).isEqualTo(90);
        assertThat(p.programaCompletado()).isTrue();
        assertThat(p.diaPostPrograma()).isZero();
    }

    /** Mismo servicio que arma {@code setUp}, con otro reloj: los tests de zona horaria lo
     * necesitan posicionado en un instante concreto, no en el de la clase. */
    private RelojProgramaService servicioCon(FixedClock reloj) {
        return new RelojProgramaService(new RequireActiveUserGuard(loadUserPort), loadParticipacionProgramaPort,
                saveParticipacionProgramaPort, listarParticipantesConProgramaActivoPort, guardarAvanceDelRelojPort, reloj);
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
        verify(guardarAvanceDelRelojPort, never()).guardarSiNoSeAjusto(any());
    }

    /**
     * D-197. La fila se leyo con la pagina (ajuste 0, dia 39 de ayer) y un admin la retrocedio al 34
     * antes del guardado: el guardado condicional no escribe, se relee y se deriva con el ajuste
     * nuevo. El barrido nunca vuelve a escribir la version leida con la pagina.
     */
    @Test
    void siLaFilaSeAjustoDespuesDeLeerLaPaginaSeReleeYSeDerivaConElAjusteNuevo() {
        var lima = java.time.ZoneId.of("America/Lima");
        // 00:05 de Lima (05:05 UTC): la hora del barrido que choca con los ajustes (regla 03).
        var alaMedianocheDeLima = FixedClock.at(Instant.parse("2026-09-26T05:05:00Z"));
        var hoy = java.time.LocalDate.of(2026, 9, 26);
        UserId id = UserId.of(UUID.randomUUID());
        var leidaConLaPagina = ParticipacionPrograma.rehydrate(id, null, null, 39,
                com.renaser.os.users.api.FasePrograma.paraDiaPrograma(39), hoy.minusDays(39), CLOCK.now(), lima, false, 0,
                CLOCK.now(), CLOCK.now(), null, null, null, hoy.minusDays(1), 0);
        var ajustadaEnElMedio = ParticipacionPrograma.rehydrate(id, null, null, 34,
                com.renaser.os.users.api.FasePrograma.paraDiaPrograma(34), hoy.minusDays(39), CLOCK.now(), lima, false, 0,
                CLOCK.now(), CLOCK.now(), null, null, null, hoy.minusDays(1), 6);
        when(listarParticipantesConProgramaActivoPort.pagina(0, 500))
                .thenReturn(new ArrayList<>(List.of(leidaConLaPagina)));
        // Las dos instancias son "iguales" (equals por participanteId): se responde por orden.
        when(guardarAvanceDelRelojPort.guardarSiNoSeAjusto(any())).thenReturn(false, true);
        when(loadParticipacionProgramaPort.byParticipanteId(id)).thenReturn(Optional.of(ajustadaEnElMedio));

        var resultado = servicioCon(alaMedianocheDeLima).avanzarParticipantesActivos();

        assertThat(resultado.avanzados()).isEqualTo(1);
        var guardadas = org.mockito.ArgumentCaptor.forClass(ParticipacionPrograma.class);
        verify(guardarAvanceDelRelojPort, times(2)).guardarSiNoSeAjusto(guardadas.capture());
        assertThat(guardadas.getAllValues().get(1)).isSameAs(ajustadaEnElMedio);
        assertThat(ajustadaEnElMedio.diaPrograma()).isEqualTo(34);
        assertThat(ajustadaEnElMedio.diasAjuste()).isEqualTo(6);
        assertThat(ajustadaEnElMedio.diaProgramaAvanzadoEl()).isEqualTo(hoy);
        verify(saveParticipacionProgramaPort, never()).save(any());
    }

    /** Si vuelve a cambiar en el reintento, se deja para la corrida siguiente sin romper el barrido. */
    @Test
    void siVuelveACambiarEnElReintentoQuedaParaLaCorridaSiguiente() {
        ParticipacionPrograma leida = participacionPendienteDeAvance();
        ParticipacionPrograma fresca = participacionPendienteDeAvance();
        when(listarParticipantesConProgramaActivoPort.pagina(0, 500)).thenReturn(new ArrayList<>(List.of(leida)));
        when(loadParticipacionProgramaPort.byParticipanteId(leida.participanteId())).thenReturn(Optional.of(fresca));
        when(guardarAvanceDelRelojPort.guardarSiNoSeAjusto(any())).thenReturn(false);

        var resultado = service.avanzarParticipantesActivos();

        assertThat(resultado.evaluados()).isEqualTo(1);
        assertThat(resultado.avanzados()).isZero();
        verify(guardarAvanceDelRelojPort, times(2)).guardarSiNoSeAjusto(any());
    }
}
