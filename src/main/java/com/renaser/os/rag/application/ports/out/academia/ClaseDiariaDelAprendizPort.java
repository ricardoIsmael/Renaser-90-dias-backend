package com.renaser.os.rag.application.ports.out.academia;

import com.renaser.os.shared.domain.UserId;

/**
 * Puerto propio de {@code rag} para la Clase Diaria del aprendiz (herramientas
 * {@code consultar_clase_de_hoy} y {@code proponer_entregar_clase_de_hoy}, 2026-09-23).
 *
 * <p>La Clase Diaria es de {@code academy}: el adaptador delega en {@code academy.api.ClaseDiariaPort}
 * (D-41), que a su vez delega en los casos de uso del {@code GET}/{@code POST
 * /api/v1/classroom/clase-diaria}. Mismo criterio que {@code ConsultarHorariosPort}: records propios,
 * el contrato ajeno no llega a {@code rag.application}.
 */
public interface ClaseDiariaDelAprendizPort {

    /** @throws RuntimeException cuenta inexistente, suspendida o fuera del programa: la herramienta lo traduce */
    ClaseDeHoy claseDeHoy(UserId actorId);

    /**
     * Cierra el habito de la Clase Diaria (otorga puntos) y marca la leccion como vista.
     *
     * @return los puntos otorgados; si ya estaba entregada con resumen, los ya otorgados (el texto
     *         nuevo no se guarda)
     * @throws RuntimeException con el rechazo del negocio, tal cual: el confirmable lo traduce
     */
    int entregar(UserId actorId, String leccionId, String resumen);

    enum EstadoClase {
        DISPONIBLE,
        /** Dia 0: el programa todavia no arranco. */
        NO_INICIADO,
        /** Hay programa pero no hay clase resuelta para este dia. */
        PROXIMAMENTE
    }

    /**
     * @param leccionVista  si la leccion ya figura como vista; NO dice si el resumen ya se entrego
     * @param resumenMinimo largo minimo del resumen, el mismo que valida {@code academy}
     * @param resumenMaximo largo maximo del resumen, el mismo que valida {@code academy}
     */
    record ClaseDeHoy(EstadoClase estado, int diaPrograma, String cursoTitulo, String leccionId,
                      String leccionTitulo, boolean leccionVista, int resumenMinimo, int resumenMaximo) {

        public boolean disponible() {
            return estado == EstadoClase.DISPONIBLE;
        }
    }
}
