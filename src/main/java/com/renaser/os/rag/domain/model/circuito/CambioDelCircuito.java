package com.renaser.os.rag.domain.model.circuito;

import java.time.Instant;
import java.util.Objects;

/**
 * Un circuito que se abrió o se cerró, con la causa en palabras: lo que queda en la auditoría como {@code AUTOMATICO}
 * y lo que lee Soporte TI en el push (spec 002, FR-016). Tomar la prueba no es un cambio de estos: no se audita ni se
 * avisa.
 *
 * @param causa por ejemplo «5 fallas del proveedor en 2 minutos; se prueba de nuevo en 2 min»
 * @param en    el instante del cambio, del reloj del sistema ({@code Clock})
 */
public record CambioDelCircuito(CircuitoDeIa antes, CircuitoDeIa despues, String causa, Instant en) {

    public CambioDelCircuito {
        Objects.requireNonNull(antes, "antes es obligatorio");
        Objects.requireNonNull(despues, "despues es obligatorio");
        Objects.requireNonNull(causa, "causa es obligatoria");
        Objects.requireNonNull(en, "en es obligatorio");
    }

    public ParteDeLaIa parte() {
        return despues.parte();
    }

    public boolean cierra() {
        return despues.estado() == EstadoDelCircuito.CERRADO;
    }
}
