package com.renaser.os.academy.infrastructure.adapter.in.rest.curso;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.renaser.os.academy.domain.model.curso.AccesoCurso;
import com.renaser.os.academy.domain.model.curso.Curso;
import com.renaser.os.users.api.UserRole;

import java.time.Instant;
import java.util.List;

/**
 * Espejo EXACTO de la fila `cursos` tal como la app movil ya la consume hoy
 * (`src/types/cursos.ts: Curso`, repo RN) — nombres de columna en ESPAÑOL,
 * `snake_case`, porque `cursos` nunca vivio en Prisma: siempre hablo
 * PostgREST/Supabase directo (ver `docs/MODULO_ACADEMY.md` §5, decision AC-03
 * — por que ACA el wire es distinto del ingles/camelCase que usan
 * `rocks`/`support`). `acceso` sale en minuscula (`"abierto"`/`"restringido"`)
 * aunque el enum de Postgres nuevo sea `ABIERTO`/`RESTRINGIDO` — la
 * traduccion vive SOLO aca, nunca en dominio ni persistencia.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CursoResponse(String id, String slug, String titulo, String descripcion, String portadaUrl, int orden,
                             boolean publicado, String acceso, String origen, Integer diaDesbloqueo,
                             List<String> rolesPermitidos, Instant creadoEn, Instant actualizadoEn) {

    public static CursoResponse from(Curso curso) {
        return new CursoResponse(curso.id().value(), curso.slug(), curso.titulo(), curso.descripcion(),
                curso.portadaRuta(), curso.orden(), curso.publicado(), aWireAcceso(curso.acceso()), curso.origen(),
                curso.diaDesbloqueo(), curso.rolesPermitidos().stream().map(UserRole::name).sorted().toList(),
                curso.creadoEn(), curso.actualizadoEn());
    }

    static String aWireAcceso(AccesoCurso acceso) {
        return switch (acceso) {
            case ABIERTO -> "abierto";
            case RESTRINGIDO -> "restringido";
        };
    }
}
