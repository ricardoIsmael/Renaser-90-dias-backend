package com.renaser.os.calendar.infrastructure.adapter.out.persistence.meet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.renaser.os.calendar.application.ports.out.meet.CifradoDeSecretosPort;
import com.renaser.os.calendar.application.ports.out.meet.TranscripcionesDeMeetPort;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.meet.transcripcion.DiagnosticoDeReunion;
import com.renaser.os.calendar.domain.model.meet.transcripcion.EstadoDeTranscripcion;
import com.renaser.os.calendar.domain.model.meet.transcripcion.PendienteDeTranscripcion;
import com.renaser.os.calendar.domain.model.meet.transcripcion.TranscripcionDeSesion;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code eventos_meet_transcripciones} y {@code eventos_meet_transcripciones_accesos} (V184, spec 047). El texto y el enlace a la grabación se
 * guardan CIFRADOS con la clave de la spec 040 ({@link CifradoDeSecretosPort}, AES-256-GCM) y solo se descifran en {@link #deLaFecha}: ninguna
 * otra lectura los trae (FR-010, FR-012).
 */
@Component
class TranscripcionesDeMeetJdbcAdapter implements TranscripcionesDeMeetPort {

    private static final String COLUMNAS = "t.id, t.evento_meet_id, t.estado, t.conferencias, t.entradas, t.largo, t.recortada, t.texto_cifrado, "
            + "t.grabacion_cifrada, t.intentos, t.proximo_intento_en, t.motivo, t.diagnostico::text AS diagnostico, t.obtenida_en, t.borrar_despues_de, t.borrada_en, t.creada_en, "
            + "t.actualizada_en";

    /** El inicio de la sesión de ESA fecha: la hora local del evento, en su zona, el día de la sala. */
    private static final String INICIO = "((m.fecha_ocurrencia + (e.inicia_en AT TIME ZONE e.timezone)::time) AT TIME ZONE e.timezone)";
    private static final String FIN = "(" + INICIO + " + make_interval(mins => COALESCE(e.duracion_minutos, 60)))";

    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final JdbcClient jdbcClient;
    private final CifradoDeSecretosPort cifrado;

    TranscripcionesDeMeetJdbcAdapter(JdbcClient jdbcClient, CifradoDeSecretosPort cifrado) {
        this.jdbcClient = jdbcClient;
        this.cifrado = cifrado;
    }

    @Override
    public List<PendienteDeTranscripcion> pendientes(Instant ahora, int limite) {
        return jdbcClient.sql("SELECT m.id AS sala_id, m.evento_id, m.fecha_ocurrencia, m.enlace, m.transcripcion_confirmada, "
                        + INICIO + " AS inicio, " + FIN + " AS fin, " + COLUMNAS
                        + " FROM renaser.eventos_meet m JOIN renaser.eventos e ON e.id = m.evento_id"
                        + " LEFT JOIN renaser.eventos_meet_transcripciones t ON t.evento_meet_id = m.id"
                        + " WHERE m.estado = 'CREADO' AND m.enlace IS NOT NULL"
                        + " AND (t.id IS NULL OR (t.estado IN ('ESPERANDO', 'SIN_PERMISO')"
                        + "      AND (t.proximo_intento_en IS NULL OR t.proximo_intento_en <= :ahora)))"
                        + " AND " + FIN + " <= :listo AND " + FIN + " >= :desde"
                        + " ORDER BY " + FIN + " LIMIT :limite")
                .param("ahora", utc(ahora))
                .param("listo", utc(ahora.minusSeconds(5 * 60)))
                .param("desde", utc(ahora.minusSeconds(29L * 24 * 3600)))
                .param("limite", limite)
                .query((rs, n) -> {
                    UUID salaId = rs.getObject("sala_id", UUID.class);
                    TranscripcionDeSesion actual = rs.getObject("id", UUID.class) == null ? null : aTranscripcion(rs, false);
                    boolean confirmada = rs.getBoolean("transcripcion_confirmada");
                    Boolean transcripcion = rs.wasNull() ? null : confirmada;
                    return new PendienteDeTranscripcion(salaId, EventoId.of(rs.getObject("evento_id", UUID.class)),
                            rs.getObject("fecha_ocurrencia", LocalDate.class), rs.getString("enlace"),
                            rs.getObject("inicio", OffsetDateTime.class).toInstant(), rs.getObject("fin", OffsetDateTime.class).toInstant(),
                            transcripcion, actual);
                })
                .list();
    }

    @Override
    public Optional<PendienteDeTranscripcion> salaDeLaFecha(EventoId eventoId, LocalDate fecha) {
        return jdbcClient.sql("SELECT m.id AS sala_id, m.evento_id, m.fecha_ocurrencia, m.enlace, " + INICIO + " AS inicio, " + FIN + " AS fin"
                        + " FROM renaser.eventos_meet m JOIN renaser.eventos e ON e.id = m.evento_id"
                        + " WHERE m.evento_id = :evento AND m.fecha_ocurrencia = :fecha AND m.estado = 'CREADO' AND m.enlace IS NOT NULL")
                .param("evento", eventoId.value())
                .param("fecha", Date.valueOf(fecha))
                .query((rs, n) -> new PendienteDeTranscripcion(rs.getObject("sala_id", UUID.class),
                        EventoId.of(rs.getObject("evento_id", UUID.class)), rs.getObject("fecha_ocurrencia", LocalDate.class),
                        rs.getString("enlace"), rs.getObject("inicio", OffsetDateTime.class).toInstant(),
                        rs.getObject("fin", OffsetDateTime.class).toInstant(), null, null))
                .optional();
    }

    @Override
    public void guardar(TranscripcionDeSesion t) {
        boolean conTexto = t.tieneTexto();
        jdbcClient.sql("""
                        INSERT INTO renaser.eventos_meet_transcripciones (id, evento_meet_id, estado, conferencias, entradas, largo, recortada,
                            texto_cifrado, grabacion_cifrada, version_clave, intentos, proximo_intento_en, motivo, diagnostico, obtenida_en, borrar_despues_de,
                            borrada_en, creada_en, actualizada_en)
                        VALUES (:id, :sala, :estado, :conferencias, :entradas, :largo, :recortada, :texto, :grabacion, :clave, :intentos,
                            :proximo, :motivo, CAST(:diagnostico AS jsonb), :obtenida, :vence, :borrada, :creada, :actualizada)
                        ON CONFLICT (id) DO UPDATE SET estado = EXCLUDED.estado, conferencias = EXCLUDED.conferencias,
                            entradas = EXCLUDED.entradas, largo = EXCLUDED.largo, recortada = EXCLUDED.recortada,
                            texto_cifrado = EXCLUDED.texto_cifrado, grabacion_cifrada = EXCLUDED.grabacion_cifrada,
                            version_clave = EXCLUDED.version_clave, intentos = EXCLUDED.intentos,
                            proximo_intento_en = EXCLUDED.proximo_intento_en, motivo = EXCLUDED.motivo, diagnostico = EXCLUDED.diagnostico,
                            obtenida_en = EXCLUDED.obtenida_en,
                            borrar_despues_de = EXCLUDED.borrar_despues_de, borrada_en = EXCLUDED.borrada_en,
                            actualizada_en = EXCLUDED.actualizada_en
                        """)
                .param("id", t.id())
                .param("sala", t.salaId())
                .param("estado", t.estado().name())
                .param("conferencias", t.conferencias())
                .param("entradas", t.entradas())
                .param("largo", t.largo())
                .param("recortada", t.recortada())
                .param("texto", conTexto ? cifrado.cifrar(t.texto()) : null)
                .param("grabacion", conTexto && t.enlaceDeLaGrabacion() != null ? cifrado.cifrar(t.enlaceDeLaGrabacion()) : null)
                .param("clave", conTexto ? (short) cifrado.versionDeLaClave() : null)
                .param("intentos", t.intentos())
                .param("proximo", utc(t.proximoIntento()))
                .param("motivo", t.motivo())
                .param("diagnostico", aJson(t.diagnostico()))
                .param("obtenida", utc(t.obtenidaEn()))
                .param("vence", utc(t.borrarDespuesDe()))
                .param("borrada", utc(t.borradaEn()))
                .param("creada", utc(t.creadaEn()))
                .param("actualizada", utc(t.actualizadaEn()))
                .update();
    }

    @Override
    public Optional<TranscripcionDeSesion> deLaFecha(EventoId eventoId, LocalDate fecha) {
        return jdbcClient.sql("SELECT " + COLUMNAS + " FROM renaser.eventos_meet_transcripciones t "
                        + "JOIN renaser.eventos_meet m ON m.id = t.evento_meet_id WHERE m.evento_id = :evento AND m.fecha_ocurrencia = :fecha")
                .param("evento", eventoId.value())
                .param("fecha", Date.valueOf(fecha))
                .query((rs, n) -> aTranscripcion(rs, true))
                .optional();
    }

    @Override
    public List<EstadoPorFecha> estadosDelEvento(EventoId eventoId) {
        return jdbcClient.sql("SELECT m.fecha_ocurrencia, t.estado, t.motivo, t.obtenida_en, t.borrar_despues_de, t.diagnostico::text AS diagnostico, "
                        + "(t.grabacion_cifrada IS NOT NULL) AS grabacion FROM renaser.eventos_meet_transcripciones t "
                        + "JOIN renaser.eventos_meet m ON m.id = t.evento_meet_id WHERE m.evento_id = :evento ORDER BY m.fecha_ocurrencia")
                .param("evento", eventoId.value())
                .query((rs, n) -> new EstadoPorFecha(rs.getObject("fecha_ocurrencia", LocalDate.class), rs.getString("estado"),
                        rs.getString("motivo"), instante(rs, "obtenida_en"), instante(rs, "borrar_despues_de"), rs.getBoolean("grabacion"),
                        deJson(rs.getString("diagnostico"))))
                .list();
    }

    @Override
    public List<ParaBorrar> paraBorrar(Instant ahora, int limite) {
        return jdbcClient.sql("SELECT t.id, m.evento_id, m.fecha_ocurrencia FROM renaser.eventos_meet_transcripciones t "
                        + "JOIN renaser.eventos_meet m ON m.id = t.evento_meet_id "
                        + "WHERE t.estado = 'LISTA' AND (t.borrar_despues_de <= :ahora OR m.estado = 'BORRADO') "
                        + "ORDER BY t.borrar_despues_de LIMIT :limite")
                .param("ahora", utc(ahora))
                .param("limite", limite)
                .query((rs, n) -> new ParaBorrar(rs.getObject("id", UUID.class), EventoId.of(rs.getObject("evento_id", UUID.class)),
                        rs.getObject("fecha_ocurrencia", LocalDate.class)))
                .list();
    }

    @Override
    public void borrar(UUID transcripcionId, Instant ahora) {
        jdbcClient.sql("UPDATE renaser.eventos_meet_transcripciones SET estado = 'BORRADA', texto_cifrado = NULL, grabacion_cifrada = NULL, "
                        + "version_clave = NULL, proximo_intento_en = NULL, borrada_en = :ahora, actualizada_en = :ahora WHERE id = :id")
                .param("id", transcripcionId)
                .param("ahora", utc(ahora))
                .update();
    }

    @Override
    public void registrarAcceso(UserId actor, EventoId eventoId, LocalDate fecha, String accion, String motivo, Instant en) {
        jdbcClient.sql("INSERT INTO renaser.eventos_meet_transcripciones_accesos (id, en, actor_id, evento_id, fecha, accion, motivo) "
                        + "VALUES (:id, :en, :actor, :evento, :fecha, :accion, :motivo)")
                .param("accion", accion)
                .param("id", UUID.randomUUID())
                .param("en", utc(en))
                .param("actor", actor == null ? null : actor.value())
                .param("evento", eventoId.value())
                .param("fecha", Date.valueOf(fecha))
                .param("motivo", motivo)
                .update();
    }

    @Override
    public int purgarLecturasAnterioresA(Instant limite) {
        return jdbcClient.sql("DELETE FROM renaser.eventos_meet_transcripciones_accesos WHERE en < :limite")
                .param("limite", utc(limite))
                .update();
    }

    private TranscripcionDeSesion aTranscripcion(ResultSet rs, boolean conTexto) throws SQLException {
        String texto = conTexto ? descifrar(rs.getString("texto_cifrado")) : null;
        String grabacion = conTexto ? descifrar(rs.getString("grabacion_cifrada")) : null;
        return TranscripcionDeSesion.rehydrate(rs.getObject("id", UUID.class), rs.getObject("evento_meet_id", UUID.class),
                EstadoDeTranscripcion.valueOf(rs.getString("estado")), rs.getInt("conferencias"), rs.getInt("entradas"), rs.getInt("largo"),
                rs.getBoolean("recortada"), texto, grabacion, rs.getInt("intentos"), instante(rs, "proximo_intento_en"), rs.getString("motivo"),
                deJson(rs.getString("diagnostico")), instante(rs, "obtenida_en"), instante(rs, "borrar_despues_de"), instante(rs, "borrada_en"), instante(rs, "creada_en"),
                instante(rs, "actualizada_en"));
    }

    private static String aJson(DiagnosticoDeReunion d) {
        try {
            return d == null ? null : JSON.writeValueAsString(d);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /** Un diagnóstico que no se puede leer se trata como ausente: nunca tumba el estado de la fecha. */
    private static DiagnosticoDeReunion deJson(String json) {
        try {
            return json == null ? null : JSON.readValue(json, DiagnosticoDeReunion.class);
        } catch (JsonProcessingException | RuntimeException e) {
            return null;
        }
    }

    private String descifrar(String valor) {
        return valor == null ? null : cifrado.descifrar(valor);
    }

    private static Instant instante(ResultSet rs, String columna) throws SQLException {
        OffsetDateTime valor = rs.getObject(columna, OffsetDateTime.class);
        return valor == null ? null : valor.toInstant();
    }

    private static OffsetDateTime utc(Instant instante) {
        return instante == null ? null : instante.atOffset(ZoneOffset.UTC);
    }
}
