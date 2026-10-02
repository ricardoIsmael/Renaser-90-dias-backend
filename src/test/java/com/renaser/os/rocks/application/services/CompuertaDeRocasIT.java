package com.renaser.os.rocks.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.rocks.api.CompuertaDeRocasFinder;
import com.renaser.os.rocks.api.PlanificacionDeRocasPort;
import com.renaser.os.rocks.api.PlanificacionDeRocasPort.MotivoRechazo;
import com.renaser.os.rocks.api.PlanificacionDeRocasPort.ObjetivoDeLaSemana;
import com.renaser.os.rocks.api.PlanificacionDeRocasPort.ResultadoPlanificacion;
import com.renaser.os.rocks.domain.model.rocasemanal.SemanaPrograma;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code rocks.api.CompuertaDeRocasFinder} contra Postgres de verdad (D-247, E-496): lo que responde es lo
 * MISMO que despues decide la escritura. Sin Rocas Maestras dice "cerrada" y el plan de la semana vuelve con
 * {@code ROCAS_BLOQUEADAS}; con las tres, abierta, y los ejes sin objetivo semanal son los que no tienen fila
 * en {@code rocas_semanales} para la semana de esa fecha.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DisplayName("Compuerta de rocas: lo que consulta el acompanante antes de proponer (D-247)")
class CompuertaDeRocasIT {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Autowired
    private CompuertaDeRocasFinder compuerta;

    @Autowired
    private PlanificacionDeRocasPort planificacion;

    @Autowired
    private Clock clock;

    @Autowired
    private JdbcTemplate jdbc;

    private UserId participante;
    private LocalDate primerDia;
    private LocalDate manana;

    @BeforeEach
    void sembrarParticipanteSinMapa() {
        UUID id = UUID.randomUUID();
        participante = UserId.of(id);
        LocalDate hoy = clock.now().atZone(LIMA).toLocalDate();
        primerDia = hoy.minusDays(9);
        manana = hoy.plusDays(1);
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado) "
                + "VALUES (?, ?, 'Prueba', 'APRENDIZ', 'ACTIVO')", id, id + "@prueba.test");
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, fecha_inicio, dia_programa, "
                + "programa_activado_en, timezone) VALUES (?, ?, 10, now(), 'America/Lima')", id, primerDia);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", participante.value());
    }

    private UUID sembrarMaestra(String eje) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.rocas_maestras (id, participante_id, eje, objetivo) "
                + "VALUES (?, ?, ?::renaser.eje_objetivo, 'Objetivo de prueba')", id, participante.value(), eje);
        return id;
    }

    @Test
    @DisplayName("sin Rocas Maestras: cerrada, los tres ejes sin objetivo, y la escritura lo rechaza igual")
    void sinMaestrasCerrada() {
        assertThat(compuerta.rocasMaestrasCompletas(participante)).isFalse();
        assertThat(compuerta.ejesSinObjetivoSemanal(participante, manana))
                .containsExactly("CUERPO", "TRABAJO", "RELACIONES");

        ResultadoPlanificacion escritura = planificacion.crearPlanDeLaSemana(participante,
                List.of(new ObjetivoDeLaSemana("CUERPO", "Bajar 1 kg", null, null, null)));
        assertThat(escritura).isEqualTo(new ResultadoPlanificacion.Rechazado(MotivoRechazo.ROCAS_BLOQUEADAS));
    }

    @Test
    @DisplayName("con dos de tres maestras sigue cerrada: hacen falta las tres")
    void dosDeTresSigueCerrada() {
        sembrarMaestra("CUERPO");
        sembrarMaestra("TRABAJO");

        assertThat(compuerta.rocasMaestrasCompletas(participante)).isFalse();
    }

    @Test
    @DisplayName("con las tres: abierta, y solo faltan los ejes sin objetivo en la semana de esa fecha")
    void conLasTresAbierta() {
        UUID cuerpo = sembrarMaestra("CUERPO");
        sembrarMaestra("TRABAJO");
        sembrarMaestra("RELACIONES");
        int semana = SemanaPrograma.desde(primerDia).numeroSemanaParaFecha(manana);
        jdbc.update("INSERT INTO renaser.rocas_semanales (roca_maestra_id, numero_semana, titulo) VALUES (?, ?, ?)",
                cuerpo, semana, "Correr 3 veces");

        assertThat(compuerta.rocasMaestrasCompletas(participante)).isTrue();
        assertThat(compuerta.ejesSinObjetivoSemanal(participante, manana)).containsExactly("TRABAJO", "RELACIONES");
    }
}
