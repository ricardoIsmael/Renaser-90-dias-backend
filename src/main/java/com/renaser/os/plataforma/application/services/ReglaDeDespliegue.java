package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.domain.model.estado.ActorEvaluado;
import com.renaser.os.plataforma.domain.model.estado.ReglaDeDisponibilidad;
import com.renaser.os.shared.domain.UserId;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * El despliegue progresivo como regla de disponibilidad (spec 002, FR-018 y FR-021; contrato fase 2 §1 y §3.2): una
 * funcionalidad en despliegue que a esta persona todavía no le toca es «no disponible» (404, y la app la esconde: P1).
 *
 * <p>{@code @Order(10)}: se pregunta antes que la de roles personalizados ({@code @Order(20)}). Solo la ve la
 * evaluación de una persona con la cadena prendida: un interruptor apagado gana siempre (FR-021), y un proceso sin
 * actor no la ve.
 *
 * <p>Responde desde la copia en memoria: se evalúa en cada pedido y unas 55 veces por {@code GET /funcionalidades/mias}.
 */
@Component
@Order(10)
class ReglaDeDespliegue implements ReglaDeDisponibilidad {

    private final CopiaDeDespliegues copia;

    ReglaDeDespliegue(CopiaDeDespliegues copia) {
        this.copia = copia;
    }

    @Override
    public Optional<String> noDisponibleEn(List<String> cadena, ActorEvaluado actor) {
        UserId persona = actor.id();
        if (persona == null) {
            return Optional.empty();
        }
        return copia.foto().fueraEn(cadena, persona);
    }
}
