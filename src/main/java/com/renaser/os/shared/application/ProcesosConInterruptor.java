package com.renaser.os.shared.application;

import com.renaser.os.shared.domain.ProcesoPospuestoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cómo un proceso automático respeta el interruptor de su funcionalidad (spec 002, FR-009 y la columna «Proceso» del
 * catálogo), en una línea y con el mismo rastro en el log para todos:
 * <ul>
 *   <li><b>{@link #saltaEsteTurno}</b>, para las tareas programadas: si está apagada, no corre este turno. Una tarea
 *       que corre cada hora y se pone al día sola (casi todas: «derivar, no incrementar») con esto ya pospone; una que
 *       trabaja una ventana que pasa, salta.</li>
 *   <li><b>{@link #posponerSiEstaApagada}</b>, para los oyentes de eventos: lanza {@link ProcesoPospuestoException} y el
 *       outbox lo reentrega cada 5 minutos hasta que se prenda.</li>
 * </ul>
 * Mira el estado global ({@link Interruptores#prendida}): un proceso no tiene rol.
 */
public final class ProcesosConInterruptor {

    private static final Logger log = LoggerFactory.getLogger(ProcesosConInterruptor.class);

    private ProcesosConInterruptor() {
    }

    /** {@code true} si la funcionalidad está apagada y el proceso no tiene que correr ahora; lo anota en el log. */
    public static boolean saltaEsteTurno(Interruptores interruptores, String clave, String proceso) {
        if (interruptores.prendida(clave)) {
            return false;
        }
        log.info("[plataforma] {}: la funcionalidad {} está apagada; no corre este turno", proceso, clave);
        return true;
    }

    /** Lanza {@link ProcesoPospuestoException} si la funcionalidad está apagada. */
    public static void posponerSiEstaApagada(Interruptores interruptores, String clave, String proceso) {
        if (!interruptores.prendida(clave)) {
            log.info("[plataforma] {}: la funcionalidad {} está apagada; el outbox lo reintenta", proceso, clave);
            throw new ProcesoPospuestoException(clave, proceso);
        }
    }
}
