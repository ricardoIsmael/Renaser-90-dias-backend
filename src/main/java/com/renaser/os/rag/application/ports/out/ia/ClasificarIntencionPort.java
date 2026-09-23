package com.renaser.os.rag.application.ports.out.ia;

import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort.HabitoDelDia;
import com.renaser.os.rag.domain.model.intencion.IntencionClasificada;

import java.util.List;

/**
 * El "router rapido" del acompanante: decide que herramienta pide un mensaje, y a cual de los
 * habitos de hoy se refiere, en milisegundos y sin razonamiento de un modelo de lenguaje (plan de
 * IA v2.1, §5.3; {@code docs/arquitectura/PROPUESTA_JEV_ROUTER_TOOL_CALLING.md}).
 *
 * <p><b>Nombrado por intencion de negocio, no por tecnologia.</b> Hoy lo implementan
 * {@code NoOpClasificarIntencionAdapter} (el default: no opina) y
 * {@code EmbeddingsClasificarIntencionAdapter} (similitud de embeddings). El dia que Jev de
 * TypeSafe AI tenga evaluaciones independientes, entra como un adaptador mas de este mismo
 * puerto, sin tocar quien lo use. Por eso el contrato no expone vectores ni probabilidades de un
 * proveedor.
 *
 * <p><b>Nunca dentro de una transaccion</b> (regla 01, C-1): un adaptador real hace llamadas de
 * red.
 *
 * <p><b>Estado 2026-09-23: nadie lo invoca todavia.</b> Primero se mide contra el dataset es-PE
 * ({@code src/test/resources/ia/dataset-intenciones-es-pe.csv}); conectarlo al chat, y con que
 * umbrales, se decide con esos numeros.
 */
public interface ClasificarIntencionPort {

    /**
     * @param mensaje      lo que escribio o dijo la persona (ya transcrito si fue por voz)
     * @param habitosDeHoy las unicas opciones validas para {@link IntencionClasificada#habito()}
     */
    IntencionClasificada clasificar(String mensaje, List<HabitoDelDia> habitosDeHoy);
}
