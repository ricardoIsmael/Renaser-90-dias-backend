package com.renaser.os.academy.infrastructure.adapter.in.rest.curso;

import com.renaser.os.academy.application.ports.in.curso.ConsultarCursosBloqueadosUseCase.CursoBloqueado;

/**
 * Item de GET /api/v1/cursos/bloqueados — espejo EXACTO de {@code CursoBloqueado}
 * (`src/types/cursos.ts`, fila de la RPC `catalogo_cursos_bloqueados` que
 * reemplaza). `portada_url` viaja SIN firmar a proposito: la app firma la
 * escalera completa (disponibles + bloqueados) en una sola llamada de cliente
 * (`resolverMediaUrls`, `useMisCursos.ts:71`), igual que ya hace hoy con la
 * RPC — firmar aca seria trabajo duplicado que la app descarta.
 */
/*
 * SIN @JsonNaming a proposito (2026-09-01). Estos DTOs declaraban
 * `@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)` importado de
 * `com.fasterxml.jackson.databind.annotation` — o sea de JACKSON 2. Spring Boot 4 serializa con
 * JACKSON 3, que vive en `tools.jackson.*`, y esa anotacion la ignora en silencio: no falla, no
 * avisa, simplemente no la aplica. Resultado: los 10 DTOs de academy declaraban snake_case y
 * mandaban camelCase, y el frontend que confio en la anotacion no pudo leer ni un curso.
 * Se quitan en vez de corregir el import porque el resto de la API ya es camelCase: dejar
 * academy en snake_case lo volveria la unica excepcion. Ver E-65 en docs/BITACORA_ERRORES.md.
 */
public record CursoBloqueadoResponse(String id, String titulo, String portadaUrl, int orden, int diaDesbloqueo,
                                      int programDayActual) {

    public static CursoBloqueadoResponse from(CursoBloqueado item) {
        return new CursoBloqueadoResponse(item.curso().id().value(), item.curso().titulo(),
                item.curso().portadaRuta(), item.curso().orden(), item.diaDesbloqueo(), item.programDayActual());
    }
}
