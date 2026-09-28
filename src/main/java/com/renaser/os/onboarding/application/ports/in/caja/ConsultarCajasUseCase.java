package com.renaser.os.onboarding.application.ports.in.caja;

import com.renaser.os.onboarding.domain.model.caja.EstadoCaja;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Map;

/** La lista de cajas del Admin y su descarga (D-219). Solo ADMIN activo. */
public interface ConsultarCajasUseCase {

    PaginaDeCajas listar(UserId actorId, FiltroDeCajas filtro);

    /** Todas las cajas que aplican (sin {@code NO_APLICA}), ordenadas por nombre: el CSV. */
    List<ResumenDeCaja> exportar(UserId actorId);

    /**
     * @param estado {@code null} = todas las que aplican (sin {@code NO_APLICA})
     * @param q      texto a buscar en el nombre; {@code null} o vacío = sin buscar
     * @param pagina desde 0
     */
    record FiltroDeCajas(EstadoCaja estado, String q, int pagina, int tamano) {
    }

    /**
     * @param total   cuántas cumplen el filtro (todas las páginas)
     * @param conteos cuántas hay en cada estado, en el padrón entero y sin filtros (las pestañas)
     */
    record PaginaDeCajas(List<ResumenDeCaja> items, int total, Map<EstadoCaja, Integer> conteos) {
    }
}
