package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.diagnostico;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.onboarding.domain.model.diagnostico.EscalaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.OpcionDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.PreguntaDelDiagnostico;
import com.renaser.os.shared.domain.Formacion;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Inserta y actualiza una pregunta del diagnóstico con sus opciones (SQL de escritura del borrador). */
final class EscritorDePreguntasDelDiagnostico {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcClient jdbc;

    EscritorDePreguntasDelDiagnostico(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Guarda la pregunta y reemplaza sus opciones; devuelve el id. */
    int guardar(PreguntaDelDiagnostico p) {
        int id = p.id() == 0 ? insertar(p) : actualizar(p);
        jdbc.sql("DELETE FROM renaser.opciones_pregunta WHERE pregunta_id = :id").param("id", id).update();
        int orden = 0;
        for (OpcionDelDiagnostico o : p.opciones()) {
            jdbc.sql("INSERT INTO renaser.opciones_pregunta (pregunta_id, orden, valor, etiqueta) VALUES (:id, :o, :v, :e)")
                    .param("id", id).param("o", orden++).param("v", o.valor()).param("e", o.etiqueta()).update();
        }
        return id;
    }

    private int insertar(PreguntaDelDiagnostico p) {
        String clave = claveDe(p.clave());
        int orden = p.orden() >= 0 ? p.orden() : jdbc.sql("SELECT COALESCE(MAX(orden), -1) + 1 FROM renaser.preguntas_onboarding WHERE seccion_id = :s AND retirada_en IS NULL")
                .param("s", p.seccionId()).query(Integer.class).single();
        try {
            return jdbc.sql("""
                            INSERT INTO renaser.preguntas_onboarding
                                (seccion_id, clave_pregunta, texto, tipo, config_escala, requerida, orden, pregunta_padre_id, ayuda, maximo_opciones,
                                 se_muestra_si_valor, alimenta, formaciones, precarga)
                            VALUES (:seccion, :clave, :texto, CAST(:tipo AS renaser.tipo_pregunta_onboarding), CAST(:escala AS jsonb), :requerida, :orden,
                                    :padre, :ayuda, :maximo, :valor, :alimenta, string_to_array(:formaciones, ','), :precarga)
                            RETURNING id
                            """)
                    .params(parametros(p, clave, orden)).query(Integer.class).single();
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException(mensajeDeDuplicado(p, e));
        }
    }

    private int actualizar(PreguntaDelDiagnostico p) {
        try {
            jdbc.sql("""
                            UPDATE renaser.preguntas_onboarding SET
                                texto = :texto, tipo = CAST(:tipo AS renaser.tipo_pregunta_onboarding), config_escala = CAST(:escala AS jsonb),
                                requerida = :requerida, pregunta_padre_id = :padre, ayuda = :ayuda, maximo_opciones = :maximo,
                                se_muestra_si_valor = :valor, alimenta = :alimenta, formaciones = string_to_array(:formaciones, ','), precarga = :precarga
                             WHERE id = :id
                            """)
                    .params(parametros(p, p.clave(), p.orden())).param("id", p.id()).update();
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException(mensajeDeDuplicado(p, e));
        }
        return p.id();
    }

    private static String mensajeDeDuplicado(PreguntaDelDiagnostico p, DuplicateKeyException e) {
        return p.alimenta() != null && String.valueOf(e.getMessage()).contains("preguntas_onb_alimenta_unica")
                ? "Ya hay otra pregunta que alimenta «" + p.alimenta().etiqueta() + "»: cada destino admite una sola"
                : "Ya existe una pregunta con la clave «" + p.clave() + "»";
    }

    private static Map<String, Object> parametros(PreguntaDelDiagnostico p, String clave, int orden) {
        Map<String, Object> m = new HashMap<>();
        m.put("seccion", p.seccionId());
        m.put("clave", clave);
        m.put("texto", p.texto());
        m.put("tipo", p.tipo().name());
        m.put("escala", p.escala() == null ? null : escalaComoJson(p.escala()));
        m.put("requerida", p.requerida());
        m.put("orden", orden);
        m.put("padre", p.condicion() == null ? null : p.condicion().preguntaId());
        m.put("valor", p.condicion() == null ? null : p.condicion().valor());
        m.put("ayuda", p.ayuda());
        m.put("maximo", p.maximoOpciones() == null ? null : p.maximoOpciones().shortValue());
        m.put("alimenta", p.alimenta() == null ? null : p.alimenta().name());
        m.put("formaciones", p.formaciones().isEmpty() ? null
                : p.formaciones().stream().map(Formacion::codigo).sorted().collect(Collectors.joining(",")));
        m.put("precarga", p.precarga());
        return m;
    }

    private static String claveDe(String clave) {
        if (clave == null || clave.isBlank()) {
            return "diag_" + UUID.randomUUID().toString().substring(0, 8);
        }
        return clave.startsWith("diag_") ? clave : "diag_" + clave;
    }

    private static String escalaComoJson(EscalaDelDiagnostico e) {
        var nodo = JSON.createObjectNode().put("min", e.min()).put("max", e.max());
        if (e.etiquetaMin() != null) {
            nodo.put("etiquetaMin", e.etiquetaMin());
        }
        if (e.etiquetaMax() != null) {
            nodo.put("etiquetaMax", e.etiquetaMax());
        }
        return nodo.toString();
    }
}
