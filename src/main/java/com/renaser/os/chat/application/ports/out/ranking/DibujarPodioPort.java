package com.renaser.os.chat.application.ports.out.ranking;

import com.renaser.os.chat.domain.model.ranking.PodioDeLaSemana;
import com.renaser.os.chat.domain.model.ranking.SemanaDelRanking;

/** La imagen del podio de la semana (D-262), dibujada por el servidor. */
public interface DibujarPodioPort {

    String TIPO_CONTENIDO = "image/jpeg";

    /** @param podio no vacío */
    byte[] dibujar(PodioDeLaSemana podio, SemanaDelRanking semana);
}
