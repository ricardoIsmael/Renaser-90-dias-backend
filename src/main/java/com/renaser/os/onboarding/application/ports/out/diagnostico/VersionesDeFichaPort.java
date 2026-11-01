package com.renaser.os.onboarding.application.ports.out.diagnostico;

import com.renaser.os.onboarding.domain.model.diagnostico.FichaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.VersionPublicada;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Las fotos de lo publicado ({@code fichas_publicadas}, V145): append-only. */
public interface VersionesDeFichaPort {

    Optional<VersionPublicada> ultima();

    Optional<VersionPublicada> version(int version);

    /**
     * Publica la foto como la versión siguiente. Si otra persona publicó entre medio, falla con {@link IllegalStateException}
     * («Otra persona publicó cambios mientras editabas»).
     */
    VersionPublicada publicar(FichaDelDiagnostico ficha, UserId por, Instant ahora);

    /** De la más nueva a la más vieja. */
    List<VersionPublicada> listar();
}
