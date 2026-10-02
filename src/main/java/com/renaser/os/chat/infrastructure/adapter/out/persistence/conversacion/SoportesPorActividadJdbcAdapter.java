package com.renaser.os.chat.infrastructure.adapter.out.persistence.conversacion;

import com.renaser.os.chat.application.ports.out.conversacion.SoportesPorActividadPort;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.CursorDeSoportes;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * La página de soportes de quien atiende (D-249), con {@link JdbcClient}: es un listado sensible a la
 * latencia y solo toca tablas de {@code chat}.
 *
 * <p><b>Cómo se ordena sin una columna nueva.</b> La actividad de cada soporte es su último mensaje (o su
 * creación, si no tiene ninguno), igual que en la lista completa. Se calcula para los soportes de quien
 * atiende —{@code participantes_usuario_idx}, uno por aprendiz— con una búsqueda {@code LATERAL ...
 * LIMIT 1} sobre {@code mensajes_conversacion_idx} (E-471): un salto de índice por soporte, sin leer
 * los mensajes. Con 300 soportes, EXPLAIN ANALYZE da unos pocos milisegundos (D-249). Una columna
 * {@code ultimo_mensaje_en} en {@code conversaciones} haría la página O(tamaño), pero obligaría a escribir
 * la conversación en cada mensaje; para cientos o pocos miles de aprendices no hace falta.
 *
 * <p>El cursor es el par (actividad, id): la página siguiente empieza estrictamente después, sin
 * {@code OFFSET}.
 */
@Component
public class SoportesPorActividadJdbcAdapter implements SoportesPorActividadPort {

    /** Los soportes de quien atiende, con su actividad. {@code %s}: el filtro opcional por claves. */
    private static final String SOPORTES_CON_ACTIVIDAD = """
            SELECT c.id, COALESCE(ultimo.creado_en, c.creado_en) AS actividad
            FROM renaser.participantes_conversacion p
            JOIN renaser.conversaciones c ON c.id = p.conversacion_id
            LEFT JOIN LATERAL (
                SELECT m.creado_en
                FROM renaser.mensajes m
                WHERE m.conversacion_id = c.id
                ORDER BY m.creado_en DESC
                LIMIT 1
            ) ultimo ON true
            WHERE p.usuario_id = :quien AND c.tipo = 'SOPORTE' %s
            """;

    static final String PAGINA = """
            SELECT s.id, s.actividad FROM (%s) s
            %s
            ORDER BY s.actividad DESC, s.id DESC
            LIMIT :limite
            """;

    static final String CONTEO = """
            SELECT count(*) AS total,
                   count(*) FILTER (WHERE EXISTS (
                       SELECT 1 FROM renaser.mensajes m
                       WHERE m.conversacion_id = c.id
                         AND (p.ultimo_leido_en IS NULL OR m.creado_en > p.ultimo_leido_en))) AS con_no_leidos
            FROM renaser.participantes_conversacion p
            JOIN renaser.conversaciones c ON c.id = p.conversacion_id
            WHERE p.usuario_id = :quien AND c.tipo = 'SOPORTE' %s
            """;

    private static final String SOLO_CLAVES = "AND c.clave_directa IN (:claves)";
    private static final String DESPUES_DEL_CURSOR = "WHERE (s.actividad, s.id) < (:actividad, :id)";

    private final JdbcClient jdbcClient;

    SoportesPorActividadJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<SoporteConActividad> pagina(PedidoDeSoportes pedido) {
        if (pedido.soloClaves() != null && pedido.soloClaves().isEmpty()) {
            return List.of();
        }
        String consulta = consultaDePagina(pedido.soloClaves() != null, pedido.desde() != null);
        JdbcClient.StatementSpec sentencia = jdbcClient.sql(consulta)
                .param("quien", pedido.quienAtiende().value())
                .param("limite", pedido.cuantos());
        if (pedido.soloClaves() != null) {
            sentencia = sentencia.param("claves", pedido.soloClaves());
        }
        if (pedido.desde() != null) {
            sentencia = sentencia.param("actividad", OffsetDateTime.ofInstant(pedido.desde().actividad(), ZoneOffset.UTC))
                    .param("id", pedido.desde().id().value());
        }
        return sentencia.query((fila, n) -> new SoporteConActividad(
                ConversacionId.of(fila.getObject("id", UUID.class)),
                fila.getObject("actividad", OffsetDateTime.class).toInstant())).list();
    }

    @Override
    public ConteoDeSoportes contar(UserId quienAtiende, Set<String> soloClaves) {
        if (soloClaves != null && soloClaves.isEmpty()) {
            return new ConteoDeSoportes(0, 0);
        }
        JdbcClient.StatementSpec sentencia = jdbcClient.sql(CONTEO.formatted(soloClaves != null ? SOLO_CLAVES : ""))
                .param("quien", quienAtiende.value());
        if (soloClaves != null) {
            sentencia = sentencia.param("claves", soloClaves);
        }
        return sentencia.query((fila, n) -> new ConteoDeSoportes(fila.getLong("total"), fila.getLong("con_no_leidos")))
                .single();
    }

    /** Solo fragmentos fijos de esta clase: lo que escribe la persona viaja siempre como parámetro. Pública
     * para que la prueba de integración mire su plan con EXPLAIN. */
    public static String consultaDePagina(boolean conClaves, boolean conCursor) {
        return PAGINA.formatted(SOPORTES_CON_ACTIVIDAD.formatted(conClaves ? SOLO_CLAVES : ""),
                conCursor ? DESPUES_DEL_CURSOR : "");
    }
}
