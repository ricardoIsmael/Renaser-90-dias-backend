package com.renaser.os.users.infrastructure.adapter.out.persistence.user;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.out.user.RutasDeAlmacenamientoDeCuentaPort;
import com.renaser.os.users.domain.model.user.ClavesDeCuenta;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * El censo de objetos que deja una cuenta, en SQL.
 *
 * <p><b>Por que SQL suelto y no los repositorios de cada modulo.</b> Las rutas viven en tablas de
 * seis modulos distintos y hay que leerlas en el mismo instante, justo antes del DELETE. La
 * alternativa limpia —publicar un evento de cuenta purgada y que cada modulo borre lo suyo—
 * necesita que cada modulo sepa ademas si su clave la referencia otro, que es precisamente lo que
 * ninguno puede saber solo (la referencia cruzada vive en {@code testimonios} y en
 * {@code mensajes}, de otros dos modulos). Este adaptador es de LECTURA pura y no toca una sola
 * fila; la decision de borrar queda entera en {@code AccountDeletionService}.
 *
 * <p><b>Ojo al mantenerlo.</b> Una columna de ruta nueva que cuelgue de {@code usuarios} y no se
 * agregue aca vuelve a dejar objetos huerfanos en el bucket, en silencio y sin que ninguna prueba
 * lo note. La lista de abajo es el censo completo al 2026-09-21; los que quedaron afuera estan
 * nombrados con su razon.
 *
 * <p><b>El {@code renaser.} de cada tabla no es decorativo.</b> Las tablas viven en ese esquema
 * ({@code V1__baseline_renaser.sql}: {@code CREATE SCHEMA renaser} y {@code SET search_path}), y
 * el {@code search_path} de la conexion de Hikari no lo incluye — las entidades JPA lo declaran
 * uno por uno en {@code @Table(schema = "renaser")}. Sin calificar, cada consulta de aca muere
 * con {@code relation "usuarios" does not exist} y la purga entera cuenta como fallida. Mismo
 * criterio que los demas adaptadores con SQL suelto ({@code TokenPushPersistenceAdapter}).
 */
@Component
public class RutasDeAlmacenamientoDeCuentaJdbcAdapter implements RutasDeAlmacenamientoDeCuentaPort {

    /**
     * Toda columna de ruta cuya fila cae con la cascada de {@code usuarios}.
     *
     * <p>Quedan afuera a proposito, y no por olvido:
     * <ul>
     *   <li>{@code adjuntos_guia}, {@code audioterapias}, {@code audios_espiritu},
     *       {@code cursos.portada_ruta}: catalogo compartido por todo el padron, no cuelga de
     *       ninguna cuenta.</li>
     *   <li>{@code eventos.portada_ruta}: {@code creado_por} es {@code ON DELETE SET NULL}, el
     *       evento sobrevive a la baja de quien lo creo y lo sigue mostrando el calendario.</li>
     *   <li>{@code testimonios.avatar_url} / {@code foto_evento_ruta}: tambien sobreviven
     *       ({@code usuario_id ON DELETE SET NULL}). No son candidatas a borrar: son justamente
     *       las referencias que impiden borrar, y por eso aparecen en la consulta de abajo.</li>
     *   <li>{@code notificaciones.ruta_app}: es un destino de navegacion dentro de la app
     *       ({@code "/"}, ver {@code WebPushAdapter}), no una clave de objeto.</li>
     * </ul>
     *
     * <p>{@code entradas_diario.audio_ruta}, {@code sesiones_bloqueo.evidencia_salida_ruta} y
     * {@code mensajes.media_ruta} SI entran, pero solo porque {@code ClavesDeCuenta} las filtra
     * despues: las tres las llena el cliente y pueden nombrar un objeto ajeno.
     */
    private static final String SQL_CANDIDATAS = """
            SELECT 'avatares/' || u.id AS ruta FROM renaser.usuarios u
             WHERE u.id = :usuarioId AND u.avatar_url IS NOT NULL
            UNION
            SELECT e.ruta_storage FROM renaser.evidencias e
             WHERE e.participante_id = :usuarioId AND e.ruta_storage IS NOT NULL
            UNION
            SELECT c.ruta_firma FROM renaser.contratos_fase c
             WHERE c.participante_id = :usuarioId AND c.ruta_firma IS NOT NULL
            UNION
            SELECT mo.ruta_storage FROM renaser.medias_onboarding mo
             WHERE mo.usuario_id = :usuarioId AND mo.ruta_storage IS NOT NULL
            UNION
            SELECT t.adjunto_ruta FROM renaser.tickets_soporte t
             WHERE t.usuario_id = :usuarioId AND t.adjunto_ruta IS NOT NULL
            UNION
            SELECT mp.ruta_storage FROM renaser.medias_publicacion mp
              JOIN renaser.publicaciones_muro p ON p.id = mp.publicacion_id
             WHERE p.autor_id = :usuarioId AND mp.ruta_storage IS NOT NULL
            UNION
            SELECT d.audio_ruta FROM renaser.entradas_diario d
             WHERE d.participante_id = :usuarioId AND d.audio_ruta IS NOT NULL
            UNION
            SELECT sb.evidencia_salida_ruta FROM renaser.sesiones_bloqueo sb
              JOIN renaser.registros_habito rh ON rh.id = sb.registro_habito_id
             WHERE rh.participante_id = :usuarioId AND sb.evidencia_salida_ruta IS NOT NULL
            UNION
            SELECT m.media_ruta FROM renaser.mensajes m
             WHERE m.emisor_id = :usuarioId AND m.media_ruta IS NOT NULL
            """;

