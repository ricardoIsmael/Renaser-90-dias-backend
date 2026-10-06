package com.renaser.os.habits.infrastructure.adapter.in.scheduler;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase.TrackDelDiaConCatalogo;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.test.util.AopTestUtils;

import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E-534 — el barrido que pasa a {@code EXPIRADO} lo {@code PENDIENTE} de los dias que ya terminaron, de punta a
 * punta contra Postgres real.
 *
 * <p><b>Como se prueba sin depender de como esta escrito el barrido.</b> El reloj avanza de hora en hora, y en
 * cada hora se disparan los {@code @Scheduled} de {@link ExpirarRegistrosScheduler} cuyo cron de PRODUCCION cae
 * en esa hora (el que esta escrito en la anotacion, o su valor por defecto si es una propiedad). Asi la misma
 * prueba describe el barrido viejo (una vez por dia, 05:00 UTC, con la fecha UTC) y el nuevo (cada hora, con el
 * dia local de cada participante), sin llamar a ningun metodo que exista en uno solo de los dos.
 *
 * <p><b>Las de Lima son caracterizacion</b> (todo el padron vive en {@code America/Lima}): pasan con el barrido de
 * antes y con el de ahora, hora por hora. Es la garantia de que para el padron real nada cambia. <b>Las de
 * {@code America/Los_Angeles} (UTC−8 en noviembre), {@code Africa/Lagos} (UTC+1), la de correr tarde y la del
 * participante que falla</b> fallan contra el barrido de antes: es E-534.
 *
 * <p>Fechas de noviembre de 2026 a proposito: Los Angeles ya salio del horario de verano (1/11) y esta a UTC−8.
 * Fixture coherente (regla 03): el programa arranco el 30/10, asi que el 9/11 es el Dia 11 y el 10/11 el Dia 12.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, ExpiracionPorZonaIT.RelojDeLaPrueba.class})
class ExpiracionPorZonaIT {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final ZoneId LOS_ANGELES = ZoneId.of("America/Los_Angeles");
    private static final ZoneId LAGOS = ZoneId.of("Africa/Lagos");
    private static final LocalDate INICIO = LocalDate.of(2026, 10, 30);
    /** 05:00 UTC del 9/11: medianoche de Lima, la hora a la que el barrido de antes corria. */
    private static final Instant ARRANQUE = Instant.parse("2026-11-09T05:00:00Z");
    /** Los habitos reales del catalogo (V4/V26): DESPERTAR sin horario, DORMIR a las 22:30 sin hora limite. */
    private static final UUID DESPERTAR = UUID.fromString("899a2151-e98c-4b61-a46c-b55134240d17");
    private static final UUID DORMIR = UUID.fromString("48d68c12-72a9-428c-a8c6-02b9b01bc2fe");
    private static final Instant COMPLETADO_ANTES = Instant.parse("2026-11-08T14:00:00Z");

    /** El reloj que la prueba mueve: lo usan el barrido, completar y la agenda, como en produccion. */
    @TestConfiguration
    static class RelojDeLaPrueba {
        @Bean
        @Primary
        RelojQueAvanza relojQueAvanza() {
            return new RelojQueAvanza(ARRANQUE);
        }
    }

    static final class RelojQueAvanza implements Clock {
        private final AtomicReference<Instant> ahora;

        RelojQueAvanza(Instant inicial) {
            this.ahora = new AtomicReference<>(inicial);
        }

        void fijar(Instant instante) {
            ahora.set(instante);
        }

        @Override
        public Instant now() {
            return ahora.get();
        }

        @Override
        public LocalDate today() {
            return ahora.get().atZone(ZoneOffset.UTC).toLocalDate();
        }
    }

    /** Una fila PENDIENTE sembrada: con la zona de su participante, para saber que deberia pasarle a cada hora. */
    private record Pendiente(UUID id, ZoneId zona, LocalDate fecha) {
    }

    /** Lo que el barrido no puede tocar de una fila que no esta PENDIENTE. */
    private record Foto(String estado, Timestamp completadoEn, int puntos, Timestamp actualizadoEn) {
    }

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ApplicationContext contexto;
    @Autowired
    private RelojQueAvanza reloj;
    @Autowired
    private CompletarRegistroUseCase completar;
    @Autowired
    private ConsultarTracksDelDiaConCatalogoUseCase agenda;

