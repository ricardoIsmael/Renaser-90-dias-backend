package com.renaser.os.notifications.application.ports.in.alarmalocal;

import com.renaser.os.shared.domain.UserId;

/**
 * ¿La persona tiene hoy un telefono que le programe la alarma local de un evento al que dijo
 * "Voy"? (D-189). Se consulta al ENTREGAR cada recordatorio, no al confirmar: un telefono
 * registrado despues del "Voy", o uno que se desinstalo, cuentan con su estado de ese momento.
 */
public interface ConsultarAlarmaLocalUseCase {

    /** {@code true} si tiene al menos un token push activo de Android o iOS. Solo web, o ninguno: {@code false}. */
    boolean tieneAlarmaLocal(UserId usuarioId);
}
