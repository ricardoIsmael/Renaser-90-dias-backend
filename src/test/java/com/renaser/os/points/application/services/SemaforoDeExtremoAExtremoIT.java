package com.renaser.os.points.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.EstadoDiaSemaforo;
import com.renaser.os.points.api.SemaforoFinder;
import com.renaser.os.points.api.SemanaDelSemaforoCerradaEvent;
import com.renaser.os.points.api.VentanaDelSemaforo;
import com.renaser.os.points.application.ports.in.semaforo.CerrarSemaforoUseCase;
import com.renaser.os.points.application.ports.in.semaforo.CerrarSemaforoUseCase.ResultadoDelCierre;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El semáforo de punta a punta contra Postgres real: registros de hábitos y objetivos del día
 * guardados como los guarda la app, el barrido del sábado 00:30 de Lima, la lectura y el aviso. Cruza
 * los cuatro módulos por sus APIs públicas (users, habits, rocks, points), sin dobles.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, SemaforoDeExtremoAExtremoIT.RelojDelSabado.class})
@RecordApplicationEvents
@Transactional
class SemaforoDeExtremoAExtremoIT {

    /** Sábado 26 de septiembre, 00:30 en Lima. */
    private static final Instant SABADO_0030_LIMA = Instant.parse("2026-09-26T05:30:00Z");
    private static final LocalDate SABADO_19 = LocalDate.of(2026, 9, 19);
    private static final LocalDate LUNES_21 = LocalDate.of(2026, 9, 21);
    private static final LocalDate VIERNES_25 = LocalDate.of(2026, 9, 25);

    @TestConfiguration
    static class RelojDelSabado {
        @Bean
        @Primary
        Clock relojDelSabado() {
            return FixedClock.at(SABADO_0030_LIMA);
        }
    }

    @Autowired
    private CerrarSemaforoUseCase cerrarSemaforo;
    @Autowired
    private SemaforoFinder semaforoFinder;
    @Autowired
    private JdbcClient jdbcClient;
    @Autowired
    private ApplicationEvents eventos;

    private UserId ana;

    /**
     * Ana arrancó el sábado 19 (día 1). Dos hábitos obligatorios por día: los cumple todos hasta el
     * jueves 23 y uno solo el 24 y el 25. Un opcional sin cumplir el lunes (no cuenta). El lunes además
     * planificó dos objetivos y cumplió uno. Días: 100, 100, 75, 100, 100, 50, 50 → promedio 82,1.
     */
    @BeforeEach
    void semanaDeAna() {
        ana = aprendizConProgramaDesde(SABADO_19);
        UUID meditar = habito("Meditar");
        UUID agua = habito("Agua");
        UUID opcional = habito("Extra opcional");
        for (LocalDate dia = SABADO_19; !dia.isAfter(VIERNES_25); dia = dia.plusDays(1)) {
            boolean completo = dia.isBefore(LocalDate.of(2026, 9, 24));
            registro(ana, meditar, dia, "COMPLETADO", false);
            registro(ana, agua, dia, completo ? "COMPLETADO" : "EXPIRADO", false);
        }
        registro(ana, opcional, LUNES_21, "EXPIRADO", true);
        objetivo(ana, LUNES_21, 1, true);
        objetivo(ana, LUNES_21, 2, false);
    }

    @Test
    void elSabadoSeCierraLaSemanaSeGuardaYSeAvisaUnaSolaVez() {
        ResultadoDelCierre primera = cerrarSemaforo.cerrarPendientes();

        assertThat(primera.semanasCerradas()).isGreaterThanOrEqualTo(1);
        VentanaDelSemaforo semana = semaforoFinder.semanaDe(List.of(ana), VIERNES_25).get(ana);
        assertThat(semana.cerrada()).isTrue();
        assertThat(semana.porcentaje()).isEqualByComparingTo("82.1");
        assertThat(semana.color()).isEqualTo(ColorSemaforo.VERDE);
        assertThat(semana.diasConDatos()).isEqualTo(7);
        assertThat(semana.dias().get(2).porcentaje()).isEqualTo(75);
        assertThat(semana.dias().get(2).objetivosProgramados()).isEqualTo(2);
        assertThat(semana.dias().get(2).habitosProgramados()).isEqualTo(2);

        VentanaDelSemaforo vigente = semaforoFinder.vigenteDe(List.of(ana)).get(ana);
        assertThat(vigente.porcentaje()).isEqualByComparingTo("82.1");
        assertThat(vigente.dias()).extracting(d -> d.estado()).containsOnly(EstadoDiaSemaforo.MEDIDO);

        assertThat(eventosDeAna()).singleElement()
                .satisfies(e -> assertThat(e.hasta()).isEqualTo(VIERNES_25));

        cerrarSemaforo.cerrarPendientes();
        assertThat(eventosDeAna()).hasSize(1);
    }

    private List<SemanaDelSemaforoCerradaEvent> eventosDeAna() {
        return eventos.stream(SemanaDelSemaforoCerradaEvent.class)
                .filter(e -> e.participanteId().equals(ana.value()))
                .toList();
    }

    private UserId aprendizConProgramaDesde(LocalDate fechaInicio) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, 'Ana Semaforo', 'APRENDIZ', 'ACTIVO')
                        """)
                .param("id", id).param("email", id + "@renaser.test").update();
        jdbcClient.sql("""
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio,
                                                                    programa_activado_en, timezone)
                        VALUES (:id, 8, :inicio, :activado, 'America/Lima')
                        """)
                .param("id", id).param("inicio", fechaInicio)
                .param("activado", java.sql.Timestamp.from(Instant.parse("2026-09-18T15:00:00Z"))).update();
        return UserId.of(id);
    }

    private UUID habito(String titulo) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO renaser.habitos (id, titulo, categoria_clave)
                        VALUES (:id, :titulo, (SELECT clave FROM renaser.categorias_habito LIMIT 1))
                        """)
                .param("id", id).param("titulo", titulo + " " + id).update();
        return id;
    }

    private void registro(UserId participante, UUID habito, LocalDate fecha, String estado, boolean opcional) {
        jdbcClient.sql("""
                        INSERT INTO renaser.registros_habito (id, participante_id, habito_id, fecha_ejecucion,
                                                             dia_programa, tipo_dia, es_opcional, estado)
                        VALUES (:id, :pid, :hid, :fecha, 1, CAST('DISCIPLINA' AS renaser.tipo_dia), :opcional,
                                CAST(:estado AS renaser.estado_registro))
                        """)
                .param("id", UUID.randomUUID()).param("pid", participante.value()).param("hid", habito)
                .param("fecha", fecha).param("opcional", opcional).param("estado", estado).update();
    }

    private void objetivo(UserId participante, LocalDate fecha, int posicion, boolean completada) {
        jdbcClient.sql("""
                        INSERT INTO renaser.rocas_diarias (id, participante_id, fecha, posicion, titulo, color,
                                                           puntaje_impacto, eje, completada)
                        VALUES (:id, :pid, :fecha, :posicion, 'Objetivo', CAST('VERDE' AS renaser.color_pareto), 5,
                                CAST('CUERPO' AS renaser.eje_objetivo), :completada)
                        """)
                .param("id", UUID.randomUUID()).param("pid", participante.value()).param("fecha", fecha)
                .param("posicion", posicion).param("completada", completada).update();
    }
}
