package com.renaser.os.leadership.domain.model.observacion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.regex.Matcher;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La línea con el enlace a la ficha que cierra el mensaje de «Avisar a su mentor» (spec 056, FR-019). La app la reconoce con {@link LineaDeLaFicha#PATRON} para
 * volverla tocable: si el formato cambia, la app deja de verla, y esta prueba es la que lo dice.
 */
class LineaDeLaFichaTest {

    private static final UUID GRUPO = UUID.fromString("00000000-0000-0000-0000-00000000f001");
    private static final UUID PERSONA = UUID.fromString("e2e00000-0000-4000-8000-000000000004");

    @Test
    @DisplayName("spec 056 FR-019: la línea es exactamente «Ficha de <nombre>: /mentor/groups/<grupo>/learners/<persona>»")
    void elFormatoExacto() {
        assertThat(LineaDeLaFicha.de("E2E Aprendiz", GRUPO, PERSONA))
                .isEqualTo("Ficha de E2E Aprendiz: /mentor/groups/00000000-0000-0000-0000-00000000f001/learners/e2e00000-0000-4000-8000-000000000004");
    }

    @Test
    @DisplayName("spec 056 FR-019: el patrón que usa la app reconoce la línea y devuelve el nombre, la ruta, el grupo y la persona")
    void elPatronLaReconoce() {
        Matcher reconocida = LineaDeLaFicha.PATRON.matcher(LineaDeLaFicha.de("Ana Pérez", GRUPO, PERSONA));

        assertThat(reconocida.matches()).isTrue();
        assertThat(reconocida.group(1)).isEqualTo("Ana Pérez");
        assertThat(reconocida.group(2)).isEqualTo("/mentor/groups/" + GRUPO + "/learners/" + PERSONA);
        assertThat(reconocida.group(3)).isEqualTo(GRUPO.toString());
        assertThat(reconocida.group(4)).isEqualTo(PERSONA.toString());
    }

    @Test
    @DisplayName("spec 056 FR-019: un nombre con saltos de línea no parte la línea en dos: se queda en una sola y el patrón la sigue reconociendo")
    void unNombreNoPartaLaLinea() {
        String linea = LineaDeLaFicha.de("Ana\nPérez\r\n  Ruiz ", GRUPO, PERSONA);

        assertThat(linea).doesNotContain("\n").doesNotContain("\r").startsWith("Ficha de Ana Pérez Ruiz: /mentor/groups/");
        assertThat(LineaDeLaFicha.PATRON.matcher(linea).matches()).isTrue();
    }

    @Test
    @DisplayName("spec 056 FR-019: sin nombre la línea dice «la persona» y sigue cumpliendo el patrón, en vez de salir «Ficha de null»")
    void sinNombre() {
        assertThat(LineaDeLaFicha.de(null, GRUPO, PERSONA)).startsWith("Ficha de la persona: /mentor/groups/").doesNotContain("null");
        assertThat(LineaDeLaFicha.PATRON.matcher(LineaDeLaFicha.de("  ", GRUPO, PERSONA)).matches()).isTrue();
    }

    @Test
    @DisplayName("spec 056 FR-019: el mensaje va en dos partes: el texto del líder y, separada por una línea en blanco, la ficha como última línea")
    void elMensajeTerminaConLaFicha() {
        String mensaje = LineaDeLaFicha.alFinalDe("Ana lleva tres días en rojo.\n¿Puedes llamarla?", "Ana Pérez", GRUPO, PERSONA);

        String[] lineas = mensaje.split("\n", -1);
        assertThat(lineas).hasSize(4);
        assertThat(lineas[0]).isEqualTo("Ana lleva tres días en rojo.");
        assertThat(lineas[1]).isEqualTo("¿Puedes llamarla?");
        assertThat(lineas[2]).as("una línea en blanco separa el texto de la ficha").isEmpty();
        assertThat(lineas[3]).isEqualTo(LineaDeLaFicha.de("Ana Pérez", GRUPO, PERSONA));
        assertThat(LineaDeLaFicha.PATRON.matcher(lineas[3]).matches()).isTrue();
    }
}
