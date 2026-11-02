package com.renaser.os.chat.domain.model.bienvenida;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Una fila de la bitácora de la bienvenida (D-210, tabla {@code cambios_bienvenida}, V73): qué pieza
 * cambió, en qué formación, a qué, quién y cuándo. La bitácora solo crece: volver al original es OTRO
 * cambio, y los dos quedan a la vista (el mismo criterio que {@code ajustes_dia_programa}).
 *
 * <p><b>La formación</b> (spec 035, V159): la bienvenida y la imagen son de cada formación. Lo que existía antes es de la
 * Escuela ({@link Formacion#POR_DEFECTO}), y la carta de la caja es siempre de la Escuela.
 *
 * @param valor       el texto nuevo, o la ruta de la imagen nueva; {@code null} = volvió al original
 *                    (el texto de {@code bienvenida/mensajes.yaml} o la imagen que corresponde sin cambios)
 * @param cambiadoPor {@code null} solo si la cuenta de quien lo cambió ya no existe (la base lo deja en
 *                    {@code NULL} al borrarla)
 */
public record CambioDeBienvenida(Formacion formacion, PiezaDeBienvenida pieza, String valor, UserId cambiadoPor,
                                 Instant cambiadoEn) {

    public CambioDeBienvenida {
        Objects.requireNonNull(formacion, "formacion");
        Objects.requireNonNull(pieza, "pieza");
        Objects.requireNonNull(cambiadoEn, "cambiadoEn");
        if (pieza == PiezaDeBienvenida.CARTA_CAJA && formacion != Formacion.POR_DEFECTO) {
            throw new IllegalArgumentException("La carta de la caja es única: es de la Escuela.");
        }
    }

    /** Un texto nuevo, ya validado ({@link TextoDeBienvenida#validar}). */
    public static CambioDeBienvenida texto(Formacion formacion, PiezaDeBienvenida pieza, String texto, UserId actor, Instant ahora) {
        return new CambioDeBienvenida(formacion, pieza, TextoDeBienvenida.validar(pieza, texto), Objects.requireNonNull(actor),
                ahora);
    }

    /** Una portada nueva, en una ruta emitida para portadas ({@link PortadaDeBienvenida#exigirRutaPropia}). */
    public static CambioDeBienvenida portada(Formacion formacion, String ruta, UserId actor, Instant ahora) {
        return new CambioDeBienvenida(formacion, PiezaDeBienvenida.PORTADA, PortadaDeBienvenida.exigirRutaPropia(ruta),
                Objects.requireNonNull(actor), ahora);
    }

    /** Una imagen nueva para la formación, en una ruta emitida para eso ({@link ImagenDeLaFormacion#exigirRutaPropia}). */
    public static CambioDeBienvenida imagenDeLaFormacion(Formacion formacion, String ruta, UserId actor, Instant ahora) {
        return new CambioDeBienvenida(formacion, PiezaDeBienvenida.IMAGEN_DE_LA_FORMACION,
                ImagenDeLaFormacion.exigirRutaPropia(ruta), Objects.requireNonNull(actor), ahora);
    }

    /** Un fondo nuevo para la carta de la caja (siempre de la Escuela), en una ruta emitida para cartas ({@link FondoDeCarta}). */
    public static CambioDeBienvenida fondoDeCarta(String ruta, UserId actor, Instant ahora) {
        return new CambioDeBienvenida(Formacion.POR_DEFECTO, PiezaDeBienvenida.CARTA_CAJA, FondoDeCarta.exigirRutaPropia(ruta),
                Objects.requireNonNull(actor), ahora);
    }

    public static CambioDeBienvenida volverAlOriginal(Formacion formacion, PiezaDeBienvenida pieza, UserId actor, Instant ahora) {
        return new CambioDeBienvenida(formacion, pieza, null, Objects.requireNonNull(actor), ahora);
    }

    public boolean esVueltaAlOriginal() {
        return valor == null;
    }

    public Optional<UserId> quien() {
        return Optional.ofNullable(cambiadoPor);
    }
}
