package com.renaser.os.academy.application.ports.out.recomendacion;

import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.shared.domain.UserId;

import java.util.Optional;

/**
 * Genera (via IA) la recomendacion de clase del dia — Academia Adaptativa.
 * Ola 5 (fuera de alcance, ver `docs/MODULO_ACADEMY.md` §6): hoy solo existe
 * el adapter NoOp. La implementacion real necesitara ademas la ultima
 * entrada de "radar" de energia/animo del participante (tabla que no
 * pertenece a `academy`, ver `academia-adaptativa/repository.ts:47-52` del
 * repo viejo — `RadarEntry`) y la lista de lecciones aun no completadas —
 * ambas quedan fuera de este puerto a proposito: quien construya la
 * integracion real decide de donde las trae sin que `academy` tenga que
 * exponer mas superficie de la que necesita hoy.
 */
public interface RecomendarClasePort {

    Optional<ClaseRecomendada> recomendar(UserId participanteId);

    record ClaseRecomendada(LeccionId leccionId, String motivo) {
    }
}
