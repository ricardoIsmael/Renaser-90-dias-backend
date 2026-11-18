package com.renaser.os.leadership.application.ports.out;

import com.renaser.os.leadership.domain.model.documento.DocumentoSemanal;
import com.renaser.os.leadership.domain.model.documento.TipoDeDocumento;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Busca un documento ya generado con exactamente esos datos (spec 056, FR-039). */
public interface LeerDocumentoSemanalPort {

    Optional<DocumentoSemanal> existente(Buscado buscado);

    /** Lo que identifica a un documento: de quién, de qué tipo, de qué período y con qué datos (la huella). */
    record Buscado(UserId personaId, TipoDeDocumento tipo, LocalDate periodoInicio, String huella) {

        public Buscado {
            Objects.requireNonNull(personaId, "personaId es obligatorio");
            Objects.requireNonNull(tipo, "tipo es obligatorio");
            Objects.requireNonNull(periodoInicio, "periodoInicio es obligatorio");
            Objects.requireNonNull(huella, "huella es obligatoria");
        }
    }
}
