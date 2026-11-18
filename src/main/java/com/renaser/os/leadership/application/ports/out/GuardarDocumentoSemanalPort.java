package com.renaser.os.leadership.application.ports.out;

import com.renaser.os.leadership.domain.model.documento.DescargaDeDocumento;
import com.renaser.os.leadership.domain.model.documento.DocumentoSemanal;

/** Guarda un documento generado y cada pedido de él (spec 056, FR-039 y FR-040). Solo agrega: los documentos se guardan para siempre. */
public interface GuardarDocumentoSemanalPort {

    /**
     * Guarda el documento. Si ya hay uno de la misma persona, tipo, período y huella —dos pedidos a la vez generaron el mismo documento—
     * no crea otra fila y devuelve el que ya estaba, para que los dos pedidos terminen con el mismo.
     */
    DocumentoSemanal insertar(DocumentoSemanal documento);

    void registrarDescarga(DescargaDeDocumento descarga);
}
