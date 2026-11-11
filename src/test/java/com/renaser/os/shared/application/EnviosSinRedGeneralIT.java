package com.renaser.os.shared.application;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.application.EntregaSinRed.Aplicado;
import com.renaser.os.shared.application.EntregaSinRed.Envio;
import com.renaser.os.shared.application.ports.out.EnviosSinRedPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.CodigoNoAplicado;
import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.NoAplicadoSinRedException;
import com.renaser.os.shared.domain.OrigenDeLaHora;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La tabla de lo hecho sin red contra Postgres de verdad (spec 048, fase 3, V190): las columnas y los CHECK, que un envío no
 * aplicado quede escrito aunque el caso de uso lance su excepción, y dos pedidos CONCURRENTES con el mismo {@code X-Id-Cliente}
 * (el candado {@code pg_advisory_xact_lock}) tienen un solo efecto.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DisplayName("Lo hecho sin red: la tabla general y el candado (spec 048, fase 3)")
class EnviosSinRedGeneralIT {

    private static final Instant HECHO = Instant.parse("2026-11-10T02:40:00Z");

    @Autowired private EnviosSinRedPort envios;
    @Autowired private Clock reloj;
    @Autowired private PlatformTransactionManager transacciones;
    @Autowired private JdbcTemplate jdbc;

    private UserId persona;

    private EntregaSinRed entrega;

