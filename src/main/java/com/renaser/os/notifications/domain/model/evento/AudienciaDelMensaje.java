package com.renaser.os.notifications.domain.model.evento;

import java.util.Locale;

/**
 * A quien le llega el mensaje de un evento (spec 041, FR-011): solo a quienes dijeron «Voy» a ESA ocurrencia, o a toda la audiencia del evento.
 * Es un valor de configuracion ({@code renaser.eventos.mensajes.audiencia}), no una columna del evento ni una eleccion en el formulario:
 * pasar de uno a otro es cambiar un valor, sin codigo ni migracion. Se lee en cada despacho, con {@link #VOY} si falta o no se entiende.
 */
public enum AudienciaDelMensaje {

    /** Quienes dijeron «Voy» a esta ocurrencia (se lee al despachar: quien lo dice despues recibe los siguientes; quien pasa a «No voy», deja de recibirlos). */
    VOY,
    /** Toda la audiencia del evento. */
    TODOS;

    public static final AudienciaDelMensaje POR_DEFECTO = VOY;

    public boolean admite(boolean dijoVoy) {
        return this == TODOS || dijoVoy;
    }

    /** Lee el valor de la configuracion; lo que falte o no se entienda es el valor por defecto (nunca abre de mas el envio). */
    public static AudienciaDelMensaje desde(String valor) {
        if (valor == null || valor.isBlank()) {
            return POR_DEFECTO;
        }
        try {
            return valueOf(valor.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException noEntendido) {
            return POR_DEFECTO;
        }
    }
}
