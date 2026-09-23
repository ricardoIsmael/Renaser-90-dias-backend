package com.renaser.os.rag.application.services.herramientas;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort.AccionDelPlan;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort.ObjetivoSemanal;
import com.renaser.os.rag.application.services.herramientas.PlanDeRocasJson.PlanDelDia;

import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/**
 * El JSON que se guarda en la propuesta, reescrito desde lo ya validado (mismo criterio que
 * {@code PropuestaDeMarcarHabito.invocacionPara}): al confirmar se ejecuta exactamente esto, sin
 * campos de mas, sin espacios alrededor, con el eje en mayusculas y la hora como {@code HH:MM}.
 *
 * <p><b>La fecha del dia va siempre escrita.</b> Si el modelo no la mando, quien propone pone la de
 * manana ANTES de guardar: una propuesta hecha a las 23:55 y confirmada a las 00:05 tiene que
 * planificar el dia que la persona vio en el resumen, no "el manana" del momento de confirmar.
 */
final class PlanDeRocasNormalizado {

    private PlanDeRocasNormalizado() {
    }

    static String delDia(PlanDelDia plan) {
        ObjectNode raiz = PlanDeRocasJson.JSON.createObjectNode();
        raiz.put("fecha", Objects.requireNonNull(plan.fecha(), "la fecha se resuelve antes de guardar").toString());
        ArrayNode acciones = raiz.putArray("acciones");
        for (AccionDelPlan accion : plan.acciones()) {
            ObjectNode nodo = acciones.addObject().put("eje", accion.eje()).put("titulo", accion.titulo());
            ponerHora(nodo, "inicio", accion.inicio());
            ponerHora(nodo, "fin", accion.fin());
        }
        return escribir(raiz);
    }

    static String deLaSemana(List<ObjetivoSemanal> objetivos) {
        ObjectNode raiz = PlanDeRocasJson.JSON.createObjectNode();
        ArrayNode lista = raiz.putArray("objetivos");
        for (ObjetivoSemanal objetivo : objetivos) {
            ObjectNode nodo = lista.addObject().put("eje", objetivo.eje()).put("titulo", objetivo.titulo());
            if (objetivo.obstaculo() != null) {
                nodo.put("obstaculo", objetivo.obstaculo());
            }
            if (objetivo.contingencia() != null) {
                nodo.put("contingencia", objetivo.contingencia());
            }
        }
        return escribir(raiz);
    }

    private static void ponerHora(ObjectNode nodo, String campo, LocalTime hora) {
        if (hora != null) {
            nodo.put(campo, hora.format(PlanDeRocasJson.HORA));
        }
    }

    private static String escribir(ObjectNode raiz) {
        try {
            return PlanDeRocasJson.JSON.writeValueAsString(raiz);
        } catch (JsonProcessingException imposible) {
            // Son textos y listas ya validados: no hay nada que Jackson no sepa escribir.
            throw new IllegalStateException("No se pudo escribir el plan normalizado", imposible);
        }
    }
}
