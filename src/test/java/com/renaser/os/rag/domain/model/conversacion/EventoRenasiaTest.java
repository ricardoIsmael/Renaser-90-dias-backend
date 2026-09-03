package com.renaser.os.rag.domain.model.conversacion;

import org.junit.jupiter.api.Test;

import java.util.List;

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
}
