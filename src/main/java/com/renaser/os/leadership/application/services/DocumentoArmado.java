package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.domain.model.documento.DatosDelDocumento;
import com.renaser.os.shared.domain.UserId;

import java.util.Objects;

/**
 * Los datos de un documento y de quién es (spec 056, FR-039): la persona del documento va aparte porque en el reporte del mes es quien lo
 * pidió, y no aparece en los datos.
 */
record DocumentoArmado(UserId personaId, DatosDelDocumento datos) {

    DocumentoArmado {
        Objects.requireNonNull(personaId, "personaId es obligatorio");
        Objects.requireNonNull(datos, "datos es obligatorio");
    }
}
