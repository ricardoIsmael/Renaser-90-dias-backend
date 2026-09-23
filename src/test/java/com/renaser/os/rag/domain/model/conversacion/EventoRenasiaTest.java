package com.renaser.os.rag.domain.model.conversacion;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventoRenasiaTest {

    @Test
    void textoRechazaFragmentoNulo() {
        assertThatThrownBy(() -> new EventoRenasia.Texto(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void fuentesRechazaListaNula() {
        assertThatThrownBy(() -> new EventoRenasia.Fuentes(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void fuentesRechazaListaVacia() {
        assertThatThrownBy(() -> new EventoRenasia.Fuentes(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fuentesEsInmutableAunquePasenUnaListaMutable() {
        var mutable = new java.util.ArrayList<String>();
        mutable.add("leccion-1");

        var fuentes = new EventoRenasia.Fuentes(mutable);
        mutable.add("leccion-2");

        assertThat(fuentes.leccionIds()).containsExactly("leccion-1");
    }

    // --- Propuesta (fase 2, D-153) ---------------------------------------------------------

    private static final UUID ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant VENCE = Instant.parse("2026-09-23T15:10:00Z");

    @Test
    void propuestaConservaSusDatos() {
        var propuesta = new EventoRenasia.Propuesta(ID, "Meditar: de 06:00 a 07:00", VENCE);

        assertThat(propuesta.id()).isEqualTo(ID);
        assertThat(propuesta.resumen()).isEqualTo("Meditar: de 06:00 a 07:00");
        assertThat(propuesta.venceEn()).isEqualTo(VENCE);
    }

    @Test
    void propuestaRechazaIdNulo() {
        assertThatThrownBy(() -> new EventoRenasia.Propuesta(null, "resumen", VENCE))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void propuestaRechazaResumenNulo() {
        assertThatThrownBy(() -> new EventoRenasia.Propuesta(ID, null, VENCE))
                .isInstanceOf(NullPointerException.class);
    }

    /** Un boton de "Confirmar" sin decir que se confirma es peor que no ofrecer nada. */
    @Test
    void propuestaRechazaResumenEnBlanco() {
        assertThatThrownBy(() -> new EventoRenasia.Propuesta(ID, "  ", VENCE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void propuestaRechazaVencimientoNulo() {
        assertThatThrownBy(() -> new EventoRenasia.Propuesta(ID, "resumen", null))
                .isInstanceOf(NullPointerException.class);
    }
}
