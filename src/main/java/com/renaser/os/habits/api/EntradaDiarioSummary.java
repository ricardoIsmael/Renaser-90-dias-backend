package com.renaser.os.habits.api;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Proyeccion publica de una entrada de diario (`entradas_diario`, tabla de `habits`) para
 * los modulos que necesitan LEERLAS sin acceder a la tabla ajena — hoy su unico consumidor
 * es el Espejo Sombra de `rag`, que analiza las entradas de la semana (D-50).
 *
 * <p>Deliberadamente NO expone {@code EntradaDiario} completa: viaja lo minimo para
 * analizar contenido, mas el {@code tipo} para que el consumidor decida si filtra o no
 * (el enum incluye un valor {@code ESPEJO_SOMBRA} dedicado, pero nada obliga a usar solo
 * ese — esa decision es del consumidor, no de este modulo).
 *
 * <p><b>Dato sensible:</b> {@code contenidoTexto} y {@code transcripcion} son escritura
 * personal del aprendiz. CLAUDE.MD §5.4.9 — nunca loguearlos.
 *
 * @param tipo espejo del enum interno {@code TipoEntradaDiario}, como String para no filtrar
 *             un tipo de dominio de `habits` fuera de su {@code @NamedInterface}
 */
public record EntradaDiarioSummary(UUID id, UserId participanteId, LocalDate fecha, String tipo,
                                    String contenidoTexto, String transcripcion) {

    /** true si la entrada tiene algo que analizar (texto propio o transcripcion del audio). */
    public boolean tieneContenido() {
        return (contenidoTexto != null && !contenidoTexto.isBlank())
                || (transcripcion != null && !transcripcion.isBlank());
    }
}
