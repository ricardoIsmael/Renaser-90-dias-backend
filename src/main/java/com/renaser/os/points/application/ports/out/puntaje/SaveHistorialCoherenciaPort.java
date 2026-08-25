package com.renaser.os.points.application.ports.out.puntaje;

import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface SaveHistorialCoherenciaPort {

    void upsert(UserId participanteId, LocalDate fecha, BigDecimal valor);
}
