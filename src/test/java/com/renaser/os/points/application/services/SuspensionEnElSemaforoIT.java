package com.renaser.os.points.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.api.EstadoDiaSemaforo;
import com.renaser.os.points.api.SemaforoFinder;
import com.renaser.os.points.api.VentanaDelSemaforo;
import com.renaser.os.points.application.ports.in.semaforo.CerrarSemaforoUseCase;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.admin.UpdateUserStatusUseCase;
import com.renaser.os.users.application.ports.in.admin.UpdateUserStatusUseCase.UpdateUserStatusCommand;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-209 de punta a punta, contra Postgres real y sin dobles: un administrador suspende y reactiva la
 * cuenta por el caso de uso de {@code users}, el evento pasa por el outbox de Modulith, el listener de
 * {@code points} anota la suspensión (V72) y el barrido real del sábado cierra la semana sin medir esos
 * días. Es lo que el unitario no puede probar: que el evento se serializa y se entrega, y que la fila
 * pasa los CHECK de la migración.
 *
 * <p>Sin {@code @Transactional}: el listener corre después del commit. Por eso se limpia a mano.
 *
 * <p>Ana arrancó el martes 8 de septiembre. Un hábito por día en la semana del sábado 19 al viernes 25:
 * cumplido el sábado, el domingo y el viernes; vencido de lunes a jueves, los días en que su cuenta
 * estuvo suspendida (lunes 21 a las 21:00 de Lima → jueves 24 a las 23:30, las dos a una hora UTC que
 * todavía es el día anterior en Lima). Antes esa semana daba (100 + 100 + 0 + 0 + 0 + 0 + 100) / 7 =
 * 42,9: rojo. Ahora se miden el sábado, el domingo y el viernes: 100, verde.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, SuspensionEnElSemaforoIT.RelojDeLaPrueba.class})
class SuspensionEnElSemaforoIT {

    private static final LocalDate INICIO = LocalDate.of(2026, 9, 8);
    private static final LocalDate SABADO_19 = LocalDate.of(2026, 9, 19);
    private static final LocalDate VIERNES_25 = LocalDate.of(2026, 9, 25);

    /** Un reloj que la prueba mueve: el mismo que usan {@code users} (instante del evento) y el barrido. */
    static final class RelojMovible implements Clock {

        private volatile Instant ahora = Instant.parse("2026-09-22T02:00:00Z");

        void poner(String instante) {
            ahora = Instant.parse(instante);
        }

        @Override
        public Instant now() {
            return ahora;
        }

        @Override
        public LocalDate today() {
            return ahora.atZone(ZoneOffset.UTC).toLocalDate();
        }
    }

    @TestConfiguration
    static class RelojDeLaPrueba {
        @Bean
        @Primary
        RelojMovible relojMovible() {
            return new RelojMovible();
        }
    }

    @Autowired
    private UpdateUserStatusUseCase cambiarEstado;
    @Autowired
    private CerrarSemaforoUseCase cerrarSemaforo;
    @Autowired
    private SemaforoFinder semaforoFinder;
    @Autowired
    private JdbcClient jdbcClient;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private RelojMovible reloj;

    private UUID admin;
    private UUID ana;
    private UUID habito;

