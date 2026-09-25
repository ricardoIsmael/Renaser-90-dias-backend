package com.renaser.os.academy.application.ports.in.recomendacion;

import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.shared.domain.UserId;

import java.util.Optional;

/**
 * GET /api/v1/academia/recomendacion — Academia Adaptativa. Cache-first: si
 * ya existe una recomendacion para el dia calendario del participante, se
 * devuelve; si no, se le pide una a {@code RecomendarClasePort} (hoy NoOp,
 * Ola 5 — ver `docs/MODULO_ACADEMY.md` §6). Espejo parcial de
 * `getClassRecommendation` (RenaserBack `academia-adaptativa/service.ts`).
 */
public interface ConsultarRecomendacionDiariaUseCase {

    RecomendacionDiaria recomendacion(UserId actorId);

    /**
     * La recomendacion de HOY solo si ya esta en cache (2026-09-23). Mismas guardas y mismo dia
     * calendario del participante que {@link #recomendacion}; a diferencia de ese, si no hay fila
     * devuelve vacio y NUNCA la genera: no llama a la IA ni guarda nada. Es la lectura que puede
     * hacer el acompanante desde una herramienta (C-1).
     */
    Optional<Disponible> recomendacionDeHoySiExiste(UserId actorId);

    sealed interface RecomendacionDiaria permits Disponible, NoDisponible {
    }

    record Disponible(LeccionId leccionId, String leccionTitulo, CursoId cursoId, String cursoTitulo, String motivo)
            implements RecomendacionDiaria {
    }

    record NoDisponible(String razon) implements RecomendacionDiaria {
    }
}
