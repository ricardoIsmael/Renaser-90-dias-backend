package com.renaser.os.habits.application.services;

import com.renaser.os.evidence.api.RegistrosConTextoFinder;
import com.renaser.os.habits.application.politica.PoliticaClaseDiaria;
import com.renaser.os.habits.application.politica.PoliticaPostDiarioComunidad;
import com.renaser.os.habits.application.politica.PoliticaSantuario;
import com.renaser.os.habits.application.ports.out.desbloqueo.LoadDesbloqueoHabitoPort;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.LoadHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.application.ports.out.registro.SaveRegistroHabitoPort;
import com.renaser.os.habits.domain.model.desbloqueo.DesbloqueoHabito;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.PlantillaHabitoPersonal;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabitoId;
import com.renaser.os.habits.domain.model.registro.EstadoRegistro;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.points.api.AjustarPuntosPort;
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

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Issue #76: un habito que la persona crea (o reactiva) a media manana tiene que tener su registro de HOY en el acto, con
 * la misma regla del armado del dia. {@code RegistroService.generarElDeHoyDe} es esa puerta: pasa por
 * {@code generarInterno}, el mismo codigo del barrido de cada hora, acotado a los habitos pedidos y sin corte por hora.
 *
 * <p>El reloj de todo este archivo marca las 10:00 de Lima de un miercoles (15:00 UTC): el dia YA esta armado desde la
 * madrugada y el barrido no vuelve a pasar. Un caso corre con el reloj en la madrugada UTC (regla 02 §3), cuando el
 * servidor ya esta en el dia siguiente pero Lima todavia no.
 */
@ExtendWith(MockitoExtension.class)
class GenerarElDeHoyDeUnHabitoTest {

    /** Miercoles 4/11/2026, 10:00 en Lima. */
    private static final Instant MEDIA_MANANA = Instant.parse("2026-11-04T15:00:00Z");
    private static final LocalDate HOY_EN_LIMA = LocalDate.of(2026, 11, 4);
        private static final int DIA_DEL_PROGRAMA = 10;

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
    private AjustarPuntosPort ajustarPuntosPort;
    @Mock
    private com.renaser.os.community.api.PublicacionMuroFinder publicacionMuroFinder;
    @Mock
    private LoadDesbloqueoHabitoPort loadDesbloqueoPort;
    @Mock
    private org.springframework.context.ApplicationEventPublisher events;
    @Mock
    private IdGenerator idGenerator;
    @Mock
    private RegistrosConTextoFinder registrosConTexto;

    private final UserId persona = UserId.of(UUID.randomUUID());
    /** Los registros que hay en la base: lo que se inserta se lee despues, como en Postgres. */
    private final List<RegistroHabito> enLaBase = new ArrayList<>();

    @BeforeEach
    void setUp() {
        lenient().when(idGenerator.newId()).thenAnswer(inv -> UUID.randomUUID());
        lenient().when(saveRegistroPort.insertarSiNoExiste(any())).thenAnswer(inv -> enLaBase.add(inv.getArgument(0)));
        lenient().when(loadRegistroPort.porParticipanteYFecha(any(), any())).thenAnswer(inv ->
                enLaBase.stream().filter(r -> r.fechaEjecucion().equals(inv.<LocalDate>getArgument(1))).toList());
        lenient().when(loadHabitoPort.personalesActivosDe(persona)).thenReturn(List.of());
    }

