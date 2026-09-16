package com.renaser.os.users.infrastructure.adapter.in.rest.admin;

import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.participante.ListTraineesUseCase.ResumenTraineeAdmin;

import java.util.UUID;

/**
 * Fila del listado del panel admin de personas (gap #7).
 *
 * <p><b>{@code role} desde 2026-09-16</b> (D-138). Mientras el listado fue solo de aprendices, el
 * rol no hacia falta: era el mismo en todas las filas. Ahora vienen los cinco, y sin este campo la
 * pantalla no podria distinguir a un mentor de un aprendiz.
 *
 * <p><b>Ojo con la ortografia: aca va la etiqueta de la BASE</b> —{@code APRENDIZ}, {@code MENTOR},
 * {@code LIDER_MENTORES}, {@code ADMIN}, {@code ALQUIMISTA}— y no el nombre del enum Java. Es el
 * contrato que fijo el dueno para esta pantalla, y <b>no coincide</b> con el campo {@code role} de
 * {@code GET /api/v1/admin/staff}, que serializa {@link UserRole} y por lo tanto dice
 * {@code TRAINEE}, {@code MENTOR_LEAD} y {@code ALCHEMIST}. Los dos listados hablan del mismo dato
 * con dos ortografias: quien compare un {@code role} de aca contra uno de alla tiene que traducir.
 * Queda anotado en D-138 como lo primero a unificar si el dueno prefiere una sola.
 */
public record TraineeSummaryResponse(String id, String fullName, String email, UserStatus status, int programDay,
                                      FasePrograma phase, UUID cellId, String mentorId, String role) {

    public static TraineeSummaryResponse from(ResumenTraineeAdmin resumen) {
        return new TraineeSummaryResponse(resumen.id().toString(), resumen.fullName(), resumen.email(),
                resumen.status(), resumen.diaPrograma(), resumen.fase(), resumen.celulaId(),
                resumen.mentorId() == null ? null : resumen.mentorId().toString(), etiquetaDe(resumen.rol()));
    }

    /**
     * La traduccion va en un {@code switch} exhaustivo y no en un {@code name()} ni en un mapa: si
     * manana se agrega un rol, esto no compila y alguien tiene que decidir como se llama en la
     * pantalla, en vez de que salga una etiqueta inventada por el compilador.
     */
    private static String etiquetaDe(UserRole rol) {
        return switch (rol) {
            case TRAINEE -> "APRENDIZ";
            case MENTOR -> "MENTOR";
            case MENTOR_LEAD -> "LIDER_MENTORES";
            case ADMIN -> "ADMIN";
            case ALCHEMIST -> "ALQUIMISTA";
        };
    }
}
