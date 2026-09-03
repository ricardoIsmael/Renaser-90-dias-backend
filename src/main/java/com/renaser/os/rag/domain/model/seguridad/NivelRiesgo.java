package com.renaser.os.rag.domain.model.seguridad;

/**
 * Nivel de riesgo de una interaccion con un agente. Es uno de los dos ejes de la compuerta de
 * seguridad; el otro es {@link Severidad}.
 *
 * <p><b>Riesgo NO es lo mismo que severidad</b>, y separarlos es deliberado. Alguien puede estar
 * sufriendo mucho sin estar en peligro inmediato, y una senal breve pero explicita de peligro
 * tiene que disparar el modo crisis aunque el resto del mensaje suene tranquilo. Mezclar los dos
 * ejes produce los dos errores a la vez: convierte "sintomas intensos" en crisis y subestima una
 * senal puntual de peligro.
 *
 * <p>El orden de declaracion importa: {@link #compareTo} lo usa la regla de monotonia de
 * {@link EvaluacionRiesgo}. De menor a mayor.
 *
 * <p><b>Decision abierta (no inventar, CLAUDE.MD §0.6):</b> falta el estado "el clasificador no
 * pudo determinarlo". No se agrega todavia porque no esta decidido a que modo de respuesta
 * mapea, y elegirlo mal tiene costo en las dos direcciones: asumir que no hay riesgo esconde una
 * senal real, y asumir que lo hay hace que la app conteste con recursos de emergencia a alguien
 * que solo esta cansado. Se decide con quien firme los criterios de deteccion.
 */
public enum NivelRiesgo {

    /** Sin senales de peligro. La conversacion sigue su curso normal. */
    NINGUNO,

    /** Hay senales que merecen atencion, sin intencion declarada de dano. */
    ELEVADO,

    /**
     * Peligro inmediato. Manda el modo crisis siempre, sin importar la severidad, la preferencia
     * de la persona ni en que dia del programa este.
     */
    CRITICO
}
