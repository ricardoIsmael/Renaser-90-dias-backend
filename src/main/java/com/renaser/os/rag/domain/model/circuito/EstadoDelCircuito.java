package com.renaser.os.rag.domain.model.circuito;

/** Los estados del contrato §6 ({@code GET /soporte-ti/ia/circuito}). */
public enum EstadoDelCircuito {

    /** El proveedor responde: todo pasa. */
    CERRADO,
    /** Demasiadas fallas seguidas: nada pasa hasta que vence la espera. */
    ABIERTO,
    /** Medio abierto: venció la espera y pasa un solo pedido de prueba. */
    PROBANDO
}
