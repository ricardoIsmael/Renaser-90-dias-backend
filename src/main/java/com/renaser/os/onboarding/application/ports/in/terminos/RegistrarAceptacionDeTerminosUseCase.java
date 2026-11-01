package com.renaser.os.onboarding.application.ports.in.terminos;

import com.renaser.os.shared.domain.UserId;

import java.util.UUID;

/** Deja la constancia de que la persona aceptó los términos vigentes de su formación (spec 027, FR-011, FR-012). */
public interface RegistrarAceptacionDeTerminosUseCase {

    /**
     * @param versionId la versión que la persona leyó; {@code null} (una app anterior) = la vigente de su formación
     * @throws IllegalStateException (409) si dejó de ser la vigente mientras leía: la app recarga el texto y avisa
     */
    void registrar(UserId persona, UUID versionId);
}
