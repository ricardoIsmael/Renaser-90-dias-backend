package com.renaser.os.onboarding.application.ports.out.terminos;

import com.renaser.os.onboarding.domain.model.terminos.VersionDeTerminos;
import com.renaser.os.shared.domain.Formacion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Las versiones de los términos de cada formación (tabla {@code terminos_versiones}, V148, spec 027). */
public interface TerminosPort {

    /** La última versión publicada de la formación; vacía si todavía no tiene ninguna. */
    Optional<VersionDeTerminos> vigente(Formacion formacion);

    /** El borrador de la formación (a lo sumo uno). */
    Optional<VersionDeTerminos> borrador(Formacion formacion);

    Optional<VersionDeTerminos> porId(UUID id);

    Optional<VersionDeTerminos> publicada(Formacion formacion, int numero);

    /** Las publicadas, de la más vieja a la más nueva. */
    List<VersionDeTerminos> publicadas(Formacion formacion);

    /** Guarda el borrador nuevo, el borrador editado o el borrador ya publicado (por {@code id}). */
    void guardar(VersionDeTerminos version);

    void descartarBorrador(Formacion formacion);
}
