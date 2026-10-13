package com.renaser.os.plataforma.application.ports.out;

import com.renaser.os.plataforma.domain.model.auditoria.CambioAuditado;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;

/** Lee la auditoría, del cambio más nuevo al más viejo. */
public interface ConsultarAuditoriaPort {

    PaginaDeCambios buscar(FiltroDeAuditoria filtro);

    /**
     * Todos los campos de filtro son opcionales.
     *
     * @param desde incluido
     * @param hasta excluido
     */
    record FiltroDeAuditoria(String clave, UserId actor, Instant desde, Instant hasta, int pagina, int tamano) {
    }

    record PaginaDeCambios(List<CambioAuditado> contenido, int pagina, long total) {
    }
}
