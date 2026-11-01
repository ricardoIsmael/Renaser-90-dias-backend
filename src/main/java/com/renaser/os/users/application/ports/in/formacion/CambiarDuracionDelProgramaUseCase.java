package com.renaser.os.users.application.ports.in.formacion;

import com.renaser.os.shared.domain.UserId;

/**
 * Administración cambia la duración del programa de un aprendiz que ya empezó (spec 025, decisión 025-2 A del dueño): con un motivo escrito, la persona
 * conserva su día, se mueve el último y queda anotado en la bitácora de ajustes del reloj. Quien todavía no empezó se corrige con
 * {@link TrasladarAprendizDeFormacionUseCase} (que también cambia la duración sin dejar historia).
 */
public interface CambiarDuracionDelProgramaUseCase {

    /**
     * @throws java.util.NoSuchElementException (404) si la persona no existe o no hace el programa
     * @throws IllegalStateException            (409) si no es aprendiz, ya terminó, ya tiene esa duración o su día no cabe en la nueva
     * @throws IllegalArgumentException         (400) si falta el motivo o la formación de la persona no ofrece esa duración
     */
    void cambiar(CambiarDuracionCommand command);

    record CambiarDuracionCommand(UserId actor, UserId aprendiz, int duracionDias, String motivo) {
    }
}
