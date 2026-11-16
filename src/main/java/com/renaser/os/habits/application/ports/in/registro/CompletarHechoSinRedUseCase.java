package com.renaser.os.habits.application.ports.in.registro;

import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.shared.domain.HechoSinRed;

/**
 * Spec 048 (FR-008, FR-013–FR-016): completar lo que la persona marcó SIN RED, con la hora real en que lo hizo.
 *
 * <p>Nada rebota (decisión D14 del dueño): lo que está en regla suma los puntos que tocaban a esa hora; lo que no, queda
 * registrado con 0 puntos y {@link MotivoSinPuntos} dice por qué. Un mismo {@code idCliente} se registra una sola vez.
 */
public interface CompletarHechoSinRedUseCase {

    CompletadoSinRed completar(CompletarRegistroCommand command, HechoSinRed sinRed);

    /** {@code motivoSinPuntos} es nulo cuando sumó sus puntos. */
    record CompletadoSinRed(RegistroHabito registro, MotivoSinPuntos motivoSinPuntos) {
    }

    enum MotivoSinPuntos {
        /** Llegó más de 48 h después de hecho. */
        LLEGO_DESPUES_DE_48_H,
        /** La hora dice que se hizo en el futuro (más de 2 minutos). */
        HORA_FUTURA,
        /** La hora cae fuera del día del registro. */
        FUERA_DE_SU_DIA,
        /** Ya estaba hecho (con red o desde otro teléfono): no se toca. */
        YA_ESTABA_HECHO
    }
}
