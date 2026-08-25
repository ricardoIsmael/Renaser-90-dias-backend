package com.renaser.os.academy.application.ports.out.recomendacion;

import com.renaser.os.academy.domain.model.recomendacion.RecomendacionAcademia;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Optional;

public interface LoadRecomendacionPort {

    Optional<RecomendacionAcademia> delDia(UserId participanteId, LocalDate fecha);
}