    /** El barrido de esa manana ya armo el dia: hay registros de otros habitos para la fecha. */
    private void elDiaYaEstabaArmado(LocalDate fecha) {
        enLaBase.add(RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), persona,
                HabitoId.of(UUID.randomUUID()), fecha, DIA_DEL_PROGRAMA, TipoDia.delDia(fecha), false, MEDIA_MANANA));
    }

    private long registrosDe(HabitoId habito) {
        return enLaBase.stream().filter(r -> r.habitoId().equals(habito)).count();
    }

    private RegistroService servicioA(Instant ahora) {
        return new RegistroService(loadRegistroPort, saveRegistroPort, loadHabitoPort, loadHorarioPort,
                loadPreferenciaPort, progresoPort, ajustarPuntosPort, publicacionMuroFinder, loadDesbloqueoPort, events,
                FixedClock.at(ahora), idGenerator,
                List.of(new PoliticaSantuario(), new PoliticaPostDiarioComunidad(), new PoliticaClaseDiaria()),
                CatalogoAplicableDePrueba.sinAudiencias(loadHabitoPort), ids -> Set.copyOf(ids), registrosConTexto,
                new com.renaser.os.shared.infrastructure.interruptores.TodoPrendido());
    }

    private RegistroService servicio() {
        return servicioA(MEDIA_MANANA);
    }

    private Habito habitoPropio() {
        return Habito.crearPersonal(HabitoId.of(UUID.randomUUID()), persona, "Leer 20 minutos", TipoHabito.CHECKBOX,
                "MENTE", PlantillaHabitoPersonal.OTRO, "meta", MEDIA_MANANA);
    }

    private Habito habitoDelCatalogo() {
        return Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "Meditar", TipoHabito.CHECKBOX, "MENTE",
                ExigenciaEvidencia.OPCIONAL, MEDIA_MANANA);
    }

    /** Como nace el horario de un habito propio ({@code MisHabitosService.crear}): abierto, TODOS, desde el dia en que se creo. */
    private static HorarioHabito horarioDesdeElDia(HabitoId habito, int dia, LocalTime disparo, LocalTime limite) {
        return HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()), habito, dia, null, TipoDia.TODOS, disparo,
                limite, MEDIA_MANANA);
    }

    /** La persona esta en su dia {@code dia}, en Lima, con el programa activado. */
    private void enElDia(int dia, LocalDate fechaDeInicio) {
        when(progresoPort.deParticipante(persona)).thenReturn(Optional.of(new ProgresoParticipanteHabits(dia,
                "America/Lima", RolParticipante.TRAINEE, false, true, fechaDeInicio)));
    }

    private void conElHabitoPropio(Habito habito, HorarioHabito horario) {
        when(loadHabitoPort.personalesActivosDe(persona)).thenReturn(List.of(habito));
        when(loadHorarioPort.porHabitos(List.of(habito.id()))).thenReturn(List.of(horario));
    }

    // ───────────────────────────── lo que se crea ─────────────────────────────

    @Test
    @DisplayName("Un habito propio creado a media manana tiene su registro de hoy, como lo habria armado el barrido")
    void creadoAMediaMananaSeGeneraHoy() {
        enElDia(DIA_DEL_PROGRAMA, HOY_EN_LIMA.minusDays(DIA_DEL_PROGRAMA - 1));
        elDiaYaEstabaArmado(HOY_EN_LIMA);
        Habito habito = habitoPropio();
        conElHabitoPropio(habito, horarioDesdeElDia(habito.id(), DIA_DEL_PROGRAMA, LocalTime.of(6, 0), null));

        List<RegistroHabito> generados = servicio().generarElDeHoyDe(persona, List.of(habito.id()));

        assertThat(generados).singleElement().satisfies(registro -> {
            assertThat(registro.habitoId()).isEqualTo(habito.id());
            assertThat(registro.participanteId()).isEqualTo(persona);
            assertThat(registro.fechaEjecucion()).isEqualTo(HOY_EN_LIMA);
            assertThat(registro.estado()).as("pendiente, como cualquier otro de hoy").isEqualTo(EstadoRegistro.PENDIENTE);
            assertThat(registro.diaPrograma()).as("el mismo snapshot que armaria el barrido").isEqualTo(DIA_DEL_PROGRAMA);
            assertThat(registro.tipoDia()).isEqualTo(TipoDia.delDia(HOY_EN_LIMA));
        });
        assertThat(registrosDe(habito.id())).isEqualTo(1L);
    }

    /** Es el MISMO armado: el registro es identico al que sale del armado del dia completo para ese habito. */
    @Test
    @DisplayName("El registro es identico al del armado del dia completo (mismo codigo, no una copia)")
    void esIgualAlDelArmadoDelDia() {
        enElDia(DIA_DEL_PROGRAMA, HOY_EN_LIMA.minusDays(DIA_DEL_PROGRAMA - 1));
        Habito habito = habitoPropio();
        conElHabitoPropio(habito, horarioDesdeElDia(habito.id(), DIA_DEL_PROGRAMA, LocalTime.of(6, 0), LocalTime.of(22, 0)));
        RegistroHabito delDia = servicio().generarDiaCompletoEnSuZona(persona).get(0);
        enLaBase.clear();
        elDiaYaEstabaArmado(HOY_EN_LIMA);

        RegistroHabito puntual = servicio().generarElDeHoyDe(persona, List.of(habito.id())).get(0);

        assertThat(puntual).usingRecursiveComparison().ignoringFields("id").isEqualTo(delDia);
    }

    /** D-259: se registra durante su dia aunque se le haya pasado la hora (solo bajan los puntos). */
    @Test
    @DisplayName("D-259: aunque la hora limite de hoy ya paso, el registro se crea y queda pendiente")
    void conLaHoraYaPasadaSeGeneraIgual() {
        enElDia(DIA_DEL_PROGRAMA, HOY_EN_LIMA.minusDays(DIA_DEL_PROGRAMA - 1));
        elDiaYaEstabaArmado(HOY_EN_LIMA);
        Habito habito = habitoPropio();
        // Son las 10:00 y el habito corre de 06:00 a 08:00.
        conElHabitoPropio(habito, horarioDesdeElDia(habito.id(), DIA_DEL_PROGRAMA, LocalTime.of(6, 0), LocalTime.of(8, 0)));

        assertThat(servicio().generarElDeHoyDe(persona, List.of(habito.id())))
                .extracting(RegistroHabito::habitoId).containsExactly(habito.id());
    }

    /**
     * El corte por hora del primer dia del programa es del staff que activa su seguimiento a media tarde
     * ({@code generarDisponiblesAhora}); quien crea o reactiva un habito lo hace a proposito, y no se le arma lo demas.
     */
    @Test
    @DisplayName("Ni el primer dia del programa corta por hora al habito que se acaba de crear")
    void elPrimerDiaDelProgramaTampocoCorta() {
        enElDia(1, HOY_EN_LIMA);
        elDiaYaEstabaArmado(HOY_EN_LIMA);
        Habito habito = habitoPropio();
        conElHabitoPropio(habito, horarioDesdeElDia(habito.id(), 1, LocalTime.of(6, 0), LocalTime.of(8, 0)));

        assertThat(servicio().generarDisponiblesAhora(persona))
                .as("control: el armado del primer dia SI descarta lo que ya cerro").isEmpty();
        assertThat(servicio().generarElDeHoyDe(persona, List.of(habito.id())))
                .extracting(RegistroHabito::habitoId).containsExactly(habito.id());
    }

    /** Regla 02 §3: a las 03:00 UTC el servidor ya esta en el 4, pero en Lima todavia es el 3 a las 22:00. */
    @Test
    @DisplayName("El dia es el de su zona: con el reloj en la madrugada UTC se crea el registro del dia de Lima")
    void conElRelojEnLaMadrugadaUtcEsElDiaDeLima() {
        enElDia(DIA_DEL_PROGRAMA, LocalDate.of(2026, 10, 25));
        elDiaYaEstabaArmado(LocalDate.of(2026, 11, 3));
        Habito habito = habitoPropio();
        conElHabitoPropio(habito, horarioDesdeElDia(habito.id(), DIA_DEL_PROGRAMA, LocalTime.of(6, 0), null));

        List<RegistroHabito> generados = servicioA(Instant.parse("2026-11-04T03:00:00Z"))
                .generarElDeHoyDe(persona, List.of(habito.id()));

        assertThat(generados).singleElement().satisfies(registro ->
                assertThat(registro.fechaEjecucion()).as("el 3 de Lima, no el 4 del servidor")
                        .isEqualTo(LocalDate.of(2026, 11, 3)));
    }

    // ───────────────────────────── el dia que ni se armo ─────────────────────────────

    /**
     * Un registro suelto en un dia vacio haria que el dia pareciera armado: ni la red de seguridad de
     * {@code GET /habit-tracks/today} (mira si viene vacio) ni el barrido (se salta a quien ya tiene registros) lo
     * completarian, y la persona veria solo ese habito hasta manana.
     */
    @Test
    @DisplayName("Si el dia ni se armo, se arma entero (no queda solo el habito nuevo) y el habito sale una vez")
    void siElDiaNoSeArmoSeArmaEntero() {
        enElDia(DIA_DEL_PROGRAMA, HOY_EN_LIMA.minusDays(DIA_DEL_PROGRAMA - 1));
        Habito habito = habitoPropio();
        Habito delPrograma = habitoDelCatalogo();
        when(loadHabitoPort.catalogoActivo()).thenReturn(List.of(delPrograma));
        when(loadHabitoPort.personalesActivosDe(persona)).thenReturn(List.of(habito));
        when(loadHorarioPort.porHabitos(List.of(delPrograma.id(), habito.id()))).thenReturn(List.of(
                horarioDesdeElDia(delPrograma.id(), 1, LocalTime.of(7, 0), null),
                horarioDesdeElDia(habito.id(), DIA_DEL_PROGRAMA, LocalTime.of(6, 0), null)));
        when(loadHorarioPort.porHabitos(List.of(habito.id()))).thenReturn(List.of(
                horarioDesdeElDia(habito.id(), DIA_DEL_PROGRAMA, LocalTime.of(6, 0), null)));

        servicio().generarElDeHoyDe(persona, List.of(habito.id()));

        assertThat(registrosDe(delPrograma.id())).as("el resto del dia tambien quedo armado").isEqualTo(1L);
        assertThat(registrosDe(habito.id())).as("y el habito nuevo, una sola vez").isEqualTo(1L);
    }

    // ───────────────────────────── lo que NO se crea ─────────────────────────────

    @Test
    @DisplayName("Idempotente: si el registro de hoy ya existe (corrio el barrido, o dos toques) no se duplica")
    void siYaExisteNoSeDuplica() {
        enElDia(DIA_DEL_PROGRAMA, HOY_EN_LIMA.minusDays(DIA_DEL_PROGRAMA - 1));
        Habito habito = habitoPropio();
        conElHabitoPropio(habito, horarioDesdeElDia(habito.id(), DIA_DEL_PROGRAMA, LocalTime.of(6, 0), null));
        enLaBase.add(RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), persona, habito.id(), HOY_EN_LIMA,
                DIA_DEL_PROGRAMA, TipoDia.delDia(HOY_EN_LIMA), false, MEDIA_MANANA));

        assertThat(servicio().generarElDeHoyDe(persona, List.of(habito.id()))).isEmpty();
        assertThat(servicio().generarElDeHoyDe(persona, List.of(habito.id()))).isEmpty();

        assertThat(registrosDe(habito.id())).isEqualTo(1L);
    }

    @Test
    @DisplayName("Si otro pedido lo creo a la vez, la UNIQUE lo resuelve: no falla y no lo cuenta dos veces")
    void siOtroPedidoLoCreoALaVezNoFalla() {
        enElDia(DIA_DEL_PROGRAMA, HOY_EN_LIMA.minusDays(DIA_DEL_PROGRAMA - 1));
        elDiaYaEstabaArmado(HOY_EN_LIMA);
        Habito habito = habitoPropio();
        conElHabitoPropio(habito, horarioDesdeElDia(habito.id(), DIA_DEL_PROGRAMA, LocalTime.of(6, 0), null));
        // doReturn y no when(...): when() invocaria la respuesta de arriba con un nulo (regla 03, «Mocks»).
        doReturn(false).when(saveRegistroPort).insertarSiNoExiste(any());

        assertThat(servicio().generarElDeHoyDe(persona, List.of(habito.id()))).isEmpty();
    }

    @Test
    @DisplayName("Dia 0: el programa no empezo, no hay registro de hoy (el habito arranca el dia 1)")
    void enElDiaCeroNoSeCreaNada() {
        enElDia(0, HOY_EN_LIMA.plusDays(1));
        Habito habito = habitoPropio();
        // Asi lo guarda MisHabitosService.crear en el dia 0: el horario arranca el dia 1.
        conElHabitoPropio(habito, horarioDesdeElDia(habito.id(), 1, LocalTime.of(6, 0), null));

        assertThat(servicio().generarElDeHoyDe(persona, List.of(habito.id()))).isEmpty();

        assertThat(enLaBase).isEmpty();
    }

    @Test
    @DisplayName("Un dia que el habito no corre (apagado ese dia de la semana) no tiene registro")
    void unDiaQueNoLeTocaNoSeCreaNada() {
        enElDia(DIA_DEL_PROGRAMA, HOY_EN_LIMA.minusDays(DIA_DEL_PROGRAMA - 1));
        elDiaYaEstabaArmado(HOY_EN_LIMA);
        Habito habito = habitoPropio();
        conElHabitoPropio(habito, horarioDesdeElDia(habito.id(), DIA_DEL_PROGRAMA, LocalTime.of(6, 0), null));
        when(loadPreferenciaPort.habitosApagadosEnDiaSemana(persona, DayOfWeek.WEDNESDAY))
                .thenReturn(List.of(habito.id()));

        assertThat(servicio().generarElDeHoyDe(persona, List.of(habito.id()))).isEmpty();

        assertThat(registrosDe(habito.id())).isZero();
    }

    @Test
    @DisplayName("Apagado solo para hoy (por fecha) no tiene registro")
    void apagadoParaHoyNoSeCreaNada() {
        enElDia(DIA_DEL_PROGRAMA, HOY_EN_LIMA.minusDays(DIA_DEL_PROGRAMA - 1));
        elDiaYaEstabaArmado(HOY_EN_LIMA);
        Habito habito = habitoPropio();
        conElHabitoPropio(habito, horarioDesdeElDia(habito.id(), DIA_DEL_PROGRAMA, LocalTime.of(6, 0), null));
        when(loadPreferenciaPort.habitosApagadosEn(persona, HOY_EN_LIMA)).thenReturn(List.of(habito.id()));

        assertThat(servicio().generarElDeHoyDe(persona, List.of(habito.id()))).isEmpty();
    }

    @Test
    @DisplayName("En pausa no tiene registro de hoy")
    void enPausaNoSeCreaNada() {
        enElDia(DIA_DEL_PROGRAMA, HOY_EN_LIMA.minusDays(DIA_DEL_PROGRAMA - 1));
        elDiaYaEstabaArmado(HOY_EN_LIMA);
        Habito habito = habitoPropio();
        conElHabitoPropio(habito, horarioDesdeElDia(habito.id(), DIA_DEL_PROGRAMA, LocalTime.of(6, 0), null));
        when(loadDesbloqueoPort.deParticipante(persona)).thenReturn(List.of(DesbloqueoHabito.rehydrate(persona,
                habito.id(), DIA_DEL_PROGRAMA, MEDIA_MANANA, MEDIA_MANANA, MEDIA_MANANA, MEDIA_MANANA)));

        assertThat(servicio().generarElDeHoyDe(persona, List.of(habito.id()))).isEmpty();
    }

    @Test
    @DisplayName("Un habito elegido para mas adelante sigue esperando su dia")
    void elegidoParaMasAdelanteNoSeCreaNada() {
        enElDia(DIA_DEL_PROGRAMA, HOY_EN_LIMA.minusDays(DIA_DEL_PROGRAMA - 1));
        elDiaYaEstabaArmado(HOY_EN_LIMA);
        Habito habito = habitoDelCatalogo();
        when(loadHabitoPort.catalogoActivo()).thenReturn(List.of(habito));
        when(loadHorarioPort.porHabitos(List.of(habito.id())))
                .thenReturn(List.of(horarioDesdeElDia(habito.id(), 1, LocalTime.of(6, 0), null)));
        when(loadDesbloqueoPort.deParticipante(persona)).thenReturn(List.of(DesbloqueoHabito.rehydrate(persona,
                habito.id(), DIA_DEL_PROGRAMA + 5, MEDIA_MANANA, MEDIA_MANANA, MEDIA_MANANA)));

        assertThat(servicio().generarElDeHoyDe(persona, List.of(habito.id()))).isEmpty();
    }

    // ───────────────────────────── solo lo pedido ─────────────────────────────

    /** No arma "el resto del dia": solo los habitos pedidos, aunque a otros les falte su registro. */
    @Test
    @DisplayName("Con el dia armado, solo crea el registro de los habitos pedidos y no el de los demas que le falten")
    void soloCreaLosPedidos() {
        enElDia(DIA_DEL_PROGRAMA, HOY_EN_LIMA.minusDays(DIA_DEL_PROGRAMA - 1));
        elDiaYaEstabaArmado(HOY_EN_LIMA);
        Habito propio = habitoPropio();
        Habito delCatalogo = habitoDelCatalogo();
        when(loadHabitoPort.catalogoActivo()).thenReturn(List.of(delCatalogo));
        conElHabitoPropio(propio, horarioDesdeElDia(propio.id(), DIA_DEL_PROGRAMA, LocalTime.of(6, 0), null));

        List<RegistroHabito> generados = servicio().generarElDeHoyDe(persona, List.of(propio.id()));

        assertThat(generados).extracting(RegistroHabito::habitoId).containsExactly(propio.id());
        assertThat(registrosDe(delCatalogo.id())).isZero();
    }

    @Test
    @DisplayName("Un id que no es del catalogo ni un habito propio de la persona no crea nada")
    void unIdAjenoNoCreaNada() {
        enElDia(DIA_DEL_PROGRAMA, HOY_EN_LIMA.minusDays(DIA_DEL_PROGRAMA - 1));
        elDiaYaEstabaArmado(HOY_EN_LIMA);
        when(loadHabitoPort.personalesActivosDe(persona)).thenReturn(List.of(habitoPropio()));

        assertThat(servicio().generarElDeHoyDe(persona, List.of(HabitoId.of(UUID.randomUUID())))).isEmpty();

        assertThat(enLaBase).hasSize(1);
    }

    @Test
    @DisplayName("Sin habitos que pedir no lee nada")
    void sinHabitosNoLeeNada() {
        assertThat(servicio().generarElDeHoyDe(persona, List.of())).isEmpty();

        verifyNoInteractions(progresoPort, loadHabitoPort, saveRegistroPort, loadRegistroPort);
    }

    @Test
    @DisplayName("Una cuenta suspendida no arma nada")
    void unaCuentaSuspendidaNoArmaNada() {
        when(progresoPort.deParticipante(persona)).thenReturn(Optional.of(new ProgresoParticipanteHabits(
                DIA_DEL_PROGRAMA, "America/Lima", RolParticipante.TRAINEE, true, true)));

        assertThatThrownBy(() -> servicio().generarElDeHoyDe(persona, List.of(HabitoId.of(UUID.randomUUID()))))
                .isInstanceOf(NotAuthorizedException.class);

        assertThat(enLaBase).isEmpty();
    }
}