    @BeforeEach
    void sembrar() {
        entrega = new EntregaSinRed(envios, reloj);
        persona = UserId.of(UUID.randomUUID());
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado) VALUES (?, ?, 'Prueba', 'APRENDIZ', 'ACTIVO')",
                persona.value(), persona + "@prueba.test");
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", persona.value());
    }

    private Envio envio(UUID id) {
        return new Envio(persona, new HechoSinRed(HECHO, id, OrigenDeLaHora.RELOJ_DEL_SISTEMA), "CIERRE_DEL_DIA", "cierre:2026-11-09");
    }

    private <T> T enTransaccion(Callable<T> trabajo) {
        return new TransactionTemplate(transacciones).execute(estado -> {
            try {
                return trabajo.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private Map<String, Object> fila(UUID id) {
        return jdbc.queryForMap("SELECT tipo, recurso, resultado, codigo, mensaje, hecho_en, origen_de_la_hora, registro_id "
                + "FROM renaser.envios_sin_red WHERE id_cliente = ?", id);
    }

    @Test
    @DisplayName("V190: los envíos de hábitos de antes siguen valiendo (tipo y resultado por defecto) y registro_id ya admite NULL")
    void v190ConservaLoDeAntes() { // spec 048 FR-019
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.envios_sin_red (id_cliente, participante_id, registro_id, motivo_sin_puntos) VALUES (?, ?, ?, 'HORA_FUTURA')",
                id, persona.value(), UUID.randomUUID());
        UUID sinRegistro = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.envios_sin_red (id_cliente, participante_id, tipo, recurso) VALUES (?, ?, 'MI_RADAR', 'mi-radar')",
                sinRegistro, persona.value());

        assertThat(fila(id)).containsEntry("tipo", "COMPLETAR_HABITO").containsEntry("resultado", "APLICADO");
        assertThat(fila(sinRegistro).get("registro_id")).isNull();
    }

    @Test
    @DisplayName("V190: un NO_APLICADO exige su código, un código solo existe si no se aplicó y solo valen los cinco cerrados")
    void v190ChecksDeResultadoYCodigo() { // spec 048 FR-019
        assertThatThrownBy(() -> jdbc.update("INSERT INTO renaser.envios_sin_red (id_cliente, participante_id, resultado) VALUES (?, ?, 'NO_APLICADO')",
                UUID.randomUUID(), persona.value())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO renaser.envios_sin_red (id_cliente, participante_id, codigo) VALUES (?, ?, 'YA_ESTABA_HECHO')",
                UUID.randomUUID(), persona.value())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO renaser.envios_sin_red (id_cliente, participante_id, resultado, codigo) VALUES (?, ?, 'NO_APLICADO', 'INVENTADO')",
                UUID.randomUUID(), persona.value())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO renaser.envios_sin_red (id_cliente, participante_id, resultado) VALUES (?, ?, 'TAL_VEZ')",
                UUID.randomUUID(), persona.value())).isInstanceOf(DataIntegrityViolationException.class);
        for (CodigoNoAplicado codigo : CodigoNoAplicado.values()) {
            jdbc.update("INSERT INTO renaser.envios_sin_red (id_cliente, participante_id, resultado, codigo) VALUES (?, ?, 'NO_APLICADO', ?)",
                    UUID.randomUUID(), persona.value(), codigo.name());
        }
    }

    @Test
    @DisplayName("V191: participantes_programa tiene mi_radar_actualizado_en y arranca vacía (muy vieja)")
    void v191ColumnaDeMiRadar() { // spec 048 FR-019
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, timezone) VALUES (?, 10, 'America/Lima')", persona.value());

        assertThat(jdbc.queryForObject("SELECT mi_radar_actualizado_en FROM renaser.participantes_programa WHERE usuario_id = ?",
                java.sql.Timestamp.class, persona.value())).isNull();
    }

    @Test
    @DisplayName("un envío aplicado deja su fila con el tipo, el recurso, la hora real y el origen; repetirlo no aplica de nuevo")
    void aplicadoYRepetido() { // spec 048 FR-008 FR-019
        UUID id = UUID.randomUUID();
        AtomicInteger aplicaciones = new AtomicInteger();

        String primera = enTransaccion(() -> entrega.entregar(envio(id), () -> {
            aplicaciones.incrementAndGet();
            return Aplicado.de("hecho");
        }, previo -> "releído"));
        String segunda = enTransaccion(() -> entrega.entregar(envio(id), () -> {
            aplicaciones.incrementAndGet();
            return Aplicado.de("hecho otra vez");
        }, previo -> "releído"));

        assertThat(primera).isEqualTo("hecho");
        assertThat(segunda).isEqualTo("releído");
        assertThat(aplicaciones).hasValue(1);
        Map<String, Object> fila = fila(id);
        assertThat(fila).containsEntry("tipo", "CIERRE_DEL_DIA").containsEntry("recurso", "cierre:2026-11-09")
                .containsEntry("resultado", "APLICADO").containsEntry("origen_de_la_hora", "RELOJ_DEL_SISTEMA");
        assertThat(((java.sql.Timestamp) fila.get("hecho_en")).toInstant()).isEqualTo(HECHO);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.envios_sin_red WHERE id_cliente = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    @DisplayName("un envío que no se aplica queda escrito como NO_APLICADO AUNQUE la transacción termine con la excepción, y repetirlo da lo mismo")
    void noAplicadoSeConserva() { // spec 048 FR-016 FR-019
        UUID id = UUID.randomUUID();
        // El caso de uso real va con noRollbackFor: aquí se replica con el mismo TransactionTemplate.
        TransactionTemplate plantilla = new TransactionTemplate(transacciones);
        plantilla.execute(estado -> {
            try {
                return entrega.entregar(envio(id), () -> {
                    throw new NoAplicadoSinRedException(CodigoNoAplicado.RECURSO_YA_NO_EXISTE);
                }, previo -> "no se llama");
            } catch (NoAplicadoSinRedException e) {
                // Lo que hace @Transactional(noRollbackFor = NoAplicadoSinRedException.class): terminar bien y relanzar.
                return e.getMessage();
            }
        });

        Map<String, Object> fila = fila(id);
        assertThat(fila).containsEntry("resultado", "NO_APLICADO").containsEntry("codigo", "RECURSO_YA_NO_EXISTE")
                .containsEntry("mensaje", CodigoNoAplicado.RECURSO_YA_NO_EXISTE.mensaje());
        assertThatThrownBy(() -> enTransaccion(() -> entrega.entregar(envio(id), () -> Aplicado.de("no"), previo -> "no")))
                .isInstanceOfSatisfying(NoAplicadoSinRedException.class, e -> {
                    assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.RECURSO_YA_NO_EXISTE);
                    assertThat(e.getMessage()).isEqualTo(CodigoNoAplicado.RECURSO_YA_NO_EXISTE.mensaje());
                });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.envios_sin_red WHERE id_cliente = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    @DisplayName("dos pedidos A LA VEZ con el mismo X-Id-Cliente se turnan por el candado: lo hecho se aplica una sola vez y los dos ven lo mismo")
    void dosPedidosConcurrentesUnSoloEfecto() throws Exception { // spec 048 FR-008 SC-003
        UUID id = UUID.randomUUID();
        AtomicInteger aplicaciones = new AtomicInteger();
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Callable<String> pedido = () -> enTransaccion(() -> entrega.entregar(envio(id), () -> {
                aplicaciones.incrementAndGet();
                pausa();
                return Aplicado.de("aplicado");
            }, previo -> "releído"));
            Future<String> a = hilos.submit(pedido);
            Future<String> b = hilos.submit(pedido);

            assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder("aplicado", "releído");
        } finally {
            hilos.shutdownNow();
        }
        assertThat(aplicaciones).hasValue(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.envios_sin_red WHERE id_cliente = ?", Integer.class, id)).isEqualTo(1);
    }

    private static void pausa() {
        try {
            Thread.sleep(400);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
