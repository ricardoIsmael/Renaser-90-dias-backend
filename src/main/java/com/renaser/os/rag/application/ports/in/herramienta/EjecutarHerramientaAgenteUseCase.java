package com.renaser.os.rag.application.ports.in.herramienta;

import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * Las dos mitades del tool calling, separadas del chat a proposito: QUE puede hacer el agente, y
 * EJECUTAR una de esas cosas.
 *
 * <p><b>Por que un caso de uso propio y no codigo dentro del adaptador del proveedor.</b> El
 * bucle "el modelo pide una herramienta, alguien la ejecuta, el resultado vuelve al modelo" lo
 * corre la libreria del proveedor (en Spring AI, dentro de {@code ChatClient}). Si la ejecucion
 * viviera ahi, cambiar de proveedor obligaria a reescribirla. Aca, el adaptador real solo tiene
 * que traducir {@link #disponibles} al formato de su SDK y llamar a {@link #ejecutar} cuando el
 * modelo pida algo: exactamente el objetivo del encargo — enchufar el modelo es configuracion,
 * no reescritura.
 *
 * <p><b>Nunca dentro de una transaccion abierta a la espera del modelo</b> (C-1). Cada ejecucion
 * es una operacion corta y completa contra los puertos de negocio; quien orquesta la conversacion
 * no tiene ninguna transaccion viva mientras espera al proveedor.
 */
public interface EjecutarHerramientaAgenteUseCase {

    /**
     * Las herramientas de ese agente. Hoy solo {@link AgenteConversacional#COMPANION} tiene
     * herramientas; Sparkie responde sobre cursos y no toca habitos (D-102).
     */
    List<DefinicionHerramienta> disponibles(AgenteConversacional agente);

    /**
     * Ejecuta lo que el modelo pidio, EN NOMBRE DE {@code actorId}.
     *
     * <p><b>El actor no viene del modelo, nunca.</b> Es el usuario autenticado de la conversacion.
     * Si el aprendiz pudiera influir en de quien se leen o se completan los habitos — pidiendole
     * al asistente que "complete el habito de Juan" — la autorizacion del sistema entero
     * dependeria de lo que un modelo decida emitir. Por eso {@code actorId} es un parametro de
     * este metodo y no un argumento de la herramienta.
     *
     * <p>No lanza por un pedido invalido: una herramienta inexistente, un argumento que falta o
     * un habito que ya vencio son {@link ResultadoHerramienta.Fallo} con un motivo legible, para
     * que el modelo pueda explicarselo a la persona en vez de quedarse mudo.
     */
    ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion);
}
