package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.diagnostico;

import com.renaser.os.onboarding.application.ports.out.diagnostico.BorradorDelDiagnosticoPort;
import com.renaser.os.onboarding.domain.model.diagnostico.Diagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.FichaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.PreguntaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.SeccionDelDiagnostico;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * El borrador del diagnóstico sobre las tablas del motor de onboarding, con {@code JdbcClient}: el catálogo ya existe y las consultas (arreglos, jsonb, conteos)
 * son más claras en SQL que en entidades. Solo toca filas del flujo {@code diagnostico}.
 */
@Component
class BorradorDelDiagnosticoJdbcAdapter implements BorradorDelDiagnosticoPort {

    /** La sección oculta donde quedan las preguntas retiradas cuando se borra la sección de la que eran. */
    static final String CLAVE_DE_LAS_RETIRADAS = "retiradas";

    private final JdbcClient jdbc;
    private final LectorDePreguntasDelDiagnostico lector;
    private final EscritorDePreguntasDelDiagnostico escritor;

    BorradorDelDiagnosticoJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
        this.lector = new LectorDePreguntasDelDiagnostico(jdbc);
        this.escritor = new EscritorDePreguntasDelDiagnostico(jdbc);
    }

    // ------------------------------------------------------------------------------------------------ lectura

    @Override
    public FichaDelDiagnostico cargar() {
        List<FilaDeSeccion> secciones = jdbc.sql("""
                        SELECT id, clave_seccion, titulo, descripcion, orden FROM renaser.secciones_onboarding
                         WHERE flujo = :flujo AND clave_seccion <> :retiradas ORDER BY orden, id
                        """)
                .param("flujo", Diagnostico.FLUJO).param("retiradas", CLAVE_DE_LAS_RETIRADAS)
                .query((rs, n) -> new FilaDeSeccion(rs.getInt("id"), rs.getString("clave_seccion"), rs.getString("titulo"),
                        rs.getString("descripcion"), rs.getInt("orden")))
                .list();
        Map<Integer, List<PreguntaDelDiagnostico>> porSeccion = new HashMap<>();
        for (PreguntaDelDiagnostico p : lector.preguntas("p.retirada_en IS NULL", Map.of())) {
            porSeccion.computeIfAbsent(p.seccionId(), k -> new ArrayList<>()).add(p);
        }
        return new FichaDelDiagnostico(secciones.stream()
                .map(s -> new SeccionDelDiagnostico(s.id(), s.clave(), s.titulo(), s.descripcion(), s.orden(),
                        porSeccion.getOrDefault(s.id(), List.of())))
                .toList());
    }

    @Override
    public List<PreguntaDelDiagnostico> retiradas() {
        return lector.preguntas("p.retirada_en IS NOT NULL", Map.of()).stream()
                .sorted((a, b) -> b.retiradaEn().compareTo(a.retiradaEn())).toList();
    }

    @Override
    public Optional<PreguntaDelDiagnostico> pregunta(int id) {
        return lector.preguntas("p.id = :id", Map.of("id", id)).stream().findFirst();
    }

    @Override
    public Optional<SeccionDelDiagnostico> seccion(int id) {
        return cargar().secciones().stream().filter(s -> s.id() == id).findFirst();
    }

    // ---------------------------------------------------------------------------------------------- secciones

    @Override
    public SeccionDelDiagnostico crearSeccion(String clave, String titulo, String descripcion) {
        int orden = jdbc.sql("SELECT COALESCE(MAX(orden), -1) + 1 FROM renaser.secciones_onboarding WHERE flujo = :f AND clave_seccion <> :r")
                .param("f", Diagnostico.FLUJO).param("r", CLAVE_DE_LAS_RETIRADAS).query(Integer.class).single();
        int id = jdbc.sql("""
                        INSERT INTO renaser.secciones_onboarding (flujo, clave_seccion, titulo, descripcion, orden)
                        VALUES (:f, :c, :t, :d, :o) RETURNING id
                        """)
                .param("f", Diagnostico.FLUJO).param("c", clave).param("t", titulo).param("d", descripcion).param("o", orden)
                .query(Integer.class).single();
        return new SeccionDelDiagnostico(id, clave, titulo, descripcion, orden, List.of());
    }

    @Override
    public void actualizarSeccion(int id, String titulo, String descripcion) {
        jdbc.sql("UPDATE renaser.secciones_onboarding SET titulo = :t, descripcion = :d WHERE id = :id AND flujo = :f")
                .param("t", titulo).param("d", descripcion).param("id", id).param("f", Diagnostico.FLUJO).update();
    }

    @Override
    public void eliminarSeccion(int id) {
        int retiradasDeLaSeccion = jdbc.sql("SELECT count(*) FROM renaser.preguntas_onboarding WHERE seccion_id = :id")
                .param("id", id).query(Integer.class).single();
        if (retiradasDeLaSeccion > 0) {
            int destino = seccionDeLasRetiradas();
            jdbc.sql("UPDATE renaser.preguntas_onboarding SET seccion_id = :d WHERE seccion_id = :id")
                    .param("d", destino).param("id", id).update();
        }
        jdbc.sql("DELETE FROM renaser.secciones_onboarding WHERE id = :id AND flujo = :f").param("id", id).param("f", Diagnostico.FLUJO).update();
    }

    private int seccionDeLasRetiradas() {
        return jdbc.sql("SELECT id FROM renaser.secciones_onboarding WHERE flujo = :f AND clave_seccion = :c")
                .param("f", Diagnostico.FLUJO).param("c", CLAVE_DE_LAS_RETIRADAS).query(Integer.class).optional()
                .orElseGet(() -> jdbc.sql("""
                                INSERT INTO renaser.secciones_onboarding (flujo, clave_seccion, titulo, descripcion, orden)
                                VALUES (:f, :c, 'Preguntas retiradas', NULL, 9999) RETURNING id
                                """)
                        .param("f", Diagnostico.FLUJO).param("c", CLAVE_DE_LAS_RETIRADAS).query(Integer.class).single());
    }

    @Override
    public void ordenarSecciones(List<Integer> ids) {
        for (int i = 0; i < ids.size(); i++) {
            jdbc.sql("UPDATE renaser.secciones_onboarding SET orden = :o WHERE id = :id AND flujo = :f")
                    .param("o", i).param("id", ids.get(i)).param("f", Diagnostico.FLUJO).update();
        }
    }

    // ----------------------------------------------------------------------------------------------- preguntas

    @Override
    public PreguntaDelDiagnostico guardarPregunta(PreguntaDelDiagnostico p) {
        return pregunta(escritor.guardar(p)).orElseThrow();
    }

    @Override
    public void moverPregunta(int id, int seccionId, int posicion) {
        List<Integer> ids = new ArrayList<>(jdbc.sql("""
                        SELECT id FROM renaser.preguntas_onboarding WHERE seccion_id = :s AND retirada_en IS NULL AND id <> :id ORDER BY orden, id
                        """)
                .param("s", seccionId).param("id", id).query(Integer.class).list());
        ids.add(Math.max(0, Math.min(posicion, ids.size())), id);
        for (int i = 0; i < ids.size(); i++) {
            jdbc.sql("UPDATE renaser.preguntas_onboarding SET seccion_id = :s, orden = :o WHERE id = :id")
                    .param("s", seccionId).param("o", i).param("id", ids.get(i)).update();
        }
    }

    @Override
    public void eliminarPreguntas(Collection<Integer> ids) {
        if (!ids.isEmpty()) {
            jdbc.sql("DELETE FROM renaser.preguntas_onboarding WHERE id IN (:ids)").param("ids", ids).update();
        }
    }

    @Override
    public void retirarPreguntas(Collection<Integer> ids, Instant ahora) {
        if (!ids.isEmpty()) {
            jdbc.sql("UPDATE renaser.preguntas_onboarding SET retirada_en = :t WHERE id IN (:ids)")
                    .param("t", Timestamp.from(ahora)).param("ids", ids).update();
        }
    }

    @Override
    public void liberarClaves(Collection<Integer> ids) {
        if (!ids.isEmpty()) {
            jdbc.sql("UPDATE renaser.preguntas_onboarding SET clave_pregunta = clave_pregunta || '#' || id WHERE id IN (:ids) AND clave_pregunta NOT LIKE '%#%'").param("ids", ids).update();
        }
    }

    @Override
    public void restaurarPregunta(int id, int seccionId) {
        try {
            jdbc.sql("""
                            UPDATE renaser.preguntas_onboarding SET retirada_en = NULL, seccion_id = :s,
                                   orden = (SELECT COALESCE(MAX(orden), -1) + 1 FROM renaser.preguntas_onboarding WHERE seccion_id = :s AND retirada_en IS NULL)
                             WHERE id = :id
                            """)
                    .param("s", seccionId).param("id", id).update();
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("Otra pregunta ya alimenta lo mismo que ésta: quita primero su destino");
        }
    }

    // ------------------------------------------------------------------------------------------------- conteos

    @Override
    public Map<Integer, Integer> respuestasPorPregunta() {
        Map<Integer, Integer> cuentas = new HashMap<>();
        jdbc.sql("""
                        SELECT r.pregunta_id, count(*) AS n FROM renaser.respuestas_onboarding r
                          JOIN renaser.preguntas_onboarding p ON p.id = r.pregunta_id
                          JOIN renaser.secciones_onboarding s ON s.id = p.seccion_id
                         WHERE s.flujo = :f GROUP BY r.pregunta_id
                        """)
                .param("f", Diagnostico.FLUJO)
                .query((rs, n) -> {
                    cuentas.put(rs.getInt("pregunta_id"), rs.getInt("n"));
                    return null;
                }).list();
        return cuentas;
    }

    @Override
    public Set<Integer> publicadasAlgunaVez(Collection<Integer> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        String lista = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        return Set.copyOf(jdbc.sql("""
                        SELECT DISTINCT q.id FROM unnest(string_to_array(:ids, ',')::int[]) AS q(id)
                         WHERE EXISTS (SELECT 1 FROM renaser.fichas_publicadas f
                                        WHERE f.flujo = :f
                                          AND jsonb_path_exists(f.contenido, '$.secciones[*].preguntas[*] ? (@.id == $id)', jsonb_build_object('id', q.id)))
                        """)
                .param("ids", lista).param("f", Diagnostico.FLUJO).query(Integer.class).list());
    }
}
