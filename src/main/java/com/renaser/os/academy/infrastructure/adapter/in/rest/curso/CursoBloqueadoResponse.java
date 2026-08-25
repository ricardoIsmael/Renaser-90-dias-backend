package com.renaser.os.academy.infrastructure.adapter.in.rest.curso;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.renaser.os.academy.application.ports.in.curso.ConsultarCursosBloqueadosUseCase.CursoBloqueado;

/**
 * Item de GET /api/v1/cursos/bloqueados — espejo EXACTO de {@code CursoBloqueado}
 * (`src/types/cursos.ts`, fila de la RPC `catalogo_cursos_bloqueados` que
 * reemplaza). `portada_url` viaja SIN firmar a proposito: la app firma la
 * escalera completa (disponibles + bloqueados) en una sola llamada de cliente
 * (`resolverMediaUrls`, `useMisCursos.ts:71`), igual que ya hace hoy con la
 * RPC — firmar aca seria trabajo duplicado que la app descarta.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CursoBloqueadoResponse(String id, String titulo, String portadaUrl, int orden, int diaDesbloqueo,
                                      int programDayActual) {

    public static CursoBloqueadoResponse from(CursoBloqueado item) {
        return new CursoBloqueadoResponse(item.curso().id().value(), item.curso().titulo(),
                item.curso().portadaRuta(), item.curso().orden(), item.diaDesbloqueo(), item.programDayActual());
    }
}