    private final List<UUID> usuarios = new ArrayList<>();
    private final List<UUID> habitos = new ArrayList<>();
    private final List<Pendiente> pendientes = new ArrayList<>();
    private UUID habito;

    @BeforeEach
    void preparar() {
        reloj.fijar(ARRANQUE);
        habito = habito("Barrido de expiracion");
    }

    @AfterEach
    void limpiar() {
        usuarios.forEach(id -> {
            jdbc.update("DELETE FROM renaser.registros_habito WHERE participante_id = ?", id);
            try {
                jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id);
            } catch (DataAccessException quedaReferenciado) {
                // Completar deja puntos y eventos que referencian a la persona (como en KilometrosDiariosIT).
            }
        });
        habitos.forEach(id -> jdbc.update("DELETE FROM renaser.habitos WHERE id = ?", id));
    }

    // ---------------------------------------------------------------------------------------------
    // Caracterizacion de Lima: pasa ANTES y DESPUES del arreglo de E-534.
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Lima, hora por hora durante dos dias: expira justo a su medianoche (05:00 UTC), ni antes ni despues")
    void limaExpiraIgualQueAntesHoraPorHora() {
        UUID ana = aprendiz("Ana Lima", LIMA);
        for (LocalDate fecha = LocalDate.of(2026, 11, 8); !fecha.isAfter(LocalDate.of(2026, 11, 11));
             fecha = fecha.plusDays(1)) {
            pendiente(ana, LIMA, habito, fecha);
        }
        // Lo que no esta PENDIENTE no se toca nunca: ni el estado, ni la hora, ni los puntos, ni actualizado_en.
        UUID otro = habito("Otros estados");
        List<UUID> intocables = List.of(
                registro(ana, otro, LocalDate.of(2026, 11, 7), "EXPIRADO"),
                registro(ana, otro, LocalDate.of(2026, 11, 8), "COMPLETADO"),
                registro(ana, otro, LocalDate.of(2026, 11, 9), "FALLIDO"),
                registro(ana, otro, LocalDate.of(2026, 11, 10), "EN_CURSO"));
        Map<UUID, Foto> antes = fotos(intocables);

        correrCadaHora(ARRANQUE, Instant.parse("2026-11-11T05:00:00Z"), hora -> {
            verificarSegunElDiaLocal(hora);
            assertThat(fotos(intocables)).as("a las %s", hora).isEqualTo(antes);
        });

        // Los dos bordes, explicitos: a las 04:00 UTC del 10 en Lima son las 23:00 del 9; a las 05:00, medianoche.
        assertThat(LocalDate.of(2026, 11, 9).atStartOfDay(LIMA).toInstant())
                .isEqualTo(Instant.parse("2026-11-09T05:00:00Z"));
    }

    @Test
    @DisplayName("Lima de noche: DORMIR vence a las 05:00 UTC (su medianoche), DESPERTAR registrado a las 23:55 queda con su hora, y lo vencido se puede registrar a las 00:30")
    void limaDespertarYDormirDeNoche() {
        UUID bea = aprendiz("Bea Noctambula", LIMA);
        LocalDate nueve = LocalDate.of(2026, 11, 9);
        UUID dormirAyer = registro(bea, DORMIR, nueve.minusDays(1), "PENDIENTE");
        UUID despertar = registro(bea, DESPERTAR, nueve, "PENDIENTE");
        UUID dormir = registro(bea, DORMIR, nueve, "PENDIENTE");

        correrHora(ARRANQUE);
        assertThat(estado(dormirAyer)).isEqualTo("EXPIRADO");

        // 03:30 UTC del 10 = 22:30 del 9 en Lima: DORMIR sigue abierto y su plazo es la medianoche de Lima.
        correrCadaHora(ARRANQUE.plus(Duration.ofHours(1)), Instant.parse("2026-11-10T03:00:00Z"), hora -> { });
        reloj.fijar(Instant.parse("2026-11-10T03:30:00Z"));
        TrackDelDiaConCatalogo trackDormir = track(bea, nueve, dormir);
        assertThat(trackDormir.registro().estado().name()).isEqualTo("PENDIENTE");
        assertThat(trackDormir.puntosEnJuego().plazo()).isEqualTo(Instant.parse("2026-11-10T05:00:00Z"));

        // 04:00 UTC = 23:00 en Lima: el barrido de esa hora (si corre) no toca nada del dia de hoy.
        correrHora(Instant.parse("2026-11-10T04:00:00Z"));
        assertThat(estado(dormir)).isEqualTo("PENDIENTE");
        assertThat(estado(despertar)).isEqualTo("PENDIENTE");

        // 23:55 en Lima: DESPERTAR (sin horario) se registra con la hora del instante, puntaje completo.
        Instant subida = Instant.parse("2026-11-10T04:55:00Z");
        reloj.fijar(subida);
        completar.completar(new CompletarRegistroCommand(UserId.of(bea), RegistroHabitoId.of(despertar), null, null));

        correrHora(Instant.parse("2026-11-10T05:00:00Z"));
        assertThat(estado(dormir)).isEqualTo("EXPIRADO");
        assertThat(foto(despertar)).satisfies(f -> {
            assertThat(f.estado()).isEqualTo("COMPLETADO");
            assertThat(f.completadoEn().toInstant()).isEqualTo(subida);
            assertThat(f.puntos()).isEqualTo(10);
        });

        // 00:30 del 10 en Lima: el DORMIR del 9 ya vencido se puede registrar igual; queda la hora real y paga 0
        // (paso su plazo, ResultadoOtorgamiento). El barrido no lo vuelve a tocar.
        Instant tarde = Instant.parse("2026-11-10T05:30:00Z");
        reloj.fijar(tarde);
        completar.completar(new CompletarRegistroCommand(UserId.of(bea), RegistroHabitoId.of(dormir), null, null));
        correrCadaHora(Instant.parse("2026-11-10T06:00:00Z"), Instant.parse("2026-11-11T06:00:00Z"), hora -> { });
        assertThat(foto(dormir)).satisfies(f -> {
            assertThat(f.estado()).isEqualTo("COMPLETADO");
            assertThat(f.completadoEn().toInstant()).isEqualTo(tarde);
            assertThat(f.puntos()).isZero();
        });
        assertThat(foto(despertar).completadoEn().toInstant()).isEqualTo(subida);
    }

    // ---------------------------------------------------------------------------------------------
    // E-534: fallan contra el barrido de antes (fecha UTC a las 05:00 UTC).
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Los Angeles (UTC−8): a las 05:00 UTC (21:00 suyas) su dia sigue abierto; expira a SU medianoche, 08:00 UTC")
    void losAngelesExpiraASuMedianoche() {
        UUID carla = aprendiz("Carla Pacifico", LOS_ANGELES);
        LocalDate nueve = LocalDate.of(2026, 11, 9);
        for (LocalDate fecha = nueve.minusDays(1); !fecha.isAfter(nueve.plusDays(1)); fecha = fecha.plusDays(1)) {
            pendiente(carla, LOS_ANGELES, habito, fecha);
        }
        UUID dormir = registro(carla, DORMIR, nueve, "PENDIENTE");
        pendientes.add(new Pendiente(dormir, LOS_ANGELES, nueve));

        correrCadaHora(ARRANQUE, Instant.parse("2026-11-10T05:00:00Z"), this::verificarSegunElDiaLocal);

        // 05:00 UTC del 10 = 21:00 del 9 en Los Angeles: DORMIR sigue en juego, con plazo a su medianoche.
        TrackDelDiaConCatalogo trackDormir = track(carla, nueve, dormir);
        assertThat(trackDormir.registro().estado().name()).isEqualTo("PENDIENTE");
        assertThat(trackDormir.puntosEnJuego().plazo()).isEqualTo(Instant.parse("2026-11-10T08:00:00Z"));

        correrCadaHora(Instant.parse("2026-11-10T06:00:00Z"), Instant.parse("2026-11-11T09:00:00Z"),
                this::verificarSegunElDiaLocal);
    }

    @Test
    @DisplayName("Lagos (UTC+1): expira a SU medianoche, 23:00 UTC, no a las 06:00 suyas del dia siguiente")
    void lagosExpiraASuMedianoche() {
        UUID dani = aprendiz("Dani Lagos", LAGOS);
        LocalDate nueve = LocalDate.of(2026, 11, 9);
        for (LocalDate fecha = nueve.minusDays(1); !fecha.isAfter(nueve.plusDays(1)); fecha = fecha.plusDays(1)) {
            pendiente(dani, LAGOS, habito, fecha);
        }

        correrCadaHora(ARRANQUE, Instant.parse("2026-11-11T00:00:00Z"), this::verificarSegunElDiaLocal);
    }

    @Test
    @DisplayName("correrlo dos veces seguidas da lo mismo, tambien de madrugada UTC")
    void dosVecesDaLoMismo() {
        sembrarLasTresZonas(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 10));

        for (String hora : List.of("2026-11-10T03:00:00Z", "2026-11-10T05:00:00Z", "2026-11-10T08:00:00Z")) {
            Instant instante = Instant.parse(hora);
            correrHora(instante);
            Map<UUID, Foto> despuesDeUna = fotos(idsPendientes());
            correrHora(instante);
            assertThat(fotos(idsPendientes())).as("dos corridas a las %s", hora).isEqualTo(despuesDeUna);
            verificarSegunElDiaLocal(instante);
        }
    }

    @Test
    @DisplayName("con el backend caido toda la noche, la primera corrida al volver (15:00 UTC) se pone al dia sola")
    void correrloTardeSePoneAlDia() {
        sembrarLasTresZonas(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 10));

        correrHora(Instant.parse("2026-11-10T15:00:00Z"));

        verificarSegunElDiaLocal(Instant.parse("2026-11-10T15:00:00Z"));
    }

    @Test
    @DisplayName("un participante con la zona rota no frena el barrido de los demas")
    void unParticipanteQueFallaNoFrenaAlResto() {
        UUID roto = aprendiz("Eli Zona Rota", LIMA);
        jdbc.update("UPDATE renaser.participantes_programa SET timezone = 'Zona/Inexistente' WHERE usuario_id = ?",
                roto);
        UUID filaDelRoto = registro(roto, habito, LocalDate.of(2026, 11, 8), "PENDIENTE");
        sembrarLasTresZonas(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 10));

        Instant ocho = Instant.parse("2026-11-10T08:00:00Z");
        correrHora(ocho);

        verificarSegunElDiaLocal(ocho);
        assertThat(estado(filaDelRoto)).as("sin zona no se puede saber que dia termino: se reintenta la proxima hora")
                .isEqualTo("PENDIENTE");
    }

    // ---------------------------------------------------------------------------------------------

    private void sembrarLasTresZonas(LocalDate desde, LocalDate hasta) {
        UUID lima = aprendiz("Lima", LIMA);
        UUID angeles = aprendiz("Los Angeles", LOS_ANGELES);
        UUID lagos = aprendiz("Lagos", LAGOS);
        for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
            pendiente(lima, LIMA, habito, fecha);
            pendiente(angeles, LOS_ANGELES, habito, fecha);
            pendiente(lagos, LAGOS, habito, fecha);
        }
    }

    /** Despues de cada corrida: lo PENDIENTE de un dia que ya termino EN SU ZONA esta vencido; lo de hoy, no. */
    private void verificarSegunElDiaLocal(Instant hora) {
        for (Pendiente fila : pendientes) {
            LocalDate hoyEnSuZona = hora.atZone(fila.zona()).toLocalDate();
            String esperado = fila.fecha().isBefore(hoyEnSuZona) ? "EXPIRADO" : "PENDIENTE";
            assertThat(estado(fila.id()))
                    .as("%s del %s a las %s UTC (%s en su zona)", fila.zona(), fila.fecha(), hora,
                            hora.atZone(fila.zona()).toLocalDateTime())
                    .isEqualTo(esperado);
        }
    }

    private void correrCadaHora(Instant desde, Instant hasta, java.util.function.Consumer<Instant> despues) {
        for (Instant hora = desde; !hora.isAfter(hasta); hora = hora.plus(Duration.ofHours(1))) {
            correrHora(hora);
            despues.accept(hora);
        }
    }

    /**
     * Lo que haria el planificador a esta hora: dispara cada {@code @Scheduled} del barrido cuyo cron de produccion
     * cae justo aca. Se llama al objeto real, sin el proxy de ShedLock: el cerrojo no es lo que se prueba, y su
     * {@code lockAtLeastFor} se saltearia las corridas de una simulacion que avanza horas en milisegundos.
     */
    private void correrHora(Instant hora) {
        reloj.fijar(hora);
        Object barrido = AopTestUtils.getUltimateTargetObject(contexto.getBean(ExpirarRegistrosScheduler.class));
        for (Method metodo : ExpirarRegistrosScheduler.class.getDeclaredMethods()) {
            Scheduled programado = metodo.getAnnotation(Scheduled.class);
            if (programado != null && disparaEn(programado, hora)) {
                try {
                    metodo.setAccessible(true);
                    metodo.invoke(barrido);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("el barrido " + metodo.getName() + " fallo a las " + hora, e);
                }
            }
        }
    }

    private static boolean disparaEn(Scheduled programado, Instant hora) {
        CronExpression cron = CronExpression.parse(cronDeProduccion(programado.cron()));
        ZonedDateTime enSuZona = hora.atZone(ZoneId.of(programado.zone().isEmpty() ? "UTC" : programado.zone()));
        ZonedDateTime siguiente = cron.next(enSuZona.minusSeconds(1));
        return siguiente != null && siguiente.toInstant().equals(hora);
    }

    /** {@code "${clave:0 0 * * * *}"} -> {@code "0 0 * * * *"}: el valor que corre en produccion si nadie lo cambia. */
    private static String cronDeProduccion(String cron) {
        if (!cron.startsWith("${")) {
            return cron;
        }
        return cron.substring(cron.indexOf(':') + 1, cron.length() - 1);
    }

    private TrackDelDiaConCatalogo track(UUID quien, LocalDate fecha, UUID registro) {
        return agenda.consultar(UserId.of(quien), UserId.of(quien), fecha).stream()
                .filter(t -> t.registro().id().value().equals(registro))
                .findFirst().orElseThrow();
    }

    private String estado(UUID registro) {
        return jdbc.queryForObject("SELECT estado::text FROM renaser.registros_habito WHERE id = ?", String.class,
                registro);
    }

    private Foto foto(UUID registro) {
        return jdbc.queryForObject("""
                SELECT estado::text AS estado, completado_en, puntos_otorgados, actualizado_en
                FROM renaser.registros_habito WHERE id = ?
                """, (rs, i) -> new Foto(rs.getString("estado"), rs.getTimestamp("completado_en"),
                rs.getInt("puntos_otorgados"), rs.getTimestamp("actualizado_en")), registro);
    }

    private Map<UUID, Foto> fotos(Iterable<UUID> ids) {
        Map<UUID, Foto> porId = new HashMap<>();
        ids.forEach(id -> porId.put(id, foto(id)));
        return porId;
    }

    private List<UUID> idsPendientes() {
        return pendientes.stream().map(Pendiente::id).toList();
    }

    private UUID aprendiz(String nombre, ZoneId zona) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, 'APRENDIZ', 'ACTIVO')
                """, id, id + "@renaser.test", nombre);
        jdbc.update("""
                INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio, programa_activado_en,
                                                            timezone)
                VALUES (?, 11, ?, ?, ?)
                """, id, INICIO, Timestamp.from(Instant.parse("2026-10-28T15:00:00Z")), zona.getId());
        usuarios.add(id);
        return id;
    }

    private UUID habito(String titulo) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.habitos (id, titulo, categoria_clave)
                VALUES (?, ?, (SELECT clave FROM renaser.categorias_habito LIMIT 1))
                """, id, titulo + " " + id);
        habitos.add(id);
        return id;
    }

    private void pendiente(UUID quien, ZoneId zona, UUID habitoId, LocalDate fecha) {
        pendientes.add(new Pendiente(registro(quien, habitoId, fecha, "PENDIENTE"), zona, fecha));
    }

    /** {@code dia_programa} y {@code tipo_dia} coherentes con la fecha; un COMPLETADO, con su hora y sus puntos. */
    private UUID registro(UUID quien, UUID habitoId, LocalDate fecha, String estado) {
        UUID id = UUID.randomUUID();
        int dia = (int) (fecha.toEpochDay() - INICIO.toEpochDay()) + 1;
        boolean completado = "COMPLETADO".equals(estado);
        jdbc.update("""
                INSERT INTO renaser.registros_habito (id, participante_id, habito_id, fecha_ejecucion, dia_programa,
                                                      tipo_dia, es_opcional, estado, puntos_otorgados, completado_en,
                                                      creado_en, actualizado_en)
                VALUES (?, ?, ?, ?, ?, CAST(? AS renaser.tipo_dia), false, CAST(? AS renaser.estado_registro), ?, ?,
                        ?, ?)
                """, id, quien, habitoId, fecha, dia, TipoDia.delDia(fecha).name(), estado, completado ? 10 : 0,
                completado ? Timestamp.from(COMPLETADO_ANTES) : null, Timestamp.from(COMPLETADO_ANTES),
                Timestamp.from(COMPLETADO_ANTES));
        return id;
    }
}
