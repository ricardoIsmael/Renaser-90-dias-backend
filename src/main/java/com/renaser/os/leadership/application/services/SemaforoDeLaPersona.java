package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.domain.model.cumplimiento.VentanaDeLaFicha;
import com.renaser.os.points.api.SemaforoFinder;
import com.renaser.os.points.api.VentanaDelSemaforo;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lo que el semáforo dice de UNA persona en la semana que el líder está mirando (spec 005). No calcula nada: el color,
 * el porcentaje y el estado de cada día salen de la misma lectura de {@code points} que usan la tabla del mentor y la
 * del administrador (FR-008). Una semana que todavía corre trae solo los días ya cerrados.
 */
@Component
class SemaforoDeLaPersona {

    private final SemaforoFinder semaforo;

    SemaforoDeLaPersona(SemaforoFinder semaforo) {
        this.semaforo = semaforo;
    }

    /** Vacío si el semáforo no la mide (por ejemplo, todavía no activó su programa): ni cero ni verde. */
    Optional<VentanaDelSemaforo> ventanaDe(UserId persona, VentanaDeLaFicha ventana) {
        Map<UserId, VentanaDelSemaforo> leidas = semaforo.semanaDe(List.of(persona), ventana.hasta());
        return Optional.ofNullable(leidas.get(persona));
    }
}
