package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.terminos;

import com.renaser.os.onboarding.application.ports.out.terminos.AceptacionesDeTerminosPort;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@code aceptaciones_de_terminos} (V148): solo se agregan filas. La columna {@code cronograma_huella} (V162) ya no se escribe ni se lee (D-315, decisión del dueño del
 * 2026-11-02: el cronograma de pagos no se firma con los términos); se deja sin borrar y nula.
 */
@Component
class AceptacionesDeTerminosJdbcAdapter implements AceptacionesDeTerminosPort {

    private final JdbcClient jdbc;

    AceptacionesDeTerminosJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void registrar(UUID id, UserId usuario, UUID versionId, Instant en, Long firmaMediaId, LoMostrado mostrado) {
        jdbc.sql("""
                INSERT INTO renaser.aceptaciones_de_terminos (id, usuario_id, version_id, aceptada_en, firma_media_id, programa_mostrado, inicio_mostrado, fin_mostrado)
                VALUES (:id, :u, :v, :en, :firma, :programa, :inicio, :fin)
                """).param("id", id).param("u", usuario.value()).param("v", versionId).param("en", Timestamp.from(en)).param("firma", firmaMediaId)
                .param("programa", mostrado.programa()).param("inicio", mostrado.inicio()).param("fin", mostrado.fin())
                .update();
    }

    @Override
    public Optional<Integer> ultimaAceptada(UserId usuario, Formacion formacion) {
        return jdbc.sql("""
                SELECT max(v.numero) FROM renaser.aceptaciones_de_terminos a JOIN renaser.terminos_versiones v ON v.id = a.version_id
                WHERE a.usuario_id = :u AND v.formacion = :f
                """).param("u", usuario.value()).param("f", formacion.codigo()).query(Integer.class).optional();
    }

    @Override
    public boolean aceptoAlgunaVez(UserId usuario) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM renaser.aceptaciones_de_terminos WHERE usuario_id = :u)").param("u", usuario.value())
                .query(Boolean.class).single();
    }

    @Override
    public List<Aceptacion> delUsuario(UserId usuario) {
        return jdbc.sql("""
                SELECT a.version_id, v.formacion, v.numero, a.aceptada_en, a.firma_media_id, a.programa_mostrado, a.inicio_mostrado, a.fin_mostrado
                FROM renaser.aceptaciones_de_terminos a JOIN renaser.terminos_versiones v ON v.id = a.version_id
                WHERE a.usuario_id = :u ORDER BY a.aceptada_en DESC
                """).param("u", usuario.value())
                .query((rs, n) -> new Aceptacion(rs.getObject("version_id", UUID.class), Formacion.desde(rs.getString("formacion")), rs.getInt("numero"),
                        rs.getTimestamp("aceptada_en").toInstant(), (Long) rs.getObject("firma_media_id"),
                        new LoMostrado(rs.getString("programa_mostrado"), rs.getObject("inicio_mostrado", LocalDate.class), rs.getObject("fin_mostrado", LocalDate.class)))).list();
    }

    @Override
    public Map<UUID, Long> aceptantesPorVersion(Formacion formacion) {
        Map<UUID, Long> cuentas = new HashMap<>();
        jdbc.sql("""
                SELECT a.version_id, count(DISTINCT a.usuario_id) AS n
                FROM renaser.aceptaciones_de_terminos a JOIN renaser.terminos_versiones v ON v.id = a.version_id
                WHERE v.formacion = :f GROUP BY a.version_id
                """).param("f", formacion.codigo())
                .query((rs, i) -> cuentas.put(rs.getObject("version_id", UUID.class), rs.getLong("n"))).list();
        return cuentas;
    }

    @Override
    public Set<UserId> quienesAceptaronDesde(Formacion formacion, int numero) {
        return jdbc.sql("""
                SELECT DISTINCT a.usuario_id FROM renaser.aceptaciones_de_terminos a JOIN renaser.terminos_versiones v ON v.id = a.version_id
                WHERE v.formacion = :f AND v.numero >= :n
                """).param("f", formacion.codigo()).param("n", numero)
                .query((rs, i) -> UserId.of(rs.getObject("usuario_id", UUID.class))).list().stream().collect(Collectors.toSet());
    }
}
