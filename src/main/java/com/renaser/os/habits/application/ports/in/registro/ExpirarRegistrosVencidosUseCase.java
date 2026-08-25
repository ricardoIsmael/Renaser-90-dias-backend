package com.renaser.os.habits.application.ports.in.registro;

import java.time.LocalDate;

public interface ExpirarRegistrosVencidosUseCase {

    /** Barrido nocturno: todo lo PENDIENTE con fecha anterior a {@code hoy} pasa a EXPIRADO
     * (blind sweep, mismo criterio que `expirePendingTracksForTrainees` — sin mirar margen). */
    int expirarPendientesAnterioresA(LocalDate hoy);
}
