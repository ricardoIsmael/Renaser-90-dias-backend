package com.renaser.os.academy.application.ports.out.recomendacion;

import com.renaser.os.academy.domain.model.recomendacion.RecomendacionAcademia;

public interface SaveRecomendacionPort {

    RecomendacionAcademia guardar(RecomendacionAcademia recomendacion);
}
