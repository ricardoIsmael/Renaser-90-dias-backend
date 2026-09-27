package com.renaser.os.points.application.ports.out.semaforo;

import com.renaser.os.points.domain.model.semaforo.PausaDeMedicion;
import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Los tramos sin medir del semáforo ({@code semaforo_pausas}): las pausas que pide el staff y los días
 * con la cuenta suspendida (D-209). Cada una sabe su {@code motivo}.
 */
public interface PausasDelSemaforoPort {

    /** Todas las pausas de cada persona, de los dos motivos, en una sola consulta; sin clave = ninguna. */
    Map<UserId, List<PausaDeMedicion>> de(Collection<UserId> usuarios);

    /**
     * Inserta la pausa nueva o guarda el cambio de una existente (nueva fecha, reanudación, fin de la
     * suspensión). Una pausa ya terminada no se vuelve a abrir.
     */
    void guardar(PausaDeMedicion pausa);
}
