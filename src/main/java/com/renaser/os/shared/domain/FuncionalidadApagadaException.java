package com.renaser.os.shared.domain;

/**
 * La funcionalidad está apagada para quien la pide (spec 002, FR-010; contrato §1 y §2). {@code GlobalExceptionHandler}
 * la traduce a <b>503</b> con {@code codigo: "FUNCIONALIDAD_APAGADA"}, la clave en {@code funcionalidad} y el aviso
 * editable como {@code message}: una app que no conoce los interruptores (APK 1.5.0) lo muestra como un error normal.
 *
 * <p>No es {@code final}: {@link FuncionalidadNoDisponibleException} (fase 2) la extiende para que el código que ya
 * corta un WebSocket o un stream con una funcionalidad apagada corte igual con una que a la persona todavía no le toca.
 */
public class FuncionalidadApagadaException extends RuntimeException {

    /** El aviso cuando Soporte TI no escribió uno (contrato §2). */
    public static final String AVISO_GENERICO = "Esta función está en mantenimiento. Vuelve a intentarlo en un rato.";

    private final String clave;

    public FuncionalidadApagadaException(String clave, String aviso) {
        super(aviso == null || aviso.isBlank() ? AVISO_GENERICO : aviso);
        this.clave = clave;
    }

    public String clave() {
        return clave;
    }
}
