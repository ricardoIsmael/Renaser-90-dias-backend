package com.renaser.os.habits.application.services;

import com.renaser.os.shared.GuardDeRol;
import com.renaser.os.habits.application.ports.in.radar.ConsultarHistorialRadarUseCase.HistorialRadarPage;
import com.renaser.os.habits.application.ports.in.radar.RegistrarCheckInRadarUseCase.RegistrarCheckInRadarCommand;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.radar.LoadMiRadarPort;
import com.renaser.os.habits.application.ports.out.radar.LoadRegistroRadarPort;
import com.renaser.os.habits.application.ports.out.radar.SaveRegistroRadarPort;
import com.renaser.os.habits.domain.model.radar.MiRadar;
import com.renaser.os.habits.domain.model.radar.RegistroRadar;
import com.renaser.os.habits.domain.model.radar.RegistroRadarId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RadarServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T14:00:00Z"));
    /** Identidad fija: con el id entrando por el puerto IdGenerator, registrar() ya no lo sortea. */
    private static final UUID ID_GENERADO = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Mock
    private LoadRegistroRadarPort loadPort;
    @Mock
    private SaveRegistroRadarPort savePort;
    @Mock
    private LoadMiRadarPort miRadarPort;
    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock
    private IdGenerator idGenerator;

    private RadarService service;

    @BeforeEach
    void setUp() {
        service = servicioConElReloj(CLOCK);
        lenient().when(idGenerator.newId()).thenReturn(ID_GENERADO);
        lenient().when(savePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // Sin horas guardadas Mi Radar esta apagado: la semana 1 (dia 5) no lo mira.
        lenient().when(miRadarPort.deParticipante(any())).thenReturn(MiRadar.apagado());
    }

    private RadarService servicioConElReloj(FixedClock reloj) {
        return new RadarService(new AccesoAlRadar(progresoPort), loadPort, savePort, miRadarPort,
                new LecturaDelRadar(loadPort, miRadarPort, progresoPort), reloj, idGenerator);
    }

    private static ProgresoParticipanteHabits trainee() {
        return new ProgresoParticipanteHabits(5, "America/Argentina/Buenos_Aires", RolParticipante.TRAINEE, false, false);
    }

    private static RegistrarCheckInRadarCommand comando(UserId actorId, UserId participanteId) {
        return new RegistrarCheckInRadarCommand(actorId, participanteId, "haciendo", "pensando", "sintiendo", 6,
                "evitando");
    }

    @Test
    void registrarRechazaActorQueNoEsElParticipante() {
        UserId dueno = UserId.of(UUID.randomUUID());
        UserId otro = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> service.registrar(comando(otro, dueno)))
                .isInstanceOf(NotAuthorizedException.class);

        verify(savePort, never()).save(any());
        verify(progresoPort, never()).deParticipante(any());
    }

    @Test
    void registrarRechazaParticipanteInexistente() {
        UserId dueno = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(dueno)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrar(comando(dueno, dueno)))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void registrarRechazaParticipanteSuspendido() {
        UserId dueno = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(dueno)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, true, false)));

        assertThatThrownBy(() -> service.registrar(comando(dueno, dueno)))
                .isInstanceOf(NotAuthorizedException.class);

        verify(savePort, never()).save(any());
    }

    @Test
    void registrarRechazaRolQueNoEsTrainee() {
        UserId dueno = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(dueno)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(5, "UTC", RolParticipante.MENTOR, false, false)));

        assertThatThrownBy(() -> service.registrar(comando(dueno, dueno)))
                .isInstanceOf(NotAuthorizedException.class);

        verify(savePort, never()).save(any());
    }

    @Test
    void registrarGuardaElCheckInParaElDuenoTrainee() {
        UserId dueno = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(dueno)).thenReturn(Optional.of(trainee()));

        RegistroRadar guardado = service.registrar(comando(dueno, dueno));

        assertThat(guardado.participanteId()).isEqualTo(dueno);
        assertThat(guardado.queHago()).isEqualTo("haciendo");
        assertThat(guardado.nivelEnergia()).isEqualTo(6);
        assertThat(guardado.creadoEn()).isEqualTo(CLOCK.now());
        // Dia 5, 11:00 en Buenos Aires: la franja de la primera semana es la hora prevista, como siempre.
        assertThat(guardado.horaPrevista()).isEqualTo(11);
        verify(savePort).save(any());
    }

    @Test
    void ultimoRechazaActorQueNoEsElParticipante() {
        UserId dueno = UserId.of(UUID.randomUUID());
        UserId otro = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> service.ultimo(otro, dueno)).isInstanceOf(NotAuthorizedException.class);
        verify(loadPort, never()).ultimoDeParticipante(any());
    }

    @Test
    void ultimoDevuelveVacioCuandoNuncaHuboCheckIn() {
        UserId dueno = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(dueno)).thenReturn(Optional.of(trainee()));
        when(loadPort.ultimoDeParticipante(dueno)).thenReturn(Optional.empty());

        assertThat(service.ultimo(dueno, dueno)).isEmpty();
    }

    @Test
    void ultimoDevuelveElRegistroMasReciente() {
        UserId dueno = UserId.of(UUID.randomUUID());
        RegistroRadar ultimo = RegistroRadar.registrar(RegistroRadarId.of(UUID.randomUUID()), dueno, "h", "p", "s", 5,
                "e", CLOCK.now());
        when(progresoPort.deParticipante(dueno)).thenReturn(Optional.of(trainee()));
        when(loadPort.ultimoDeParticipante(dueno)).thenReturn(Optional.of(ultimo));

        assertThat(service.ultimo(dueno, dueno)).contains(ultimo);
    }

    @Test
    void historialRechazaActorQueNoEsElParticipante() {
        UserId dueno = UserId.of(UUID.randomUUID());
        UserId otro = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> service.historial(otro, dueno, null, 20))
                .isInstanceOf(NotAuthorizedException.class);
        verify(loadPort, never()).historialDeParticipante(any(), any(), eq(20));
    }

    @Test
    void historialSinSiguienteCursorCuandoLaPaginaNoEstaLlena() {
        UserId dueno = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(dueno)).thenReturn(Optional.of(trainee()));
        List<RegistroRadar> pagina = List.of(RegistroRadar.registrar(RegistroRadarId.of(UUID.randomUUID()), dueno, "h",
                "p", "s", 5, "e", CLOCK.now()));
        when(loadPort.historialDeParticipante(dueno, null, 20)).thenReturn(pagina);

        HistorialRadarPage resultado = service.historial(dueno, dueno, null, 20);

        assertThat(resultado.entradas()).isEqualTo(pagina);
        assertThat(resultado.siguienteCursor()).isNull();
    }

    @Test
    void historialConSiguienteCursorCuandoLaPaginaEstaLlena() {
        UserId dueno = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(dueno)).thenReturn(Optional.of(trainee()));
        Instant t1 = CLOCK.now().minusSeconds(120);
        Instant t2 = CLOCK.now().minusSeconds(60);
        List<RegistroRadar> pagina = List.of(
                RegistroRadar.registrar(RegistroRadarId.of(UUID.randomUUID()), dueno, "h", "p", "s", 5, "e",
                        t2), RegistroRadar.registrar(RegistroRadarId.of(UUID.randomUUID()), dueno, "h", "p", "s", 5,
                        "e", t1));
        when(loadPort.historialDeParticipante(dueno, null, 2)).thenReturn(pagina);

        HistorialRadarPage resultado = service.historial(dueno, dueno, null, 2);

        assertThat(resultado.siguienteCursor()).isEqualTo(t1);
    }

    @Test
    void historialPropagaElCursorRecibidoAlPuerto() {
        UserId dueno = UserId.of(UUID.randomUUID());
        Instant cursor = CLOCK.now().minusSeconds(600);
        when(progresoPort.deParticipante(dueno)).thenReturn(Optional.of(trainee()));
        when(loadPort.historialDeParticipante(dueno, cursor, 20)).thenReturn(List.of());

        service.historial(dueno, dueno, cursor, 20);

        verify(loadPort).historialDeParticipante(dueno, cursor, 20);
    }

    /**
     * E-169: un MENTOR que activó su seguimiento personal opera su programa como cualquiera.
     *
     * <p>Es el reverso exacto del caso de arriba, y se construye sobre su mismo fixture para que
     * la única diferencia sea el dato que importa: `programaActivado`. Antes de esto,
     * {@code POST /api/v1/mentor/activate-tracking} inscribía al staff y después el guard lo
     * echaba — la inscripción estaba construida y el uso prohibido.
     */
    @Test
    @DisplayName("E-169: un MENTOR con su programa ACTIVADO ya no lo rechaza el guard de rol")
    void staffConProgramaActivadoOperaSuPrograma() {
        UserId dueno = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(dueno)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(5, "UTC", RolParticipante.MENTOR, false, true)));

        GuardDeRol.noRechaza(() -> service.registrar(comando(dueno, dueno)),
                "El Codigo Renaser es exclusivo de aprendices");

        // El `verify(never())` del caso de rechazo se cae aca a proposito: ahora SI guarda, y eso
        // es exactamente el arreglo. Se afirma lo contrario que alla, sobre el mismo fixture.
        verify(savePort).save(any());
    }

    // ─── Spec 012: Mi Radar desde el dia 8 ─────────────────────────────────

    /** 10:00 en Lima: el reloj normal de los casos del dia. */
    private static final FixedClock LIMA_10_00 = FixedClock.at(Instant.parse("2026-10-20T15:00:00Z"));
    /** 22:00 del 19 en Lima, pero 03:00 UTC del 20: el reloj que esconde el desfase de zona (regla 02). */
    private static final FixedClock LIMA_22_00_ES_03_UTC = FixedClock.at(Instant.parse("2026-10-20T03:00:00Z"));
    /** 01:30 en Lima, pero 06:30 UTC: la UTC ya esta en horario de Mi Radar, la persona no. */
    private static final FixedClock LIMA_01_30_ES_06_UTC = FixedClock.at(Instant.parse("2026-10-20T06:30:00Z"));
    /** Lunes 15:00 en Lima (20:00 UTC): un dia con horas propias en los casos por dia. */
    private static final FixedClock LUNES_LIMA_15_00 = FixedClock.at(Instant.parse("2026-10-19T20:00:00Z"));
    /** DOMINGO 22:00 en Lima, pero LUNES 03:00 UTC: con el reloj en la madrugada UTC el dia de la semana local es el anterior. */
    private static final FixedClock DOMINGO_LIMA_22_00_ES_LUNES_03_UTC = FixedClock.at(
            Instant.parse("2026-10-26T03:00:00Z"));

    private UserId personaEnLima(int diaPrograma) {
        UserId dueno = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(dueno)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(diaPrograma, "America/Lima", RolParticipante.TRAINEE, false, true)));
        return dueno;
    }

    private void conMiRadar(UserId persona, MiRadar miRadar) {
        when(miRadarPort.deParticipante(persona)).thenReturn(miRadar);
    }

    @Test
    @DisplayName("FR-001: dias 0 a 7 se registra a cualquier hora y sin Mi Radar, como hoy, con hora prevista solo de 08 a 19")
    void primeraSemanaComoHoy() {
        for (int dia : new int[]{0, 1, 7}) {
            UserId persona = personaEnLima(dia);

            RegistroRadar enFranja = servicioConElReloj(LIMA_10_00).registrar(comando(persona, persona));
            RegistroRadar fueraDeFranja = servicioConElReloj(LIMA_22_00_ES_03_UTC).registrar(comando(persona, persona));
            RegistroRadar madrugada = servicioConElReloj(LIMA_01_30_ES_06_UTC).registrar(comando(persona, persona));

            assertThat(enFranja.horaPrevista()).as("dia %d, 10:00", dia).isEqualTo(10);
            assertThat(fueraDeFranja.horaPrevista()).as("dia %d, 22:00", dia).isNull();
            assertThat(madrugada.horaPrevista()).as("dia %d, 01:30", dia).isNull();
        }
    }

    @Test
    @DisplayName("FR-008a: desde el dia 8 con Mi Radar apagado el servidor rechaza con 409 y no guarda nada")
    void apagadoDesdeElDia8Rechaza() {
        for (int dia : new int[]{8, 9, 90}) {
            UserId persona = personaEnLima(dia);

            assertThatThrownBy(() -> service.registrar(comando(persona, persona)))
                    .as("dia %d", dia)
                    .isInstanceOf(IllegalStateException.class)
                    .isNotInstanceOf(NotAuthorizedException.class)
                    .hasMessage("Mi Radar está apagado");
        }
        verify(savePort, never()).save(any());
    }

    @Test
    @DisplayName("desde el dia 8 encendido, a una hora elegida: se guarda con esa hora prevista")
    void horaElegida() {
        UserId persona = personaEnLima(10);
        conMiRadar(persona, MiRadar.encendido(List.of(9, 10, 20)));

        RegistroRadar guardado = servicioConElReloj(LIMA_10_00).registrar(comando(persona, persona));

        assertThat(guardado.horaPrevista()).isEqualTo(10);
        assertThat(guardado.creadoEn()).isEqualTo(LIMA_10_00.now());
        verify(savePort).save(guardado);
    }

    @Test
    @DisplayName("desde el dia 8 encendido, a una hora que no elegi (Registrar ahora): se guarda sin hora prevista")
    void horaLibre() {
        UserId persona = personaEnLima(10);
        conMiRadar(persona, MiRadar.encendido(List.of(9, 14, 20)));

        RegistroRadar guardado = servicioConElReloj(LIMA_10_00).registrar(comando(persona, persona));

        assertThat(guardado.horaPrevista()).isNull();
        verify(savePort).save(guardado);
    }

    @Test
    @DisplayName("regla 02: la hora es la de Lima y no la UTC; a las 22:00 de Lima (03:00 UTC) la hora 22 elegida es la prevista")
    void laHoraPrevistaEsLaLocalConElRelojEnLaMadrugadaUtc() {
        UserId persona = personaEnLima(10);
        conMiRadar(persona, MiRadar.encendido(List.of(22)));

        RegistroRadar guardado = servicioConElReloj(LIMA_22_00_ES_03_UTC).registrar(comando(persona, persona));

        assertThat(guardado.horaPrevista()).isEqualTo(22);
    }

    @Test
    @DisplayName("regla 02: a la 01:30 de Lima (06:30 UTC) no se registra aunque Mi Radar este encendido: 409 de 03:00 a 23:59")
    void antesDeLas03ConLaUtcYaEnHorario() {
        UserId persona = personaEnLima(10);
        conMiRadar(persona, MiRadar.encendido(List.of(9, 14, 20)));

        assertThatThrownBy(() -> servicioConElReloj(LIMA_01_30_ES_06_UTC).registrar(comando(persona, persona)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Mi Radar funciona de 03:00 a 23:59");
        verify(savePort, never()).save(any());
    }

    @Test
    @DisplayName("sigue la regla de un registro por hora: si ya hay uno en esta hora se devuelve ese y no se guarda otro")
    void unoPorHoraDesdeElDia8() {
        UserId persona = personaEnLima(10);
        conMiRadar(persona, MiRadar.encendido(List.of(10)));
        FixedClock a10y30 = FixedClock.at(Instant.parse("2026-10-20T15:30:00Z"));
        RegistroRadar deEstaHora = RegistroRadar.registrar(RegistroRadarId.of(UUID.randomUUID()), persona, "h", "p",
                "s", 5, "e", Instant.parse("2026-10-20T15:06:00Z")).conHoraPrevista(10);
        when(loadPort.ultimoDeParticipante(persona)).thenReturn(Optional.of(deEstaHora));

        RegistroRadar devuelto = servicioConElReloj(a10y30).registrar(comando(persona, persona));

        assertThat(devuelto).isSameAs(deEstaHora);
        verify(savePort, never()).save(any());
    }

    @Test
    @DisplayName("la cuenta suspendida y el actor ajeno se rechazan con 403 antes de mirar Mi Radar")
    void elGuardDeSiempreCorreAntes() {
        UserId dueno = UserId.of(UUID.randomUUID());
        UserId otro = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(dueno)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(10, "America/Lima", RolParticipante.TRAINEE, true, true)));

        assertThatThrownBy(() -> service.registrar(comando(otro, dueno))).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.registrar(comando(dueno, dueno))).isInstanceOf(NotAuthorizedException.class);

        verify(miRadarPort, never()).deParticipante(any());
        verify(savePort, never()).save(any());
    }

    @Test
    @DisplayName("el historial trae Mi Radar de la persona junto a la pagina")
    void historialTraeMiRadar() {
        UserId persona = personaEnLima(10);
        conMiRadar(persona, MiRadar.encendido(List.of(14, 9)));
        when(loadPort.historialDeParticipante(persona, null, 20)).thenReturn(List.of());

        HistorialRadarPage pagina = service.historial(persona, persona, null, 20);

        assertThat(pagina.miRadar().estaEncendido()).isTrue();
        assertThat(pagina.miRadar().horas()).containsExactly(9, 14);
    }

    @Test
    @DisplayName("el historial de la semana 1 (dia menor que 8) no trae Mi Radar: va en null y ni se lee la columna")
    void historialSinMiRadarEnLaSemana1() {
        for (int dia : new int[]{0, 1, 7}) {
            UserId persona = personaEnLima(dia);
            when(loadPort.historialDeParticipante(persona, null, 84)).thenReturn(List.of());

            HistorialRadarPage pagina = service.historial(persona, persona, null, 84);

            assertThat(pagina.miRadar()).as("dia %d", dia).isNull();
            verify(miRadarPort, never()).deParticipante(persona);
        }
    }

    // ─── Spec 012, cuarta ronda: por dia de la semana ──────────────────────

    private static MiRadar.DiaPropio dia(int numero, Integer... horas) {
        return new MiRadar.DiaPropio(numero, List.of(horas));
    }

    @Test
    @DisplayName("un dia con horas propias: a una de ellas se guarda con esa hora prevista (el lunes 15:00 de Lima)")
    void diaConHorasPropias() {
        UserId persona = personaEnLima(10);
        conMiRadar(persona, MiRadar.encendido(List.of(9, 14, 20), List.of(dia(1, 15))));

        RegistroRadar guardado = servicioConElReloj(LUNES_LIMA_15_00).registrar(comando(persona, persona));

        assertThat(guardado.horaPrevista()).isEqualTo(15);
        verify(savePort).save(guardado);
    }

    @Test
    @DisplayName("un dia con horas propias: las de todos los dias NO son previstas ese dia, y lo que se registra a esa hora sale libre")
    void lasDeTodosLosDiasNoValenEnUnDiaPropio() {
        UserId persona = personaEnLima(10);
        // El lunes solo tiene la 15; a las 15:00 de Lima del lunes la 15 de todos los dias no existe.
        conMiRadar(persona, MiRadar.encendido(List.of(15, 20), List.of(dia(1, 9))));

        RegistroRadar guardado = servicioConElReloj(LUNES_LIMA_15_00).registrar(comando(persona, persona));

        assertThat(guardado.horaPrevista()).isNull();
        verify(savePort).save(guardado);
    }

    @Test
    @DisplayName("un dia que no esta en dias sigue las horas de todos los dias (el martes 10:00 de Lima)")
    void diaSinHorasPropiasSigueLasDeTodos() {
        UserId persona = personaEnLima(10);
        conMiRadar(persona, MiRadar.encendido(List.of(9, 10, 20), List.of(dia(1, 15))));

        RegistroRadar guardado = servicioConElReloj(LIMA_10_00).registrar(comando(persona, persona));

        assertThat(guardado.horaPrevista()).isEqualTo(10);
    }

    @Test
    @DisplayName("«Sin Radar ese dia»: el servidor rechaza con 409 «Hoy no tienes Mi Radar», no es 403 y no guarda nada")
    void diaSinRadarRechaza() {
        UserId persona = personaEnLima(10);
        // LIMA_10_00 es martes (dia 2): ese dia no tiene Radar, aunque Mi Radar este encendido el resto de la semana.
        conMiRadar(persona, MiRadar.encendido(List.of(9, 10, 20), List.of(dia(2))));

        assertThatThrownBy(() -> servicioConElReloj(LIMA_10_00).registrar(comando(persona, persona)))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(NotAuthorizedException.class)
                .hasMessage("Hoy no tienes Mi Radar");
        verify(savePort, never()).save(any());
    }

    @Test
    @DisplayName("en un dia sin Radar el motivo es ese y no el horario, tambien a la 01:30")
    void diaSinRadarGanaAlHorario() {
        UserId persona = personaEnLima(10);
        conMiRadar(persona, MiRadar.encendido(List.of(9), List.of(dia(2))));

        assertThatThrownBy(() -> servicioConElReloj(LIMA_01_30_ES_06_UTC).registrar(comando(persona, persona)))
                .isInstanceOf(IllegalStateException.class).hasMessage("Hoy no tienes Mi Radar");
    }

    @Test
    @DisplayName("«Sin Radar ese dia» no cierra la semana 1: los dias 0 a 7 se registra igual, como hoy")
    void diaSinRadarNoAplicaEnLaSemana1() {
        UserId persona = personaEnLima(7);
        conMiRadar(persona, MiRadar.encendido(List.of(9), List.of(dia(2))));

        RegistroRadar guardado = servicioConElReloj(LIMA_10_00).registrar(comando(persona, persona));

        assertThat(guardado.horaPrevista()).isEqualTo(10);
    }

    @Test
    @DisplayName("regla 02: con el reloj en la madrugada UTC (lunes 03:00Z) el dia local es el domingo; las horas del domingo mandan, no las del lunes")
    void elDiaDeLaSemanaEsElLocalEnLaMadrugadaUtc() {
        UserId persona = personaEnLima(10);
        // Lunes: solo la 15. Domingo: solo la 22. A las 22:00 de Lima del domingo (03:00Z del lunes) la prevista es la 22.
        conMiRadar(persona, MiRadar.encendido(List.of(9), List.of(dia(1, 15), dia(7, 22))));

        RegistroRadar guardado = servicioConElReloj(DOMINGO_LIMA_22_00_ES_LUNES_03_UTC)
                .registrar(comando(persona, persona));

        assertThat(guardado.horaPrevista()).isEqualTo(22);
    }

    @Test
    @DisplayName("regla 02: lunes 03:00Z es domingo en Lima, y si el domingo es «Sin Radar» se rechaza aunque el lunes tenga Radar")
    void domingoSinRadarConElRelojEnLaMadrugadaUtc() {
        UserId persona = personaEnLima(10);
        conMiRadar(persona, MiRadar.encendido(List.of(9, 22), List.of(dia(7))));

        assertThatThrownBy(() -> servicioConElReloj(DOMINGO_LIMA_22_00_ES_LUNES_03_UTC)
                .registrar(comando(persona, persona)))
                .isInstanceOf(IllegalStateException.class).hasMessage("Hoy no tienes Mi Radar");
        verify(savePort, never()).save(any());
    }

    @Test
    @DisplayName("el historial trae los dias propios de Mi Radar junto a las horas de todos los dias")
    void historialTraeLosDias() {
        UserId persona = personaEnLima(10);
        conMiRadar(persona, MiRadar.encendido(List.of(14, 9), List.of(dia(7), dia(6, 11))));
        when(loadPort.historialDeParticipante(persona, null, 84)).thenReturn(List.of());

        HistorialRadarPage pagina = service.historial(persona, persona, null, 84);

        assertThat(pagina.miRadar().horas()).containsExactly(9, 14);
        assertThat(pagina.miRadar().dias()).extracting(MiRadar.DiaPropio::dia).containsExactly(6, 7);
        assertThat(pagina.miRadar().horasDelDia(java.time.DayOfWeek.SATURDAY)).containsExactly(11);
        assertThat(pagina.miRadar().horasDelDia(java.time.DayOfWeek.SUNDAY)).isEmpty();
    }
}
