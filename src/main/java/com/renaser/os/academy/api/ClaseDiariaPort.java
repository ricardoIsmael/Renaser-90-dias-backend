package com.renaser.os.academy.api;

import com.renaser.os.habits.api.CompletarClaseDiariaHabitoUseCase;
import com.renaser.os.shared.domain.UserId;

/**
 * Contrato publico de {@code academy} para la Clase Diaria (2026-09-23). Primer consumidor: el
 * acompanante de {@code rag} ({@code consultar_clase_de_hoy} y {@code proponer_entregar_clase_de_hoy}).
 *
 * <p>No agrega reglas: la implementacion delega en {@code ConsultarClaseDiariaUseCase} y
 * {@code CompletarClaseDiariaUseCase}, los mismos de {@code GET}/{@code POST
 * /api/v1/classroom/clase-diaria}. Que clase toca hoy, el gate de dia de programa, la suspension y
 * la idempotencia de la entrega los siguen decidiendo esos casos de uso.
 *
 * <p>No expone la recomendacion de Academia Adaptativa: {@code ConsultarRecomendacionDiariaUseCase}
 * la GENERA con IA si todavia no esta en cache, y una herramienta del chat no puede disparar ese
 * costo ni esa espera (C-1).
 */
public interface ClaseDiariaPort {

    /** El mismo minimo que valida la entrega (dueno del producto, 2026-09-04: "minimo 15 letras"). */
    int RESUMEN_MIN_LENGTH = CompletarClaseDiariaHabitoUseCase.RESUMEN_MIN_LENGTH;

    /** El mismo maximo que valida la entrega ("hasta 2000"). */
    int RESUMEN_MAX_LENGTH = CompletarClaseDiariaHabitoUseCase.RESUMEN_MAX_LENGTH;

    /**
     * @throws RuntimeException si la cuenta no existe, esta suspendida o no sigue el programa
     *                          ({@code NoSuchElementException} / {@code NotAuthorizedException})
     */
    ClaseDeHoy claseDeHoy(UserId actorId);

    /**
     * Entrega el resumen de la Clase Diaria de HOY: cierra el habito {@code DAILY_CLASS} (otorga
     * puntos) y marca la leccion como vista. Idempotente: si ya estaba entregada con resumen,
     * devuelve los puntos ya otorgados y NO guarda el texto nuevo.
     *
     * @throws RuntimeException con las mismas guardas que el {@code POST}: leccion que no es la de
     *                          hoy, resumen fuera de largo, sin clase disponible, cuenta suspendida
     */
    Entrega entregar(UserId actorId, String leccionId, String resumen);

    enum Estado {
        /** Hay clase hoy: {@link ClaseDeHoy#leccionId()} y los titulos vienen completos. */
        DISPONIBLE,
        /** Dia 0: el reloj de los 90 dias todavia no arranco. */
        NO_INICIADO,
        /** Dia con programa pero sin clase resuelta (fuera de 1-90 o hueco del catalogo). */
        PROXIMAMENTE
    }

    /**
     * @param leccionVista si la leccion de hoy ya figura como vista ({@code progreso_leccion}). NO
     *                     dice si el habito de la Clase Diaria ya se entrego con resumen
     */
    record ClaseDeHoy(Estado estado, int diaPrograma, String cursoId, String cursoTitulo, String leccionId,
                      String leccionTitulo, boolean leccionVista) {
    }

    record Entrega(String leccionId, int puntosOtorgados) {
    }
}
