package com.renaser.os.points.infrastructure.adapter.out.persistence.semaforo.mensaje;

import com.renaser.os.points.application.ports.out.semaforo.mensaje.MensajesElegidosPort;
import com.renaser.os.points.domain.model.semaforo.mensaje.CanalDelMensaje;
import com.renaser.os.points.domain.model.semaforo.mensaje.ClaveDeLaEleccion;
import com.renaser.os.points.domain.model.semaforo.mensaje.EleccionesDelDia;
import com.renaser.os.points.domain.model.semaforo.mensaje.MensajeVisto;
import com.renaser.os.points.domain.model.semaforo.mensaje.MensajesVistos;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * {@code mensajes_semaforo_elegidos} con {@link JdbcClient}. Solo se inserta: el {@code INSERT … ON CONFLICT DO NOTHING}
 * resuelve el cruce de dos lecturas dentro de la misma sentencia (en Postgres una violación de UNIQUE aborta la
 * transacción, así que no se puede «atrapar y releer»), y {@code RETURNING} devuelve el id propio sin una segunda
 * consulta; solo quien perdió el cruce relee la fila ganadora. Dos lecturas simultáneas terminan, por lo tanto, con el
 * mismo mensaje.
 *
 * <p>La fila es por persona, fecha, canal (Hoy o el chat, D-281) y grupo.
 */
@Component
class MensajesElegidosJdbcAdapter implements MensajesElegidosPort {

    /**
     * Una consulta, dos respuestas posibles: si hay elección de hoy para el canal y grupo, es la única fila que vuelve
     * ({@code propia}); si no, vuelven las de los 14 días anteriores —de cualquier canal y grupo— y lo que el OTRO canal
     * mostró hoy. Todo sale del rango por la clave primaria {@code (participante_id, fecha, canal, grupo)}.
     */
    private static final String DE_LA_PERSONA = """
            SELECT fecha, mensaje_id, canal, (fecha = :hoy AND canal = :canal AND grupo = :grupo) AS propia
            FROM renaser.mensajes_semaforo_elegidos
            WHERE participante_id = :persona
              AND ((fecha = :hoy AND canal = :canal AND grupo = :grupo)
                   OR (NOT EXISTS (SELECT 1 FROM renaser.mensajes_semaforo_elegidos
                                   WHERE participante_id = :persona AND fecha = :hoy AND canal = :canal AND grupo = :grupo)
                       AND (fecha BETWEEN :desde AND :ayer OR (fecha = :hoy AND canal <> :canal))))
            ORDER BY fecha
            """;

    private static final String DEL_DIA = """
            SELECT mensaje_id FROM renaser.mensajes_semaforo_elegidos
            WHERE participante_id = :persona AND fecha = :fecha AND canal = :canal AND grupo = :grupo
            """;

    private static final String REGISTRAR = """
            INSERT INTO renaser.mensajes_semaforo_elegidos (participante_id, fecha, canal, grupo, mensaje_id, elegido_en)
            VALUES (:persona, :fecha, :canal, :grupo, :mensaje, :elegidoEn)
            ON CONFLICT (participante_id, fecha, canal, grupo) DO NOTHING
            RETURNING mensaje_id
            """;

    private final JdbcClient jdbcClient;

    MensajesElegidosJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public EleccionesDelDia deLaPersona(ClaveDeLaEleccion clave) {
        LocalDate hoy = clave.fecha();
        List<Fila> filas = jdbcClient.sql(DE_LA_PERSONA)
                .param("persona", clave.persona().value()).param("hoy", hoy)
                .param("canal", clave.canal().name()).param("grupo", clave.grupo().name())
                .param("desde", MensajesVistos.desde(hoy)).param("ayer", hoy.minusDays(1))
                .query((rs, fila) -> new Fila(new MensajeVisto(rs.getObject("fecha", LocalDate.class),
                        rs.getString("mensaje_id"), CanalDelMensaje.valueOf(rs.getString("canal"))),
                        rs.getBoolean("propia")))
                .list();
        return filas.stream().filter(Fila::propia).findFirst()
                .map(propia -> EleccionesDelDia.yaElegido(propia.visto().mensajeId()))
                .orElseGet(() -> EleccionesDelDia.sinElegir(MensajesVistos.de(
                        filas.stream().map(Fila::visto).toList(), hoy, clave.canal())));
    }

    @Override
    public String registrar(ClaveDeLaEleccion clave, String mensajeId, Instant ahora) {
        return jdbcClient.sql(REGISTRAR)
                .param("persona", clave.persona().value()).param("fecha", clave.fecha())
                .param("canal", clave.canal().name()).param("grupo", clave.grupo().name())
                .param("mensaje", mensajeId).param("elegidoEn", Timestamp.from(ahora))
                .query(String.class)
                .optional()
                .orElseGet(() -> ganadora(clave));
    }

    /** Otra lectura se adelantó y no hubo fila nueva: queda la suya. */
    private String ganadora(ClaveDeLaEleccion clave) {
        Optional<String> guardada = jdbcClient.sql(DEL_DIA)
                .param("persona", clave.persona().value()).param("fecha", clave.fecha())
                .param("canal", clave.canal().name()).param("grupo", clave.grupo().name())
                .query(String.class)
                .optional();
        return guardada.orElseThrow(() -> new IllegalStateException("La eleccion del mensaje de " + clave.persona()
                + " el " + clave.fecha() + " (" + clave.canal() + ") no quedo guardada"));
    }

    /** Una fila leída: lo visto y si es justo la elección propia que se pidió. */
    private record Fila(MensajeVisto visto, boolean propia) {
    }
}
