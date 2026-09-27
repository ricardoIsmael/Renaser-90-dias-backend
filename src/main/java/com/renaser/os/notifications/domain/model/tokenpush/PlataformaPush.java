package com.renaser.os.notifications.domain.model.tokenpush;

/** Espejo 1:1 del tipo Postgres {@code plataforma_push} (V1__baseline_renaser.sql:85). */
public enum PlataformaPush {
    IOS,
    ANDROID,
    WEB;

    /**
     * La app del telefono programa una alarma local cuando la persona responde "Voy" a un evento;
     * el navegador no tiene nada equivalente (D-189).
     */
    public boolean programaAlarmaLocal() {
        return this == IOS || this == ANDROID;
    }
}
