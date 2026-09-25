package com.renaser.os.points.application.ports.out.semaforo;

import com.renaser.os.points.domain.model.semaforo.CumplimientoDelDia;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** Lectura de los días ya calculados del semáforo ({@code semaforo_dias}), EN LOTE. */
public interface CargarDiasDelSemaforoPort {

    /** Una sola consulta para todos; sin clave = esa persona no tiene días calculados en el rango. */
    Map<UserId, Map<LocalDate, CumplimientoDelDia>> entre(Collection<UserId> participantes, LocalDate desde,
                                                          LocalDate hasta);

    /** Cuándo el barrido escribió por última vez un día de esta persona. */
    Optional<Instant> ultimoCalculoDe(UserId participante);
}
