package com.renaser.os.habits.application.services;

import com.renaser.os.community.api.PublicacionMuroFinder;
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
import com.renaser.os.habits.domain.model.habito.DetallesHabito;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabitoId;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.points.api.AjustarPuntosPort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * D-169 en la generacion del dia: el snapshot {@code es_opcional} de cada registro sale de
 * {@code Habito.esOpcionalEnDia(diaPrograma)} y no del catalogo. Contra el codigo anterior (que
 * copiaba {@code habito.esOpcional()}) fallan los casos de dia 8: el jugo verde salia exigible.
 *
 * <p><b>El reloj esta a las 03:00 UTC a proposito</b> (regla 02 §3): en Lima todavia es el 9 a las
 * 22:00, un dia ANTES que la fecha UTC. El registro tiene que quedar en la fecha de Lima. El numero
 * del dia lo da {@code users} ya derivado en esa zona; ese tramo se prueba contra Postgres real en
 * {@code CicloIntoxicacionGeneracionIT}.
 */
@ExtendWith(MockitoExtension.class)
class GenerarTracksCicloIntoxicacionTest {

    private static final FixedClock TRES_AM_UTC = FixedClock.at(Instant.parse("2026-09-10T03:00:00Z"));
    /** El miercoles 9 en Lima: la fecha del participante, no la del servidor. */
    private static final LocalDate HOY_EN_LIMA = LocalDate.of(2026, 9, 9);

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
    private PublicacionMuroFinder publicacionMuroFinder;
    @Mock
    private LoadDesbloqueoHabitoPort loadDesbloqueoPort;
    @Mock
    private ApplicationEventPublisher events;
    @Mock
    private IdGenerator idGenerator;
    @Mock
    private PlatformTransactionManager transactionManager;

    private RegistroService service;
    private final UserId participante = UserId.of(UUID.randomUUID());
    private final Habito jugoVerde = deCatalogo("JUGO VERDE", false, false);
    private final Habito postDiario = deCatalogo("POST DIARIO EN COMUNIDAD", false, true);
    private final Habito diaSinCelular = deCatalogo("DIA SIN CELULAR", true, false);

    @BeforeEach
    void setUp() {
        service = new RegistroService(loadRegistroPort, saveRegistroPort, loadHabitoPort, loadHorarioPort,
                loadPreferenciaPort, progresoPort, ajustarPuntosPort, publicacionMuroFinder, loadDesbloqueoPort, events,
                TRES_AM_UTC, idGenerator,
                List.of(new PoliticaSantuario(), new PoliticaPostDiarioComunidad(), new PoliticaClaseDiaria()),
                transactionManager);
        lenient().when(idGenerator.newId()).thenAnswer(inv -> UUID.randomUUID());
        lenient().when(saveRegistroPort.insertarSiNoExiste(any())).thenReturn(true);
        when(loadHabitoPort.catalogoActivo()).thenReturn(List.of(jugoVerde, postDiario, diaSinCelular));
        for (Habito habito : List.of(jugoVerde, postDiario, diaSinCelular)) {
            when(loadHorarioPort.porHabito(habito.id())).thenReturn(List.of(todosLosDias(habito)));
        }
    }

    @Test
    @DisplayName("dia 8 (VER): todo pasa a opcional salvo el post diario, en la fecha de Lima")
    void enElPrimerDiaDeUnCicloTodoEsOpcionalSalvoElPost() {
        Map<HabitoId, RegistroHabito> generados = generarDelDia(8);

        assertThat(generados.get(jugoVerde.id()).esOpcional()).isTrue();
        assertThat(generados.get(postDiario.id()).esOpcional()).isFalse();
        assertThat(generados.get(diaSinCelular.id()).esOpcional()).isTrue();
        assertThat(generados.values()).allSatisfy(registro -> {
            assertThat(registro.fechaEjecucion()).isEqualTo(HOY_EN_LIMA);
            assertThat(registro.diaPrograma()).isEqualTo(8);
            // El tipo de dia NO pasa a INTOXICACION: con el se vuelve a buscar el horario vigente
            // (ventana, avisos, Santuario), y un DISCIPLINA dejaria de encontrarse.
            assertThat(registro.tipoDia()).isEqualTo(TipoDia.DISCIPLINA);
        });
    }

    @Test
    @DisplayName("dia 7: dia normal, cada habito conserva la opcionalidad de su catalogo")
    void elDiaAnteriorAlCicloEsUnDiaNormal() {
        Map<HabitoId, RegistroHabito> generados = generarDelDia(7);

        assertThat(generados.get(jugoVerde.id()).esOpcional()).isFalse();
        assertThat(generados.get(postDiario.id()).esOpcional()).isFalse();
        assertThat(generados.get(diaSinCelular.id()).esOpcional()).isTrue();
    }

    @Test
    @DisplayName("dia 28 (ultimo de RENASER) opcional; dia 29 vuelve a ser exigible")
    void elUltimoDiaDelUltimoCicloYElSiguiente() {
        assertThat(generarDelDia(28).get(jugoVerde.id()).esOpcional()).isTrue();
        assertThat(generarDelDia(29).get(jugoVerde.id()).esOpcional()).isFalse();
    }

    /** El barrido nocturno: jornada completa de HOY en la zona del participante. */
    private Map<HabitoId, RegistroHabito> generarDelDia(int diaPrograma) {
        when(progresoPort.deParticipante(participante)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(diaPrograma, "America/Lima", RolParticipante.TRAINEE, false, true)));
        return service.generarDiaCompletoEnSuZona(participante).stream()
                .collect(Collectors.toMap(RegistroHabito::habitoId, registro -> registro));
    }

    private static Habito deCatalogo(String titulo, boolean esOpcional, boolean obligatorioEnIntoxicacion) {
        return Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), titulo, TipoHabito.CHECKBOX,
                new DetallesHabito(null, "CUERPO", ExigenciaEvidencia.OPCIONAL, esOpcional, obligatorioEnIntoxicacion),
                TRES_AM_UTC.now());
    }

    /** Como el catalogo real (V4): del dia 1 al 90, tipo TODOS. */
    private static HorarioHabito todosLosDias(Habito habito) {
        return HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()), habito.id(), 1, 90, TipoDia.TODOS,
                LocalTime.of(9, 0), null, TRES_AM_UTC.now());
    }
}
