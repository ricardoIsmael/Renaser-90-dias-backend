package com.renaser.os.community.infrastructure.adapter.out.persistence.publicacion;

import com.renaser.os.community.application.ports.out.publicacion.ReferenciasDeMediaDelMuroPort;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * El censo, dentro de community, de quien mas mira una clave {@code muro/}.
 *
 * <p><b>SQL suelto y no los repositorios.</b> {@code medias_publicacion} no es un {@code @Entity}
 * —la maneja {@code PublicacionPersistenceAdapter} con SQL nativo, mismo criterio que
 * {@code rocks/evidencia}— y lo que se necesita de {@code testimonios} es una sola columna
 * cruzada contra una lista. Las dos tablas son de community, asi que no se cruza la regla de
 * {@code .claude/rules/01-arquitectura-hexagonal} ("ningun modulo manda SQL nativo contra una
 * tabla ajena"); {@code mensajes}, que es de {@code chat}, la responde ese modulo por
 * {@code community.api.ReferenciasExternasDeMediaDelMuro}.
 *
 * <p><b>El {@code renaser.} de cada tabla no es decorativo.</b> Las tablas viven en ese esquema
 * ({@code V1__baseline_renaser.sql}: {@code CREATE SCHEMA renaser}) y el {@code search_path} de la
 * conexion de Hikari no lo incluye — las entidades JPA lo declaran una por una en
 * {@code @Table(schema = "renaser")}. Sin calificar, cada consulta de aca muere con
 * {@code relation "medias_publicacion" does not exist} y el borrado entero falla.
 *
 * <p><b>Al mantenerlo.</b> Una columna nueva de community que guarde una clave {@code muro/} y no
 * se agregue aca vuelve a habilitar el borrado de un objeto que alguien sigue mirando, en silencio
 * y sin que ninguna prueba lo note. Las dos de abajo son el censo completo al 2026-09-21.
 */
@Component
class ReferenciasDeMediaDelMuroJdbcAdapter implements ReferenciasDeMediaDelMuroPort {

    /**
     * Otra publicacion viva que use la misma clave. Es posible hoy: la clave se ata al autor pero
     * nada exige que sea unica, y las publicaciones anteriores al arreglo pueden compartirla.
     *
     * <p>{@code oculta} NO se filtra: una publicacion oculta es reversible por {@code restaurar}
     * (la columna esta documentada en V1:1086 como "semantica propia, distinta de borrar"), asi
     * que su media tiene que seguir en el bucket.
     */
    private static final String SQL_EN_OTRA_PUBLICACION = """
            SELECT mp.ruta_storage AS ruta FROM renaser.medias_publicacion mp
             WHERE mp.ruta_storage IN (:rutas)
               AND mp.publicacion_id <> :publicacionId
            """;

    /**
     * Un testimonio que congelo esta misma clave como foto del evento
     * ({@code TestimonioService.promover}). {@code publicacion_muro_id} es
     * {@code ON DELETE SET NULL}, asi que la fila sobrevive al borrado de la publicacion y
     * {@code aVista} la sigue firmando en la vitrina publica: no se filtra por
     * {@code publicacion_muro_id} a proposito, porque justamente la fila que apunta a ESTA
     * publicacion es la que se va a quedar huerfana y sigue necesitando el objeto.
     */
    private static final String SQL_EN_TESTIMONIO = """
            SELECT t.foto_evento_ruta AS ruta FROM renaser.testimonios t
             WHERE t.foto_evento_ruta IN (:rutas)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    ReferenciasDeMediaDelMuroJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<String> referenciadasFueraDe(Collection<String> rutas, PublicacionId publicacionId) {
        if (rutas == null || rutas.isEmpty()) {
            return Set.of();
        }
        var parametros = new MapSqlParameterSource()
                .addValue("rutas", rutas)
                .addValue("publicacionId", publicacionId.value());
        Set<String> referenciadas = new HashSet<>(
                jdbc.queryForList(SQL_EN_OTRA_PUBLICACION, parametros, String.class));
        referenciadas.addAll(jdbc.queryForList(SQL_EN_TESTIMONIO, parametros, String.class));
        return referenciadas;
    }
}
