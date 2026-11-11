package com.renaser.os.rocks.application.services;

import com.renaser.os.RelojDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.rocks.application.ports.in.rocadiaria.AccionesSinRedUseCase;
import com.renaser.os.rocks.application.ports.in.rocadiaria.CompletarRocaDiariaUseCase.CompletarRocaDiariaCommand;
import com.renaser.os.rocks.application.ports.in.rocadiaria.CrearPlanDiarioUseCase.CrearPlanDiarioCommand;
import com.renaser.os.rocks.application.ports.in.rocadiaria.CrearPlanDiarioUseCase.ItemRocaDiaria;
import com.renaser.os.rocks.application.ports.in.rocamaestra.DefinirRocaMaestraUseCase.DefinirRocaMaestraCommand;
import com.renaser.os.rocks.application.ports.in.rocamensual.DefinirRocaMensualUseCase.DefinirRocaMensualCommand;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CrearPlanSemanalUseCase.CrearPlanSemanalCommand;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CrearPlanSemanalUseCase.ItemRocaSemanal;
import com.renaser.os.rocks.application.ports.in.rocasemanal.ObjetivosSinRedUseCase;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiariaId;
import com.renaser.os.rocks.domain.model.rocadiaria.TipoEvidenciaRoca;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocasemanal.SemanaPrograma;
import com.renaser.os.shared.domain.CodigoNoAplicado;
import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.MotivoSinPuntos;
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
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El Plan hecho sin red contra Postgres de verdad (spec 048, fase 3, FR-019). La persona lo hace el lunes 9 a las 21:40 de Lima (02:40 UTC del 10, el
 * día local ANTERIOR en UTC) y la app lo envía el martes 10 a las 08:00. Fija lo que ningún doble puede: la hora real en las filas ({@code creado_en} y
 * {@code actualizado_en}, R1), los puntos, y que una fila cambiada después en el servidor gane.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DisplayName("Plan sin red: lo que se guarda de verdad (spec 048, fase 3)")
class PlanDeRocasSinRedIT {

    private static final LocalDate EL_9 = LocalDate.of(2026, 11, 9);
    private static final LocalDate EL_10 = LocalDate.of(2026, 11, 10);
    private static final Instant HECHO = Instant.parse("2026-11-10T02:40:00Z");
    private static final Instant LLEGA = Instant.parse("2026-11-10T13:00:00Z");
    private static final Instant VIEJO = Instant.parse("2026-10-30T14:00:00Z");
    private static final LocalDate PRIMER_DIA = LocalDate.of(2026, 10, 26);

    @Autowired private AccionesSinRedUseCase acciones;
    @Autowired private ObjetivosSinRedUseCase objetivos;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RelojDePrueba reloj;

    private UserId persona;
    private UUID maestraCuerpo;

