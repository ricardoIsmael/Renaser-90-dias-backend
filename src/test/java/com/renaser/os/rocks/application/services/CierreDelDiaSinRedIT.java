package com.renaser.os.rocks.application.services;

import com.renaser.os.RelojDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.rocks.application.ports.in.cierre.CerrarElDiaSinRedUseCase;
import com.renaser.os.rocks.application.ports.in.cierre.CerrarElDiaUseCase;
import com.renaser.os.rocks.application.ports.in.cierre.CerrarElDiaUseCase.CerrarElDia;
import com.renaser.os.rocks.application.ports.in.cierre.CerrarElDiaUseCase.RespuestaDeAccion;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * «Cierra tu día» hecho sin red contra Postgres de verdad (spec 048, fase 3, FR-019). La persona cierra su lunes 9 a las 23:50 de Lima
 * (04:50 UTC del 10) y la app lo envía el martes 10 a las 08:00 de Lima: pasó la medianoche de Lima y, en UTC, los dos instantes
 * caen el mismo día. Fija lo que ningún doble puede: la hora real en {@code rocas_diarias} y {@code entradas_diario} (R1: cerrar
 * mueve {@code actualizado_en}), la fila de {@code envios_sin_red} y el choque contra una escritura posterior del servidor.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DisplayName("Cierra tu día sin red: lo que se guarda de verdad (spec 048, fase 3)")
class CierreDelDiaSinRedIT {

    private static final LocalDate EL_9 = LocalDate.of(2026, 11, 9);
    /** 23:50 del lunes 9 en Lima. */
    private static final Instant HECHO = Instant.parse("2026-11-10T04:50:00Z");
    /** 08:00 del martes 10 en Lima. */
    private static final Instant LLEGA = Instant.parse("2026-11-10T13:00:00Z");
    private static final Instant CREADA = Instant.parse("2026-11-09T14:00:00Z");

    @Autowired private CerrarElDiaSinRedUseCase cerrarSinRed;
    @Autowired private CerrarElDiaUseCase cerrar;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RelojDePrueba reloj;

    private UserId persona;
    private UUID pendiente;
    private UUID cumplida;

