package com.renaser.os.habits.application.services;

import com.renaser.os.community.api.PublicacionMuroFinder;
import com.renaser.os.evidence.api.RegistrosConEvidenciaFinder;
import com.renaser.os.habits.application.politica.PoliticaClaseDiaria;
import com.renaser.os.habits.application.politica.PoliticaPostDiarioComunidad;
import com.renaser.os.habits.application.politica.PoliticaSantuario;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase.TrackDelDiaConCatalogo;
import com.renaser.os.habits.application.ports.out.desbloqueo.LoadDesbloqueoHabitoPort;
import com.renaser.os.habits.application.ports.out.guia.LoadGuiaHabitoPort;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.LoadHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.registro.ConsultarDiasProgramadosPort;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.application.ports.out.registro.SaveRegistroHabitoPort;
import com.renaser.os.habits.application.ports.out.renombre.LoadRenombreHabitoPort;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.points.api.AjustarPuntosPort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * D-288 (spec 014): {@code GET /habit-tracks/day?date=} con la proyeccion y el {@link RegistroService} REALES
 * conectados —solo los puertos de salida son mocks—, que es la unica forma de probar de punta a punta la regla de
 * fechas en la zona de la PERSONA y que el pedido solo lee.
 *
 * <p>El reloj siempre esta en la madrugada UTC (regla 02, E-91): en Lima (UTC-5) todavia es el dia anterior, y con
 * el cambian «hoy», «ayer» y «el lunes». Una implementacion que midiera con {@code clock.today()} (UTC) o con el
 * {@code LocalDate} del servidor fallaria en cada caso de aca. Calendario: lunes 26/10/2026, miercoles 28/10,
 * domingo 1/11, lunes 2/11.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DiaCerradoConsultaTest {

    /** Jueves 29/10 03:00 UTC = miercoles 28/10 22:00 en Lima. */
    private static final Instant MIERCOLES_NOCHE_EN_LIMA = Instant.parse("2026-10-29T03:00:00Z");
    /** Viernes 30/10 03:00 UTC = jueves 29/10 22:00 en Lima. */
    private static final Instant JUEVES_NOCHE_EN_LIMA = Instant.parse("2026-10-30T03:00:00Z");
    /** Martes 3/11 03:00 UTC = lunes 2/11 22:00 en Lima. */
    private static final Instant LUNES_NOCHE_EN_LIMA = Instant.parse("2026-11-03T03:00:00Z");
    /** Lunes 2/11 03:00 UTC = domingo 1/11 22:00 en Lima. */
    private static final Instant DOMINGO_NOCHE_EN_LIMA = Instant.parse("2026-11-02T03:00:00Z");

    private static final LocalDate PROGRAMA_VIEJO = LocalDate.of(2026, 9, 1);
    private static final LocalDate LUNES_26 = LocalDate.of(2026, 10, 26);
    private static final LocalDate MARTES_27 = LocalDate.of(2026, 10, 27);
    private static final LocalDate MIERCOLES_28 = LocalDate.of(2026, 10, 28);
    private static final LocalDate JUEVES_29 = LocalDate.of(2026, 10, 29);
    private static final LocalDate DOMINGO_25 = LocalDate.of(2026, 10, 25);

    @Mock private LoadRegistroHabitoPort loadRegistroPort;
    @Mock private SaveRegistroHabitoPort saveRegistroPort;
    @Mock private LoadHabitoPort loadHabitoPort;
    @Mock private LoadHorarioHabitoPort loadHorarioPort;
    @Mock private LoadPreferenciaHorarioPort loadPreferenciaPort;
    @Mock private ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock private LoadDesbloqueoHabitoPort loadDesbloqueoPort;
    @Mock private LoadGuiaHabitoPort loadGuiaPort;
    @Mock private RegistrosConEvidenciaFinder registrosConEvidenciaFinder;
    @Mock private LoadRenombreHabitoPort loadRenombrePort;
    @Mock private IdGenerator idGenerator;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private ConsultarDiasProgramadosPort diasProgramadosPort;

    private final UserId participante = UserId.of(UUID.randomUUID());
    private TracksDelDiaProyeccionService proyeccion;

    private void armar(Instant ahora, LocalDate diaUno) {
        FixedClock reloj = FixedClock.at(ahora);
        var registros = new RegistroService(loadRegistroPort, saveRegistroPort, loadHabitoPort, loadHorarioPort,
                loadPreferenciaPort, progresoPort, mock(AjustarPuntosPort.class), mock(PublicacionMuroFinder.class),
                loadDesbloqueoPort, mock(ApplicationEventPublisher.class), reloj, idGenerator,
                List.of(new PoliticaSantuario(), new PoliticaPostDiarioComunidad(), new PoliticaClaseDiaria()),
                CatalogoAplicableDePrueba.sinAudiencias(loadHabitoPort), ids -> Set.copyOf(ids),
                (ids, largoMinimo) -> Set.of(),
                new com.renaser.os.shared.infrastructure.interruptores.TodoPrendido());
        proyeccion = new TracksDelDiaProyeccionService(registros, registros, loadHabitoPort, loadHorarioPort,
                loadPreferenciaPort, loadGuiaPort, registrosConEvidenciaFinder, loadRenombrePort,
                new MedicionesDelDia(List.of(), (p, c, h) -> java.util.Map.of()),
                new RachasDeHabitos(diasProgramadosPort), reloj, transactionManager);
        when(progresoPort.deParticipante(participante)).thenReturn(Optional.of(new ProgresoParticipanteHabits(
                12, "America/Lima", RolParticipante.TRAINEE, false, true, diaUno)));
    }

    private RegistroHabito delDia(Habito habito, LocalDate fecha) {
        return RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), participante, habito.id(), fecha, 12,
                TipoDia.TODOS, false, MIERCOLES_NOCHE_EN_LIMA);
    }

    private static Habito habito(String titulo) {
        return Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), titulo, TipoHabito.CHECKBOX, "MENTE",
                ExigenciaEvidencia.OPCIONAL, MIERCOLES_NOCHE_EN_LIMA);
    }

    private void rechaza(LocalDate fecha) {
        clearInvocations(loadRegistroPort);
        assertThatThrownBy(() -> proyeccion.consultarDiaCerradoDe(participante, fecha))
                .isInstanceOf(IllegalArgumentException.class);
        // Una fecha rechazada no llega a leer los registros.
        verify(loadRegistroPort, never()).porParticipanteYFecha(any(), any());
    }

    @Test
    @DisplayName("miercoles 22:00 en Lima (ya jueves en UTC): ayer y el lunes si; HOY en Lima no, aunque en UTC sea ayer")
    void conElRelojEnLaMadrugadaUtcHoyEsElDiaDeLima() {
        armar(MIERCOLES_NOCHE_EN_LIMA, PROGRAMA_VIEJO);
        Habito meditar = habito("MEDITAR");
        when(loadRegistroPort.porParticipanteYFecha(participante, MARTES_27)).thenReturn(List.of(delDia(meditar, MARTES_27)));
        when(loadRegistroPort.porParticipanteYFecha(participante, LUNES_26)).thenReturn(List.of());
        when(loadHabitoPort.porIds(any())).thenReturn(List.of(meditar));

        assertThat(proyeccion.consultarDiaCerradoDe(participante, MARTES_27))
                .extracting(TrackDelDiaConCatalogo::tituloHabito).containsExactly("MEDITAR");
        assertThat(proyeccion.consultarDiaCerradoDe(participante, LUNES_26)).isEmpty();

        // En UTC ya es jueves 29: «ayer» (28) seria valido si se midiera con la fecha del servidor. En Lima es HOY.
        rechaza(MIERCOLES_28);
        rechaza(JUEVES_29);
        rechaza(DOMINGO_25);
    }

    @Test
    @DisplayName("lunes 22:00 en Lima (ya martes en UTC): ni hoy ni el domingo anterior; martes UTC no deja ver el lunes")
    void elLunesNoSePuedeVerNada() {
        armar(LUNES_NOCHE_EN_LIMA, PROGRAMA_VIEJO);

        rechaza(LocalDate.of(2026, 11, 1));
        // En UTC ya es martes 3: el lunes 2 pareceria «ayer». En Lima es hoy.
        rechaza(LocalDate.of(2026, 11, 2));
        rechaza(LocalDate.of(2026, 11, 3));
    }

    @Test
    @DisplayName("domingo 22:00 en Lima (ya lunes en UTC): la semana sigue siendo la de Lima, hasta el lunes 26")
    void elDomingoEnLimaLaSemanaNoSeReiniciaConElLunesUtc() {
        armar(DOMINGO_NOCHE_EN_LIMA, PROGRAMA_VIEJO);
        when(loadRegistroPort.porParticipanteYFecha(any(), any())).thenReturn(List.of());

        // Con la fecha de UTC (lunes 2/11) el sabado 31 y todo lo anterior serian «otra semana».
        assertThat(proyeccion.consultarDiaCerradoDe(participante, LocalDate.of(2026, 10, 31))).isEmpty();
        assertThat(proyeccion.consultarDiaCerradoDe(participante, LUNES_26)).isEmpty();
        rechaza(DOMINGO_25);
        rechaza(LocalDate.of(2026, 11, 1));
    }

    @Test
    @DisplayName("Dia 1 a mitad de semana: nada antes, ni de esta semana")
    void nuncaAntesDelDiaUno() {
        armar(JUEVES_NOCHE_EN_LIMA, MARTES_27);
        when(loadRegistroPort.porParticipanteYFecha(any(), any())).thenReturn(List.of());

        assertThat(proyeccion.consultarDiaCerradoDe(participante, MARTES_27)).isEmpty();
        assertThat(proyeccion.consultarDiaCerradoDe(participante, MIERCOLES_28)).isEmpty();
        rechaza(LUNES_26);
    }

    @Test
    @DisplayName("sin Dia 1 elegido, ningun dia se puede ver")
    void sinDiaUnoNada() {
        armar(MIERCOLES_NOCHE_EN_LIMA, null);

        rechaza(MARTES_27);
    }

    @Test
    @DisplayName("SOLO LECTURA: un dia sin registros devuelve vacio y no genera ni guarda nada (no es la red de /today)")
    void unDiaVacioNoCreaTracks() {
        armar(MIERCOLES_NOCHE_EN_LIMA, PROGRAMA_VIEJO);
        when(loadRegistroPort.porParticipanteYFecha(participante, MARTES_27)).thenReturn(List.of());

        assertThat(proyeccion.consultarDiaCerradoDe(participante, MARTES_27)).isEmpty();

        verify(saveRegistroPort, never()).insertarSiNoExiste(any());
        verify(saveRegistroPort, never()).save(any());
        verify(loadHabitoPort, never()).catalogoActivo();
        verify(loadHabitoPort, never()).personalesActivosDe(any());
        verify(idGenerator, never()).newId();
        verifyNoInteractions(diasProgramadosPort);
    }

    @Test
    @DisplayName("UNA lectura por pedido: el progreso, los registros y cada tabla del catalogo se piden una sola vez")
    void unaLecturaPorPedidoSinImportarCuantosHabitos() {
        armar(MIERCOLES_NOCHE_EN_LIMA, PROGRAMA_VIEJO);
        List<Habito> habitos = List.of(habito("A"), habito("B"), habito("C"), habito("D"), habito("E"), habito("F"));
        when(loadRegistroPort.porParticipanteYFecha(participante, MARTES_27)).thenReturn(
                habitos.stream().map(h -> delDia(h, MARTES_27)).toList());
        when(loadHabitoPort.porIds(any())).thenReturn(habitos);

        assertThat(proyeccion.consultarDiaCerradoDe(participante, MARTES_27)).hasSize(6);

        verify(progresoPort, times(1)).deParticipante(participante);
        verify(loadRegistroPort, times(1)).porParticipanteYFecha(participante, MARTES_27);
        verify(loadHabitoPort, times(1)).porIds(any());
        verify(loadHorarioPort, times(1)).porHabitos(any());
        verify(loadGuiaPort, times(1)).porHabitos(any());
        verify(loadPreferenciaPort, times(1)).porParticipanteHabitosYFecha(any(), any(), any());
        verify(loadRenombrePort, times(1)).deParticipante(participante);
        verify(registrosConEvidenciaFinder, times(1)).deEntre(any());
        // Y ninguna racha: un dia cerrado no la lleva, asi que tampoco hay consultas de dias programados.
        verifyNoInteractions(diasProgramadosPort);
    }

    @Test
    @DisplayName("la forma es la de /today, pero un dia cerrado no tiene puntos en juego ni racha")
    void sinPuntosEnJuegoNiRacha() {
        armar(MIERCOLES_NOCHE_EN_LIMA, PROGRAMA_VIEJO);
        Habito meditar = habito("MEDITAR");
        RegistroHabito pendiente = delDia(meditar, MARTES_27);
        when(loadRegistroPort.porParticipanteYFecha(participante, MARTES_27)).thenReturn(List.of(pendiente));
        when(loadHabitoPort.porIds(any())).thenReturn(List.of(meditar));

        TrackDelDiaConCatalogo vista = proyeccion.consultarDiaCerradoDe(participante, MARTES_27).get(0);

        assertThat(vista.registro()).isEqualTo(pendiente);
        assertThat(vista.tituloHabito()).isEqualTo("MEDITAR");
        assertThat(vista.tipoHabito()).isEqualTo(TipoHabito.CHECKBOX);
        assertThat(vista.puntosEnJuego()).isNull();
        assertThat(vista.rachaDias()).isNull();
        // Control: el mismo registro leido por el camino de siempre SI trae puntos en juego (sin horario, el maximo).
        TrackDelDiaConCatalogo deSiempre = proyeccion.consultar(participante, participante, MARTES_27).get(0);
        assertThat(deSiempre.puntosEnJuego()).isNotNull();
        assertThat(deSiempre.rachaDias()).isNotNull();
    }

    @Test
    @DisplayName("la cuenta suspendida es 403 y no lee registros; quien no es participante, 404")
    void suspendidoYSinParticipacion() {
        armar(MIERCOLES_NOCHE_EN_LIMA, PROGRAMA_VIEJO);
        when(progresoPort.deParticipante(participante)).thenReturn(Optional.of(new ProgresoParticipanteHabits(
                12, "America/Lima", RolParticipante.TRAINEE, true, true, PROGRAMA_VIEJO)));

        assertThatThrownBy(() -> proyeccion.consultarDiaCerradoDe(participante, MARTES_27))
                .isInstanceOf(NotAuthorizedException.class);

        when(progresoPort.deParticipante(participante)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> proyeccion.consultarDiaCerradoDe(participante, MARTES_27))
                .isInstanceOf(java.util.NoSuchElementException.class);
        verify(loadRegistroPort, never()).porParticipanteYFecha(any(), any());
    }
}
