package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.diagnostico;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.renaser.os.onboarding.application.ports.out.diagnostico.VersionesDeFichaPort;
import com.renaser.os.onboarding.domain.model.cuestionario.TipoPreguntaOnboarding;
import com.renaser.os.onboarding.domain.model.diagnostico.CondicionDeVisibilidad;
import com.renaser.os.onboarding.domain.model.diagnostico.DestinoDeRespuesta;
import com.renaser.os.onboarding.domain.model.diagnostico.Diagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.EscalaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.FichaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.OpcionDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.PreguntaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.SeccionDelDiagnostico;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Las fotos publicadas del diagnóstico ({@code fichas_publicadas}). El documento se arma y se lee a mano (sin mapear los records): el formato de una versión ya
 * publicada no puede cambiar por renombrar un campo de Java, porque las personas que la empezaron la siguen leyendo.
 */
@Component
class VersionesDeFichaJdbcAdapter implements VersionesDeFichaPort {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcClient jdbc;

    VersionesDeFichaJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<VersionPublicada> ultima() {
        return desdeSql("ORDER BY f.version DESC LIMIT 1", null).stream().findFirst();
    }

    @Override
    public Optional<VersionPublicada> version(int version) {
        return desdeSql("AND f.version = :version", version).stream().findFirst();
    }

    @Override
    public List<VersionPublicada> listar() {
        return desdeSql("ORDER BY f.version DESC", null);
    }

    @Override
    public VersionPublicada publicar(FichaDelDiagnostico ficha, UserId por, Instant ahora) {
        try {
            int version = jdbc.sql("""
                            INSERT INTO renaser.fichas_publicadas (flujo, version, contenido, publicada_por, publicada_en)
                            SELECT :f, COALESCE(MAX(version), 0) + 1, CAST(:c AS jsonb), :por, :en
                              FROM renaser.fichas_publicadas WHERE flujo = :f
                            RETURNING version
                            """)
                    .param("f", Diagnostico.FLUJO).param("c", aTexto(ficha)).param("por", por.value()).param("en", Timestamp.from(ahora))
                    .query(Integer.class).single();
            return version(version).orElseThrow();
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("Otra persona publicó cambios mientras editabas");
        }
    }

    private List<VersionPublicada> desdeSql(String final_, Integer version) {
        String where = final_.startsWith("AND") ? " " + final_ : "";
        String order = final_.startsWith("AND") ? "" : " " + final_;
        var consulta = jdbc.sql("""
                SELECT f.version, f.contenido::text AS contenido, f.publicada_por, u.nombre_completo AS nombre, f.publicada_en
                  FROM renaser.fichas_publicadas f LEFT JOIN renaser.usuarios u ON u.id = f.publicada_por
                 WHERE f.flujo = :flujo""" + where + order).param("flujo", Diagnostico.FLUJO);
        if (version != null) {
            consulta = consulta.param("version", version);
        }
        return consulta.query((rs, n) -> {
            UUID por = rs.getObject("publicada_por", UUID.class);
            return new VersionPublicada(rs.getInt("version"), deTexto(rs.getString("contenido")), por == null ? null : UserId.of(por),
                    rs.getString("nombre"), rs.getTimestamp("publicada_en").toInstant());
        }).list();
    }

    // ---------------------------------------------------------------------------------------------- el documento

    static String aTexto(FichaDelDiagnostico ficha) {
        ObjectNode raiz = JSON.createObjectNode();
        ArrayNode secciones = raiz.putArray("secciones");
        for (SeccionDelDiagnostico s : ficha.secciones()) {
            ObjectNode sn = secciones.addObject().put("id", s.id()).put("clave", s.clave()).put("titulo", s.titulo()).put("orden", s.orden());
            sn.put("descripcion", s.descripcion());
            ArrayNode preguntas = sn.putArray("preguntas");
            s.preguntas().forEach(p -> preguntas.add(pregunta(p)));
        }
        return raiz.toString();
    }

