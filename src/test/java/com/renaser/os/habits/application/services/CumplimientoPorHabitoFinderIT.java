package com.renaser.os.habits.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.points.api.CumplimientoDeHabito;
import com.renaser.os.points.api.CumplimientoPorHabitoFinder;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-277 contra Postgres real: lo que {@code habits} reporta hábito por hábito en un rango, como lo guarda la app
 * ({@code registros_habito}). Los opcionales no cumplidos no cuentan y el renombre de la persona manda sobre el
 * nombre del catálogo, que se ve en mayúsculas.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class CumplimientoPorHabitoFinderIT {

    private static final LocalDate DESDE = LocalDate.of(2026, 10, 12);
    private static final LocalDate HASTA = LocalDate.of(2026, 10, 18);

    @Autowired
    private CumplimientoPorHabitoFinder finder;
    @Autowired
    private JdbcClient jdbcClient;

    private UserId persona;
    private UserId otra;
    private final String sufijo = UUID.randomUUID().toString().substring(0, 8);

    @BeforeEach
    void personas() {
        persona = aprendiz();
        otra = aprendiz();
    }

    @Test
    @DisplayName("por hábito devuelve los días que contaban y los cumplidos dentro del rango")
    void porHabitoEnElRango() {
        UUID agua = habito("AGUA " + sufijo);
        for (int dia = 0; dia < 7; dia++) {
            registro(persona, agua, DESDE.plusDays(dia), dia < 5 ? "COMPLETADO" : "EXPIRADO", false);
        }
        registro(persona, agua, DESDE.minusDays(1), "COMPLETADO", false);
        registro(persona, agua, HASTA.plusDays(1), "COMPLETADO", false);

        List<CumplimientoDeHabito> resultado = finder.enRango(persona, DESDE, HASTA);

        assertThat(resultado).containsExactly(new CumplimientoDeHabito("AGUA " + sufijo, 7, 5));
    }

    @Test
    @DisplayName("un opcional sin cumplir no cuenta; uno cumplido sí")
    void losOpcionalesNoCumplidosNoCuentan() {
        UUID extra = habito("EXTRA " + sufijo);
        registro(persona, extra, DESDE, "COMPLETADO", true);
        registro(persona, extra, DESDE.plusDays(1), "EXPIRADO", true);
        registro(persona, extra, DESDE.plusDays(2), "PENDIENTE", true);
        registro(persona, extra, DESDE.plusDays(3), "EXPIRADO", false);

        List<CumplimientoDeHabito> resultado = finder.enRango(persona, DESDE, HASTA);

        assertThat(resultado).containsExactly(new CumplimientoDeHabito("EXTRA " + sufijo, 2, 1));
    }

    @Test
    @DisplayName("el nombre es el que ve la persona: el renombre si lo hay, y el del catálogo si no")
    void respetaElRenombre() {
        UUID renombrado = habito("JUGO VERDE " + sufijo);
        UUID catalogo = habito("LECTURA " + sufijo);
        for (int dia = 0; dia < 3; dia++) {
            registro(persona, renombrado, DESDE.plusDays(dia), "COMPLETADO", false);
            registro(persona, catalogo, DESDE.plusDays(dia), "COMPLETADO", false);
        }
        jdbcClient.sql("""
                        INSERT INTO renaser.renombres_habito (participante_id, habito_id, titulo_personal, motivo)
                        VALUES (:persona, :habito, 'Mi batido', 'prefiero este nombre')
                        """)
                .param("persona", persona.value()).param("habito", renombrado).update();
        registro(otra, catalogo, DESDE, "COMPLETADO", false);

        List<CumplimientoDeHabito> resultado = finder.enRango(persona, DESDE, HASTA);

        assertThat(resultado).extracting(CumplimientoDeHabito::nombre)
                .containsExactlyInAnyOrder("Mi batido", "LECTURA " + sufijo);
    }

    @Test
    @DisplayName("lo de otra persona no entra, y sin registros no hay nada")
    void soloLaPersonaPedida() {
        UUID agua = habito("AGUA " + sufijo);
        registro(otra, agua, DESDE, "COMPLETADO", false);

        assertThat(finder.enRango(persona, DESDE, HASTA)).isEmpty();
    }

    private UserId aprendiz() {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, 'Fixture Cumplimiento', 'APRENDIZ', 'ACTIVO')
                        """)
                .param("id", id).param("email", id + "@renaser.test").update();
        jdbcClient.sql("""
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio,
                                                                    programa_activado_en, timezone)
                        VALUES (:id, 12, :inicio, :activado, 'America/Lima')
                        """)
                .param("id", id).param("inicio", DESDE.minusDays(10))
                .param("activado", Timestamp.from(Instant.parse("2026-10-01T15:00:00Z"))).update();
        return UserId.of(id);
    }

    private UUID habito(String titulo) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO renaser.habitos (id, titulo, categoria_clave)
                        VALUES (:id, :titulo, (SELECT clave FROM renaser.categorias_habito LIMIT 1))
                        """)
                .param("id", id).param("titulo", titulo).update();
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
}
