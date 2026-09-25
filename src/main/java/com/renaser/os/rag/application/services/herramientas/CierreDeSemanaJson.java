package com.renaser.os.rag.application.services.herramientas;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.renaser.os.rag.application.ports.out.rocas.CerrarSemanaDeRocasPort.ReglasDelCierre;
import com.renaser.os.rag.application.ports.out.rocas.CerrarSemanaDeRocasPort.RevisionDelEje;
import com.renaser.os.rag.application.services.herramientas.PlanDeRocasJson.PlanMalFormadoException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lee y escribe el argumento {@code cierre} de {@code proponer_cerrar_semana} (2026-09-23), con las
 * mismas reglas de forma que {@link PlanDeRocasJson}: estricto, sin campos de mas, y el motivo del
 * rechazo ya escrito para el modelo.
 *
 * <p>Dos formas: la que manda el modelo ({@code {"ejes":[...]}}) y la que se guarda en la propuesta,
 * que agrega {@code "semana"}: el numero de semana de programa que se le mostro a la persona. El
 * modelo NO puede elegir la semana; la pone la herramienta con lo que dice {@code rocks}.
 *
 * <p>La escala de la autoevaluacion y los ejes vienen de {@code rocks} ({@link ReglasDelCierre}), no
 * se escriben aca.
 */
final class CierreDeSemanaJson {

    static final String CAMPO_SEMANA = "semana";
    private static final String CAMPO_EJES = "ejes";
    private static final Set<String> CAMPOS_DE_REVISION = Set.of("eje", "autoevaluacion", "bloqueoPrincipal",
            "correccion");

    private CierreDeSemanaJson() {
    }

    /** Lo que se guardo en la propuesta: la semana que vio la persona y sus revisiones. */
    record CierreGuardado(int semana, List<RevisionDelEje> revisiones) {
    }

    static List<RevisionDelEje> leerDelModelo(String texto, ReglasDelCierre reglas) {
        return revisiones(PlanDeRocasJson.objeto(leer(texto), "El cierre", Set.of(CAMPO_EJES)), reglas);
    }

    static CierreGuardado leerGuardado(String texto, ReglasDelCierre reglas) {
        JsonNode raiz = PlanDeRocasJson.objeto(leer(texto), "El cierre", Set.of(CAMPO_SEMANA, CAMPO_EJES));
        JsonNode semana = raiz.get(CAMPO_SEMANA);
        if (semana == null || !semana.canConvertToInt() || !semana.isIntegralNumber()) {
            throw new PlanMalFormadoException("El cierre guardado no tiene la semana.");
        }
        return new CierreGuardado(semana.intValue(), revisiones(raiz, reglas));
    }

    static String normalizado(int semana, List<RevisionDelEje> revisiones) {
        ObjectNode raiz = PlanDeRocasJson.JSON.createObjectNode();
        raiz.put(CAMPO_SEMANA, semana);
        ArrayNode lista = raiz.putArray(CAMPO_EJES);
        for (RevisionDelEje revision : revisiones) {
            lista.addObject().put("eje", revision.eje()).put("autoevaluacion", revision.autoevaluacion())
                    .put("bloqueoPrincipal", revision.bloqueoPrincipal()).put("correccion", revision.correccion());
        }
        try {
            return PlanDeRocasJson.JSON.writeValueAsString(raiz);
        } catch (JsonProcessingException imposible) {
            throw new IllegalStateException("No se pudo escribir el cierre normalizado", imposible);
        }
    }

    private static JsonNode leer(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new PlanMalFormadoException("Falta el cierre.");
        }
        try {
            return PlanDeRocasJson.JSON.readTree(texto);
        } catch (JsonProcessingException noEsJson) {
            throw new PlanMalFormadoException("El cierre no es un JSON valido: mandalo como texto con el formato "
                    + "que indica la descripcion de la herramienta.");
        }
    }

    private static List<RevisionDelEje> revisiones(JsonNode raiz, ReglasDelCierre reglas) {
        List<RevisionDelEje> revisiones = new ArrayList<>();
        Set<String> ejesVistos = new HashSet<>();
        for (JsonNode nodo : PlanDeRocasJson.lista(raiz, CAMPO_EJES)) {
            String donde = "La revision " + (revisiones.size() + 1);
            JsonNode revision = PlanDeRocasJson.objeto(nodo, donde, CAMPOS_DE_REVISION);
            String eje = PlanDeRocasJson.eje(revision, donde, reglas.ejes());
            if (!ejesVistos.add(eje)) {
                throw new PlanMalFormadoException("El eje " + eje + " esta repetido: va una sola revision por eje.");
            }
            revisiones.add(new RevisionDelEje(eje, autoevaluacion(revision, donde, reglas),
                    PlanDeRocasJson.obligatorio(revision, "bloqueoPrincipal", donde),
                    PlanDeRocasJson.obligatorio(revision, "correccion", donde)));
        }
        return List.copyOf(revisiones);
    }

    private static int autoevaluacion(JsonNode revision, String donde, ReglasDelCierre reglas) {
        JsonNode valor = revision.get("autoevaluacion");
        boolean enEscala = valor != null && valor.isIntegralNumber() && valor.canConvertToInt()
                && valor.intValue() >= reglas.autoevaluacionMinima() && valor.intValue() <= reglas.autoevaluacionMaxima();
        if (!enEscala) {
            throw new PlanMalFormadoException(donde + ": 'autoevaluacion' tiene que ser un numero entero del "
                    + reglas.autoevaluacionMinima() + " al " + reglas.autoevaluacionMaxima() + ".");
        }
        return valor.intValue();
    }
}