    private static ObjectNode pregunta(PreguntaDelDiagnostico p) {
        ObjectNode n = JSON.createObjectNode().put("id", p.id()).put("clave", p.clave()).put("seccionId", p.seccionId()).put("orden", p.orden());
        n.put("texto", p.texto()).put("ayuda", p.ayuda()).put("tipo", p.tipo().name()).put("requerida", p.requerida());
        if (p.maximoOpciones() != null) {
            n.put("maximoOpciones", p.maximoOpciones());
        }
        ArrayNode opciones = n.putArray("opciones");
        p.opciones().forEach(o -> opciones.addObject().put("valor", o.valor()).put("etiqueta", o.etiqueta()));
        if (p.escala() != null) {
            ObjectNode e = n.putObject("escala").put("min", p.escala().min()).put("max", p.escala().max());
            e.put("etiquetaMin", p.escala().etiquetaMin()).put("etiquetaMax", p.escala().etiquetaMax());
        }
        if (p.condicion() != null) {
            n.putObject("condicion").put("preguntaId", p.condicion().preguntaId()).put("valor", p.condicion().valor());
        }
        if (p.alimenta() != null) {
            n.put("alimenta", p.alimenta().name());
        }
        ArrayNode formaciones = n.putArray("formaciones");
        p.formaciones().stream().map(Formacion::codigo).sorted().forEach(formaciones::add);
        n.put("precarga", p.precarga());
        return n;
    }

    static FichaDelDiagnostico deTexto(String json) {
        try {
            JsonNode raiz = JSON.readTree(json);
            List<SeccionDelDiagnostico> secciones = new ArrayList<>();
            for (JsonNode sn : raiz.withArray("secciones")) {
                List<PreguntaDelDiagnostico> preguntas = new ArrayList<>();
                for (JsonNode pn : sn.withArray("preguntas")) {
                    preguntas.add(pregunta(pn));
                }
                secciones.add(new SeccionDelDiagnostico(sn.get("id").asInt(), sn.get("clave").asText(), sn.get("titulo").asText(),
                        texto(sn, "descripcion"), sn.get("orden").asInt(), preguntas));
            }
            return new FichaDelDiagnostico(secciones);
        } catch (Exception e) {
            throw new IllegalStateException("La versión publicada del diagnóstico no se pudo leer", e);
        }
    }

    private static PreguntaDelDiagnostico pregunta(JsonNode n) {
        List<OpcionDelDiagnostico> opciones = new ArrayList<>();
        n.withArray("opciones").forEach(o -> opciones.add(new OpcionDelDiagnostico(o.get("valor").asText(), o.get("etiqueta").asText())));
        JsonNode e = n.get("escala");
        EscalaDelDiagnostico escala = e == null || e.isNull() ? null
                : new EscalaDelDiagnostico(e.get("min").asInt(), e.get("max").asInt(), texto(e, "etiquetaMin"), texto(e, "etiquetaMax"));
        JsonNode c = n.get("condicion");
        CondicionDeVisibilidad condicion = c == null || c.isNull() ? null : new CondicionDeVisibilidad(c.get("preguntaId").asInt(), c.get("valor").asText());
        Set<Formacion> formaciones = EnumSet.noneOf(Formacion.class);
        n.withArray("formaciones").forEach(f -> formaciones.add(Formacion.desde(f.asText())));
        String alimenta = texto(n, "alimenta");
        return new PreguntaDelDiagnostico(n.get("id").asInt(), texto(n, "clave"), n.get("seccionId").asInt(), n.get("orden").asInt(), n.get("texto").asText(),
                texto(n, "ayuda"), TipoPreguntaOnboarding.valueOf(n.get("tipo").asText()), n.get("requerida").asBoolean(),
                n.hasNonNull("maximoOpciones") ? n.get("maximoOpciones").asInt() : null, opciones, escala, condicion,
                alimenta == null ? null : DestinoDeRespuesta.valueOf(alimenta), formaciones, texto(n, "precarga"), null);
    }

    private static String texto(JsonNode n, String campo) {
        return n.hasNonNull(campo) ? n.get(campo).asText() : null;
    }
}
