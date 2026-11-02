package com.renaser.os.habits.infrastructure.adapter.out.persistence.habito;

import com.renaser.os.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V164 (issue #62, D-314): las descripciones del catalogo que SER entrega con {@code consultar_como_se_hace_habito} dejan de contradecir lo que el dueno
 * decidio (rituales con texto, ultima comida con circulo, kilometros con pasos), y la llave de {@code base_conocimiento.leccion_id} pasa a
 * {@code ON DELETE CASCADE}. La parte clave es que es CONDICIONAL: si el dueno ya edito una descripcion desde el panel de produccion, la migracion no la pisa.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DescripcionesDeHabitosParaSerIT {

    private static final String MIGRACION = "db/migration/V164__textos_viejos_de_ser_y_transcripciones_con_su_leccion.sql";

    private static final UUID ULTIMA_COMIDA = UUID.fromString("3a8a82a0-787d-46bc-a69e-b29c110a37bc");
    /** El ritual del domingo: sin clave de sistema y de evidencia opcional, su «Foto/video del ritual» sigue siendo verdad. */
    private static final UUID RITUAL_DEL_DOMINGO = UUID.fromString("60d6c870-df03-4d36-8a88-8c7411ae406b");

    private static final List<String> CLAVES_DE_RITUAL = List.of("RITUAL_MORNING", "RITUAL_MIDDAY", "RITUAL_NIGHT");

    private static final String RITUAL_ORIGINAL = "Foto/video del ritual";
    private static final String ULTIMA_COMIDA_ORIGINAL = "Foto de la última comida del día 6pm";
    private static final String KM_ORIGINAL =
            "Sube la captura de tu app de actividad con la distancia y la fecha de hoy, y escribe cuántos km recorriste.";
    private static final String APRENDIZ_ORIGINAL = "Participante del programa de 90 días";

    @Autowired
    private JdbcTemplate jdbc;

    private String sqlDeLaMigracion() throws Exception {
        return new ClassPathResource(MIGRACION).getContentAsString(StandardCharsets.UTF_8);
    }

    private String descripcionPorClave(String clave) {
        return jdbc.queryForObject("SELECT descripcion FROM habitos WHERE clave_sistema = ? AND formacion = 'ESCUELA'", String.class, clave);
    }

    private String descripcionPorId(UUID id) {
        return jdbc.queryForObject("SELECT descripcion FROM habitos WHERE id = ?", String.class, id);
    }

    private Timestamp actualizadoEnPorClave(String clave) {
        return jdbc.queryForObject("SELECT actualizado_en FROM habitos WHERE clave_sistema = ? AND formacion = 'ESCUELA'", Timestamp.class, clave);
    }

    /** Deja el catalogo como lo sembraron V4 y V84, antes de que V164 lo tocara. */
    private void volverALosTextosOriginales() {
        jdbc.update("UPDATE habitos SET descripcion = ? WHERE formacion = 'ESCUELA' AND clave_sistema IN ('RITUAL_MORNING', 'RITUAL_MIDDAY', 'RITUAL_NIGHT')", RITUAL_ORIGINAL);
        jdbc.update("UPDATE habitos SET descripcion = ? WHERE id = ?", ULTIMA_COMIDA_ORIGINAL, ULTIMA_COMIDA);
        jdbc.update("UPDATE habitos SET descripcion = ? WHERE formacion = 'ESCUELA' AND clave_sistema = 'DAILY_KM'", KM_ORIGINAL);
        jdbc.update("UPDATE roles SET descripcion = ? WHERE clave = 'APRENDIZ'", APRENDIZ_ORIGINAL);
    }

    /** Las demas pruebas comparten esta base: lo que dejan es el estado que deja la migracion. */
    @AfterEach
    void dejarElEstadoQueDejaLaMigracion() throws Exception {
        volverALosTextosOriginales();
        jdbc.execute(sqlDeLaMigracion());
    }

    // issue #62
    @Test
    @DisplayName("V164: los tres rituales se registran escribiendo, la ultima comida es opcional y los km cuentan los pasos")
    void lasDescripcionesDejanDeContradecirLoDecidido() {
        for (String clave : CLAVES_DE_RITUAL) {
            assertThat(descripcionPorClave(clave)).as(clave).contains("escribiendo").contains("¿cómo te sientes después de realizar el ritual?")
                    .doesNotContain("Foto").doesNotContain("video");
        }
        assertThat(descripcionPorId(ULTIMA_COMIDA)).contains("opcional").contains("con el círculo").contains("sin foto obligatoria")
                .doesNotContain("Foto de la última comida");
        assertThat(descripcionPorClave("DAILY_KM")).contains("Health Connect").contains("se cuentan solos")
                .contains("captura de tu app de actividad");
        assertThat(jdbc.queryForObject("SELECT descripcion FROM roles WHERE clave = 'APRENDIZ'", String.class))
                .isEqualTo("Participante de un programa de Renaser").doesNotContain("90");
    }

    // issue #62
    @Test
    @DisplayName("V164 no toca el ritual del domingo: es de evidencia opcional y su «Foto/video del ritual» sigue siendo verdad")
    void elRitualDelDomingoSeQueda() {
        assertThat(descripcionPorId(RITUAL_DEL_DOMINGO)).isEqualTo(RITUAL_ORIGINAL);
    }

    // issue #62
    @Test
    @DisplayName("V164 es condicional: una descripcion que el dueno ya cambio en el panel no se pisa, ni mueve su fecha")
    void noPisaLoQueElDuenoYaEdito() throws Exception {
        volverALosTextosOriginales();
        String delDueno = "Ritual de la mañana: agua, tierra y fuego, como lo explica el dueño.";
        String kmDelDueno = "Camina o corre y cuéntanos cuántos km hiciste.";
        jdbc.update("UPDATE habitos SET descripcion = ? WHERE formacion = 'ESCUELA' AND clave_sistema = 'RITUAL_MORNING'", delDueno);
        jdbc.update("UPDATE habitos SET descripcion = ? WHERE formacion = 'ESCUELA' AND clave_sistema = 'DAILY_KM'", kmDelDueno);
        Timestamp antesRitual = actualizadoEnPorClave("RITUAL_MORNING");
        Timestamp antesKm = actualizadoEnPorClave("DAILY_KM");

        jdbc.execute(sqlDeLaMigracion());

        // Lo editado queda exactamente como estaba, y su fecha de actualizacion no se mueve.
        assertThat(descripcionPorClave("RITUAL_MORNING")).isEqualTo(delDueno);
        assertThat(actualizadoEnPorClave("RITUAL_MORNING")).isEqualTo(antesRitual);
        assertThat(descripcionPorClave("DAILY_KM")).isEqualTo(kmDelDueno);
        assertThat(actualizadoEnPorClave("DAILY_KM")).isEqualTo(antesKm);
        // Lo que sigue siendo el original del catalogo si se actualiza.
        assertThat(descripcionPorClave("RITUAL_MIDDAY")).contains("escribiendo");
        assertThat(descripcionPorClave("RITUAL_NIGHT")).contains("escribiendo");
        assertThat(descripcionPorId(ULTIMA_COMIDA)).contains("opcional");
    }

    // issue #62
    @Test
    @DisplayName("V164 es idempotente: volver a correrla no cambia nada ni mueve la fecha de lo ya actualizado")
    void esIdempotente() throws Exception {
        volverALosTextosOriginales();
        jdbc.execute(sqlDeLaMigracion());
        String ritual = descripcionPorClave("RITUAL_MORNING");
        String km = descripcionPorClave("DAILY_KM");
        String comida = descripcionPorId(ULTIMA_COMIDA);
        Timestamp antes = actualizadoEnPorClave("RITUAL_MORNING");

        jdbc.execute(sqlDeLaMigracion());
        jdbc.execute(sqlDeLaMigracion());

        assertThat(descripcionPorClave("RITUAL_MORNING")).isEqualTo(ritual);
        assertThat(descripcionPorClave("DAILY_KM")).isEqualTo(km);
        assertThat(descripcionPorId(ULTIMA_COMIDA)).isEqualTo(comida);
        assertThat(actualizadoEnPorClave("RITUAL_MORNING")).isEqualTo(antes);
        assertThat(llaveDeLaTranscripcion()).isEqualTo("c");
    }

    // issue #62
    @Test
    @DisplayName("V164: la llave de la transcripcion queda en ON DELETE CASCADE (c) y conserva ON UPDATE CASCADE")
    void laTranscripcionSeBorraConSuLeccion() {
        assertThat(llaveDeLaTranscripcion()).as("confdeltype: a = no action, r = restrict, c = cascade, n = set null").isEqualTo("c");
        assertThat(jdbc.queryForObject("""
                SELECT confupdtype::text FROM pg_constraint
                 WHERE conname = 'base_conocimiento_leccion_id_fkey' AND conrelid = 'renaser.base_conocimiento'::regclass
                """, String.class)).isEqualTo("c");
    }

    private String llaveDeLaTranscripcion() {
        return jdbc.queryForObject("""
                SELECT confdeltype::text FROM pg_constraint
                 WHERE conname = 'base_conocimiento_leccion_id_fkey' AND conrelid = 'renaser.base_conocimiento'::regclass
                """, String.class);
    }
}
