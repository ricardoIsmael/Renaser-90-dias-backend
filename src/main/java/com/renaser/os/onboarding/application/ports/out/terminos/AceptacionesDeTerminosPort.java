package com.renaser.os.onboarding.application.ports.out.terminos;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** La constancia de qué versión aceptó cada persona (tabla {@code aceptaciones_de_terminos}, V148, spec 027). Solo se agregan filas. */
public interface AceptacionesDeTerminosPort {

    /** Anota que {@code usuario} aceptó la versión; {@code firmaMediaId} es la firma que dibujó (puede faltar si el archivo se borró). */
    void registrar(UUID id, UserId usuario, UUID versionId, Instant en, Long firmaMediaId);

    /** El número de la última versión de ESA formación que aceptó la persona; vacío si nunca aceptó una de esa formación. */
    Optional<Integer> ultimaAceptada(UserId usuario, Formacion formacion);

    /** Si aceptó términos alguna vez, de cualquier formación. */
    boolean aceptoAlgunaVez(UserId usuario);

    /** Lo que aceptó la persona, de lo más reciente a lo más viejo, con la versión a la vista. */
    List<Aceptacion> delUsuario(UserId usuario);

    /** Cuántas personas distintas aceptaron cada versión (por su id). */
    Map<UUID, Long> aceptantesPorVersion(Formacion formacion);

    /** Las personas que aceptaron la versión {@code numero} o una posterior de esa formación. */
    Set<UserId> quienesAceptaronDesde(Formacion formacion, int numero);

    record Aceptacion(UUID versionId, Formacion formacion, int numero, Instant aceptadaEn, Long firmaMediaId) {
    }
}
