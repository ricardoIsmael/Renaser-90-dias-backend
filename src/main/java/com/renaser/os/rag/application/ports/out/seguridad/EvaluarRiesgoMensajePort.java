package com.renaser.os.rag.application.ports.out.seguridad;

import com.renaser.os.rag.domain.model.seguridad.EvaluacionRiesgo;

/**
 * Evalúa el riesgo de UN mensaje de un turno de conversación con un agente (hoy: Renasia).
 * Es la puerta de entrada al eje de seguridad descrito en {@link EvaluacionRiesgo}: recibe el
 * texto crudo del mensaje y devuelve el veredicto de los dos ejes ({@code NivelRiesgo} +
 * {@code Severidad}) para ESE mensaje puntual.
 *
 * <p><b>Lo que este puerto NO hace — a propósito.</b> No acumula estado entre mensajes (eso,
 * si hace falta, lo resuelve quien orqueste la conversación combinando evaluaciones sucesivas
 * con {@link EvaluacionRiesgo#combinar}) y no decide qué hacer con el veredicto: mapear un
 * {@link EvaluacionRiesgo} a un modo de respuesta (qué apaga herramientas, qué escala a
 * mentor, qué entra en crisis) es una regla de negocio sin confirmar — CLAUDE.MD §0.6 prohíbe
 * rellenarla con supuestos, y los criterios de detección en sí los tiene que firmar un
 * profesional con licencia, no este puerto ni su implementación.
 *
 * <p><b>Sin implementación real todavía.</b> La única implementación hoy es
 * {@code NoOpEvaluacionRiesgoAdapter} — un placeholder, no un clasificador. Este puerto
 * TODAVÍA NO está conectado a {@code ConversacionRenasiaService} ni a ningún otro caso de
 * uso: existe la estructura (puerto + adaptador) para que el día que haya un clasificador
 * real y un mapeo de riesgo confirmado, conectarlos sea un cambio acotado a la capa de
 * aplicación, no un rediseño.
 *
 * <p>CLAUDE.MD §5.4.9: {@code mensaje} es contenido de conversación de un aprendiz — dato
 * personal. Ninguna implementación de este puerto debe loguearlo; ver el javadoc de
 * {@code NoOpEvaluacionRiesgoAdapter}.
 */
public interface EvaluarRiesgoMensajePort {

    /** Nunca devuelve {@code null} — ver el javadoc de la implementación para qué devuelve
     * cuando no hay forma real de determinar el riesgo. */
    EvaluacionRiesgo evaluar(String mensaje);
}
