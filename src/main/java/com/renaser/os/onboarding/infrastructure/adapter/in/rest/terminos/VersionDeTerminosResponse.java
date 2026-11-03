package com.renaser.os.onboarding.infrastructure.adapter.in.rest.terminos;

import com.renaser.os.onboarding.domain.model.terminos.VersionDeTerminos;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Una versión de los términos tal como la ve la app (spec 027): el texto con las cláusulas en orden (la numeración la pone quien las dibuja), su número
 * dentro de la formación ({@code null} en el borrador) y la nota del cambio.
 */
public record VersionDeTerminosResponse(UUID id, String formation, String state, Integer number, String title, String introduction, List<String> clauses,
                                        String checkboxText, String note, boolean asksNewAcceptance, Integer baseNumber, Instant publishedAt,
                                        Instant updatedAt) {

    public static VersionDeTerminosResponse from(VersionDeTerminos v) {
        return new VersionDeTerminosResponse(v.id(), v.formacion().codigo(), v.estado().name(), v.numero(), v.contenido().titulo(),
                v.contenido().introduccion(), v.contenido().clausulas(), v.contenido().textoCasilla(), v.nota(), v.pideNuevaAceptacion(), v.baseNumero(),
                v.publicadaEn(), v.actualizadaEn());
    }
}
