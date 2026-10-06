package com.renaser.os.habits.infrastructure.adapter.in.scheduler;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.shared.domain.Clock;
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
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Etapa 1 de zonas (E-556 a E-559) — la generacion del dia ({@link GenerarTracksDelDiaScheduler}, antes a las
 * 05:02 UTC) y la promocion de los cambios de horario ({@link PromoverCambiosHorarioScheduler}, antes a las
 * 04:40 UTC), de punta a punta contra Postgres real.
 *
 * <p><b>Como se prueba sin depender de como esta escrito.</b> El reloj avanza minuto a minuto y en cada minuto se
 * disparan los {@code @Scheduled} de los dos barridos cuyo cron de PRODUCCION cae en ese minuto (el escrito en la
 * anotacion, o su valor por defecto si es una propiedad), como el planificador real. La misma prueba describe el
 * barrido viejo (una vez por noche, a hora UTC fija) y el nuevo (cada hora, el dia local de cada participante) sin
 * llamar a ningun metodo que exista en uno solo de los dos. Mismo metodo que {@link ExpiracionPorZonaIT} (E-534).
 *
 * <p><b>Las de Lima son caracterizacion</b> (todo el padron vive en {@code America/Lima}): pasan con el barrido de
 * antes y con el de ahora. <b>Las de {@code America/Los_Angeles} (UTC−8 en noviembre), {@code Europe/Madrid}
 * (UTC+1) y {@code Asia/Tokyo} (UTC+9)</b> fallan contra el de antes: el dia de esa gente se genera a destiempo y su
 * cambio de horario llega a regir en una hora que no es la de su medianoche.
 *
 * <p>Fixture coherente (regla 03): el programa arranco el 30/10, asi que el 9/11 es el Dia 11. El reloj arranca a las
 * 03:30 UTC del 9/11, una hora UTC que cae en el dia local ANTERIOR en toda America (regla 02 §3), y cada persona
 * arranca con su "hoy" local ya generado, como en regimen.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, GeneracionYPromocionPorZonaIT.RelojDeLaPrueba.class})
class GeneracionYPromocionPorZonaIT {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final ZoneId LOS_ANGELES = ZoneId.of("America/Los_Angeles");
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final ZoneId TOKIO = ZoneId.of("Asia/Tokyo");
    private static final LocalDate INICIO = LocalDate.of(2026, 10, 30);
    /** 03:30 UTC del 9/11: Lima 22:30 del 8/11, Los Angeles 19:30 del 8/11, Madrid 04:30 y Tokio 12:30 del 9/11. */
    private static final Instant ARRANQUE = Instant.parse("2026-11-09T03:30:00Z");
    private static final Instant FIN = ARRANQUE.plus(Duration.ofHours(31));
    /** El "minuto 2" de la hora: a esa altura, y no antes, la generacion del dia debe haber corrido. */
    private static final Duration MARGEN_DE_GENERACION = Duration.ofMinutes(2);
    private static final UUID DORMIR = UUID.fromString("48d68c12-72a9-428c-a8c6-02b9b01bc2fe");
    private static final Instant CREADO = Instant.parse("2026-11-01T12:00:00Z");

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

    /** Una persona sembrada: su zona y el dia local en el que arranco la simulacion (ya generado). */
    private record Persona(UUID id, ZoneId zona, LocalDate hoyAlArrancar) {
        LocalDate manana() {
            return hoyAlArrancar.plusDays(1);
        }
    }

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ApplicationContext contexto;
    @Autowired
    private RelojQueAvanza reloj;

    private final List<Persona> personas = new ArrayList<>();
    private final List<UUID> habitos = new ArrayList<>();
    private UUID habito;

