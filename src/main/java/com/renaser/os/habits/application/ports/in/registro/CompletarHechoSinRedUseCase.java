package com.renaser.os.habits.application.ports.in.registro;

import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Spec 048 (FR-008, FR-013–FR-016): completar lo que la persona marcó SIN RED, con la hora real en que lo hizo.
 *
 * <p>Nada rebota (decisión D14 del dueño): lo que está en regla suma los puntos que tocaban a esa hora; lo que no, queda
 * registrado con 0 puntos y {@link MotivoSinPuntos} dice por qué. Un mismo {@code idCliente} se registra una sola vez.
 */
public interface CompletarHechoSinRedUseCase {

    CompletadoSinRed completar(CompletarRegistroCommand command, HechoSinRed sinRed);

    /** @param origenDeLaHora cómo calculó la app {@code hechoEn} (FR-013); solo se anota en el log */
    record HechoSinRed(Instant hechoEn, UUID idCliente, OrigenDeLaHora origenDeLaHora) {
        public HechoSinRed {
            Objects.requireNonNull(hechoEn, "hechoEn");
            Objects.requireNonNull(idCliente, "idCliente");
            origenDeLaHora = origenDeLaHora == null ? OrigenDeLaHora.TELEFONO_CORREGIDO : origenDeLaHora;
        }
    }

    enum OrigenDeLaHora {
        /** La última hora del servidor más el reloj del sistema desde el encendido: cambiar la hora del teléfono no la mueve. */
        RELOJ_DEL_SISTEMA,
        /** El teléfono se reinició sin señal: su reloj corregido con el último desfase conocido. */
        TELEFONO_CORREGIDO
    }

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
