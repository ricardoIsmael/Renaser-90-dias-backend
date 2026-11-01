package com.renaser.os.onboarding.domain.model.diagnostico;

import com.renaser.os.onboarding.domain.model.respuesta.Respuesta;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
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

    /**
     * El objetivo de un eje: lo que escribió y, si respondió la parte medible, su meta. {@code meta} es {@code null} cuando falta alguna parte
     * (el objetivo vale en palabras, sin cifra del mes).
     */
    public record ObjetivoPorEje(EjeDelDiagnostico eje, String texto, MetaDeclarada meta) {

        public ObjetivoPorEje(EjeDelDiagnostico eje, String texto) {
            this(eje, texto, null);
        }
    }

    /**
     * La meta medible que declaró: a dónde quiere llegar, desde dónde parte y en qué unidad. Relaciones es una escala del 1 al 10: su unidad es
     * {@link #UNIDAD_DE_LA_ESCALA}, la regla que ya tenía el Mapa.
     */
    public record MetaDeclarada(BigDecimal meta, BigDecimal partida, String unidad) {
    }

    /** La unidad de la escala subjetiva de Relaciones. */
    public static final String UNIDAD_DE_LA_ESCALA = "/10";

    public static ObjetivosDelDiagnostico vacios() {
        return new ObjetivosDelDiagnostico(List.of(), null, null, null);
    }

    /** Solo cuentan las preguntas que se muestran (las ocultas por una condición no alimentan nada). */
    public static ObjetivosDelDiagnostico de(FichaDelDiagnostico ficha, Map<Integer, Respuesta> respuestas) {
        var visibles = ficha.visibles(respuestas);
        List<ObjetivoPorEje> porEje = new ArrayList<>();
        Map<EjeDelDiagnostico, PartesDeLaMeta> metas = new EnumMap<>(EjeDelDiagnostico.class);
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
                default -> p.alimenta().ejeDeLaMeta().ifPresent(eje -> metas.computeIfAbsent(eje, e -> new PartesDeLaMeta()).poner(p.alimenta(), r));
            }
        }
        List<ObjetivoPorEje> conMeta = porEje.stream()
                .map(o -> new ObjetivoPorEje(o.eje(), o.texto(), metas.containsKey(o.eje()) ? metas.get(o.eje()).completa(o.eje()) : null)).toList();
        return new ObjetivosDelDiagnostico(ordenados(conMeta, prioridad), prioridad, primeraAccion, trato);
    }

    /** Las tres respuestas de la meta de un eje, a medida que se leen. La meta solo existe si están todas las que ese eje pide. */
    private static final class PartesDeLaMeta {

        private BigDecimal meta;
        private BigDecimal partida;
        private String unidad;

        void poner(DestinoDeRespuesta destino, Respuesta r) {
            switch (destino.parteDeLaMeta().orElseThrow()) {
                case META -> meta = r.valorNumero();
                case PARTIDA -> partida = r.valorNumero();
                case UNIDAD -> unidad = r.valorTexto() == null ? null : r.valorTexto().trim();
            }
        }

        MetaDeclarada completa(EjeDelDiagnostico eje) {
            String unidadDelEje = eje == EjeDelDiagnostico.RELACIONES ? UNIDAD_DE_LA_ESCALA : unidad;
            if (meta == null || partida == null || unidadDelEje == null || unidadDelEje.isBlank()) {
                return null;
            }
            return new MetaDeclarada(meta, partida, unidadDelEje);
        }
    }

    private static List<ObjetivoPorEje> ordenados(List<ObjetivoPorEje> porEje, EjeDelDiagnostico prioridad) {
        return porEje.stream().sorted(Comparator.comparingInt(o -> o.eje() == prioridad ? -1 : ORDEN_BASE.indexOf(o.eje()))).toList();
    }
}
