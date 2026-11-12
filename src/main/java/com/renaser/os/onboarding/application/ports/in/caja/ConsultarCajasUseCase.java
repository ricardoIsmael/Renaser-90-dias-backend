package com.renaser.os.onboarding.application.ports.in.caja;

import com.renaser.os.onboarding.domain.model.caja.EstadoCaja;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** La lista de cajas del Admin y su descarga (D-219). Solo ADMIN activo. */
public interface ConsultarCajasUseCase {

    /** El máximo de {@code size} de la lista (spec 050, tramo F): lo que pase de esto se recorta a esto. */
    int TAMANO_MAXIMO = 200;

    PaginaDeCajas listar(UserId actorId, FiltroDeCajas filtro);

    /**
     * La descarga: todas las cajas que aplican (sin {@code NO_APLICA}), ordenadas por nombre. <b>El guard de ADMIN pasa
     * aquí, al pedirla</b>, antes de que se escriba un solo byte: lo que viene después (las filas, por lotes) ya no puede
     * cambiar el estado HTTP. Las filas no se juntan: cada lote se entrega a quien escribe y se suelta.
     */
    ExportacionDeCajas exportar(UserId actorId);

    /** Las filas de la descarga, por lotes y en el orden del CSV; no se arman hasta que se llama a {@link #porLotes}. */
    @FunctionalInterface
    interface ExportacionDeCajas {

        void porLotes(Consumer<List<ResumenDeCaja>> escritor);
    }

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