    @BeforeEach
    void semanaDeAna() {
        admin = usuario("ADMIN");
        ana = usuario("APRENDIZ");
        jdbcClient.sql("""
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio,
                                                                    programa_activado_en, timezone)
                        VALUES (:id, 14, :inicio, :activado, 'America/Lima')
                        """)
                .param("id", ana).param("inicio", INICIO)
                .param("activado", Timestamp.from(Instant.parse("2026-09-07T15:00:00Z"))).update();
        // La semana anterior ya cerró (sin datos: no tenía hábitos). Así el sábado solo cierra la del 25.
        jdbcClient.sql("""
                        INSERT INTO renaser.semaforo_semanas (participante_id, semana_hasta, semana_desde, porcentaje,
                                                              dias_con_datos, dias_medidos, version_formula, cerrada_en)
                        VALUES (:id, '2026-09-18', '2026-09-12', NULL, 0, 7, '2026-09-25-promedio-de-dias', :cerrada)
                        """)
                .param("id", ana).param("cerrada", Timestamp.from(Instant.parse("2026-09-19T05:25:00Z"))).update();
        habito = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO renaser.habitos (id, titulo, categoria_clave)
                        VALUES (:id, :titulo, (SELECT clave FROM renaser.categorias_habito LIMIT 1))
                        """)
                .param("id", habito).param("titulo", "Meditar " + habito).update();
        for (LocalDate dia = SABADO_19; !dia.isAfter(VIERNES_25); dia = dia.plusDays(1)) {
            boolean activa = dia.isBefore(LocalDate.of(2026, 9, 21)) || dia.equals(VIERNES_25);
            registro(dia, activa ? "COMPLETADO" : "EXPIRADO");
        }
    }

    @AfterEach
    void limpiar() {
        jdbcClient.sql("DELETE FROM renaser.usuarios WHERE id IN (:ids)").param("ids", List.of(ana, admin)).update();
        jdbcClient.sql("DELETE FROM renaser.habitos WHERE id = :id").param("id", habito).update();
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void suspenderYReactivarDejaEsosDiasSinMedirYLaSemanaNoQuedaEnRojo() throws InterruptedException {
        reloj.poner("2026-09-22T02:00:00Z");   // lunes 21, 21:00 en Lima
        cambiarEstado.updateStatus(new UpdateUserStatusCommand(UserId.of(admin), UserId.of(ana), UserStatus.SUSPENDED));

        Map<String, Object> suspension = esperar(() -> suspensionDeAna().filter(s -> s.get("desde") != null));
        assertThat(suspension).containsEntry("motivo", "CUENTA_SUSPENDIDA").containsEntry("hasta", null)
                .containsEntry("reanudada_el", null);
        assertThat(((java.sql.Date) suspension.get("desde")).toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 21));

        reloj.poner("2026-09-25T04:30:00Z");   // jueves 24, 23:30 en Lima
        cambiarEstado.updateStatus(new UpdateUserStatusCommand(UserId.of(admin), UserId.of(ana), UserStatus.ACTIVE));

        Map<String, Object> terminada = esperar(() -> suspensionDeAna().filter(s -> s.get("reanudada_el") != null));
        assertThat(((java.sql.Date) terminada.get("reanudada_el")).toLocalDate()).isEqualTo(VIERNES_25);

        reloj.poner("2026-09-26T05:30:00Z");   // sábado 26, 00:30 en Lima
        cerrarSemaforo.cerrarPendientes();

        VentanaDelSemaforo semana = semaforoFinder.semanaDe(List.of(UserId.of(ana)), VIERNES_25).get(UserId.of(ana));
        assertThat(semana.cerrada()).isTrue();
        assertThat(semana.color()).isEqualTo(ColorSemaforo.VERDE);
        assertThat(semana.porcentaje()).isEqualByComparingTo("100.0");
        assertThat(semana.diasConDatos()).isEqualTo(3);
        var suspendido = EstadoDiaSemaforo.CUENTA_SUSPENDIDA;
        assertThat(semana.dias()).extracting(DiaDelSemaforo::estado).containsExactly(EstadoDiaSemaforo.MEDIDO,
                EstadoDiaSemaforo.MEDIDO, suspendido, suspendido, suspendido, suspendido, EstadoDiaSemaforo.MEDIDO);
    }

    /**
     * El relleno de V72, en una base aparte migrada hasta V71 con datos (mismo método que la prueba de V71 en
     * {@code BienvenidaEnGrupoIT}): quien ya estaba suspendido con su programa activado recibe una suspensión
     * abierta desde el día del despliegue en Lima; nadie más, y la pausa del staff que ya existía queda como
     * «pedida por la persona», con su fecha.
     */
    @Test
    @DisplayName("V72 abre la suspensión desde el día del despliegue solo a quien ya está suspendido")
    void elRellenoDeV72SoloAbreLaSuspensionDeQuienYaEstaSuspendido() {
        HikariDataSource principal = (HikariDataSource) dataSource;
        JdbcTemplate principalJdbc = new JdbcTemplate(dataSource);
        String base = "renaser_v72_" + UUID.randomUUID().toString().replace("-", "");
        principalJdbc.execute("CREATE DATABASE " + base);
        try {
            String url = principal.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + base + "$1");
            DriverManagerDataSource aparte = new DriverManagerDataSource(url, principal.getUsername(), principal.getPassword());
            migrarHasta(aparte, "71");
            JdbcTemplate jdbc = new JdbcTemplate(aparte);
            UUID suspendida = conPrograma(jdbc, "APRENDIZ", "SUSPENDIDO", true);
            conPrograma(jdbc, "APRENDIZ", "ACTIVO", true);
            conPrograma(jdbc, "APRENDIZ", "SUSPENDIDO", false);
            UUID mentora = conPrograma(jdbc, "MENTOR", "ACTIVO", true);
            UUID pausaDelStaff = UUID.randomUUID();
            jdbc.update("INSERT INTO renaser.semaforo_pausas (id, usuario_id, desde, hasta, creada_en) "
                    + "VALUES (?, ?, DATE '2026-09-20', DATE '2026-09-30', now())", pausaDelStaff, mentora);
            LocalDate antes = LocalDate.now(ZoneId.of("America/Lima"));

            migrarHasta(aparte, "72");

            LocalDate despues = LocalDate.now(ZoneId.of("America/Lima"));
            List<Map<String, Object>> suspensiones = jdbc.queryForList(
                    "SELECT usuario_id, desde, hasta, reanudada_el FROM renaser.semaforo_pausas "
                            + "WHERE motivo = 'CUENTA_SUSPENDIDA'");
            assertThat(suspensiones).singleElement().satisfies(fila -> {
                assertThat(fila).containsEntry("usuario_id", suspendida).containsEntry("hasta", null)
                        .containsEntry("reanudada_el", null);
                assertThat(((java.sql.Date) fila.get("desde")).toLocalDate()).isBetween(antes, despues);
            });
            assertThat(jdbc.queryForObject("SELECT motivo || ' ' || hasta FROM renaser.semaforo_pausas WHERE id = ?",
                    String.class, pausaDelStaff)).isEqualTo("PEDIDA_POR_LA_PERSONA 2026-09-30");
        } finally {
            principalJdbc.execute("DROP DATABASE IF EXISTS " + base + " WITH (FORCE)");
        }
    }

    /** V72 solo mira el estado de la cuenta y si el programa está activado; el día 20 es el del 27/09/2026. */
    private static UUID conPrograma(JdbcTemplate jdbc, String rol, String estado, boolean activado) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado) "
                        + "VALUES (?, ?, 'X', CAST(? AS renaser.rol_usuario), CAST(? AS renaser.estado_usuario))",
                id, id + "@renaser.test", rol, estado);
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio, "
                        + "programa_activado_en, timezone) VALUES (?, 20, DATE '2026-09-08', ?, 'America/Lima')",
                id, activado ? Timestamp.from(Instant.parse("2026-09-07T15:00:00Z")) : null);
        return id;
    }

    private static void migrarHasta(DataSource ds, String version) {
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target(version).load().migrate();
    }

    private UUID usuario(String rol) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, 'Fixture suspension', CAST(:rol AS renaser.rol_usuario), 'ACTIVO')
                        """)
                .param("id", id).param("email", id + "@renaser.test").param("rol", rol).update();
        return id;
    }

    /** Con el día del programa que le toca a esa fecha: el sábado 19 es su día 12. */
    private void registro(LocalDate fecha, String estado) {
        jdbcClient.sql("""
                        INSERT INTO renaser.registros_habito (id, participante_id, habito_id, fecha_ejecucion,
                                                             dia_programa, tipo_dia, es_opcional, estado)
                        VALUES (:id, :pid, :hid, :fecha, :dia, CAST('DISCIPLINA' AS renaser.tipo_dia), false,
                                CAST(:estado AS renaser.estado_registro))
                        """)
                .param("id", UUID.randomUUID()).param("pid", ana).param("hid", habito).param("fecha", fecha)
                .param("dia", (int) ChronoUnit.DAYS.between(INICIO, fecha) + 1).param("estado", estado).update();
    }

    private Optional<Map<String, Object>> suspensionDeAna() {
        return jdbcClient.sql("""
                        SELECT motivo, desde, hasta, reanudada_el FROM renaser.semaforo_pausas
                         WHERE usuario_id = :id AND motivo = 'CUENTA_SUSPENDIDA'
                        """)
                .param("id", ana).query().listOfRows().stream().findFirst();
    }

    /** El listener corre después del commit y en otro hilo: se espera a que su efecto aparezca. */
    private static <T> T esperar(Supplier<Optional<T>> condicion) throws InterruptedException {
        long limite = System.currentTimeMillis() + 15_000;
        Optional<T> valor = condicion.get();
        while (valor.isEmpty() && System.currentTimeMillis() < limite) {
            Thread.sleep(200);
            valor = condicion.get();
        }
        return valor.orElseThrow(() -> new AssertionError("El listener del semaforo no escribio a tiempo"));
    }
}
