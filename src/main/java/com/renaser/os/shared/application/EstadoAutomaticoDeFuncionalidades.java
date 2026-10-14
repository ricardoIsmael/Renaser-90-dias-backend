package com.renaser.os.shared.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Apagados <b>automáticos</b> de una funcionalidad (spec 002, FR-016; contrato §5): el circuito de la IA, que se abre
 * solo tras fallas seguidas del proveedor. Lo implementa {@code rag} (agente B) y lo consulta la evaluación de
 * {@code plataforma}: lo manual manda (FR-017), y si lo manual está prendido pero el circuito abierto, la funcionalidad
 * está apagada con el aviso automático.
 *
 * <p>Puede no haber ninguna implementación (sin {@code rag}, o antes de que exista el circuito): entonces nada está
 * apagado automáticamente. La implementación tiene que ser una lectura en memoria: se consulta en cada pedido de
 * una clave {@code ia.*}.
 */
public interface EstadoAutomaticoDeFuncionalidades {

    /** El aviso automático si esa clave (o la parte de la IA que la cubre) está apagada por el circuito. */
    Optional<String> apagadaAutomaticamente(String clave);

    /**
     * Cuántas veces cambió el estado automático (cada apertura y cada cierre de un circuito suma uno). Se suma a la
     * versión de {@code GET /funcionalidades/mias}, así la versión y el ETag que lee la app cambian también cuando el
     * circuito abre o cierra, no solo con los cambios manuales. No puede bajar mientras la instancia corre; si el
     * estado vive en Redis (compartido entre instancias), conviene que este número también, para que todas las
     * instancias respondan la misma versión.
     */
    long version();

    /** El estado de cada circuito, para la consola ({@code GET /soporte-ti/ia/circuito}). */
    List<CircuitoAutomatico> circuitos();

    /**
     * @param parte           {@code "ia.chat"} o {@code "ia.voz"}
     * @param estado          {@code CERRADO}, {@code ABIERTO} o {@code PROBANDO}
     * @param abiertoDesde    {@code null} si está cerrado
     * @param proximaPrueba   {@code null} si está cerrado
     */
    record CircuitoAutomatico(String parte, String estado, int fallasEnVentana, Instant abiertoDesde,
                              Instant proximaPrueba) {
    }
}
