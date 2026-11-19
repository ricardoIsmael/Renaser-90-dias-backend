package com.renaser.os.leadership.domain.model.mentor;

import com.renaser.os.leadership.domain.model.mentor.PeorEvaluacion.MentorEvaluado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** El mentor con la peor evaluación del mes para «Por resolver» (spec 056, FR-002): la más baja, solo si está por debajo del 60 %. */
class PeorEvaluacionTest {

    private static MentorEvaluado mentor(String nombre, String porcentaje) {
        return new MentorEvaluado(UUID.randomUUID(), nombre, porcentaje == null ? null : new BigDecimal(porcentaje));
    }

    @Test
    @DisplayName("spec 056 FR-002: es el de porcentaje más bajo de los que están por debajo del 60 %")
    void elMasBajo() {
        MentorEvaluado luisa = mentor("Luisa", "45.5");

        assertThat(PeorEvaluacion.de(List.of(mentor("Raúl", "58.0"), luisa, mentor("Marta", "90.0")))).contains(luisa);
    }

    @Test
    @DisplayName("spec 056 FR-002: el 60 % justo no está por debajo; sin nadie por debajo no hay mentor que mostrar")
    void elUmbralEsEstricto() {
        assertThat(PeorEvaluacion.UMBRAL).isEqualByComparingTo("60");
        assertThat(PeorEvaluacion.de(List.of(mentor("Luisa", "60.0"), mentor("Raúl", "75.5")))).isEmpty();
        assertThat(PeorEvaluacion.de(List.of(mentor("Luisa", "59.9")))).isPresent();
    }

    @Test
    @DisplayName("spec 056 FR-002: una evaluación sin calcular (sin muestra o sin historial) no es una evaluación mala: no entra")
    void sinEvaluacionNoEntra() {
        assertThat(PeorEvaluacion.de(List.of(mentor("Luisa", null), mentor("Raúl", null)))).isEmpty();
        assertThat(PeorEvaluacion.de(List.of())).isEmpty();
        MentorEvaluado raul = mentor("Raúl", "30.0");
        assertThat(PeorEvaluacion.de(List.of(mentor("Luisa", null), raul))).contains(raul);
    }

    @Test
    @DisplayName("spec 056 FR-002: ante un empate gana el de nombre primero, y el resultado no depende del orden de la lista")
    void elEmpateEsEstable() {
        MentorEvaluado ana = mentor("Ana", "40.0");
        MentorEvaluado beto = mentor("beto", "40.0");

        assertThat(PeorEvaluacion.de(List.of(beto, ana))).contains(ana);
        assertThat(PeorEvaluacion.de(List.of(ana, beto))).contains(ana);
    }
}
