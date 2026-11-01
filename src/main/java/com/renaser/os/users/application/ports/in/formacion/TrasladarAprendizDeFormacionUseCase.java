package com.renaser.os.users.application.ports.in.formacion;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

/**
 * Administración pasa a un aprendiz a otra formación (spec 022, FR-028, FR-029, FR-030; decisión Q1-A del dueño): solo cuando terminó la suya, o
 * dándola por terminada con un motivo escrito. Rige al instante; la persona elige su nuevo Día 1.
 */
public interface TrasladarAprendizDeFormacionUseCase {

    /**
     * @throws java.util.NoSuchElementException (404) si la persona no existe o no hace el programa
     * @throws IllegalStateException            (409) si todavía no terminó y no se la dio por terminada, o no es aprendiz
     * @throws IllegalArgumentException         (400) si falta el motivo o la formación no ofrece esa duración
     */
    void trasladar(TrasladarCommand command);

    record TrasladarCommand(UserId actor, UserId aprendiz, Formacion destino, int duracionDias, boolean darPorTerminada,
                            String motivo) {
    }
}