    @BeforeEach
    void preparar() {
        reloj.fijar(ARRANQUE);
        habito = habito("Cambio de horario por zona");
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM renaser.registros_habito WHERE fecha_ejecucion BETWEEN ? AND ?",
                Date.valueOf("2026-11-05"), Date.valueOf("2026-11-15"));
        personas.forEach(p -> {
            jdbc.update("DELETE FROM renaser.registros_habito WHERE participante_id = ?", p.id());
            jdbc.update("DELETE FROM renaser.historial_cambios_horario WHERE participante_id = ?", p.id());
            jdbc.update("DELETE FROM renaser.cambios_horario_pendientes WHERE participante_id = ?", p.id());
            jdbc.update("DELETE FROM renaser.preferencias_horario WHERE participante_id = ?", p.id());
            try {
                jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", p.id());
            } catch (DataAccessException quedaReferenciado) {
                // Lo que generaron deja filas que lo referencian; la base de la suite es descartable.
            }
        });
        habitos.forEach(id -> jdbc.update("DELETE FROM renaser.habitos WHERE id = ?", id));
    }

    // ---------------------------------------------------------------------------------------------
    // Caracterizacion de Lima: pasa ANTES y DESPUES.
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Lima: su dia se genera a las 05:02 UTC (00:02 suyas) y no antes, dia tras dia")
    void limaGeneraSuDiaAlEmpezar() {
        Persona ana = persona("Ana Lima", LIMA);

        simular(ARRANQUE, FIN, minuto -> {
            if (minuto.atZone(ZoneOffset.UTC).getMinute() == 30) {
                assertThat(fechasGeneradas(ana)).as("a las %s UTC", minuto).isEqualTo(esperadas(ana, minuto));
            }
        });

        // Los dos bordes, explicitos: 05:02 UTC del 9/11 y del 10/11 son las 00:02 de Lima.
        assertThat(LocalDate.of(2026, 11, 9).atStartOfDay(LIMA).toInstant().plus(MARGEN_DE_GENERACION))
                .isEqualTo(Instant.parse("2026-11-09T05:02:00Z"));
        assertThat(fechasGeneradas(ana)).containsExactly(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 9),
                LocalDate.of(2026, 11, 10));
    }

    @Test
    @DisplayName("Lima: el cambio de horario de manana rige al llegar su dia: sigue pendiente a las 04:00 UTC y ya rigio a las 06:00")
    void limaPromueveElCambioAlLlegarSuDia() {
        Persona bea = persona("Bea Lima", LIMA);
        cambioProgramado(bea, bea.manana());
        Instant empiezaSuDia = bea.manana().atStartOfDay(LIMA).toInstant();

        simular(ARRANQUE, empiezaSuDia.plus(Duration.ofHours(1)), minuto -> {
            if (minuto.equals(empiezaSuDia.minus(Duration.ofHours(1)))) {
                assertThat(pendientes(bea)).as("una hora antes de su dia").isEqualTo(1);
            }
        });

        assertThat(pendientes(bea)).as("una hora despues de que empezo su dia").isZero();
        assertThat(horaDeDisparo(bea)).isEqualTo("06:00:00");
        assertThat(cobrosEnElHistorial(bea)).isEqualTo(1);
    }

    // ---------------------------------------------------------------------------------------------
    // Etapa 1: fallan contra el barrido de antes (hora UTC fija).
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Los Angeles, Madrid y Tokio: el dia de cada uno se genera a SU medianoche, no a las 05:02 UTC")
    void generaElDiaDeCadaUnoEnSuZona() {
        Persona carla = persona("Carla Los Angeles", LOS_ANGELES);
        Persona dani = persona("Dani Madrid", MADRID);
        Persona eli = persona("Eli Tokio", TOKIO);
        Persona ana = persona("Ana Lima", LIMA);

        simular(ARRANQUE, FIN, minuto -> {
            int minutoDeLaHora = minuto.atZone(ZoneOffset.UTC).getMinute();
            if (minutoDeLaHora == 1 || minutoDeLaHora == 30) {
                for (Persona persona : List.of(carla, dani, eli, ana)) {
                    assertThat(fechasGeneradas(persona)).as("%s a las %s UTC", persona.zona(), minuto)
                            .isEqualTo(esperadas(persona, minuto));
                }
            }
        });
    }

    @Test
    @DisplayName("Los Angeles, Madrid y Tokio: el cambio de horario rige cuando empieza SU dia, no a las 04:40 UTC")
    void promueveElCambioCuandoEmpiezaElDiaDeCadaUno() {
        List<Persona> gente = List.of(persona("Carla", LOS_ANGELES), persona("Dani", MADRID),
                persona("Eli", TOKIO), persona("Ana", LIMA));
        gente.forEach(p -> cambioProgramado(p, p.manana()));

        simular(ARRANQUE, FIN, minuto -> {
            for (Persona persona : gente) {
                Instant empiezaSuDia = persona.manana().atStartOfDay(persona.zona()).toInstant();
                if (minuto.equals(empiezaSuDia.minus(Duration.ofHours(1)))) {
                    assertThat(pendientes(persona)).as("%s, una hora antes de su dia", persona.zona()).isEqualTo(1);
                }
                if (minuto.equals(empiezaSuDia.plus(Duration.ofHours(1)))) {
                    assertThat(pendientes(persona)).as("%s, una hora despues de que empezo su dia", persona.zona())
                            .isZero();
                }
            }
        });

        gente.forEach(p -> assertThat(cobrosEnElHistorial(p)).as("%s: un solo cobro", p.zona()).isEqualTo(1));
    }

    @Test
    @DisplayName("correrlo dos veces seguidas da lo mismo, tambien de madrugada UTC")
    void dosVecesDaLoMismo() {
        Persona carla = persona("Carla", LOS_ANGELES);
        Persona eli = persona("Eli", TOKIO);
        cambioProgramado(eli, eli.manana());

        Instant madrugada = Instant.parse("2026-11-10T08:02:00Z");
        correrTodo(madrugada);
        List<TreeSet<LocalDate>> despuesDeUna = List.of(fechasGeneradas(carla), fechasGeneradas(eli));
        int filasDeUna = filas(carla) + filas(eli);
        correrTodo(madrugada);

        assertThat(List.of(fechasGeneradas(carla), fechasGeneradas(eli))).isEqualTo(despuesDeUna);
        assertThat(filas(carla) + filas(eli)).isEqualTo(filasDeUna);
        assertThat(cobrosEnElHistorial(eli)).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("con el backend caido toda la noche, la primera corrida al volver (15:30 UTC) se pone al dia sola")
    void correrloTardeSePoneAlDia() {
        List<Persona> gente = List.of(persona("Carla", LOS_ANGELES), persona("Dani", MADRID),
                persona("Eli", TOKIO), persona("Ana", LIMA));
        gente.forEach(p -> cambioProgramado(p, p.manana()));

        Instant tarde = Instant.parse("2026-11-10T15:30:00Z");
        correrTodo(tarde);

        for (Persona persona : gente) {
            LocalDate hoy = tarde.atZone(persona.zona()).toLocalDate();
            assertThat(fechasGeneradas(persona)).as("%s", persona.zona()).contains(hoy);
            assertThat(pendientes(persona)).as("%s: su cambio ya rige", persona.zona()).isZero();
        }
    }

    @Test
    @DisplayName("un participante con la zona rota no frena a los demas")
    void unParticipanteQueFallaNoFrenaAlResto() {
        Persona rota = persona("Fran Zona Rota", LIMA);
        jdbc.update("UPDATE renaser.participantes_programa SET timezone = 'Zona/Inexistente' WHERE usuario_id = ?",
                rota.id());
        Persona carla = persona("Carla", LOS_ANGELES);
        Persona eli = persona("Eli", TOKIO);
        cambioProgramado(eli, eli.manana());

        Instant tarde = Instant.parse("2026-11-10T15:30:00Z");
        correrTodo(tarde);

        assertThat(fechasGeneradas(carla)).contains(tarde.atZone(LOS_ANGELES).toLocalDate());
        assertThat(fechasGeneradas(eli)).contains(tarde.atZone(TOKIO).toLocalDate());
        assertThat(pendientes(eli)).isZero();
        assertThat(fechasGeneradas(rota)).as("sin zona no se sabe que dia es: queda para la proxima hora")
                .containsExactly(rota.hoyAlArrancar());
    }

    // ---------------------------------------------------------------------------------------------

    /** Lo que debe haber generado la persona a esa hora: su dia de arranque y, desde que empieza cada uno, los demas. */
    private TreeSet<LocalDate> esperadas(Persona persona, Instant ahora) {
        TreeSet<LocalDate> fechas = new TreeSet<>();
        fechas.add(persona.hoyAlArrancar());
        LocalDate hoy = ahora.atZone(persona.zona()).toLocalDate();
        for (LocalDate dia = persona.manana(); !dia.isAfter(hoy); dia = dia.plusDays(1)) {
            Instant generado = dia.atStartOfDay(persona.zona()).toInstant().plus(MARGEN_DE_GENERACION);
            if (!ahora.isBefore(generado)) {
                fechas.add(dia);
            }
        }
        return fechas;
    }

    private void simular(Instant desde, Instant hasta, java.util.function.Consumer<Instant> despues) {
        for (Instant minuto = desde; !minuto.isAfter(hasta); minuto = minuto.plus(Duration.ofMinutes(1))) {
            correrLoQueDisparaEn(minuto);
            despues.accept(minuto);
        }
    }

    /** Dispara los dos barridos a esta hora, sin mirar si su cron cae aca: la corrida "tarde" de un backend caido. */
    private void correrTodo(Instant hora) {
        reloj.fijar(hora);
        for (Class<?> tipo : List.of(PromoverCambiosHorarioScheduler.class, GenerarTracksDelDiaScheduler.class)) {
            for (Method metodo : tipo.getDeclaredMethods()) {
                if (metodo.getAnnotation(Scheduled.class) != null) {
                    invocar(tipo, metodo);
                }
            }
        }
    }

    /**
     * Lo que haria el planificador a este minuto. Se llama al objeto real, sin el proxy de ShedLock: el cerrojo no es
     * lo que se prueba, y su {@code lockAtLeastFor} se saltearia las corridas de una simulacion que avanza horas en
     * milisegundos.
     */
    private void correrLoQueDisparaEn(Instant minuto) {
        reloj.fijar(minuto);
        for (Class<?> tipo : List.of(PromoverCambiosHorarioScheduler.class, GenerarTracksDelDiaScheduler.class)) {
            for (Method metodo : tipo.getDeclaredMethods()) {
                Scheduled programado = metodo.getAnnotation(Scheduled.class);
                if (programado != null && disparaEn(programado, minuto)) {
                    invocar(tipo, metodo);
                }
            }
        }
    }

    private void invocar(Class<?> tipo, Method metodo) {
        Object barrido = AopTestUtils.getUltimateTargetObject(contexto.getBean(tipo));
        try {
            metodo.setAccessible(true);
            metodo.invoke(barrido);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(tipo.getSimpleName() + "." + metodo.getName() + " fallo", e);
        }
    }

    private static boolean disparaEn(Scheduled programado, Instant minuto) {
        CronExpression cron = CronExpression.parse(cronDeProduccion(programado.cron()));
        ZonedDateTime enSuZona = minuto.atZone(ZoneId.of(programado.zone().isEmpty() ? "UTC" : programado.zone()));
        ZonedDateTime siguiente = cron.next(enSuZona.minusSeconds(1));
        return siguiente != null && siguiente.toInstant().equals(minuto);
    }

    /** {@code "${clave:0 0 * * * *}"} -> {@code "0 0 * * * *"}: el valor que corre en produccion si nadie lo cambia. */
    private static String cronDeProduccion(String cron) {
        return cron.startsWith("${") ? cron.substring(cron.indexOf(':') + 1, cron.length() - 1) : cron;
    }

    private TreeSet<LocalDate> fechasGeneradas(Persona persona) {
        return new TreeSet<>(jdbc.queryForList(
                "SELECT DISTINCT fecha_ejecucion FROM renaser.registros_habito WHERE participante_id = ?",
                LocalDate.class, persona.id()));
    }

    private int filas(Persona persona) {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.registros_habito WHERE participante_id = ?",
                Integer.class, persona.id());
    }

    private int pendientes(Persona persona) {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.cambios_horario_pendientes WHERE participante_id = ?",
                Integer.class, persona.id());
    }

    private int cobrosEnElHistorial(Persona persona) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM renaser.historial_cambios_horario WHERE participante_id = ?", Integer.class,
                persona.id());
    }

    private String horaDeDisparo(Persona persona) {
        return jdbc.queryForObject("SELECT hora_disparo::text FROM renaser.preferencias_horario "
                + "WHERE participante_id = ? AND habito_id = ?", String.class, persona.id(), habito);
    }

    /** Una persona activada, con su "hoy" local ya generado: arranca la simulacion en regimen. */
    private Persona persona(String nombre, ZoneId zona) {
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
        Persona persona = new Persona(id, zona, ARRANQUE.atZone(zona).toLocalDate());
        sembrarDia(persona, persona.hoyAlArrancar());
        personas.add(persona);
        return persona;
    }

    /** Un registro del dia, para que el barrido lo vea como ya generado: {@code dia_programa} coherente con la fecha. */
    private void sembrarDia(Persona persona, LocalDate fecha) {
        int dia = (int) (fecha.toEpochDay() - INICIO.toEpochDay()) + 1;
        jdbc.update("""
                INSERT INTO renaser.registros_habito (id, participante_id, habito_id, fecha_ejecucion, dia_programa,
                                                      tipo_dia, es_opcional, estado, puntos_otorgados, creado_en,
                                                      actualizado_en)
                VALUES (?, ?, ?, ?, ?, CAST(? AS renaser.tipo_dia), false, CAST('PENDIENTE' AS renaser.estado_registro),
                        0, ?, ?)
                """, UUID.randomUUID(), persona.id(), DORMIR, fecha, dia, TipoDia.delDia(fecha).name(),
                Timestamp.from(CREADO), Timestamp.from(CREADO));
    }

    /** Un cambio que debe regir el {@code fecha} (un dia local de la persona): preferencia vigente + el pendiente. */
    private void cambioProgramado(Persona persona, LocalDate fecha) {
        jdbc.update("""
                INSERT INTO renaser.preferencias_horario (participante_id, habito_id, hora_disparo, hora_limite)
                VALUES (?, ?, TIME '09:00', TIME '11:00')
                """, persona.id(), habito);
        jdbc.update("""
                INSERT INTO renaser.cambios_horario_pendientes (participante_id, habito_id, hora_disparo, hora_limite,
                                                                fecha_efectiva)
                VALUES (?, ?, TIME '06:00', TIME '08:00', ?)
                """, persona.id(), habito, fecha);
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
}
