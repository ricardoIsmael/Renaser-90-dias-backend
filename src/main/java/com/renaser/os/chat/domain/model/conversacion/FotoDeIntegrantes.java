package com.renaser.os.chat.domain.model.conversacion;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Qué foto representa a cada integrante de un grupo o de un soporte en el chat (D-206, 2026-09-27).
 *
 * <p>Decisión del dueño: por defecto, <b>siempre su tarjeta con nombre</b>, aunque haya subido una foto
 * en «Yo». Pidió dejar listo también el otro modo («hazlo los 2 por si acaso»): su foto si la subió; si
 * no, la tarjeta. Se elige en el servidor ({@code CHAT_FOTO_DE_INTEGRANTES}), sin APK nuevo: el modo
 * decide a quién se le manda la ruta de la tarjeta, y la app muestra la tarjeta cuando la recibe.
 */
public enum FotoDeIntegrantes {

    /** Siempre la tarjeta con su primer nombre. El default. */
    TARJETA,

    /** Su foto si la subió; si no, la tarjeta. */
    FOTO_SUBIDA;

    /** Si a esta persona se la muestra con su tarjeta. */
    public boolean llevaTarjeta(boolean subioFoto) {
        return this == TARJETA || !subioFoto;
    }

    /**
     * El modo escrito en la configuración, sin importar mayúsculas ni espacios. Cualquier otro valor
     * (vacío, con un error de tipeo) no es un modo: quien lee decide qué hacer.
     */
    public static Optional<FotoDeIntegrantes> de(String valor) {
        if (valor == null) {
            return Optional.empty();
        }
        String limpio = valor.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(modo -> modo.name().equals(limpio)).findFirst();
    }
}
