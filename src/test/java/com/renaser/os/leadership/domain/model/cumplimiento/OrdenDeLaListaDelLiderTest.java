package com.renaser.os.leadership.domain.model.cumplimiento;

import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-007: de rojo a verde, «sin datos» donde lo deja el semáforo y «no se mide» al final; menor % primero. */
class OrdenDeLaListaDelLiderTest {

    private static PersonaDeLaLista persona(String id, String nombre, ColorDeLaFila color, String porcentaje) {
        return new PersonaDeLaLista(UUID.fromString("00000000-0000-0000-0000-0000000000" + id), nombre, null,
                UserRole.TRAINEE, color, porcentaje == null ? null : new BigDecimal(porcentaje), 0, List.of(), null);
    }

    private static List<String> ordenados(PersonaDeLaLista... personas) {
        List<PersonaDeLaLista> lista = new ArrayList<>(List.of(personas));
        Collections.shuffle(lista, new java.util.Random(7));
        lista.sort(OrdenDeLaListaDelLider.orden());
        return lista.stream().map(PersonaDeLaLista::nombre).toList();
    }

    @Test
    @DisplayName("rojo, amarillo, sin datos, verde y no se mide, en ese orden")
    void ordenDeLosColores() {
        assertThat(ordenados(
                persona("01", "Verde", ColorDeLaFila.VERDE, "90"),
                persona("02", "No se mide", ColorDeLaFila.NO_SE_MIDE, "10"),
                persona("03", "Sin datos", ColorDeLaFila.SIN_DATOS, null),
                persona("04", "Rojo", ColorDeLaFila.ROJO, "40"),
                persona("05", "Amarillo", ColorDeLaFila.AMARILLO, "70")))
                .containsExactly("Rojo", "Amarillo", "Sin datos", "Verde", "No se mide");
    }

    @Test
    @DisplayName("dentro de cada color, el menor porcentaje primero; sin porcentaje, al final de su color")
    void menorPorcentajePrimero() {
        assertThat(ordenados(
                persona("01", "Ana", ColorDeLaFila.ROJO, "55.5"),
                persona("02", "Beto", ColorDeLaFila.ROJO, "20"),
                persona("03", "Carla", ColorDeLaFila.ROJO, null),
                persona("04", "Dora", ColorDeLaFila.ROJO, "55.4"),
                persona("05", "Elsa", ColorDeLaFila.NO_SE_MIDE, "80"),
                persona("06", "Fabio", ColorDeLaFila.NO_SE_MIDE, "30")))
                .containsExactly("Beto", "Dora", "Ana", "Carla", "Fabio", "Elsa");
    }

    @Test
    @DisplayName("con el mismo color y porcentaje, por nombre en castellano (una Ángela antes que una Zoila) y por id")
    void empateSeResuelvePorNombreYPorId() {
        assertThat(ordenados(
                persona("01", "Zoila", ColorDeLaFila.AMARILLO, "65"),
                persona("02", "Ángela", ColorDeLaFila.AMARILLO, "65"),
                persona("04", "Marta", ColorDeLaFila.AMARILLO, "65"),
                persona("03", "Marta", ColorDeLaFila.AMARILLO, "65")))
                .containsExactly("Ángela", "Marta", "Marta", "Zoila");
        List<PersonaDeLaLista> homonimas = new ArrayList<>(List.of(
                persona("04", "Marta", ColorDeLaFila.AMARILLO, "65"),
                persona("03", "Marta", ColorDeLaFila.AMARILLO, "65")));
        homonimas.sort(OrdenDeLaListaDelLider.orden());
        assertThat(homonimas).extracting(p -> p.personaId().toString().substring(34)).containsExactly("03", "04");
    }

    @Test
    @DisplayName("quien no tiene nombre va al final de su grupo")
    void sinNombreAlFinal() {
        assertThat(ordenados(
                persona("01", null, ColorDeLaFila.ROJO, "40"),
                persona("02", "Ana", ColorDeLaFila.ROJO, "40"))).containsExactly("Ana", null);
    }

    @Test
    @DisplayName("la etiqueta de cada color es la palabra del semaforo, y la de «no se mide» es la de la spec")
    void etiquetas() {
        assertThat(ColorDeLaFila.ROJO.etiqueta()).isEqualTo("Con problemas");
        assertThat(ColorDeLaFila.AMARILLO.etiqueta()).isEqualTo("Requiere atención");
        assertThat(ColorDeLaFila.SIN_DATOS.etiqueta()).isEqualTo("Sin datos");
        assertThat(ColorDeLaFila.VERDE.etiqueta()).isEqualTo("Al día");
        assertThat(ColorDeLaFila.NO_SE_MIDE.etiqueta()).isEqualTo("No se mide en el semáforo");
    }
}
