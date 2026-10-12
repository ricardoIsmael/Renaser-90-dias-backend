package com.renaser.os.habits.infrastructure.adapter.in.rest.habitoadmin;

import com.renaser.os.habits.domain.model.audiencia.Audiencia;
import com.renaser.os.habits.domain.model.audiencia.CodigoDeRol;

import java.util.List;
import java.util.stream.Collectors;

/** Traduce {@code targetRoles} del JSON a la audiencia. Un código mal formado lanza 400 desde {@link CodigoDeRol}. */
final class TargetRolesDto {

    private TargetRolesDto() {
    }

    /** {@code null} se conserva como {@code null}: quien llama decide si significa "para todos" o "no se tocó". */
    static Audiencia aAudiencia(List<String> targetRoles) {
        if (targetRoles == null) {
            return null;
        }
        return Audiencia.de(targetRoles.stream().map(CodigoDeRol::of).collect(Collectors.toSet()));
    }
}
