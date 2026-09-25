package com.renaser.os.points.application.ports.out.semaforo;

import com.renaser.os.points.domain.model.semaforo.PausaDeMedicion;
import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Las pausas del semáforo del staff ({@code semaforo_pausas}). */
public interface PausasDelSemaforoPort {

    /** Todas las pausas de cada persona en una sola consulta; sin clave = nunca pausó. */
    Map<UserId, List<PausaDeMedicion>> de(Collection<UserId> usuarios);

    /** Inserta la pausa nueva o guarda el cambio de una existente (nueva fecha, reanudación). */
    void guardar(PausaDeMedicion pausa);
}
