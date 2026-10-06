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
import com.renaser.os.habits.domain.model.desbloqueo.DesbloqueoHabito;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabitoId;
import com.renaser.os.habits.domain.model.registro.DiaProgramado;
import com.renaser.os.habits.domain.model.registro.EstadoRegistro;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.points.api.AjustarPuntosPort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V-5 (D-180): cuanto le cuesta a la base {@code GET /habit-tracks/today}, con la proyeccion y el
 * {@link RegistroService} REALES conectados entre si —solo los puertos de salida son mocks—, que es
 * la unica forma de contar lo que el pedido entero consulta.
 *
 * <p>Contra el codigo anterior fallan: la lectura del progreso (salia 3 veces por pedido), las
 * consultas por habito de la generacion (dos por habito: registro existente y horarios) y la
 * transaccion de solo lectura (no habia ninguna).
 *
 * <p>El reloj esta a las 01:50 UTC del 10/09: en Lima (UTC-5) todavia son las 20:50 del 9. Es la
 * franja donde la fecha del servidor y la del aprendiz no coinciden (regla 02, E-91/E-105).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TracksDeHoyConsultasTest {

    private static final Instant NOCHE_EN_LIMA = Instant.parse("2026-09-10T01:50:00Z");
    private static final LocalDate HOY_EN_LIMA = LocalDate.of(2026, 9, 9);
    private static final int DIA_PROGRAMA = 5;

    @Mock
    private LoadRegistroHabitoPort loadRegistroPort;
    @Mock
    private SaveRegistroHabitoPort saveRegistroPort;
    @Mock
    private LoadHabitoPort loadHabitoPort;
    @Mock
    private LoadHorarioHabitoPort loadHorarioPort;
    @Mock
    private LoadPreferenciaHorarioPort loadPreferenciaPort;
    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock
    private LoadDesbloqueoHabitoPort loadDesbloqueoPort;
    @Mock
    private LoadGuiaHabitoPort loadGuiaPort;
    @Mock
    private RegistrosConEvidenciaFinder registrosConEvidenciaFinder;
    @Mock
    private LoadRenombreHabitoPort loadRenombrePort;
    @Mock
    private IdGenerator idGenerator;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private ConsultarDiasProgramadosPort diasProgramadosPort;

    private final UserId participante = UserId.of(UUID.randomUUID());
    private RegistroService registros;
    private TracksDelDiaProyeccionService proyeccion;

    @BeforeEach
    void setUp() {
        FixedClock reloj = FixedClock.at(NOCHE_EN_LIMA);
        registros = new RegistroService(loadRegistroPort, saveRegistroPort, loadHabitoPort, loadHorarioPort,
                loadPreferenciaPort, progresoPort, org.mockito.Mockito.mock(AjustarPuntosPort.class),
                org.mockito.Mockito.mock(PublicacionMuroFinder.class), loadDesbloqueoPort,
                org.mockito.Mockito.mock(ApplicationEventPublisher.class), reloj, idGenerator,
                List.of(new PoliticaSantuario(), new PoliticaPostDiarioComunidad(), new PoliticaClaseDiaria()));
        proyeccion = new TracksDelDiaProyeccionService(registros, registros, loadHabitoPort, loadHorarioPort,
                loadPreferenciaPort, loadGuiaPort, registrosConEvidenciaFinder, loadRenombrePort, new MedicionesDelDia(java.util.List.of(), (p, c, h) -> java.util.Map.of()),
                new RachasDeHabitos(diasProgramadosPort), reloj,
                transactionManager);
        when(idGenerator.newId()).thenAnswer(inv -> UUID.randomUUID());
        when(saveRegistroPort.insertarSiNoExiste(any())).thenReturn(true);
        when(progresoPort.deParticipante(participante)).thenReturn(Optional.of(new ProgresoParticipanteHabits(
                DIA_PROGRAMA, "America/Lima", RolParticipante.TRAINEE, false, true)));
    }

    @Test
    @DisplayName("camino normal: el progreso se lee UNA vez, en el dia de Lima, y no se escribe nada")
    void elCaminoNormalLeeElProgresoUnaVezYNoEscribe() {
        Habito habito = habito("MEDITAR");
        RegistroHabito deHoy = RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), participante,
                habito.id(), HOY_EN_LIMA, DIA_PROGRAMA, TipoDia.TODOS, false, NOCHE_EN_LIMA);
        when(loadRegistroPort.porParticipanteYFecha(participante, HOY_EN_LIMA)).thenReturn(List.of(deHoy));
        when(loadHabitoPort.porIds(any())).thenReturn(List.of(habito));

        List<TrackDelDiaConCatalogo> vista = proyeccion.consultarHoyDe(participante);

        assertThat(vista).extracting(TrackDelDiaConCatalogo::tituloHabito).containsExactly("MEDITAR");
        verify(progresoPort, times(1)).deParticipante(participante);
        // La fecha es la de Lima, no la del servidor (ya es 10/09 en UTC).
        verify(loadRegistroPort, never()).porParticipanteYFecha(participante, HOY_EN_LIMA.plusDays(1));
        verify(saveRegistroPort, never()).insertarSiNoExiste(any());
        verify(loadHabitoPort, never()).catalogoActivo();
    }

    @Test
    @DisplayName("camino normal: todo corre en UNA transaccion de solo lectura")
    void elCaminoNormalEsUnaSolaTransaccionDeSoloLectura() {
        Habito habito = habito("MEDITAR");
        when(loadRegistroPort.porParticipanteYFecha(participante, HOY_EN_LIMA)).thenReturn(List.of(
                RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), participante, habito.id(),
                        HOY_EN_LIMA, DIA_PROGRAMA, TipoDia.TODOS, false, NOCHE_EN_LIMA)));
        when(loadHabitoPort.porIds(any())).thenReturn(List.of(habito));

        proyeccion.consultarHoyDe(participante);

        ArgumentCaptor<TransactionDefinition> definiciones = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager, times(1)).getTransaction(definiciones.capture());
        assertThat(definiciones.getValue().isReadOnly()).isTrue();
    }

    @Test
    @DisplayName("dia vacio: la generacion corre FUERA de la lectura, y despues se relee")
    void laGeneracionNoSeAnidaDentroDeLaLectura() {
        Habito conCierreTarde = habito("LEER");
        when(loadHabitoPort.catalogoActivo()).thenReturn(List.of(conCierreTarde));
        when(loadHorarioPort.porHabitos(any())).thenReturn(List.of(horario(conCierreTarde, 1, LocalTime.of(22, 0))));

        proyeccion.consultarHoyDe(participante);

        InOrder orden = inOrder(transactionManager, saveRegistroPort);
        orden.verify(transactionManager).getTransaction(any());
        orden.verify(transactionManager).commit(any());
        orden.verify(saveRegistroPort).insertarSiNoExiste(any());
        orden.verify(transactionManager).getTransaction(any());
        orden.verify(transactionManager).commit(any());
    }

    /**
     * La decision de la generacion es la de siempre, habito por habito; lo unico que cambia es que
     * lo existente y los horarios llegan en dos consultas de lote. Un caso por cada motivo para NO
     * generar, y uno que si se genera.
     */
    @Test
    @DisplayName("generacion a demanda: dos consultas de lote y el mismo resultado que habito por habito")
    void laGeneracionADemandaUsaConsultasDeLoteYDecideIgual() {
        Habito yaExiste = habito("YA EXISTE");
        Habito paraMasAdelante = habito("PARA EL DIA 9");
        Habito yaCerro = habito("CERRO A LAS 19");
        Habito sinHorario = habito("SIN HORARIO");
        Habito todaviaNoEmpieza = habito("EMPIEZA EL DIA 10");
        Habito alcanzable = habito("CIERRA A LAS 22");
        when(loadHabitoPort.catalogoActivo()).thenReturn(
                List.of(yaExiste, paraMasAdelante, yaCerro, sinHorario, todaviaNoEmpieza, alcanzable));
        when(loadRegistroPort.porParticipanteYFecha(participante, HOY_EN_LIMA)).thenReturn(List.of(
                RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), participante, yaExiste.id(),
                        HOY_EN_LIMA, DIA_PROGRAMA, TipoDia.TODOS, false, NOCHE_EN_LIMA)));
        when(loadDesbloqueoPort.deParticipante(participante)).thenReturn(List.of(DesbloqueoHabito.rehydrate(
                participante, paraMasAdelante.id(), 9, NOCHE_EN_LIMA, NOCHE_EN_LIMA, NOCHE_EN_LIMA)));
        when(loadHorarioPort.porHabitos(any())).thenReturn(List.of(
                horario(yaExiste, 1, null), horario(paraMasAdelante, 1, null),
                horario(yaCerro, 1, LocalTime.of(19, 0)), horario(todaviaNoEmpieza, 10, null),
                horario(alcanzable, 1, LocalTime.of(22, 0))));

        List<RegistroHabito> generados = registros.generarDisponiblesAhora(participante);

        assertThat(generados).extracting(RegistroHabito::habitoId).containsExactly(alcanzable.id());
        assertThat(generados.get(0).fechaEjecucion()).isEqualTo(HOY_EN_LIMA);
        verify(loadRegistroPort, times(1)).porParticipanteYFecha(participante, HOY_EN_LIMA);
        verify(loadHorarioPort, times(1)).porHabitos(any());
        verify(loadRegistroPort, never()).porParticipanteHabitoYFecha(any(), any(), any());
        verify(loadHorarioPort, never()).porHabito(any());
        verify(progresoPort, times(1)).deParticipante(participante);
    }

    /** El barrido nocturno no corta por hora: el que cierra a las 19 tambien entra. */
    @Test
    @DisplayName("barrido nocturno: mismo lote, sin corte por hora")
    void elBarridoNocturnoGeneraElDiaCompleto() {
        Habito yaCerro = habito("CERRO A LAS 19");
        Habito alcanzable = habito("CIERRA A LAS 22");
        when(loadHabitoPort.catalogoActivo()).thenReturn(List.of(yaCerro, alcanzable));
        when(loadHorarioPort.porHabitos(any())).thenReturn(List.of(
                horario(yaCerro, 1, LocalTime.of(19, 0)), horario(alcanzable, 1, LocalTime.of(22, 0))));

        List<RegistroHabito> generados = registros.generarDiaCompletoEnSuZona(participante);

        assertThat(generados).extracting(RegistroHabito::habitoId)
                .containsExactlyInAnyOrder(yaCerro.id(), alcanzable.id());
        verify(loadHorarioPort, never()).porHabito(any());
    }

    /**
     * D-254 a las 01:50 UTC: la racha se pide con el HOY de Lima (el 9), no con la fecha del servidor
     * (ya el 10). Con el 10 como hoy, el pendiente del 9 seria un dia terminado sin cumplir y la racha
     * de todo el padron caeria a 0 cada noche desde las 19:00 de Lima (familia E-91/E-105).
     */
    @Test
    @DisplayName("racha: a las 01:50 UTC se calcula con el hoy de Lima, y el pendiente de hoy no corta")
    void laRachaUsaElHoyDeLima() {
        Habito habito = habito("MEDITAR");
        LocalDate inicio = HOY_EN_LIMA.minusDays(DIA_PROGRAMA - 1L); // Dia 5 el 9/09: arranco el 5/09 (regla 03)
        conInicio(inicio);
        when(loadRegistroPort.porParticipanteYFecha(participante, HOY_EN_LIMA)).thenReturn(List.of(deHoy(habito)));
        when(loadHabitoPort.porIds(any())).thenReturn(List.of(habito));
        when(diasProgramadosPort.deHabitosEntre(any(), any(), any(), any())).thenReturn(Map.of(habito.id(), List.of(
                new DiaProgramado(HOY_EN_LIMA, EstadoRegistro.PENDIENTE, false),
                new DiaProgramado(HOY_EN_LIMA.minusDays(1), EstadoRegistro.COMPLETADO, false),
                new DiaProgramado(HOY_EN_LIMA.minusDays(2), EstadoRegistro.COMPLETADO, false))));

        List<TrackDelDiaConCatalogo> vista = proyeccion.consultarHoyDe(participante);

        assertThat(vista).extracting(TrackDelDiaConCatalogo::rachaDias).containsExactly(2);
        verify(diasProgramadosPort).deHabitosEntre(participante, Set.of(habito.id()), inicio, HOY_EN_LIMA);
        verify(progresoPort, times(1)).deParticipante(participante);
    }

    @Test
    @DisplayName("racha: UNA consulta de lote para todos los habitos del dia, nunca una por habito")
    void laRachaEsUnaConsultaDeLote() {
        Habito meditar = habito("MEDITAR");
        Habito leer = habito("LEER");
        conInicio(HOY_EN_LIMA.minusDays(DIA_PROGRAMA - 1L));
        when(loadRegistroPort.porParticipanteYFecha(participante, HOY_EN_LIMA))
                .thenReturn(List.of(deHoy(meditar), deHoy(leer)));
        when(loadHabitoPort.porIds(any())).thenReturn(List.of(meditar, leer));

        List<TrackDelDiaConCatalogo> vista = proyeccion.consultarHoyDe(participante);

        assertThat(vista).extracting(TrackDelDiaConCatalogo::rachaDias).containsExactly(0, 0);
        verify(diasProgramadosPort, times(1)).deHabitosEntre(any(), any(), any(), any());
        verify(diasProgramadosPort).deHabitosEntre(participante, Set.of(meditar.id(), leer.id()),
                HOY_EN_LIMA.minusDays(DIA_PROGRAMA - 1L), HOY_EN_LIMA);
    }

    private void conInicio(LocalDate inicio) {
        when(progresoPort.deParticipante(participante)).thenReturn(Optional.of(new ProgresoParticipanteHabits(
                DIA_PROGRAMA, "America/Lima", RolParticipante.TRAINEE, false, true, inicio)));
    }

    private RegistroHabito deHoy(Habito habito) {
        return RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), participante, habito.id(), HOY_EN_LIMA,
                DIA_PROGRAMA, TipoDia.TODOS, false, NOCHE_EN_LIMA);
    }

    private Habito habito(String titulo) {
        return Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), titulo, TipoHabito.CHECKBOX, "MENTE",
                ExigenciaEvidencia.OPCIONAL, NOCHE_EN_LIMA);
    }

    private static HorarioHabito horario(Habito habito, int diaInicio, LocalTime horaLimite) {
        return HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()), habito.id(), diaInicio, null,
                TipoDia.TODOS, LocalTime.of(7, 0), horaLimite, NOCHE_EN_LIMA);
    }
}
