package com.renaser.os.community.infrastructure.adapter.out.persistence.cuenta;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.BorradoDeDatosDeCuenta;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lo que {@code community} borra o anonimiza de una persona cuando su cuenta se borra para siempre (D-243).
 *
 * <ul>
 *   <li><b>Muro</b>: sus reacciones y comentarios, y sus publicaciones con todo lo que cuelga de ellas
 *       (medias, reacciones y comentarios de otros sobre esas publicaciones).</li>
 *   <li><b>Testimonios</b>: los que hablan de ella (nombre, foto y texto suyos) se borran. Un testimonio
 *       es «de ella» si salió de una publicación suya ({@code TestimonioService.promover}: lleva
 *       {@code publicacion_muro_id} y siempre {@code foto_evento_ruta}, porque toda publicación tiene al
 *       menos una media). El testimonio escrito a mano ({@code TestimonioService.crear}) guarda en
 *       {@code usuario_id} al ADMIN que lo cargó, con el nombre de otra persona y sin publicación ni
 *       foto: ese NO se borra al borrar al Admin, solo pierde el {@code usuario_id} (lo mismo que haría
 *       la FK {@code ON DELETE SET NULL}).</li>
 *   <li><b>Grupos</b>: sus pertenencias ({@code asignaciones_celula.usuario_id}); donde figura como quien
 *       hizo un cambio ({@code actor_id}) o como mentor del puntero {@code celulas.mentor_id}, queda en
 *       NULL: la historia y el grupo son de los demás.</li>
 *   <li>{@code anomalias_acompanamiento} (V45, sin dueño en Java; es del tema acompañamiento, que es de
 *       este módulo): las filas de la persona.</li>
 * </ul>
 *
 * <p><b>Archivos.</b> Las medias de sus publicaciones y la foto de sus testimonios. Siguen en uso las que
 * nombra otra publicación, un testimonio que sobrevive o la foto de un grupo, y su avatar si un testimonio
 * que sobrevive lo guarda como URL (se compara por sufijo, como hacía {@code users} hasta D-243).
 */
@Component
@Order(20)
class BorradoDeCuentaEnCommunityAdapter implements BorradoDeDatosDeCuenta {

    /** El testimonio habla de la persona: salió de una publicación suya (ver javadoc de la clase). */
    private static final String ES_TESTIMONIO_SUYO =
            "(t.usuario_id IS NOT DISTINCT FROM :id AND (t.publicacion_muro_id IS NOT NULL OR t.foto_evento_ruta IS NOT NULL))";

    private static final String SQL_ARCHIVOS = """
            SELECT mp.ruta_storage FROM renaser.medias_publicacion mp
              JOIN renaser.publicaciones_muro p ON p.id = mp.publicacion_id
             WHERE p.autor_id = :id AND mp.ruta_storage IS NOT NULL
            UNION
            SELECT t.foto_evento_ruta FROM renaser.testimonios t
             WHERE %s AND t.foto_evento_ruta IS NOT NULL
            """.formatted(ES_TESTIMONIO_SUYO);

    private static final String SQL_EN_USO = """
            SELECT mp.ruta_storage FROM renaser.medias_publicacion mp
              JOIN renaser.publicaciones_muro p ON p.id = mp.publicacion_id
             WHERE mp.ruta_storage IN (:claves) AND p.autor_id <> :id
            UNION
            SELECT t.foto_evento_ruta FROM renaser.testimonios t
             WHERE t.foto_evento_ruta IN (:claves) AND NOT %s
            UNION
            SELECT c.foto_ruta FROM renaser.celulas c WHERE c.foto_ruta IN (:claves)
            """.formatted(ES_TESTIMONIO_SUYO);

    private static final String SQL_AVATAR_EN_TESTIMONIO_QUE_SOBREVIVE = """
            SELECT EXISTS (SELECT 1 FROM renaser.testimonios t
                            WHERE t.avatar_url IS NOT NULL AND t.avatar_url LIKE '%%' || :clave
                              AND NOT %s)
            """.formatted(ES_TESTIMONIO_SUYO);

    private static final String PREFIJO_AVATAR = "avatares/";

    /** Hijos antes que padres: lo que cuelga de sus publicaciones antes que las publicaciones. */
    private static final List<String> BORRADOS = List.of(
            "DELETE FROM renaser.testimonios t WHERE " + ES_TESTIMONIO_SUYO,
            "UPDATE renaser.testimonios SET usuario_id = NULL WHERE usuario_id = :id",
            "DELETE FROM renaser.reacciones_muro WHERE usuario_id = :id OR publicacion_id IN "
                    + "(SELECT id FROM renaser.publicaciones_muro WHERE autor_id = :id)",
            "DELETE FROM renaser.comentarios_muro WHERE autor_id = :id OR publicacion_id IN "
                    + "(SELECT id FROM renaser.publicaciones_muro WHERE autor_id = :id)",
            "DELETE FROM renaser.medias_publicacion WHERE publicacion_id IN "
                    + "(SELECT id FROM renaser.publicaciones_muro WHERE autor_id = :id)",
            "UPDATE renaser.testimonios SET publicacion_muro_id = NULL WHERE publicacion_muro_id IN "
                    + "(SELECT id FROM renaser.publicaciones_muro WHERE autor_id = :id)",
            "DELETE FROM renaser.publicaciones_muro WHERE autor_id = :id",
            "DELETE FROM renaser.asignaciones_celula WHERE usuario_id = :id",
            "UPDATE renaser.asignaciones_celula SET actor_id = NULL WHERE actor_id = :id",
            "UPDATE renaser.celulas SET mentor_id = NULL WHERE mentor_id = :id",
            "DELETE FROM renaser.anomalias_acompanamiento WHERE usuario_id = :id");

    private final NamedParameterJdbcTemplate jdbc;

    BorradoDeCuentaEnCommunityAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<String> archivosDe(UserId cuenta) {
        return new HashSet<>(jdbc.queryForList(SQL_ARCHIVOS, parametros(cuenta), String.class));
    }

    @Override
    public Set<String> archivosEnUsoTrasBorrar(UserId cuenta, Set<String> claves) {
        if (claves == null || claves.isEmpty()) {
            return Set.of();
        }
        Set<String> enUso = new HashSet<>(jdbc.queryForList(SQL_EN_USO,
                parametros(cuenta).addValue("claves", claves), String.class));
        claves.stream()
                .filter(clave -> clave.startsWith(PREFIJO_AVATAR))
                .filter(clave -> avatarEnUnTestimonioQueSobrevive(cuenta, clave))
                .forEach(enUso::add);
        return enUso;
    }

    @Override
    public void borrarDatosDe(UserId cuenta) {
        BORRADOS.forEach(sql -> jdbc.update(sql, parametros(cuenta)));
    }

    /** La clave del avatar no tiene comodines de LIKE ({@code avatares/} más un UUID). */
    private boolean avatarEnUnTestimonioQueSobrevive(UserId cuenta, String clave) {
        return Boolean.TRUE.equals(jdbc.queryForObject(SQL_AVATAR_EN_TESTIMONIO_QUE_SOBREVIVE,
                parametros(cuenta).addValue("clave", clave), Boolean.class));
    }

    private static MapSqlParameterSource parametros(UserId cuenta) {
        return new MapSqlParameterSource("id", cuenta.value());
    }
}
