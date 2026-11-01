package com.renaser.os.habits.infrastructure.adapter.out.persistence.categoria;

import com.renaser.os.habits.application.ports.out.categoria.LoadCategoriaHabitoPort;
import com.renaser.os.habits.application.ports.out.categoria.SaveCategoriaHabitoPort;
import com.renaser.os.habits.domain.model.categoria.CategoriaHabito;
import com.renaser.os.shared.domain.Formacion;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * {@code categorias_habito} con {@link JdbcClient} (V134): una tabla chica, sin relaciones que mapear. El nombre unico por formacion lo
 * respalda un indice (sin tildes ni mayusculas); la pregunta «¿ya existe?» se hace con la MISMA normalizacion de la base.
 */
@Component
class CategoriaHabitoJdbcAdapter implements LoadCategoriaHabitoPort, SaveCategoriaHabitoPort {

    private static final String COLUMNAS = "clave, formacion, nombre, orden, icono, activa, creado_en, actualizado_en";
    private static final String NORMALIZAR = "lower(translate(%s, 'ÁÀÄÂÉÈËÊÍÌÏÎÓÒÖÔÚÙÜÛÑÇáàäâéèëêíìïîóòöôúùüûñç', "
            + "'AAAAEEEEIIIIOOOOUUUUNCaaaaeeeeiiiioooouuuunc'))";

    private static final RowMapper<CategoriaHabito> FILA = (rs, n) -> CategoriaHabito.rehydrate(rs.getString("clave"),
            Formacion.desde(rs.getString("formacion")), rs.getString("nombre"), rs.getInt("orden"), rs.getString("icono"),
            rs.getBoolean("activa"), rs.getTimestamp("creado_en").toInstant(), rs.getTimestamp("actualizado_en").toInstant());

    private final JdbcClient jdbc;

    CategoriaHabitoJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CategoriaHabito> byClave(String clave) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM renaser.categorias_habito WHERE clave = :clave")
                .param("clave", clave).query(FILA).optional();
    }

    @Override
    public List<CategoriaHabito> deFormacion(Formacion formacion) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM renaser.categorias_habito WHERE formacion = :formacion ORDER BY orden, nombre")
                .param("formacion", formacion.codigo()).query(FILA).list();
    }

    @Override
    public List<CategoriaHabito> activasDe(Formacion formacion) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM renaser.categorias_habito WHERE formacion = :formacion AND activa ORDER BY orden, nombre")
                .param("formacion", formacion.codigo()).query(FILA).list();
    }

    @Override
    public boolean existeClave(String clave) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM renaser.categorias_habito WHERE clave = :clave)")
                .param("clave", clave).query(Boolean.class).single();
    }

    @Override
    public boolean existeNombreEn(Formacion formacion, String nombre, String excluyendo) {
        String sql = "SELECT EXISTS (SELECT 1 FROM renaser.categorias_habito WHERE formacion = :formacion AND "
                + NORMALIZAR.formatted("nombre") + " = " + NORMALIZAR.formatted(":nombre")
                + " AND (CAST(:excluyendo AS text) IS NULL OR clave <> :excluyendo))";
        return jdbc.sql(sql).param("formacion", formacion.codigo()).param("nombre", nombre.trim())
                .param("excluyendo", excluyendo).query(Boolean.class).single();
    }

    @Override
    public ConteoDeHabitos contarHabitos(String clave) {
        return jdbc.sql("""
                        SELECT count(*) FILTER (WHERE activo) AS activos, count(*) AS total
                        FROM renaser.habitos WHERE ambito = 'SISTEMA' AND categoria_clave = :clave
                        """)
                .param("clave", clave)
                .query((rs, n) -> new ConteoDeHabitos(rs.getInt("activos"), rs.getInt("total"))).single();
    }

    @Override
    public int siguienteOrden(Formacion formacion) {
        return jdbc.sql("SELECT COALESCE(max(orden), 0) + 1 FROM renaser.categorias_habito WHERE formacion = :formacion")
                .param("formacion", formacion.codigo()).query(Integer.class).single();
    }

    @Override
    public CategoriaHabito save(CategoriaHabito c) {
        jdbc.sql("""
                        INSERT INTO renaser.categorias_habito (clave, formacion, nombre, orden, icono, activa, creado_en, actualizado_en)
                        VALUES (:clave, :formacion, :nombre, :orden, :icono, :activa, :creado, :actualizado)
                        ON CONFLICT (clave) DO UPDATE SET nombre = EXCLUDED.nombre, orden = EXCLUDED.orden,
                            icono = EXCLUDED.icono, activa = EXCLUDED.activa, actualizado_en = EXCLUDED.actualizado_en
                        """)
                .param("clave", c.clave()).param("formacion", c.formacion().codigo()).param("nombre", c.nombre())
                .param("orden", c.orden()).param("icono", c.icono()).param("activa", c.activa())
                .param("creado", Timestamp.from(c.creadoEn())).param("actualizado", Timestamp.from(c.actualizadoEn()))
                .update();
        return c;
    }

    @Override
    public void eliminar(String clave) {
        jdbc.sql("DELETE FROM renaser.categorias_habito WHERE clave = :clave").param("clave", clave).update();
    }
}
