package com.renaser.os.chat.domain.model.conversacion;

/**
 * Espejo del tipo Postgres `tipo_conversacion` (V1__baseline_renaser.sql:82). En espanol
 * por vivir asi en la base y en el dominio (D-36); la traduccion a ingles para la app
 * publicada vive solo en `infrastructure/adapter/in/rest`.
 */
public enum TipoConversacion {
    CELULA,
    DIRECTA,
    GLOBAL,

    /**
     * El chat de soporte de UN aprendiz: el aprendiz mas todo el staff administrativo
     * (ADMIN/ALCHEMIST activos), creado solo cuando la persona entra al programa
     * (V53/V54, D-136). Viaja por HTTP como {@code SUPPORT}.
     *
     * <p>No es una DIRECTA con varios: una DIRECTA es de dos y la abre cualquiera de los dos.
     * Esta nace sola, tiene a la casa entera adentro y el aprendiz no se puede ir. Tampoco es
     * una CELULA: no cuelga de ningun grupo de `community` ni rota con el.
     */
    SOPORTE;

    /**
     * Si en este chat los mensajes propios pasan de ✓ a ✓✓ cuando los leyeron (D-208, decisión del
     * dueño del 2026-09-27). En un 1 a 1 la doble marca dice que el otro leyó; en un grupo y en un
     * soporte, que leyeron <b>todos</b> los demás, como en WhatsApp.
     *
     * <p>La comunidad (GLOBAL) no: es el chat de toda la generación, con cientos de personas que
     * entran cuando quieren, y «lo leyeron todos» no llegaría nunca o no diría nada. Ahí queda un
     * solo ✓ (el servidor lo guardó), y el servidor ni calcula ni avisa lecturas: cada lectura de
     * la comunidad les llegaría en vivo a todos los conectados.
     */
    public boolean confirmaLectura() {
        return this != GLOBAL;
    }
}