    @BeforeEach
    void sembrar() {
        reloj.fijar(LLEGA);
        UUID id = UUID.randomUUID();
        persona = UserId.of(id);
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado) VALUES (?, ?, 'Prueba', 'APRENDIZ', 'ACTIVO')",
                id, id + "@prueba.test");
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, fecha_inicio, dia_programa, programa_activado_en, timezone) "
                + "VALUES (?, ?, 10, now(), 'America/Lima')", id, EL_9.minusDays(9));
        cumplida = sembrarRoca(1, "Enviar la propuesta", "CUERPO", true);
        pendiente = sembrarRoca(2, "Revisar las facturas", "TRABAJO", false);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", persona.value());
    }

    private UUID sembrarRoca(int posicion, String titulo, String eje, boolean completada) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.rocas_diarias (id, participante_id, fecha, posicion, titulo, color, puntaje_impacto, eje, completada, "
                        + "completada_en, puntos_otorgados, creado_en, actualizado_en) VALUES (?, ?, ?, ?, ?, ?::renaser.color_pareto, 5, "
                        + "?::renaser.eje_objetivo, ?, ?, ?, ?, ?)",
                id, persona.value(), EL_9, posicion, titulo, posicion == 1 ? "VERDE" : "AMARILLA", eje, completada,
                completada ? Timestamp.from(CREADA) : null, completada ? 8 : 0, Timestamp.from(CREADA), Timestamp.from(CREADA));
        return id;
    }

    private HechoSinRed envio(UUID id, Instant hechoEn) {
        return new HechoSinRed(hechoEn, id, OrigenDeLaHora.RELOJ_DEL_SISTEMA);
    }

    private CerrarElDia pedido(LocalDate fecha, String nota) {
        return new CerrarElDia(persona, fecha, List.of(new RespuestaDeAccion(pendiente, "SIN_TIEMPO", "OTRA_HORA", "Se alargó la reunión")), nota);
    }

    private Map<String, Object> roca(UUID id) {
        return jdbc.queryForMap("SELECT completada, puntos_otorgados, cierre_motivo, cierre_en, actualizado_en FROM renaser.rocas_diarias WHERE id = ?", id);
    }

    private String notaDelDia(LocalDate fecha) {
        return jdbc.queryForObject("SELECT contenido_texto FROM renaser.entradas_diario WHERE participante_id = ? AND fecha = ? "
                + "AND tipo = 'EVALUACION_DIA'::renaser.tipo_entrada_diario", String.class, persona.value(), fecha);
    }

    private Integer filas(UUID envio) {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.envios_sin_red WHERE id_cliente = ?", Integer.class, envio);
    }

    @Test
    @DisplayName("cerrar a las 23:50 y llegar al día siguiente se aplica al lunes: el cierre queda con la hora real y las filas del lunes")
    void cruzaLaMedianoche() { // spec 048 FR-019
        UUID envio = UUID.randomUUID();

        var vista = cerrarSinRed.cerrar(pedido(EL_9, "Cerré tarde"), envio(envio, HECHO));

        assertThat(vista.dia().fecha()).isEqualTo(EL_9);
        assertThat(vista.puedeCerrar()).isFalse();
        Map<String, Object> sinHacer = roca(pendiente);
        assertThat(sinHacer.get("cierre_motivo")).isEqualTo("SIN_TIEMPO");
        assertThat(((Timestamp) sinHacer.get("cierre_en")).toInstant()).isEqualTo(HECHO);
        assertThat(((Timestamp) sinHacer.get("actualizado_en")).toInstant()).isEqualTo(HECHO);
        assertThat(sinHacer.get("completada")).isEqualTo(false);
        assertThat(roca(cumplida).get("completada")).isEqualTo(true);
        assertThat(((Number) roca(cumplida).get("puntos_otorgados")).intValue()).isEqualTo(8);
        assertThat(notaDelDia(EL_9)).isEqualTo("Cerré tarde");
        assertThat(jdbc.queryForObject("SELECT actualizado_en FROM renaser.entradas_diario WHERE participante_id = ? AND fecha = ?",
                Timestamp.class, persona.value(), EL_9).toInstant()).isEqualTo(HECHO);
        Map<String, Object> fila = jdbc.queryForMap("SELECT tipo, recurso, resultado FROM renaser.envios_sin_red WHERE id_cliente = ?", envio);
        assertThat(fila).containsEntry("tipo", "CIERRE_DEL_DIA").containsEntry("recurso", "cierre:2026-11-09").containsEntry("resultado", "APLICADO");
    }

    @Test
    @DisplayName("el mismo envío dos veces: el segundo no cambia nada (ni la nota) y hay una sola fila de envío")
    void repetirElEnvio() { // spec 048 FR-008 FR-019
        UUID envio = UUID.randomUUID();
        cerrarSinRed.cerrar(pedido(EL_9, "La primera"), envio(envio, HECHO));

        cerrarSinRed.cerrar(pedido(EL_9, "Otra distinta"), envio(envio, HECHO));

        assertThat(notaDelDia(EL_9)).isEqualTo("La primera");
        assertThat(filas(envio)).isEqualTo(1);
    }

    @Test
    @DisplayName("D13: si desde otra sesión se cerró el día DESPUÉS de lo hecho en el teléfono, gana el servidor: no se aplica, queda anotado y repetirlo da lo mismo")
    void gana_el_servidor_si_cerro_despues() { // spec 048 FR-019
        jdbc.update("INSERT INTO renaser.entradas_diario (participante_id, fecha, tipo, contenido_texto, creado_en, actualizado_en) "
                        + "VALUES (?, ?, 'EVALUACION_DIA'::renaser.tipo_entrada_diario, 'Desde la web', ?, ?)",
                persona.value(), EL_9, Timestamp.from(HECHO.plusSeconds(600)), Timestamp.from(HECHO.plusSeconds(600)));
        UUID envio = UUID.randomUUID();

        assertThatThrownBy(() -> cerrarSinRed.cerrar(pedido(EL_9, "Del teléfono"), envio(envio, HECHO)))
                .isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR));

        assertThat(notaDelDia(EL_9)).isEqualTo("Desde la web");
        assertThat(roca(pendiente).get("cierre_motivo")).isNull();
        Map<String, Object> fila = jdbc.queryForMap("SELECT resultado, codigo FROM renaser.envios_sin_red WHERE id_cliente = ?", envio);
        assertThat(fila).containsEntry("resultado", "NO_APLICADO").containsEntry("codigo", "CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR");
        assertThatThrownBy(() -> cerrarSinRed.cerrar(pedido(EL_9, "Del teléfono"), envio(envio, HECHO)))
                .isInstanceOf(NoAplicadoSinRedException.class);
        assertThat(filas(envio)).isEqualTo(1);
    }

    @Test
    @DisplayName("lo contrario: si el cambio del servidor fue ANTES de lo hecho en el teléfono, se aplica lo del teléfono")
    void gana_el_telefono_si_el_cambio_fue_antes() { // spec 048 FR-019
        jdbc.update("INSERT INTO renaser.entradas_diario (participante_id, fecha, tipo, contenido_texto, creado_en, actualizado_en) "
                        + "VALUES (?, ?, 'EVALUACION_DIA'::renaser.tipo_entrada_diario, 'Desde la web', ?, ?)",
                persona.value(), EL_9, Timestamp.from(HECHO.minusSeconds(7200)), Timestamp.from(HECHO.minusSeconds(7200)));

        cerrarSinRed.cerrar(pedido(EL_9, "Del teléfono"), envio(UUID.randomUUID(), HECHO));

        assertThat(notaDelDia(EL_9)).isEqualTo("Del teléfono");
        assertThat(roca(pendiente).get("cierre_motivo")).isEqualTo("SIN_TIEMPO");
    }

    @Test
    @DisplayName("cerrar un día que no es el de la hora real es FUERA_DE_SU_DIA y no escribe nada")
    void otroDia() { // spec 048 FR-019
        UUID envio = UUID.randomUUID();

        assertThatThrownBy(() -> cerrarSinRed.cerrar(pedido(EL_9.plusDays(1), null), envio(envio, HECHO)))
                .isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.FUERA_DE_SU_DIA));

        assertThat(roca(pendiente).get("cierre_motivo")).isNull();
        assertThat(filas(envio)).isEqualTo(1);
    }

    @Test
    @DisplayName("R1: cerrar el día CON red también mueve rocas_diarias.actualizado_en a la hora del cierre (antes lo dejaba como estaba)")
    void cerrarConRedMueveActualizadoEn() { // spec 048 FR-019
        Instant lasNueve = Instant.parse("2026-11-10T02:00:00Z");
        reloj.fijar(lasNueve);

        cerrar.cerrar(pedido(EL_9, null));

        assertThat(((Timestamp) roca(pendiente).get("actualizado_en")).toInstant()).isEqualTo(lasNueve);
        assertThat(((Timestamp) roca(pendiente).get("cierre_en")).toInstant()).isEqualTo(lasNueve);
    }
}
