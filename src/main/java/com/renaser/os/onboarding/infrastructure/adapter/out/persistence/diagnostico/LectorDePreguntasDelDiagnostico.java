package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.diagnostico;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.onboarding.domain.model.cuestionario.TipoPreguntaOnboarding;
import com.renaser.os.onboarding.domain.model.diagnostico.CondicionDeVisibilidad;
import com.renaser.os.onboarding.domain.model.diagnostico.DestinoDeRespuesta;
import com.renaser.os.onboarding.domain.model.diagnostico.Diagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.EscalaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.OpcionDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.PreguntaDelDiagnostico;
import com.renaser.os.shared.domain.Formacion;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lee las preguntas del flujo {@code diagnostico} con sus opciones (SQL de lectura del borrador). */
final class LectorDePreguntasDelDiagnostico {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String COLUMNAS_DE_PREGUNTA = """
            p.id, p.seccion_id, p.clave_pregunta, p.orden, p.texto, p.ayuda, p.tipo::text AS tipo, p.requerida, p.maximo_opciones,
            p.config_escala::text AS config_escala, p.pregunta_padre_id, p.se_muestra_si_valor, p.alimenta,
            array_to_string(p.formaciones, ',') AS formaciones, p.precarga, p.retirada_en
            """;

    private final JdbcClient jdbc;

    LectorDePreguntasDelDiagnostico(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Las preguntas del flujo que cumplen el filtro SQL (sobre el alias {@code p}), en el orden de la ficha. */
    List<PreguntaDelDiagnostico> preguntas(String filtro, Map<String, Object> parametros) {
        var consulta = jdbc.sql("SELECT " + COLUMNAS_DE_PREGUNTA + """
                  FROM renaser.preguntas_onboarding p
                  JOIN renaser.secciones_onboarding s ON s.id = p.seccion_id
                 WHERE s.flujo = :flujo AND\s""" + filtro + " ORDER BY s.orden, s.id, p.orden, p.id")
                .param("flujo", Diagnostico.FLUJO);
        for (Map.Entry<String, Object> e : parametros.entrySet()) {
            consulta = consulta.param(e.getKey(), e.getValue());
        }
        List<FilaDePregunta> filas = consulta.query((rs, n) -> FilaDePregunta.de(rs)).list();
        Map<Integer, List<OpcionDelDiagnostico>> opciones = opcionesDe(filas.stream().map(FilaDePregunta::id).toList());
        return filas.stream().map(f -> f.aDominio(opciones.getOrDefault(f.id(), List.of()))).toList();
    }

    private Map<Integer, List<OpcionDelDiagnostico>> opcionesDe(List<Integer> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Integer, List<OpcionDelDiagnostico>> opciones = new LinkedHashMap<>();
        jdbc.sql("SELECT pregunta_id, valor, etiqueta FROM renaser.opciones_pregunta WHERE pregunta_id IN (:ids) ORDER BY pregunta_id, orden")
                .param("ids", ids)
                .query((rs, n) -> {
                    opciones.computeIfAbsent(rs.getInt("pregunta_id"), k -> new ArrayList<>())
                            .add(new OpcionDelDiagnostico(rs.getString("valor"), rs.getString("etiqueta")));
                    return null;
                }).list();
        return opciones;
    }

    private record FilaDePregunta(int id, int seccionId, String clave, int orden, String texto, String ayuda, String tipo, boolean requerida,
                                  Integer maximo, String escala, Integer padre, String valorCondicion, String alimenta, String formaciones,
                                  String precarga, Instant retiradaEn) {

        static FilaDePregunta de(ResultSet rs) throws SQLException {
            Timestamp retirada = rs.getTimestamp("retirada_en");
            return new FilaDePregunta(rs.getInt("id"), rs.getInt("seccion_id"), rs.getString("clave_pregunta"), rs.getInt("orden"),
                    rs.getString("texto"), rs.getString("ayuda"), rs.getString("tipo"), rs.getBoolean("requerida"),
                    (Integer) rs.getObject("maximo_opciones"), rs.getString("config_escala"), (Integer) rs.getObject("pregunta_padre_id"),
                    rs.getString("se_muestra_si_valor"), rs.getString("alimenta"), rs.getString("formaciones"), rs.getString("precarga"),
                    retirada == null ? null : retirada.toInstant());
        }

        PreguntaDelDiagnostico aDominio(List<OpcionDelDiagnostico> opciones) {
            Set<Formacion> delasFormaciones = EnumSet.noneOf(Formacion.class);
            if (formaciones != null && !formaciones.isBlank()) {
                for (String codigo : formaciones.split(",")) {
                    delasFormaciones.add(Formacion.desde(codigo));
                }
            }
            return new PreguntaDelDiagnostico(id, clave, seccionId, orden, texto, ayuda, TipoPreguntaOnboarding.valueOf(tipo), requerida, maximo,
                    opciones, escalaDesdeJson(escala),
                    padre != null && valorCondicion != null ? new CondicionDeVisibilidad(padre, valorCondicion) : null,
                    alimenta == null ? null : DestinoDeRespuesta.valueOf(alimenta), delasFormaciones, precarga, retiradaEn);
        }
    }

    private static EscalaDelDiagnostico escalaDesdeJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode n = JSON.readTree(json);
            if (!n.hasNonNull("min") || !n.hasNonNull("max")) {
                return null;
            }
            return new EscalaDelDiagnostico(n.get("min").asInt(), n.get("max").asInt(), textoOnulo(n, "etiquetaMin"), textoOnulo(n, "etiquetaMax"));
        } catch (Exception e) {
            return null;
        }
    }

    private static String textoOnulo(JsonNode n, String campo) {
        return n.hasNonNull(campo) ? n.get(campo).asText() : null;
    }
}
