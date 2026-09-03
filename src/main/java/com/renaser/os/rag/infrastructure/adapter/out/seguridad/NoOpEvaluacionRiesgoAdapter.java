package com.renaser.os.rag.infrastructure.adapter.out.seguridad;

import com.renaser.os.rag.application.ports.out.seguridad.EvaluarRiesgoMensajePort;
import com.renaser.os.rag.domain.model.seguridad.EvaluacionRiesgo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Placeholder: todavía no existe un clasificador de riesgo real (ni el criterio clínico que
 * lo tiene que firmar, ni el proveedor que lo va a correr) — mismo patrón EXACTO que
 * {@code NoOpInsightSemanalAdapter}/{@code NoOpEmbeddingAdapter}: loguea que es un
 * placeholder y devuelve un valor válido, nunca {@code null}, para que el resto del flujo
 * pueda compilar y probarse de punta a punta sin un clasificador real todavía conectado.
 *
 * <p><b>Qué devuelve cuando "no puede determinar el riesgo" — decisión deliberadamente NO
 * tomada acá.</b> El javadoc de {@code NivelRiesgo} deja abierta la pregunta de si hace falta
 * un tercer estado explícito ("el clasificador no pudo determinarlo"), y la deja abierta a
 * propósito: agregarlo exige decidir a qué modo de respuesta mapea, y esa tabla es la regla
 * de negocio sin confirmar que CLAUDE.MD §0.6 prohíbe rellenar con supuestos. Como ese estado
 * no existe todavía, este adaptador tiene que devolver uno de los tres valores YA definidos
 * de {@code NivelRiesgo}, y esa elección sí es mía, documentada acá:
 *
 * <ul>
 *   <li>Devolver {@link EvaluacionRiesgo#sinSenales()} (NINGUNO/BAJA) — la opción elegida.
 *   Es la misma filosofía que el resto de los {@code NoOp} de este módulo: un placeholder
 *   inerte que no le miente a nadie sobre haber evaluado algo, y dado que ESTE puerto
 *   todavía no está conectado a ningún flujo que actúe sobre el veredicto (ver el javadoc de
 *   {@link EvaluarRiesgoMensajePort}), hoy no protege nada — pero tampoco rompe nada.</li>
 *   <li>La alternativa descartada — devolver {@code CRITICO} "por las dudas" — NO es más
 *   segura, es la respuesta contraria. {@code NivelRiesgo.CRITICO} dispara el modo crisis
 *   {@code siempre, sin importar la severidad}: usarlo como default de un adaptador que
 *   literalmente nunca leyó el mensaje significaría que, el día que alguien conecte este
 *   puerto sin releer este comentario, TODA conversación con Renasia entra en modo crisis.
 *   Eso no es conservador, es una falsa alarma permanente que además desensibiliza la señal
 *   real el día que exista un clasificador de verdad.</li>
 * </ul>
 *
 * <p><b>Lo que queda pendiente, explícitamente.</b> Esta clase NO es un clasificador: no lee
 * el contenido de {@code mensaje} en absoluto. El día que se conecte un clasificador real
 * (proveedor de IA o reglas), alguien con el criterio clínico confirmado tiene que decidir:
 * (1) si hace falta el tercer estado "indeterminado" en {@code NivelRiesgo}, y (2) el mapeo
 * completo de {@link EvaluacionRiesgo} a modo de respuesta. Ninguna de las dos cosas se
 * resuelve acá.
 *
 * <p>CLAUDE.MD §5.4.9: {@code mensaje} nunca se loguea, ni siquiera parcialmente — es
 * contenido de conversación de un aprendiz.
 */
@Component
class NoOpEvaluacionRiesgoAdapter implements EvaluarRiesgoMensajePort {

    private static final Logger log = LoggerFactory.getLogger(NoOpEvaluacionRiesgoAdapter.class);

    @Override
    public EvaluacionRiesgo evaluar(String mensaje) {
        log.warn("EvaluarRiesgoMensajePort.evaluar(...) placeholder: todavia no hay clasificador de riesgo real. "
                + "Devolviendo EvaluacionRiesgo.sinSenales() (ver javadoc de esta clase para por que).");
        return EvaluacionRiesgo.sinSenales();
    }
}
