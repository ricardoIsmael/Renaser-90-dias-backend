package com.renaser.os.community.infrastructure.adapter.out.persistence.sobrenombre;

import com.renaser.os.community.application.ports.out.sobrenombre.SobrenombresPort;
import com.renaser.os.community.domain.model.sobrenombre.Sobrenombre;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Spec 046: las tablas del sobrenombre (V181). */
@Component
class SobrenombresJdbcAdapter implements SobrenombresPort {

    /** El nombre real normalizado como `Sobrenombre.normalizar`: sin tildes, en minúsculas y con los espacios juntos. */
    private static final String NOMBRE_NORMALIZADO =
            "regexp_replace(lower(translate(btrim(u.nombre_completo), 'ÁÉÍÓÚÜÑáéíóúüñÀÈÌÒÙàèìòù', 'AEIOUUNaeiouunAEIOUaeiou')), '\\s+', ' ', 'g')";

    private final JdbcClient jdbc;

    SobrenombresJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<FilaDeSobrenombre> de(UserId persona) {
        return jdbc.sql("""
                        SELECT sobrenombre, elegido_en, cambiado_por_la_persona_en, invitacion_descartada_en
                          FROM renaser.sobrenombres WHERE usuario_id = :id
                        """)
                .param("id", persona.value())
                .query((f, n) -> new FilaDeSobrenombre(f.getString("sobrenombre"), instante(f.getTimestamp("elegido_en")),
                        instante(f.getTimestamp("cambiado_por_la_persona_en")), instante(f.getTimestamp("invitacion_descartada_en"))))
                .optional();
    }

    @Override
    public Map<UserId, String> sobrenombresDe(Collection<UserId> personas) {
        if (personas.isEmpty()) {
            return Map.of();
        }
        return jdbc.sql("SELECT usuario_id, sobrenombre FROM renaser.sobrenombres WHERE usuario_id IN (:ids) AND sobrenombre IS NOT NULL")
                .param("ids", personas.stream().map(UserId::value).distinct().toList())
                .query((f, n) -> Map.entry(UserId.of(f.getObject("usuario_id", UUID.class)), f.getString("sobrenombre")))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));
    }

    @Override
    public List<UserId> conElMismo(String normalizado, UserId excepto) {
        return jdbc.sql("SELECT usuario_id FROM renaser.sobrenombres WHERE normalizado = :n AND usuario_id <> :yo")
                .param("n", normalizado)
                .param("yo", excepto.value())
                .query((f, n) -> UserId.of(f.getObject("usuario_id", UUID.class)))
                .list();
    }

    @Override
    public boolean esNombreRealDeOtra(String normalizado, UserId excepto) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM renaser.usuarios u WHERE u.id <> :yo AND " + NOMBRE_NORMALIZADO + " = :n)")
                .param("n", normalizado)
                .param("yo", excepto.value())
                .query(Boolean.class)
                .single();
    }

    @Override
    public void bloquear(String normalizado) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtext('sobrenombre:' || :n))").param("n", normalizado).query().singleRow();
    }

    @Override
    public void guardar(UserId persona, Sobrenombre sobrenombre, Instant ahora, boolean porLaPersona) {
        jdbc.sql("""
                        INSERT INTO renaser.sobrenombres (usuario_id, sobrenombre, normalizado, elegido_en, cambiado_por_la_persona_en, actualizado_en)
                        VALUES (:id, :texto, :norm, :elegido, :porLaPersona, :ahora)
                        ON CONFLICT (usuario_id) DO UPDATE SET
                            sobrenombre = EXCLUDED.sobrenombre,
                            normalizado = EXCLUDED.normalizado,
                            elegido_en = EXCLUDED.elegido_en,
                            cambiado_por_la_persona_en = COALESCE(EXCLUDED.cambiado_por_la_persona_en,
                                                                  renaser.sobrenombres.cambiado_por_la_persona_en),
                            actualizado_en = EXCLUDED.actualizado_en
                        """)
                .param("id", persona.value())
                .param("texto", sobrenombre == null ? null : sobrenombre.texto())
                .param("norm", sobrenombre == null ? null : sobrenombre.normalizado())
                .param("elegido", sobrenombre == null ? null : Timestamp.from(ahora))
                .param("porLaPersona", porLaPersona ? Timestamp.from(ahora) : null)
                .param("ahora", Timestamp.from(ahora))
                .update();
    }

    @Override
    public void descartarInvitacion(UserId persona, Instant ahora) {
        jdbc.sql("""
                        INSERT INTO renaser.sobrenombres (usuario_id, invitacion_descartada_en, actualizado_en) VALUES (:id, :ahora, :ahora)
                        ON CONFLICT (usuario_id) DO UPDATE SET
                            invitacion_descartada_en = COALESCE(renaser.sobrenombres.invitacion_descartada_en, EXCLUDED.invitacion_descartada_en)
                        """)
                .param("id", persona.value())
                .param("ahora", Timestamp.from(ahora))
                .update();
    }

    @Override
    public void anotarCambio(UserId persona, String antes, String despues, UserId por, String motivo, Instant en) {
        jdbc.sql("""
                        INSERT INTO renaser.sobrenombres_cambios (usuario_id, antes, despues, por, motivo, en)
                        VALUES (:id, :antes, :despues, :por, :motivo, :en)
                        """)
                .param("id", persona.value())
                .param("antes", antes)
                .param("despues", despues)
                .param("por", por == null ? null : por.value())
                .param("motivo", motivo)
                .param("en", Timestamp.from(en))
                .update();
    }

    @Override
    public List<String> palabrasDeAdministracion() {
        return jdbc.sql("SELECT palabra FROM renaser.palabras_prohibidas_sobrenombre ORDER BY palabra").query(String.class).list();
    }

    @Override
    public void agregarPalabra(String palabraNormalizada, UserId por, Instant en) {
        jdbc.sql("""
                        INSERT INTO renaser.palabras_prohibidas_sobrenombre (palabra, agregada_por, agregada_en) VALUES (:p, :por, :en)
                        ON CONFLICT (palabra) DO NOTHING
                        """)
                .param("p", palabraNormalizada)
                .param("por", por.value())
                .param("en", Timestamp.from(en))
                .update();
    }

    @Override
    public void quitarPalabra(String palabraNormalizada) {
        jdbc.sql("DELETE FROM renaser.palabras_prohibidas_sobrenombre WHERE palabra = :p").param("p", palabraNormalizada).update();
    }

    @Override
    public List<FilaDeLaLista> listar(String buscar, int limite) {
        return jdbc.sql("SELECT s.usuario_id, s.sobrenombre, u.nombre_completo, s.elegido_en FROM renaser.sobrenombres s "
                        + "JOIN renaser.usuarios u ON u.id = s.usuario_id WHERE s.sobrenombre IS NOT NULL "
                        + "AND (:q = '' OR s.normalizado LIKE '%' || :q || '%' OR " + NOMBRE_NORMALIZADO + " LIKE '%' || :q || '%') "
                        + "ORDER BY s.normalizado LIMIT :limite")
                .param("q", buscar)
                .param("limite", limite)
                .query((f, n) -> new FilaDeLaLista(UserId.of(f.getObject("usuario_id", UUID.class)), f.getString("sobrenombre"),
                        f.getString("nombre_completo"), instante(f.getTimestamp("elegido_en"))))
                .list();
    }

    private static Instant instante(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
