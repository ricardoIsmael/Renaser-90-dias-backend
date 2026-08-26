package com.renaser.os.habits.domain.model.politica;

import com.renaser.os.habits.domain.model.habito.TipoHabito;

import java.util.Objects;

/**
 * Como una politica declara a que habitos atiende. Hay exactamente dos formas, y por eso
 * va SELLADA: el compilador obliga a cubrir ambas al indexar.
 *
 * <ul>
 *   <li>{@link PorClaveSistema} — un habito puntual del catalogo
 *       ({@code PASTILLA_RENACER}, {@code DAILY_CLASS}...). Es lo mas especifico.</li>
 *   <li>{@link PorTipo} — todos los habitos de una forma estructural
 *       ({@code BLOQUEO} = Santuario: cualquier habito de bloqueo necesita una sesion,
 *       tenga la clave que tenga). Es lo mas general.</li>
 * </ul>
 *
 * <p>La distincion no es cosmetica: el tipo describe COMO funciona el habito (dato del
 * modelo), la clave describe CUAL habito es (dato del catalogo). Una regla que el cliente
 * pide para "el jugo verde" es por clave; una que aplica a "todo habito de bloqueo" es
 * por tipo.
 */
public sealed interface SelectorHabito permits SelectorHabito.PorClaveSistema, SelectorHabito.PorTipo {

    record PorClaveSistema(String clave) implements SelectorHabito {
        public PorClaveSistema {
            if (clave == null || clave.isBlank()) {
                throw new IllegalArgumentException("claveSistema es obligatoria en este selector");
            }
        }
    }

    record PorTipo(TipoHabito tipo) implements SelectorHabito {
        public PorTipo {
            Objects.requireNonNull(tipo, "tipo es obligatorio en este selector");
        }
    }

    static SelectorHabito porClave(String clave) {
        return new PorClaveSistema(clave);
    }

    static SelectorHabito porTipo(TipoHabito tipo) {
        return new PorTipo(tipo);
    }
}
