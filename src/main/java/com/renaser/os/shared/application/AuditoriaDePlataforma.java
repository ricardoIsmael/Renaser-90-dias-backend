package com.renaser.os.shared.application;

import java.util.Map;

/**
 * Deja en la auditoría append-only de {@code plataforma} un cambio que nadie hizo a mano (spec 002, FR-016, SC-005):
 * la apertura y el cierre del circuito de la IA. Lo implementa {@code plataforma}; lo llama {@code rag} (agente B) sin
 * conocer las tablas ni importar {@code plataforma}.
 *
 * <p>Sin {@code plataforma} en el contexto, no hace nada.
 */
public interface AuditoriaDePlataforma {

    /**
     * @param clave   la funcionalidad, por ejemplo {@code "ia.chat"}
     * @param antes   el estado anterior, por ejemplo {@code {"estado": "CERRADO"}}
     * @param despues el estado nuevo
     * @param causa   qué lo provocó, en palabras ("5 fallas del proveedor en 2 minutos"); va como motivo
     */
    void registrarCambioAutomatico(String clave, Map<String, Object> antes, Map<String, Object> despues, String causa);
}
