package com.renaser.os.chat.domain.model.bienvenida;

import java.util.Objects;
import java.util.Optional;

/**
 * Una pieza de la bienvenida como está hoy (D-210): su original, su último cambio y lo que sale.
 *
 * <p><b>La regla, en un solo lugar:</b> manda el último cambio de la bitácora; si no hubo ninguno, o el
 * último fue volver al original, sale el original. Lo usan los textos que se mandan, la portada que se
 * dibuja y la pantalla de Administración: si cada uno lo resolviera por su cuenta, se desincronizarían.
 *
 * @param original     el texto del recurso, o {@code "original"} para la portada
 * @param ultimoCambio {@code null} si nadie la cambió nunca
 */
public record EstadoDePieza(PiezaDeBienvenida pieza, String original, CambioDeBienvenida ultimoCambio) {

    public EstadoDePieza {
        Objects.requireNonNull(pieza, "pieza");
        Objects.requireNonNull(original, "original");
        if (ultimoCambio != null && ultimoCambio.pieza() != pieza) {
            throw new IllegalArgumentException("El cambio es de " + ultimoCambio.pieza() + ", no de " + pieza);
        }
    }

    /** Si hoy sale algo que guardó Administración (y no el original). */
    public boolean cambiada() {
        return ultimoCambio != null && !ultimoCambio.esVueltaAlOriginal();
    }

    /** Lo que sale hoy: el texto (o la ruta de la portada) guardado, o el original. */
    public String vigente() {
        return cambiada() ? ultimoCambio.valor() : original;
    }

    public Optional<CambioDeBienvenida> ultimo() {
        return Optional.ofNullable(ultimoCambio);
    }
}
