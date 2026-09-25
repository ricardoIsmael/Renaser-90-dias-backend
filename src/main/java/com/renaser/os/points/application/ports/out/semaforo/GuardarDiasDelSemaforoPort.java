package com.renaser.os.points.application.ports.out.semaforo;

import com.renaser.os.points.domain.model.semaforo.CumplimientoDelDia;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Collection;

/** Escritura de los días calculados del semáforo ({@code semaforo_dias}). */
public interface GuardarDiasDelSemaforoPort {

    /**
     * Inserta o actualiza cada día. Un día que no cambió no se reescribe (el barrido corre cada hora
     * y recalcula los días de la semana abierta: sin esto, reescribiría lo mismo 24 veces por día).
     *
     * @return cuántos días se insertaron o cambiaron
     */
    int guardar(UserId participante, Collection<CumplimientoDelDia> dias, Instant calculadoEn);
}
