package com.renaser.os.points.application.ports.in.puntaje;

import com.renaser.os.points.domain.model.puntaje.PuntajeParticipante;
import com.renaser.os.shared.domain.UserId;

/**
 * Consulta el puntaje/coherencia/racha cacheados de un participante. Si todavía no tiene
 * fila propia (nunca se le aplicó un ajuste ni se le registró coherencia), devuelve el
 * estado inicial (100 puntos, 100 coherencia, sin racha) SIN escribir nada — a diferencia
 * de AjustarPuntosUseCase/RegistrarCoherenciaDiariaUseCase, una consulta no debe tener
 * efectos secundarios.
 */
public interface ConsultarPuntajeUseCase {

    /**
     * Cada uno ve el suyo; para ver el de otro hace falta ser administrativo activo.
     */
    PuntajeParticipante consultar(UserId actorId, UserId participanteId);
}
