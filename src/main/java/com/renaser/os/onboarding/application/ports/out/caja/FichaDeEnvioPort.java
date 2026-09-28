package com.renaser.os.onboarding.application.ports.out.caja;

import com.renaser.os.onboarding.domain.model.caja.DestinoAlternativo;
import com.renaser.os.onboarding.domain.model.caja.FichaDeEnvio;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

/**
 * Los datos de envío: los de la Ficha Inicial (solo lectura: son del aprendiz) y el destino alternativo de
 * la caja (las preguntas de {@code caja_renaser}/{@code destino}).
 */
public interface FichaDeEnvioPort {

    /** Las fichas de varios aprendices en UNA consulta; quien no respondió nada tiene una ficha vacía. */
    Map<UserId, FichaDeEnvio> de(Collection<UserId> aprendices);

    /** Guarda las cinco respuestas: una vacía borra la que había. */
    void guardarDestino(UserId aprendizId, DestinoAlternativo destino, Instant en);
}
