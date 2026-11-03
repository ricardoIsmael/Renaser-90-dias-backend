package com.renaser.os.onboarding.domain.model.diagnostico;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Qué alimenta una respuesta del diagnóstico («Alimenta» en el editor). Cada destino admite una sola pregunta activa (índice único de la V144).
 * {@code etiqueta()} es como lo escribe Administración en la plantilla de importación y como lo lee en el editor.
 */
public enum DestinoDeRespuesta {

    OBJETIVO_CUERPO("Objetivo de Cuerpo", EjeDelDiagnostico.CUERPO),
    OBJETIVO_NEGOCIO("Objetivo de Negocio", EjeDelDiagnostico.TRABAJO),
    OBJETIVO_RELACIONES("Objetivo de Relaciones", EjeDelDiagnostico.RELACIONES),
    PRIORIDAD_PRINCIPAL("Prioridad principal", null),
    PRIMERA_ACCION("Primera acción", null),
    TRATO_DE_LA_PERSONA("Trato de la persona", null),

    /* La parte medible del objetivo de cada eje (D-303, pedido del dueño del 2026-10-31): la meta, desde dónde parte y, donde la unidad no es obvia, en qué
       se mide. Con ellas el plan mensual saca la cifra del mes y los hitos; sin ellas el objetivo vale en palabras, como siempre. Relaciones es una escala
       del 1 al 10 y no pregunta la unidad. */
    META_CUERPO("Meta de Cuerpo", null, EjeDelDiagnostico.CUERPO, ParteDeLaMeta.META),
    UNIDAD_CUERPO("Unidad de Cuerpo", null, EjeDelDiagnostico.CUERPO, ParteDeLaMeta.UNIDAD),
    PARTIDA_CUERPO("Punto de partida de Cuerpo", null, EjeDelDiagnostico.CUERPO, ParteDeLaMeta.PARTIDA),
    META_NEGOCIO("Meta de Negocio", null, EjeDelDiagnostico.TRABAJO, ParteDeLaMeta.META),
    UNIDAD_NEGOCIO("Unidad de Negocio", null, EjeDelDiagnostico.TRABAJO, ParteDeLaMeta.UNIDAD),
    PARTIDA_NEGOCIO("Punto de partida de Negocio", null, EjeDelDiagnostico.TRABAJO, ParteDeLaMeta.PARTIDA),
    META_RELACIONES("Meta de Relaciones", null, EjeDelDiagnostico.RELACIONES, ParteDeLaMeta.META),
    PARTIDA_RELACIONES("Punto de partida de Relaciones", null, EjeDelDiagnostico.RELACIONES, ParteDeLaMeta.PARTIDA);

    /** Qué parte de la meta medible de un objetivo es la respuesta. */
    public enum ParteDeLaMeta {
        META,
        UNIDAD,
        PARTIDA
    }

    /** Los valores que puede tener la opción de la prioridad principal (el eje al que corresponde cada una). */
    public static final Set<String> VALORES_DE_LA_PRIORIDAD = Set.of("CUERPO", "TRABAJO", "RELACIONES");
    /** Los valores que puede tener la opción del trato. */
    public static final Set<String> VALORES_DEL_TRATO = Set.of("MASCULINO", "FEMENINO", "NEUTRO");

    private final String etiqueta;
    private final EjeDelDiagnostico eje;
    private final EjeDelDiagnostico ejeDeLaMeta;
    private final ParteDeLaMeta parteDeLaMeta;

    DestinoDeRespuesta(String etiqueta, EjeDelDiagnostico eje) {
        this(etiqueta, eje, null, null);
    }

    DestinoDeRespuesta(String etiqueta, EjeDelDiagnostico eje, EjeDelDiagnostico ejeDeLaMeta, ParteDeLaMeta parteDeLaMeta) {
        this.etiqueta = etiqueta;
        this.eje = eje;
        this.ejeDeLaMeta = ejeDeLaMeta;
        this.parteDeLaMeta = parteDeLaMeta;
    }

    public String etiqueta() {
        return etiqueta;
    }

    /** El eje del objetivo que alimenta; vacío si no alimenta un objetivo. */
    public Optional<EjeDelDiagnostico> eje() {
        return Optional.ofNullable(eje);
    }

    /** Es el texto del objetivo de un eje (no una parte de su meta medible). */
    public boolean esObjetivo() {
        return eje != null;
    }

    /** Es una parte de la meta medible de un objetivo: la meta, la unidad o el punto de partida. */
    public boolean esParteDeUnaMeta() {
        return parteDeLaMeta != null;
    }

    /** El eje de cuya meta es parte; vacío si no es parte de una meta. */
    public Optional<EjeDelDiagnostico> ejeDeLaMeta() {
        return Optional.ofNullable(ejeDeLaMeta);
    }

    public Optional<ParteDeLaMeta> parteDeLaMeta() {
        return Optional.ofNullable(parteDeLaMeta);
    }

    /** Las opciones de esta pregunta llevan un código fijo en lugar de su etiqueta. */
    public Optional<Set<String>> valoresPermitidos() {
        return switch (this) {
            case PRIORIDAD_PRINCIPAL -> Optional.of(VALORES_DE_LA_PRIORIDAD);
            case TRATO_DE_LA_PERSONA -> Optional.of(VALORES_DEL_TRATO);
            default -> Optional.empty();
        };
    }

    /** De lo que escribe Administración: el nombre del destino sin tildes ni mayúsculas, con o sin lo que va entre paréntesis. */
    public static Optional<DestinoDeRespuesta> desdeEtiqueta(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        String buscado = plano(texto.replaceAll("\\(.*\\)", ""));
        return Arrays.stream(values()).filter(d -> plano(d.etiqueta).equals(buscado)).findFirst();
    }

    private static String plano(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim()
                .replaceAll("\\s+", " ");
    }
}
