package com.renaser.os.rag.domain.model.espejosombra;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InformeEspejoSombraTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    private static UserId participante() {
        return UserId.of(UUID.randomUUID());
    }

    private static DistribucionTemporal distribucion() {
        return new DistribucionTemporal(30, 50, 20);
    }

    private static List<PreguntaConfrontacion> tresPreguntas() {
        return List.of(new PreguntaConfrontacion(1, "uno"), new PreguntaConfrontacion(2, "dos"),
                new PreguntaConfrontacion(3, "tres"));
    }

    @Test
    void generaUnInformeValido() {
        InformeEspejoSombra informe = InformeEspejoSombra.generar(participante(), LocalDate.of(2026, 8, 17), 4,
                "Evitacion", distribucion(), "Insight de la semana", tresPreguntas(), CLOCK);

        assertThat(informe.id()).isNotNull();
        assertThat(informe.cantidadEntradas()).isEqualTo(4);
        assertThat(informe.patronDominante()).isEqualTo("Evitacion");
        assertThat(informe.preguntas()).hasSize(3);
        assertThat(informe.creadoEn()).isEqualTo(CLOCK.now());
    }

    @Test
    void rechazaMasDeDiezPreguntas() {
        // cada PreguntaConfrontacion individual tiene orden valido (1..10); la lista
        // llega a 11 elementos repitiendo el orden 1 — ejercita el limite de CANTIDAD
        // del agregado (MAX_PREGUNTAS), que se chequea antes que la unicidad de orden.
        List<PreguntaConfrontacion> once = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            once.add(new PreguntaConfrontacion(i, "pregunta " + i));
        }
        once.add(new PreguntaConfrontacion(1, "pregunta once, orden repetido"));

        assertThatThrownBy(() -> InformeEspejoSombra.generar(participante(), LocalDate.of(2026, 8, 17), 4,
                "patron", distribucion(), "insight", once, CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Maximo");

        // diez es el maximo permitido, no debe fallar
        List<PreguntaConfrontacion> diezValidas = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            diezValidas.add(new PreguntaConfrontacion(i, "pregunta " + i));
        }
        InformeEspejoSombra informe = InformeEspejoSombra.generar(participante(), LocalDate.of(2026, 8, 17), 4,
                "patron", distribucion(), "insight", diezValidas, CLOCK);
        assertThat(informe.preguntas()).hasSize(10);
    }

    @Test
    void rechazaOrdenDuplicadoEntrePreguntas() {
        List<PreguntaConfrontacion> conDuplicado = List.of(new PreguntaConfrontacion(1, "uno"),
                new PreguntaConfrontacion(1, "otra con el mismo orden"));

        assertThatThrownBy(() -> InformeEspejoSombra.generar(participante(), LocalDate.of(2026, 8, 17), 4,
                "patron", distribucion(), "insight", conDuplicado, CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicado");
    }

    @Test
    void aceptaListaDePreguntasVacia() {
        InformeEspejoSombra informe = InformeEspejoSombra.generar(participante(), LocalDate.of(2026, 8, 17), 0,
                "patron", distribucion(), "insight", List.of(), CLOCK);

        assertThat(informe.preguntas()).isEmpty();
    }

    @Test
    void rechazaCantidadEntradasNegativa() {
        assertThatThrownBy(() -> InformeEspejoSombra.generar(participante(), LocalDate.of(2026, 8, 17), -1,
                "patron", distribucion(), "insight", tresPreguntas(), CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaPatronDominanteVacio() {
        assertThatThrownBy(() -> InformeEspejoSombra.generar(participante(), LocalDate.of(2026, 8, 17), 4,
                " ", distribucion(), "insight", tresPreguntas(), CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaInsightVacio() {
        assertThatThrownBy(() -> InformeEspejoSombra.generar(participante(), LocalDate.of(2026, 8, 17), 4,
                "patron", distribucion(), "  ", tresPreguntas(), CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rehydrateNoRevalidaPeroPreservaLosDatos() {
        UserId participanteId = participante();
        InformeEspejoSombra informe = InformeEspejoSombra.rehydrate(InformeEspejoSombraId.newId(), participanteId,
                LocalDate.of(2026, 8, 10), 5, "patron", distribucion(), "insight", tresPreguntas(),
                Instant.parse("2026-08-11T00:00:00Z"));

        assertThat(informe.participanteId()).isEqualTo(participanteId);
        assertThat(informe.preguntas()).hasSize(3);
    }
}
