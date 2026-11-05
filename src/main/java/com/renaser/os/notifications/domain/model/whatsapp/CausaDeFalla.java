package com.renaser.os.notifications.domain.model.whatsapp;

/**
 * Por que fallo un envio, en la medida en que cambia lo que se hace (spec 041, FR-024). Las tres primeras son las que le tocan a
 * Administracion arreglar —la clave, la plantilla o el saldo de YCloud— y por eso reciben un aviso; el resto solo queda anotado.
 */
public enum CausaDeFalla {
    NINGUNA("", false),
    /** El proveedor no acepto la clave (401 o 403). */
    CLAVE("YCloud no aceptó la clave de la API.", true),
    /** La plantilla no existe, no esta aprobada o no coincide con los huecos. */
    PLANTILLA("La plantilla de WhatsApp no está aprobada o no existe.", true),
    /** No hay saldo en la billetera del proveedor. */
    SALDO("La billetera de YCloud se quedó sin saldo.", true),
    /** Se llego al tope mensual de mensajes que se puso en la configuracion (opcional y apagado de fabrica). */
    TOPE("Se llegó al tope mensual de WhatsApp de eventos.", true),
    /** El proveedor pidio ir mas despacio (429). No se reintenta: el WhatsApp es un canal extra. */
    LIMITE("YCloud pidió ir más despacio.", false),
    OTRA("", false);

    private final String aviso;
    private final boolean leTocaAAdministracion;

    CausaDeFalla(String aviso, boolean leTocaAAdministracion) {
        this.aviso = aviso;
        this.leTocaAAdministracion = leTocaAAdministracion;
    }

    public boolean leTocaAAdministracion() {
        return leTocaAAdministracion;
    }

    /** Lo que se le dice a Administracion; vacio si la causa no la avisa. */
    public String aviso() {
        return aviso;
    }

    /**
     * Que causa tiene una falla segun lo que contesto el proveedor (estado HTTP y texto del error), para saber si le toca a Administracion. Es de mejor esfuerzo:
     * el texto exacto de cada error de YCloud no esta documentado en esta spec, asi que se reconocen las palabras claras y todo lo demas es {@link #OTRA}.
     *
     * @param http  el estado HTTP, o {@code null} si el aviso vino por el webhook (que no lo trae)
     * @param texto el mensaje o codigo de error del proveedor, o {@code null}
     */
    public static CausaDeFalla segun(Integer http, String texto) {
        if (http != null && (http == 401 || http == 403)) {
            return CLAVE;
        }
        if (http != null && http == 429) {
            return LIMITE;
        }
        String t = texto == null ? "" : texto.toLowerCase(java.util.Locale.ROOT);
        if (t.contains("template") || t.contains("plantilla")) {
            return PLANTILLA;
        }
        if (t.contains("balance") || t.contains("insufficient") || t.contains("saldo") || t.contains("credit")) {
            return SALDO;
        }
        return OTRA;
    }
}
