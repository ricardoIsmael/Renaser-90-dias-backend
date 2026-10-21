package com.renaser.os.points.domain.model.semaforo.mensaje;

import java.util.EnumSet;
import java.util.Set;

/**
 * Por dónde le llega el mensaje del semáforo a la persona (D-281). Cada canal guarda SU elección en
 * {@code mensajes_semaforo_elegidos}, pero los dos comparten el mismo catálogo y la misma regla de no repetir: lo que
 * se vio por un canal cuenta como visto por el otro.
 *
 * <p>El canal decide además qué datos puede nombrar su mensaje: {@code {faltan}} («te faltan 2 para subir de color») solo
 * tiene sentido mientras el día está abierto.
 */
public enum CanalDelMensaje {

    /** La tarjeta del semáforo de Hoy (D-277): puede decir cuánto falta para el color siguiente. */
    HOY(EnumSet.allOf(Variable.class)),
    /**
     * La tarjeta diaria del chat de soporte (D-223, D-281), que sale a las 23:50 del aprendiz: el día ya casi cerró, así
     * que no existe «cuánto falta». Un mensaje con {@code {faltan}} no es candidato aunque el catálogo lo tenga.
     */
    CHAT(EnumSet.complementOf(EnumSet.of(Variable.FALTAN)));

    private final Set<Variable> variablesPermitidas;

    CanalDelMensaje(Set<Variable> variablesPermitidas) {
        this.variablesPermitidas = Set.copyOf(variablesPermitidas);
    }

    /** Los datos que un mensaje de este canal puede pedir entre llaves. */
    public Set<Variable> variablesPermitidas() {
        return variablesPermitidas;
    }

    /** Si el mensaje puede salir por este canal: todo lo que pide está permitido. */
    public boolean admite(MensajeDelSemaforo mensaje) {
        return variablesPermitidas.containsAll(mensaje.variables());
    }
}
