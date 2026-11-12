package com.renaser.os.shared.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * Los grupos en que el filtro de límite por cuenta reparte los pedidos de {@code /api/v1} (spec 050, FR-013). Cada grupo tiene su
 * propio tope y su propio modo, y se cuenta por separado: publicar en el Muro no gasta lo que se puede leer.
 *
 * <p>La {@link #clave()} es el nombre que lleva en la configuración ({@code renaser.proteccion.limites.grupos.<clave>}), en la
 * clave de Redis del modo, en las métricas y en el script del dueño. No es el nombre del enum: tiene guiones.
 *
 * <p><b>Sin grupo {@code crm}.</b> La spec 050 lo traía para la cuenta de servicio del CRM, pero el dueño la descartó (D15): el
 * consumo del CRM cae en {@link #ADMIN}.
 */
public enum GrupoDeLimite {

    LECTURA("lectura"),
    ESCRITURA("escritura"),
    MURO("muro"),
    CHAT("chat"),
    SUBIDAS("subidas"),
    IA_FUERA_DE_SER("ia-fuera-de-ser"),
    SER("ser"),
    EXPORTACIONES("exportaciones"),
    ADMIN("admin"),
    WEBHOOK_WHATSAPP("webhook-whatsapp");

    private final String clave;

    GrupoDeLimite(String clave) {
        this.clave = clave;
    }

    public String clave() {
        return clave;
    }

    /** El grupo que se llama así en la configuración; vacío si no existe (un nombre mal escrito no es un grupo). */
    public static Optional<GrupoDeLimite> deClave(String clave) {
        return Arrays.stream(values()).filter(grupo -> grupo.clave.equals(clave)).findFirst();
    }
}