    @BeforeEach
    void sembrar() {
        reloj.fijar(LLEGA);
        UUID id = UUID.randomUUID();
        persona = UserId.of(id);
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado) VALUES (?, ?, 'Prueba', 'APRENDIZ', 'ACTIVO')", id, id + "@prueba.test");
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, fecha_inicio, dia_programa, programa_activado_en, timezone) "
                + "VALUES (?, ?, 16, now(), 'America/Lima')", id, PRIMER_DIA);
        maestraCuerpo = sembrarMaestra("CUERPO");
        sembrarMaestra("TRABAJO");
        sembrarMaestra("RELACIONES");
        int semana = SemanaPrograma.desde(PRIMER_DIA).numeroSemanaParaFecha(EL_9);
        jdbc.update("INSERT INTO renaser.rocas_semanales (roca_maestra_id, numero_semana, titulo, obstaculo, contingencia, autoevaluacion_inicio, creado_en, actualizado_en) "
                + "VALUES (?, ?, 'Correr 3 veces', 'Pereza', 'Salir igual', 5, ?, ?)", maestraCuerpo, semana, Timestamp.from(VIEJO), Timestamp.from(VIEJO));
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", persona.value());
    }

    private UUID sembrarMaestra(String eje) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.rocas_maestras (id, participante_id, eje, objetivo, creado_en, actualizado_en, confirmada_en) "
                + "VALUES (?, ?, ?::renaser.eje_objetivo, 'Objetivo de prueba', ?, ?, ?)", id, persona.value(), eje, Timestamp.from(VIEJO), Timestamp.from(VIEJO), Timestamp.from(VIEJO));
        return id;
    }

    private UUID sembrarRocaDiaria(LocalDate fecha) {
        UUID id = UUID.randomUUID();
        UUID semanal = jdbc.queryForObject("SELECT id FROM renaser.rocas_semanales WHERE roca_maestra_id = ?", UUID.class, maestraCuerpo);
        jdbc.update("INSERT INTO renaser.rocas_diarias (id, participante_id, fecha, posicion, titulo, color, puntaje_impacto, eje, roca_semanal_id, completada, puntos_otorgados, creado_en, actualizado_en) "
                        + "VALUES (?, ?, ?, 1, 'Salir a correr', 'VERDE'::renaser.color_pareto, 5, 'CUERPO'::renaser.eje_objetivo, ?, false, 0, ?, ?)",
                id, persona.value(), fecha, semanal, Timestamp.from(VIEJO), Timestamp.from(VIEJO));
        return id;
    }

    private static HechoSinRed envio(UUID id, Instant hechoEn) {
        return new HechoSinRed(hechoEn, id, OrigenDeLaHora.RELOJ_DEL_SISTEMA);
    }

    private static CompletarRocaDiariaCommand marca(UserId persona, UUID roca) {
        return new CompletarRocaDiariaCommand(persona, RocaDiariaId.of(roca), TipoEvidenciaRoca.TEXTO, null, null, "Lo hice", null, null, null, true, false);
    }

    private Map<String, Object> rocaDiaria(UUID id) {
        return jdbc.queryForMap("SELECT completada, completada_en, puntos_otorgados, creado_en, actualizado_en FROM renaser.rocas_diarias WHERE id = ?", id);
    }

    @Test
    @DisplayName("agendar mañana el lunes por la noche y llegar el martes: las acciones nacen con la hora real; el mismo envío no las duplica; un día que ya pasó no se aplica")
    void agendar() { // spec 048 FR-019
        CrearPlanDiarioCommand plan = new CrearPlanDiarioCommand(persona, EL_10, List.of(
                new ItemRocaDiaria(EjeObjetivo.CUERPO, 1, "Salir a correr", null, 5, false, LocalTime.of(6, 0), null, List.of())));
        UUID envio = UUID.randomUUID();

        var creadas = acciones.agendar(plan, envio(envio, HECHO));
        acciones.agendar(plan, envio(envio, HECHO));

        assertThat(creadas).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.rocas_diarias WHERE participante_id = ? AND fecha = ?", Integer.class, persona.value(), EL_10)).isEqualTo(1);
        Map<String, Object> fila = jdbc.queryForMap("SELECT creado_en, actualizado_en FROM renaser.rocas_diarias WHERE participante_id = ? AND fecha = ?", persona.value(), EL_10);
        assertThat(((Timestamp) fila.get("creado_en")).toInstant()).isEqualTo(HECHO);
        assertThat(((Timestamp) fila.get("actualizado_en")).toInstant()).isEqualTo(HECHO);
        assertThat(jdbc.queryForObject("SELECT tipo FROM renaser.envios_sin_red WHERE id_cliente = ?", String.class, envio)).isEqualTo("PLAN_AGENDAR");

        CrearPlanDiarioCommand ayer = new CrearPlanDiarioCommand(persona, EL_9, plan.rocas());
        assertThatThrownBy(() -> acciones.agendar(ayer, envio(UUID.randomUUID(), HECHO))).isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.FUERA_DE_SU_DIA));
    }

    @Test
    @DisplayName("D13: un día ya armado cuyas acciones cambiaron en el servidor DESPUÉS de lo que hiciste gana el servidor y no se reemplaza")
    void agendaConChoque() { // spec 048 FR-019
        UUID delServidor = sembrarRocaDiaria(EL_10);
        jdbc.update("UPDATE renaser.rocas_diarias SET actualizado_en = ? WHERE id = ?", Timestamp.from(HECHO.plusSeconds(600)), delServidor);
        CrearPlanDiarioCommand plan = new CrearPlanDiarioCommand(persona, EL_10, List.of(
                new ItemRocaDiaria(EjeObjetivo.CUERPO, 1, "Otra", null, 5, false, null, null, List.of())));

        assertThatThrownBy(() -> acciones.agendar(plan, envio(UUID.randomUUID(), HECHO))).isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR));

        assertThat(jdbc.queryForObject("SELECT titulo FROM renaser.rocas_diarias WHERE id = ?", String.class, delServidor)).isEqualTo("Salir a correr");
    }

    @Test
    @DisplayName("marcar la acción de anoche sin red: queda cumplida con la hora real y CON puntos; el reintento no paga dos veces")
    void marcarEnRegla() { // spec 048 FR-019
        UUID roca = sembrarRocaDiaria(EL_9);
        UUID envio = UUID.randomUUID();

        var primera = acciones.marcar(marca(persona, roca), envio(envio, HECHO));
        var repetida = acciones.marcar(marca(persona, roca), envio(envio, HECHO));

        assertThat(primera.motivoSinPuntos()).isNull();
        assertThat(repetida.roca().id().value()).isEqualTo(roca);
        Map<String, Object> fila = rocaDiaria(roca);
        assertThat(fila.get("completada")).isEqualTo(true);
        assertThat(((Timestamp) fila.get("completada_en")).toInstant()).isEqualTo(HECHO);
        assertThat(((Timestamp) fila.get("actualizado_en")).toInstant()).isEqualTo(HECHO);
        assertThat(((Number) fila.get("puntos_otorgados")).intValue()).isEqualTo(10);
    }

    @Test
    @DisplayName("D14: marcar lo que llega a más de 48 h lo deja cumplido SIN puntos y con su motivo; nunca rebota")
    void marcarTarde() { // spec 048 FR-019
        LocalDate hace3 = LocalDate.of(2026, 11, 6);
        UUID roca = sembrarRocaDiaria(hace3);
        Instant hechoHace3 = Instant.parse("2026-11-07T02:00:00Z");

        var marcada = acciones.marcar(marca(persona, roca), envio(UUID.randomUUID(), hechoHace3));

        assertThat(marcada.motivoSinPuntos()).isEqualTo(MotivoSinPuntos.LLEGO_DESPUES_DE_48_H);
        Map<String, Object> fila = rocaDiaria(roca);
        assertThat(fila.get("completada")).isEqualTo(true);
        assertThat(((Number) fila.get("puntos_otorgados")).intValue()).isZero();
    }

    @Test
    @DisplayName("la semana: un eje nuevo se crea con la hora real; con todos los ejes ya planificados no se aplica (YA_ESTABA_HECHO)")
    void semana() { // spec 048 FR-019
        CrearPlanSemanalCommand trabajo = new CrearPlanSemanalCommand(persona, List.of(new ItemRocaSemanal(EjeObjetivo.TRABAJO, "Cerrar 2 ventas", "Miedo", "Llamar igual", 5)));

        objetivos.crearSemana(trabajo, envio(UUID.randomUUID(), HECHO));

        Map<String, Object> fila = jdbc.queryForMap("SELECT s.creado_en, s.actualizado_en FROM renaser.rocas_semanales s JOIN renaser.rocas_maestras m ON m.id = s.roca_maestra_id "
                + "WHERE m.participante_id = ? AND m.eje = 'TRABAJO'::renaser.eje_objetivo", persona.value());
        assertThat(((Timestamp) fila.get("creado_en")).toInstant()).isEqualTo(HECHO);
        assertThat(((Timestamp) fila.get("actualizado_en")).toInstant()).isEqualTo(HECHO);
        assertThatThrownBy(() -> objetivos.crearSemana(trabajo, envio(UUID.randomUUID(), HECHO))).isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.YA_ESTABA_HECHO));
    }

    @Test
    @DisplayName("objetivo del mes: se crea con la hora real; si el del servidor cambió DESPUÉS, gana el servidor")
    void objetivoDelMes() { // spec 048 FR-019
        DefinirRocaMensualCommand mes = new DefinirRocaMensualCommand(persona, EjeObjetivo.CUERPO, 1, "Correr 12 km", null, null, null);
        UUID envio = UUID.randomUUID();

        objetivos.definirObjetivoDelMes(mes, envio(envio, HECHO));
        objetivos.definirObjetivoDelMes(mes, envio(envio, HECHO));

        Map<String, Object> fila = jdbc.queryForMap("SELECT titulo, creado_en, actualizado_en FROM renaser.rocas_mensuales WHERE roca_maestra_id = ?", maestraCuerpo);
        assertThat(fila.get("titulo")).isEqualTo("Correr 12 km");
        assertThat(((Timestamp) fila.get("actualizado_en")).toInstant()).isEqualTo(HECHO);

        jdbc.update("UPDATE renaser.rocas_mensuales SET titulo = 'Del servidor', actualizado_en = ? WHERE roca_maestra_id = ?", Timestamp.from(HECHO.plusSeconds(3600)), maestraCuerpo);
        assertThatThrownBy(() -> objetivos.definirObjetivoDelMes(new DefinirRocaMensualCommand(persona, EjeObjetivo.CUERPO, 1, "Correr 15 km", null, null, null),
                envio(UUID.randomUUID(), HECHO.plusSeconds(60)))).isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR));
        assertThat(jdbc.queryForObject("SELECT titulo FROM renaser.rocas_mensuales WHERE roca_maestra_id = ?", String.class, maestraCuerpo)).isEqualTo("Del servidor");
    }

    @Test
    @DisplayName("objetivo de 90 días: lo mismo confirmado es idempotente; un avance anotado en el servidor DESPUÉS gana el servidor")
    void objetivoDe90Dias() { // spec 048 FR-019
        DefinirRocaMaestraCommand mismo = new DefinirRocaMaestraCommand(persona, EjeObjetivo.CUERPO, "Objetivo de prueba", null, null, null, null, false);
        objetivos.definirObjetivoDe90Dias(mismo, envio(UUID.randomUUID(), HECHO));

        jdbc.update("UPDATE renaser.rocas_maestras SET actualizado_en = ? WHERE id = ?", Timestamp.from(HECHO.plusSeconds(120)), maestraCuerpo);
        assertThatThrownBy(() -> objetivos.definirObjetivoDe90Dias(mismo, envio(UUID.randomUUID(), HECHO))).isInstanceOfSatisfying(
                NoAplicadoSinRedException.class, e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR));
    }
}