    /**
     * Las dos formas en que una clave de esta cuenta sigue viva despues de la purga.
     *
     * <p>{@code testimonios} sobrevive entero ({@code usuario_id ON DELETE SET NULL}), asi que
     * cualquier fila suya que apunte a la clave la bloquea — sea del purgado o de otro. Y de
     * {@code mensajes} solo cuentan los de OTRO emisor: los del purgado caen con la cascada de
     * {@code emisor_id}, asi que no son una referencia que sobreviva.
     *
     * <p>El avatar se compara por sufijo porque {@code testimonios.avatar_url} guarda la URL
     * permanente del objeto ({@code urlPublica}), no la clave. La clave no tiene comodines de
     * LIKE adentro ({@code avatares/} mas un UUID), asi que no hay nada que escapar.
     */
    private static final String SQL_REFERENCIADAS_POR_TERCEROS = """
            SELECT t.foto_evento_ruta AS ruta FROM renaser.testimonios t
             WHERE t.foto_evento_ruta IN (:rutas)
            UNION
            SELECT m.media_ruta FROM renaser.mensajes m
             WHERE m.media_ruta IN (:rutas) AND m.emisor_id <> :usuarioId
            """;

    private static final String SQL_AVATAR_EN_TESTIMONIO = """
            SELECT count(*) FROM renaser.testimonios t
             WHERE t.avatar_url IS NOT NULL AND t.avatar_url LIKE '%' || :claveAvatar
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public RutasDeAlmacenamientoDeCuentaJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<String> candidatas(UserId usuarioId) {
        return jdbc.queryForList(SQL_CANDIDATAS, Map.of("usuarioId", usuarioId.value()), String.class);
    }

    @Override
    public Set<String> referenciadasPorTerceros(UserId usuarioId, Collection<String> rutas) {
        if (rutas == null || rutas.isEmpty()) {
            return Set.of();
        }
        var parametros = new MapSqlParameterSource()
                .addValue("usuarioId", usuarioId.value())
                .addValue("rutas", rutas);
        Set<String> compartidas = new HashSet<>(
                jdbc.queryForList(SQL_REFERENCIADAS_POR_TERCEROS, parametros, String.class));

        String claveAvatar = ClavesDeCuenta.de(usuarioId).avatar();
        if (rutas.contains(claveAvatar)) {
            Integer enTestimonios = jdbc.queryForObject(SQL_AVATAR_EN_TESTIMONIO,
                    Map.of("claveAvatar", claveAvatar), Integer.class);
            if (enTestimonios != null && enTestimonios > 0) {
                compartidas.add(claveAvatar);
            }
        }
        return compartidas;
    }
}
