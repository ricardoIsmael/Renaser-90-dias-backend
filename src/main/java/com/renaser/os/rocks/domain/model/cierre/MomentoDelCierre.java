package com.renaser.os.rocks.domain.model.cierre;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * El instante en que se cierra el día y la hora que marca el reloj de pared de la persona en ese instante (spec 017, corregida el
 * 2026-10-30). El instante es el que se guarda como «cuándo se cerró»; la hora local sirve para saber si la hora de una acción ya
 * llegó. Se arma siempre con la zona de la persona, nunca con la del servidor (regla 02).
 *
 * @param instante  el momento del cierre
 * @param horaLocal la hora de ese instante en la zona de la persona
 */
public record MomentoDelCierre(Instant instante, LocalTime horaLocal) {

    public MomentoDelCierre {
        Objects.requireNonNull(instante, "instante");
        Objects.requireNonNull(horaLocal, "horaLocal");
    }

    public static MomentoDelCierre de(Instant instante, ZoneId zonaDeLaPersona) {
        return new MomentoDelCierre(instante, instante.atZone(zonaDeLaPersona).toLocalTime());
    }

    /**
     * ¿Ya llegó la hora de inicio de una acción? Sin hora fijada se considera que sí: «las acciones sin hora se preguntan como
     * hoy» (decisión del dueño, 2026-10-30). Una hora igual a la actual ya llegó.
     */
    public boolean llegoLaHora(LocalTime horaDeInicio) {
        return horaDeInicio == null || !horaDeInicio.isAfter(horaLocal);
    }
}
