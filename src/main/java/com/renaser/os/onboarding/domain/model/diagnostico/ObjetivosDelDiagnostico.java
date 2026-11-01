package com.renaser.os.onboarding.domain.model.diagnostico;

import com.renaser.os.onboarding.domain.model.respuesta.Respuesta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Lo que nace de las respuestas del diagnóstico (Q14-B): un objetivo de 90 días por cada eje cuya pregunta «alimenta» tenga respuesta, cuál eje va primero
 * (la prioridad principal) y la acción sugerida (la primera acción). Un eje sin respuesta no trae objetivo: se escribe en Plan como si no hubiera diagnóstico.
 */
public record ObjetivosDelDiagnostico(List<ObjetivoPorEje> objetivos, EjeDelDiagnostico prioridad, String primeraAccion,
                                      String trato) {

    private static final List<EjeDelDiagnostico> ORDEN_BASE = List.of(EjeDelDiagnostico.CUERPO, EjeDelDiagnostico.TRABAJO,
            EjeDelDiagnostico.RELACIONES);

    public ObjetivosDelDiagnostico {
        objetivos = List.copyOf(objetivos);
    }

    public record ObjetivoPorEje(EjeDelDiagnostico eje, String texto) {
    }

    public static ObjetivosDelDiagnostico vacios() {
        return new ObjetivosDelDiagnostico(List.of(), null, null, null);
    }

    /** Solo cuentan las preguntas que se muestran (las ocultas por una condición no alimentan nada). */
    public static ObjetivosDelDiagnostico de(FichaDelDiagnostico ficha, Map<Integer, Respuesta> respuestas) {
        var visibles = ficha.visibles(respuestas);
        List<ObjetivoPorEje> porEje = new ArrayList<>();
        EjeDelDiagnostico prioridad = null;
        String primeraAccion = null;
        String trato = null;
        for (PreguntaDelDiagnostico p : ficha.preguntas()) {
            if (p.alimenta() == null || !visibles.contains(p.id())) {
                continue;
            }
            Respuesta r = respuestas.get(p.id());
            if (!ReglasDeRespuesta.estaContestada(p, r)) {
                continue;
            }
            switch (p.alimenta()) {
                case OBJETIVO_CUERPO, OBJETIVO_NEGOCIO, OBJETIVO_RELACIONES ->
                        porEje.add(new ObjetivoPorEje(p.alimenta().eje().orElseThrow(), r.valorTexto().trim()));
                case PRIORIDAD_PRINCIPAL -> prioridad = EjeDelDiagnostico.valueOf(r.valorTexto());
                case PRIMERA_ACCION -> primeraAccion = r.valorTexto().trim();
                case TRATO_DE_LA_PERSONA -> trato = r.valorTexto();
            }
        }
        return new ObjetivosDelDiagnostico(ordenados(porEje, prioridad), prioridad, primeraAccion, trato);
    }

    private static List<ObjetivoPorEje> ordenados(List<ObjetivoPorEje> porEje, EjeDelDiagnostico prioridad) {
        return porEje.stream().sorted(Comparator.comparingInt(o -> o.eje() == prioridad ? -1 : ORDEN_BASE.indexOf(o.eje()))).toList();
    }
}
