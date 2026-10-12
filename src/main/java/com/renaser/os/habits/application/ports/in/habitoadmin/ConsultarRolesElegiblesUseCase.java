package com.renaser.os.habits.application.ports.in.habitoadmin;

import com.renaser.os.shared.domain.UserId;

import java.util.List;

/** Los roles que se pueden elegir como audiencia de un hábito, con su nombre visible. Solo ADMIN/ALCHEMIST. */
public interface ConsultarRolesElegiblesUseCase {

    List<RolElegible> rolesElegibles(UserId actorId);

    record RolElegible(String codigo, String nombre) {
    }
}
