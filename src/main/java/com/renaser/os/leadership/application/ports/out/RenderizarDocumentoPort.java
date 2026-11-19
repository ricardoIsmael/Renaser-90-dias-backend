package com.renaser.os.leadership.application.ports.out;

import com.renaser.os.leadership.domain.model.documento.DatosDelDocumento;

import java.time.Instant;

/**
 * Dibuja los datos de un documento como la página que se guarda y se entrega (spec 056, FR-035, FR-036 y FR-032): HTML de una sola página
 * A4 que el navegador guarda como PDF. Quien lo implementa no lee nada más que los datos: no decide qué entra al documento, solo cómo se ve.
 */
public interface RenderizarDocumentoPort {

    /**
     * @param generadoEn el instante de la generación: se dice en la zona de la persona de quien es el documento. No forma parte de los datos
     *                   para que no cambie la huella (FR-039)
     * @return el documento completo, listo para guardarse como {@code text/html; charset=utf-8}
     */
    String renderizar(DatosDelDocumento datos, Instant generadoEn);
}
