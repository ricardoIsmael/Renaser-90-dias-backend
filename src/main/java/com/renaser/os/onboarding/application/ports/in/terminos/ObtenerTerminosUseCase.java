package com.renaser.os.onboarding.application.ports.in.terminos;

import com.renaser.os.onboarding.domain.model.terminos.QuePideLaAceptacion;
import com.renaser.os.onboarding.domain.model.terminos.VersionDeTerminos;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Optional;

/**
 * Los términos que le tocan a la persona: los vigentes de SU formación (spec 027, FR-010, FR-022) y lo que debe hacer con ellos. Releer lo aceptado (FR-023).
 */
public interface ObtenerTerminosUseCase {

    /** Lo vigente para la persona y qué se le pide; los marcadores {@code {formacion}} y {@code {duracion}} ya van reemplazados. */
    TerminosParaLaPersona para(UserId persona);

    /** El texto de la versión que la persona aceptó por última vez, aunque haya una más nueva; vacío si nunca aceptó. */
    Optional<TerminosAceptados> aceptados(UserId persona);

    /**
     * @param vigente  vacío si su formación todavía no publicó términos (la app usa el texto que lleva)
     * @param nota     lo que cambió, solo cuando se le pide aceptar de nuevo
     */
    record TerminosParaLaPersona(Formacion formacion, String nombreDeLaFormacion, Optional<VersionDeTerminos> vigente, QuePideLaAceptacion pide,
                                 Optional<Integer> ultimaAceptada, Optional<String> nota) {
    }

    /** @param firmaUrl enlace de lectura de la firma que dibujó al aceptar (vence a los 15 minutos); vacío si no quedó firma o el archivo ya no está */
    record TerminosAceptados(VersionDeTerminos version, Instant aceptadaEn, Optional<String> firmaUrl) {
    }
}
