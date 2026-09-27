package com.renaser.os.chat.domain.model.bienvenida;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Una fila de la bitácora de la bienvenida (D-210, tabla {@code cambios_bienvenida}, V73): qué pieza
 * cambió, a qué, quién y cuándo. La bitácora solo crece: volver al original es OTRO cambio, y los dos
 * quedan a la vista (el mismo criterio que {@code ajustes_dia_programa}).
 *
 * @param valor       el texto nuevo, o la ruta de la portada nueva; {@code null} = volvió al original
 *                    (el texto de {@code bienvenida/mensajes.yaml} o el fondo de Canva)
 * @param cambiadoPor {@code null} solo si la cuenta de quien lo cambió ya no existe (la base lo deja en
 *                    {@code NULL} al borrarla)
 */
public record CambioDeBienvenida(PiezaDeBienvenida pieza, String valor, UserId cambiadoPor, Instant cambiadoEn) {

    public CambioDeBienvenida {
        Objects.requireNonNull(pieza, "pieza");
        Objects.requireNonNull(cambiadoEn, "cambiadoEn");
    }

    /** Un texto nuevo, ya validado ({@link TextoDeBienvenida#validar}). */
    public static CambioDeBienvenida texto(PiezaDeBienvenida pieza, String texto, UserId actor, Instant ahora) {
        return new CambioDeBienvenida(pieza, TextoDeBienvenida.validar(pieza, texto), Objects.requireNonNull(actor),
                ahora);
    }

    /** Una portada nueva, en una ruta emitida para portadas ({@link PortadaDeBienvenida#exigirRutaPropia}). */
    public static CambioDeBienvenida portada(String ruta, UserId actor, Instant ahora) {
        return new CambioDeBienvenida(PiezaDeBienvenida.PORTADA, PortadaDeBienvenida.exigirRutaPropia(ruta),
                Objects.requireNonNull(actor), ahora);
    }

    public static CambioDeBienvenida volverAlOriginal(PiezaDeBienvenida pieza, UserId actor, Instant ahora) {
        return new CambioDeBienvenida(pieza, null, Objects.requireNonNull(actor), ahora);
    }

    public boolean esVueltaAlOriginal() {
        return valor == null;
    }

    public Optional<UserId> quien() {
        return Optional.ofNullable(cambiadoPor);
    }
}
