package com.renaser.os.rag.domain.model.propuesta;

/**
 * Estados GUARDADOS de una propuesta del acompanante (espejo del CHECK
 * {@code propuesta_estado_valido} de V63).
 *
 * <p>No hay {@code VENCIDA} a proposito: vencer es funcion del reloj, asi que se deriva con
 * {@link PropuestaAccion#estaVencidaEn} y no se guarda (regla 02, "derivar, no incrementar").
 * Una {@code PENDIENTE} cuyo {@code venceEn} ya paso es una propuesta vencida.
 */
public enum EstadoPropuesta {

    /** Guardada, esperando el boton de la persona. */
    PENDIENTE,

    /**
     * La persona confirmo. Se guarda ANTES de ejecutar la accion, para que un doble toque ejecute
     * una sola vez; el resultado se agrega despues. Una CONFIRMADA sin resultado es una ejecucion
     * en curso (o una que se corto a la mitad).
     */
    CONFIRMADA,

    /** La persona toco "Cancelar". */
    CANCELADA,

    /** Se intento ejecutar y el negocio la rechazo; {@code resultado} dice por que. */
    FALLIDA
}
