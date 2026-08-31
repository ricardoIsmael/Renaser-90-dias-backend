package com.renaser.os.habits.infrastructure.adapter.out.persistence.habitosaprendiz;

import com.renaser.os.habits.application.ports.out.habitosaprendiz.LeerHabitosPersonalizadosPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Lectura por proyeccion (CLAUDE.MD §11): {@link JdbcClient} trae exactamente las columnas
 * de la respuesta, sin materializar entidades JPA ni sus grafos. Es el primer adaptador del
 * repo que usa {@code JdbcClient} — la grilla del panel admin es justo el caso que §11
 * reservaba para lectura pura sensible a latencia.
 *
 * <p><b>Una sola consulta</b> para las seis tablas (habitos + renombres + horarios +
 * preferencias + cambios pendientes + desbloqueos + dia semanal): el hit a disco es un
 * scan del catalogo activo (indice parcial {@code habitos_catalogo_idx}) con seis lookups
 * por PK/indice por fila, no una consulta por habito.
 *
 * <p>Los dos {@code LATERAL} existen porque esas dos relaciones son 1:N y hay que quedarse
 * con UNA fila: el horario del catalogo vigente para el dia de programa, y la eleccion de
 * dia de la semana en curso. Un {@code LEFT JOIN} plano multiplicaria filas.
 *
 * <p>El desempate del horario ({@code dia_inicio DESC}, y a igualdad el tramo especifico
 * antes que el {@code TODOS}) es una decision de ESTE adaptador: el codigo que ya existia
 * ({@code RegistroService}) tomaba el primero que apareciera, sin orden declarado — o sea,
 * el que la base devolviera. Se elige el tramo mas reciente/especifico, que es el mismo
 * criterio que {@code TracksDelDiaProyeccionService} ya usa para resolver la guia vigente.
 */
@Component
class HabitosDeAprendizJdbcAdapter implements LeerHabitosPersonalizadosPort {

    private static final String SQL = """
            SELECT h.id                       AS habito_id,
                   h.titulo                   AS titulo_catalogo,
                   h.ambito                   AS ambito,
                   h.tipo                     AS tipo,
                   h.categoria_clave          AS categoria_clave,
                   h.eleccion_dia_semanal     AS eleccion_dia_semanal,
                   ren.titulo_personal        AS titulo_personal,
                   hor.hora_disparo           AS hora_disparo_catalogo,
                   hor.hora_limite            AS hora_limite_catalogo,
                   pre.hora_disparo           AS hora_disparo_preferencia,
                   pre.hora_limite            AS hora_limite_preferencia,
                   pre.recordatorio_activo    AS recordatorio_activo,
                   pre.minutos_recordatorio   AS minutos_recordatorio,
                   cam.hora_disparo           AS hora_disparo_pendiente,
                   cam.hora_limite            AS hora_limite_pendiente,
                   cam.fecha_efectiva         AS fecha_efectiva_pendiente,
                   des.dia_desbloqueo         AS dia_desbloqueo,
                   (des.elegido_en IS NOT NULL) AS desbloqueo_elegido,
                   sem.fecha_ejecucion        AS dia_semanal_elegido
            FROM renaser.habitos h
            LEFT JOIN renaser.renombres_habito ren
                   ON ren.habito_id = h.id AND ren.participante_id = :aprendizId
            LEFT JOIN LATERAL (
                   SELECT hh.hora_disparo, hh.hora_limite
                   FROM renaser.horarios_habito hh
                   WHERE hh.habito_id = h.id
                     AND :diaPrograma >= hh.dia_inicio
                     AND (hh.dia_fin IS NULL OR :diaPrograma <= hh.dia_fin)
                     AND (hh.tipo_dia = 'TODOS' OR hh.tipo_dia = CAST(:tipoDia AS renaser.tipo_dia))
                   ORDER BY hh.dia_inicio DESC, (hh.tipo_dia = 'TODOS')
                   LIMIT 1
            ) hor ON true
            LEFT JOIN renaser.preferencias_horario pre
                   ON pre.habito_id = h.id AND pre.participante_id = :aprendizId
            LEFT JOIN renaser.cambios_horario_pendientes cam
                   ON cam.habito_id = h.id AND cam.participante_id = :aprendizId
            LEFT JOIN renaser.desbloqueos_habito des
                   ON des.habito_id = h.id AND des.participante_id = :aprendizId
            LEFT JOIN LATERAL (
                   SELECT ds.fecha_ejecucion
                   FROM renaser.dias_semanales_habito ds
                   WHERE ds.habito_id = h.id AND ds.participante_id = :aprendizId
                     AND ds.semana_inicio = :semanaInicio
                   ORDER BY ds.fecha_ejecucion
                   LIMIT 1
            ) sem ON true
            WHERE h.activo = true
              AND (h.ambito = 'SISTEMA' OR h.participante_id = :aprendizId)
            ORDER BY h.orden, h.titulo
            """;

    private static final RowMapper<FilaHabitoDeAprendiz> MAPPER = HabitosDeAprendizJdbcAdapter::mapear;

    private final JdbcClient jdbcClient;

    HabitosDeAprendizJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<FilaHabitoDeAprendiz> deAprendiz(UserId aprendizId, int diaPrograma, TipoDia tipoDia,
                                                  LocalDate semanaInicio) {
        return jdbcClient.sql(SQL)
                .param("aprendizId", aprendizId.value())
                .param("diaPrograma", diaPrograma)
                .param("tipoDia", tipoDia.name())
                .param("semanaInicio", semanaInicio)
                .query(MAPPER)
                .list();
    }

    /**
     * El orden de precedencia NO se resuelve aca: la fila lleva catalogo y preferencia por
     * separado, y el caso de uso decide. Este metodo solo traduce columnas a tipos Java.
     */
    private static FilaHabitoDeAprendiz mapear(ResultSet rs, int rowNum) throws SQLException {
        return new FilaHabitoDeAprendiz(
                HabitoId.of(rs.getObject("habito_id", UUID.class)),
                rs.getString("titulo_catalogo"),
                rs.getString("titulo_personal"),
                "PERSONAL".equals(rs.getString("ambito")),
                TipoHabito.valueOf(rs.getString("tipo")),
                rs.getString("categoria_clave"),
                rs.getBoolean("eleccion_dia_semanal"),
                rs.getObject("hora_disparo_catalogo", LocalTime.class),
                rs.getObject("hora_limite_catalogo", LocalTime.class),
                rs.getObject("hora_disparo_preferencia", LocalTime.class),
                rs.getObject("hora_limite_preferencia", LocalTime.class),
                rs.getObject("recordatorio_activo", Boolean.class),
                rs.getObject("minutos_recordatorio", Integer.class),
                rs.getObject("hora_disparo_pendiente", LocalTime.class),
                rs.getObject("hora_limite_pendiente", LocalTime.class),
                rs.getObject("fecha_efectiva_pendiente", LocalDate.class),
                rs.getObject("dia_desbloqueo", Integer.class),
                rs.getObject("desbloqueo_elegido", Boolean.class),
                rs.getObject("dia_semanal_elegido", LocalDate.class));
    }
}
