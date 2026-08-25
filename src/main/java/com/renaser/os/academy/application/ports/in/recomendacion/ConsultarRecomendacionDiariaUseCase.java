package com.renaser.os.academy.application.ports.in.recomendacion;

import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.shared.domain.UserId;

/**
 * GET /api/v1/academia/recomendacion — Academia Adaptativa. Cache-first: si
 * ya existe una recomendacion para el dia calendario del participante, se
 * devuelve; si no, se le pide una a {@code RecomendarClasePort} (hoy NoOp,
 * Ola 5 — ver `docs/MODULO_ACADEMY.md` §6). Espejo parcial de
 * `getClassRecommendation` (RenaserBack `academia-adaptativa/service.ts`).
 */
public interface ConsultarRecomendacionDiariaUseCase {

    RecomendacionDiaria recomendacion(UserId actorId);

    sealed interface RecomendacionDiaria permits Disponible, NoDisponible {
    }

    record Disponible(LeccionId leccionId, String leccionTitulo, CursoId cursoId, String cursoTitulo, String motivo)
            implements RecomendacionDiaria {
    }

    record NoDisponible(String razon) implements RecomendacionDiaria {
    }
}
