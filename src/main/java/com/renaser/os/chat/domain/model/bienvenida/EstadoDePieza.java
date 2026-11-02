package com.renaser.os.chat.domain.model.bienvenida;

import com.renaser.os.shared.domain.Formacion;

import java.util.Objects;
import java.util.Optional;

/**
 * Una pieza de la bienvenida como está hoy en una formación (D-210, spec 035): su original, su último cambio y lo que sale.
 *
 * <p><b>La regla, en un solo lugar:</b> manda el último cambio de la bitácora; si no hubo ninguno, o el
 * último fue volver al original, sale el original. Lo usan los textos que se mandan, la portada que se
 * dibuja, la imagen que se sirve y la pantalla de Administración: si cada uno lo resolviera por su cuenta,
 * se desincronizarían.
 *
 * <p><b>Qué es «el original»</b> depende de la pieza (spec 035, FR-003 a FR-005):
 * <ul>
 *   <li>un <b>texto</b>: el de su formación en {@code bienvenida/mensajes.yaml}. No hereda lo que Administración editó en otra formación;</li>
 *   <li>la <b>portada</b> y la <b>imagen</b> de una formación que no es la Escuela: lo que la Escuela tiene vigente ({@link #deImagen}).</li>
 * </ul>
 *
 * @param original     el texto del recurso, o la versión de la imagen a la que se vuelve
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

    /**
     * La portada o la imagen de {@code formacion}: lo suyo si lo cambió; si no, lo que la Escuela tiene vigente (su último cambio o {@code sinCambios}).
     *
     * @param sinCambios       lo que sale en la Escuela cuando nadie la cambió ({@code "original"} en la portada)
     * @param ultimoDeLaEscuela el último cambio de la Escuela en esta pieza, o {@code null}
     * @param ultimoPropio     el último cambio de {@code formacion} en esta pieza, o {@code null}
     */
    public static EstadoDePieza deImagen(Formacion formacion, PiezaDeBienvenida pieza, String sinCambios,
                                         CambioDeBienvenida ultimoDeLaEscuela, CambioDeBienvenida ultimoPropio) {
        EstadoDePieza escuela = new EstadoDePieza(pieza, sinCambios, ultimoDeLaEscuela);
        return formacion == Formacion.POR_DEFECTO ? escuela : new EstadoDePieza(pieza, escuela.vigente(), ultimoPropio);
    }

    /** Si hoy sale algo que guardó Administración (y no el original). */
    public boolean cambiada() {
        return ultimoCambio != null && !ultimoCambio.esVueltaAlOriginal();
    }

    /** Lo que sale hoy: el texto (o la ruta de la imagen) guardado, o el original. */
    public String vigente() {
        return cambiada() ? ultimoCambio.valor() : original;
    }

    public Optional<CambioDeBienvenida> ultimo() {
        return Optional.ofNullable(ultimoCambio);
    }
}
