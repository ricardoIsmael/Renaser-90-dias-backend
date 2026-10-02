package com.renaser.os.chat.infrastructure.adapter.out.persistence.cuenta;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.BorradoDeDatosDeCuenta;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Lo que {@code chat} borra de una persona cuando su cuenta se borra para siempre (D-243).
 *
 * <ul>
 *   <li><b>Su chat de soporte</b> ({@code clave_directa = 'soporte:<id>'}) y <b>cada chat DIRECTO</b> en
 *       el que participa se borran ENTEROS, con los mensajes de los dos lados: un uno a uno sin la otra
 *       persona no tiene sentido y la app espera encontrar a la contraparte.</li>
 *   <li>En los chats de grupo ({@code CELULA}) y el {@code GLOBAL} se borran SUS mensajes y su fila de
 *       participante; los demás quedan. Las respuestas de otros a un mensaje suyo quedan con
 *       {@code respuesta_a_id} en NULL (FK {@code ON DELETE SET NULL}). Esto incluye su bienvenida de
 *       grupo: {@code BienvenidaEnGrupoService} la guarda como mensaje {@code SISTEMA} a nombre del
 *       propio aprendiz ({@code emisor_id}), así que cae con el resto de sus mensajes.</li>
 *   <li>{@code mensajes_bienvenida} (la marca de la bienvenida de soporte) de la persona.</li>
 *   <li>{@code cambios_bienvenida.cambiado_por}: es la bitácora de lo que editó el staff; la fila es
 *       historia del texto, no de la persona, así que sobrevive y pierde el autor.</li>
 * </ul>
 *
 * <p><b>Archivos.</b> Se reportan las {@code media_ruta} de todos los mensajes que se borran (incluidos
 * los de la otra persona en un chat directo). Siguen en uso las que nombra un mensaje que sobrevive
 * —compartir una publicación al chat no copia el archivo, manda la misma clave— y las portadas de la
 * bitácora de la bienvenida.
 *
 * <p>Mismo patrón que {@code MediaDelMuroEnMensajesAdapter}: el SPI es de otro módulo y la respuesta
 * la da este, contra sus propias tablas.
 */
@Component
@Order(10)
class BorradoDeCuentaEnChatAdapter implements BorradoDeDatosDeCuenta {

    /** Los chats que se borran enteros: su soporte y sus directos (por la clave o por la proyección). */
    private static final String SQL_CHATS_ENTEROS = """
            SELECT c.id FROM renaser.conversaciones c
             WHERE (c.tipo = 'SOPORTE' AND c.clave_directa = :claveSoporte)
                OR (c.tipo = 'DIRECTA' AND (split_part(c.clave_directa, '_', 1) = :idTexto
                    OR split_part(c.clave_directa, '_', 2) = :idTexto
                    OR EXISTS (SELECT 1 FROM renaser.participantes_conversacion pc
                                WHERE pc.conversacion_id = c.id AND pc.usuario_id = :id)))
            """;

    private static final String SQL_ARCHIVOS = """
            SELECT DISTINCT m.media_ruta FROM renaser.mensajes m
             WHERE m.media_ruta IS NOT NULL
               AND (m.emisor_id = :id OR m.conversacion_id IN (%s))
            """.formatted(SQL_CHATS_ENTEROS);

    private static final String SQL_EN_USO = """
            SELECT m.media_ruta FROM renaser.mensajes m
             WHERE m.media_ruta IN (:claves) AND m.emisor_id IS DISTINCT FROM :id
               AND m.conversacion_id NOT IN (%s)
            UNION
            SELECT cb.portada_ruta FROM renaser.cambios_bienvenida cb WHERE cb.portada_ruta IN (:claves)
            """.formatted(SQL_CHATS_ENTEROS);

    /** Hijos antes que padres. Los chats enteros van por id, calculados ANTES de tocar la proyección. */
    private static final List<String> BORRADOS_DE_CHATS_ENTEROS = List.of(
            "DELETE FROM renaser.mensajes_bienvenida WHERE mensaje_id IN "
                    + "(SELECT id FROM renaser.mensajes WHERE conversacion_id IN (:chats))",
            "DELETE FROM renaser.mensajes WHERE conversacion_id IN (:chats)",
            "DELETE FROM renaser.participantes_conversacion WHERE conversacion_id IN (:chats)",
            "DELETE FROM renaser.conversaciones WHERE id IN (:chats)");

    private static final List<String> BORRADOS_DE_LA_PERSONA = List.of(
            "DELETE FROM renaser.mensajes_bienvenida WHERE usuario_destinatario_id = :id "
                    + "OR mensaje_id IN (SELECT id FROM renaser.mensajes WHERE emisor_id = :id)",
            "DELETE FROM renaser.mensajes WHERE emisor_id = :id",
            "DELETE FROM renaser.participantes_conversacion WHERE usuario_id = :id",
            "UPDATE renaser.cambios_bienvenida SET cambiado_por = NULL WHERE cambiado_por = :id");

    private final NamedParameterJdbcTemplate jdbc;

    BorradoDeCuentaEnChatAdapter(NamedParameterJdbcTemplate jdbc) {
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
        return new HashSet<>(jdbc.queryForList(SQL_EN_USO, parametros(cuenta).addValue("claves", claves),
                String.class));
    }

    @Override
    public void borrarDatosDe(UserId cuenta) {
        List<UUID> chats = jdbc.queryForList(SQL_CHATS_ENTEROS, parametros(cuenta), UUID.class);
        if (!chats.isEmpty()) {
            var conChats = parametros(cuenta).addValue("chats", chats);
            BORRADOS_DE_CHATS_ENTEROS.forEach(sql -> jdbc.update(sql, conChats));
        }
        BORRADOS_DE_LA_PERSONA.forEach(sql -> jdbc.update(sql, parametros(cuenta)));
    }

    private static MapSqlParameterSource parametros(UserId cuenta) {
        return new MapSqlParameterSource()
                .addValue("id", cuenta.value())
                .addValue("idTexto", cuenta.value().toString())
                .addValue("claveSoporte", "soporte:" + cuenta.value());
    }
}
