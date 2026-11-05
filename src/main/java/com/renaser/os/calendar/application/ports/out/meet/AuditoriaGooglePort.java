package com.renaser.os.calendar.application.ports.out.meet;

import com.renaser.os.calendar.domain.model.meet.RegistroDeAuditoriaGoogle;

import java.time.Instant;
import java.util.List;

public interface AuditoriaGooglePort {

    void registrar(RegistroDeAuditoriaGoogle registro);

    List<RegistroDeAuditoriaGoogle> ultimos(int cuantos);

    /** Retención de 12 meses (FR-016). */
    int purgarAnterioresA(Instant limite);
}
