package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.terminos;

import com.renaser.os.onboarding.application.ports.out.terminos.TerminosPort;
import com.renaser.os.onboarding.domain.model.terminos.ContenidoDeTerminos;
import com.renaser.os.onboarding.domain.model.terminos.EstadoDeVersion;
import com.renaser.os.onboarding.domain.model.terminos.VersionDeTerminos;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code terminos_versiones} (V148). Las cláusulas son un {@code text[]}: se escriben como literal de arreglo con cada texto entrecomillado y escapado
 * ({@link LiteralDeArreglo}), porque una cláusula puede llevar comas, comillas y saltos de línea.
 */
@Component
class TerminosJdbcAdapter implements TerminosPort {

    private static final String COLUMNAS = """
            id, formacion, estado, numero, titulo, introduccion, clausulas, texto_casilla, nota, pide_nueva_aceptacion, base_numero,
            publicada_en, publicada_por, actualizada_en
            """;

    private final JdbcClient jdbc;

    TerminosJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<VersionDeTerminos> vigente(Formacion formacion) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM renaser.terminos_versiones WHERE formacion = :f AND estado = 'PUBLICADA' ORDER BY numero DESC LIMIT 1")
                .param("f", formacion.codigo()).query(TerminosJdbcAdapter::version).optional();
    }

    @Override
    public Optional<VersionDeTerminos> borrador(Formacion formacion) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM renaser.terminos_versiones WHERE formacion = :f AND estado = 'BORRADOR'")
                .param("f", formacion.codigo()).query(TerminosJdbcAdapter::version).optional();
    }

    @Override
    public Optional<VersionDeTerminos> porId(UUID id) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM renaser.terminos_versiones WHERE id = :id").param("id", id)
                .query(TerminosJdbcAdapter::version).optional();
    }

    @Override
    public Optional<VersionDeTerminos> publicada(Formacion formacion, int numero) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM renaser.terminos_versiones WHERE formacion = :f AND estado = 'PUBLICADA' AND numero = :n")
                .param("f", formacion.codigo()).param("n", numero).query(TerminosJdbcAdapter::version).optional();
    }

    @Override
    public List<VersionDeTerminos> publicadas(Formacion formacion) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM renaser.terminos_versiones WHERE formacion = :f AND estado = 'PUBLICADA' ORDER BY numero")
                .param("f", formacion.codigo()).query(TerminosJdbcAdapter::version).list();
    }

    @Override
    public void guardar(VersionDeTerminos v) {
        jdbc.sql("""
                INSERT INTO renaser.terminos_versiones (id, formacion, estado, numero, titulo, introduccion, clausulas, texto_casilla, nota,
                                                        pide_nueva_aceptacion, base_numero, publicada_en, publicada_por, actualizada_en)
                VALUES (:id, :formacion, :estado, :numero, :titulo, :introduccion, CAST(:clausulas AS text[]), :casilla, :nota, :pide, :base,
                        :publicadaEn, :publicadaPor, :actualizadaEn)
                ON CONFLICT (id) DO UPDATE SET estado = EXCLUDED.estado, numero = EXCLUDED.numero, titulo = EXCLUDED.titulo,
                    introduccion = EXCLUDED.introduccion, clausulas = EXCLUDED.clausulas, texto_casilla = EXCLUDED.texto_casilla, nota = EXCLUDED.nota,
                    pide_nueva_aceptacion = EXCLUDED.pide_nueva_aceptacion, base_numero = EXCLUDED.base_numero, publicada_en = EXCLUDED.publicada_en,
                    publicada_por = EXCLUDED.publicada_por, actualizada_en = EXCLUDED.actualizada_en
                """)
                .param("id", v.id()).param("formacion", v.formacion().codigo()).param("estado", v.estado().name()).param("numero", v.numero())
                .param("titulo", v.contenido().titulo()).param("introduccion", v.contenido().introduccion())
                .param("clausulas", LiteralDeArreglo.deTextos(v.contenido().clausulas())).param("casilla", v.contenido().textoCasilla())
                .param("nota", v.nota()).param("pide", v.pideNuevaAceptacion()).param("base", v.baseNumero())
                .param("publicadaEn", v.publicadaEn() == null ? null : Timestamp.from(v.publicadaEn()))
                .param("publicadaPor", v.publicadaPor() == null ? null : v.publicadaPor().value())
                .param("actualizadaEn", Timestamp.from(v.actualizadaEn()))
                .update();
    }

    @Override
    public void descartarBorrador(Formacion formacion) {
        jdbc.sql("DELETE FROM renaser.terminos_versiones WHERE formacion = :f AND estado = 'BORRADOR'").param("f", formacion.codigo()).update();
    }

    private static VersionDeTerminos version(ResultSet rs, int fila) throws SQLException {
        Timestamp publicadaEn = rs.getTimestamp("publicada_en");
        UUID publicadaPor = rs.getObject("publicada_por", UUID.class);
        return new VersionDeTerminos(rs.getObject("id", UUID.class), Formacion.desde(rs.getString("formacion")),
                EstadoDeVersion.valueOf(rs.getString("estado")), enteroONulo(rs, "numero"),
                new ContenidoDeTerminos(rs.getString("titulo"), rs.getString("introduccion"), textos(rs.getArray("clausulas")), rs.getString("texto_casilla")),
                rs.getString("nota"), rs.getBoolean("pide_nueva_aceptacion"), enteroONulo(rs, "base_numero"),
                publicadaEn == null ? null : publicadaEn.toInstant(), publicadaPor == null ? null : UserId.of(publicadaPor),
                rs.getTimestamp("actualizada_en").toInstant());
    }

    private static Integer enteroONulo(ResultSet rs, String columna) throws SQLException {
        int valor = rs.getInt(columna);
        return rs.wasNull() ? null : valor;
    }

    private static List<String> textos(Array arreglo) throws SQLException {
        return Arrays.asList((String[]) arreglo.getArray());
    }
}
