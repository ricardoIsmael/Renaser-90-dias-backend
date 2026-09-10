package com.renaser.os.habits.application.ports.out.registro;

import com.renaser.os.habits.api.ObligacionHabito;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ConsultarObligacionesHistoricasPort {

    /** Una sola consulta por rango y conjunto de participantes. Nunca una por persona. */
    List<ObligacionHabito> entre(Collection<UserId> participantes, LocalDate desde, LocalDate hasta);
}
