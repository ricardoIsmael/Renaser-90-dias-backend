package com.renaser.os.plataforma.application.ports.in;

import com.renaser.os.plataforma.domain.model.auditoria.TipoDeCambio;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** La auditoría paginada, con filtros (contrato §6, {@code GET /soporte-ti/auditoria}). */
public interface ConsultarAuditoriaUseCase {

    /** El máximo de {@code size} (spec 050, tramo F): el controller responde 400 pasado esto, el servicio lo recorta como segunda línea. */
    int TAMANO_MAXIMO = 100;

    PaginaDeAuditoria consultar(AccesoDeConsola acceso, Filtro filtro);

    /** Todos opcionales. {@code tamano}: de 1 a {@link #TAMANO_MAXIMO}. */
    record Filtro(String clave, UserId actor, Instant desde, Instant hasta, int pagina, int tamano) {
    }

    record PaginaDeAuditoria(List<Fila> contenido, int pagina, long total) {
    }

    /** @param actor {@code null} en un cambio automático */
    record Fila(UUID id, TipoDeCambio tipo, FuncionalidadEnConsola.Actor actor, String clave, Map<String, Object> antes,
                Map<String, Object> despues, String motivo, String dispositivo, Instant creadoEn) {
    }
}
