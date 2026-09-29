package com.renaser.os.chat.application.ports.in.conversacion;

import com.renaser.os.shared.domain.UserId;

/**
 * Que todo aprendiz activo tenga los chats que la regla le da, sin importar por qué camino entró
 * (D-224): su soporte con la casa (D-136) y su chat de dos con quien lo acompaña hoy (D-173).
 *
 * <p><b>Por qué hace falta.</b> Los dos chats nacen de eventos —la aprobación de la cuenta y el cambio
 * de composición de un grupo—, así que quien ya estaba antes de que existieran, quien entró por otro
 * camino, o el grupo cuyo período empieza sin que nadie lo toque, quedaban sin ellos para siempre. El
 * dueño lo reportó el 29-09: «los anteriores no tienen sus grupos que se generan automáticamente».
 *
 * <p><b>Derivar, no acumular</b> (regla 02 §2): el estado deseado sale del padrón y de las asignaciones
 * vigentes, y esto solo crea lo que falta. Correrlo dos veces crea cero la segunda; correrlo tarde se
 * pone al día solo. <b>No manda mensajes</b>: ni bienvenida de soporte ni de grupo.
 */
public interface CompletarChatsDeAprendicesUseCase {

    /** El barrido de todo el padrón y de todos los grupos operativos. */
    ResultadoCompletar completarTodos();

    /** Lo mismo para UNA persona (al reactivarse su cuenta): su soporte y los chats de sus grupos. */
    void completarDe(UserId aprendizId);

    /**
     * @param soportesCreados     chats de soporte que faltaban y se crearon
     * @param soportesFallidos    aprendices cuyo soporte no se pudo crear (se reintenta en la próxima corrida)
     * @param gruposRevisados     grupos operativos revisados
     * @param chatsDeDosAbiertos  chats de dos que faltaban y se abrieron
     * @param gruposFallidos      grupos con alguna pareja que falló (se reintenta en la próxima corrida)
     */
    record ResultadoCompletar(int soportesCreados, int soportesFallidos, int gruposRevisados,
                              int chatsDeDosAbiertos, int gruposFallidos) {

        public boolean incompleto() {
            return soportesFallidos > 0 || gruposFallidos > 0;
        }
    }
}
