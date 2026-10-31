package com.renaser.os.leadership.domain.model.cumplimiento;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Cómo busca y ordena por nombre la lista de aprendices del líder (spec 021). */
class BusquedaPorNombreTest {

    @Test
    @DisplayName("normalizar: minúsculas, sin tildes ni espacios sobrantes; sin texto es vacío")
    void normalizar() {
        assertThat(BusquedaPorNombre.normalizar("  JOSÉ   Ñandú ")).isEqualTo("jose nandu");
        assertThat(BusquedaPorNombre.normalizar(null)).isEmpty();
        assertThat(BusquedaPorNombre.normalizar("   ")).isEmpty();
    }

    @Test
    @DisplayName("sin tildes ni mayúsculas: «jose» encuentra a «José», y «PEREZ» a «Pérez»")
    void sinTildesNiMayusculas() {
        assertThat(BusquedaPorNombre.coincide("José Pérez", "jose")).isTrue();
        assertThat(BusquedaPorNombre.coincide("José Pérez", "PEREZ")).isTrue();
        assertThat(BusquedaPorNombre.coincide("Jose Perez", "josé pérez")).isTrue();
    }

    @Test
    @DisplayName("cada palabra buscada tiene que estar en el nombre, en cualquier orden")
    void cadaPalabra() {
        assertThat(BusquedaPorNombre.coincide("Ana María Pérez", "perez ana")).isTrue();
        assertThat(BusquedaPorNombre.coincide("Ana María Pérez", "ana luis")).isFalse();
    }

    @Test
    @DisplayName("un texto vacío o ausente no filtra a nadie")
    void vacioNoFiltra() {
        assertThat(BusquedaPorNombre.coincide("Ana", null)).isTrue();
        assertThat(BusquedaPorNombre.coincide("Ana", "  ")).isTrue();
    }

    @Test
    @DisplayName("ordena por nombre sin tildes ni mayúsculas: «Álvaro» va antes que «ana» y «bea» antes que «Beto»")
    void orden() {
        assertThat(List.of("Beto", "Álvaro", "bea", "ana").stream().sorted(BusquedaPorNombre.porNombre()).toList())
                .containsExactly("Álvaro", "ana", "bea", "Beto");
    }
}
