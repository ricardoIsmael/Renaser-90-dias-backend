package com.renaser.os.users.infrastructure.adapter.out.persistence.formacion;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.out.formacion.ParticipacionesAnterioresPort;
import com.renaser.os.users.domain.model.formacion.ParticipacionAnterior;
import com.renaser.os.users.domain.model.formacion.ResultadoDelTraslado;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/** {@code participaciones_anteriores} (V127): solo se agrega y se lee. */
@Component
class ParticipacionesAnterioresJdbcAdapter implements ParticipacionesAnterioresPort {

    private static final String INSERTAR = """
            INSERT INTO renaser.participaciones_anteriores
              (id, usuario_id, formacion, duracion_dias, fecha_inicio, fecha_fin, dia_final, resultado, motivo,
               puntos_liga, coherencia, racha_maxima, trasladado_a, trasladado_por, creado_en)
            VALUES (:id, :usuario, :formacion, :duracion, :inicio, :fin, :diaFinal, :resultado, :motivo,
                    :puntos, :coherencia, :racha, :hacia, :por, :creadoEn)
            """;

    private final JdbcClient jdbc;

    ParticipacionesAnterioresJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void guardar(ParticipacionAnterior a) {
        jdbc.sql(INSERTAR)
                .param("id", a.id()).param("usuario", a.usuarioId().value()).param("formacion", a.formacion().codigo())
                .param("duracion", a.duracionDias()).param("inicio", a.fechaInicio() == null ? null : Date.valueOf(a.fechaInicio()))
                .param("fin", Date.valueOf(a.fechaFin())).param("diaFinal", a.diaFinal()).param("resultado", a.resultado().name())
                .param("motivo", a.motivo()).param("puntos", a.puntosLiga()).param("coherencia", a.coherencia())
                .param("racha", a.rachaMaxima()).param("hacia", a.trasladadoA().codigo())
                .param("por", a.trasladadoPor() == null ? null : a.trasladadoPor().value())
                .param("creadoEn", Timestamp.from(a.creadoEn()))
                .update();
    }

    @Override
    public List<ParticipacionAnterior> de(UserId persona) {
        return jdbc.sql("SELECT * FROM renaser.participaciones_anteriores WHERE usuario_id = :id ORDER BY creado_en DESC")
                .param("id", persona.value()).query((rs, n) -> aParticipacion(rs)).list();
    }

    private static ParticipacionAnterior aParticipacion(ResultSet rs) throws SQLException {
        Date inicio = rs.getDate("fecha_inicio");
        UUID por = rs.getObject("trasladado_por", UUID.class);
        return new ParticipacionAnterior(rs.getObject("id", UUID.class), UserId.of(rs.getObject("usuario_id", UUID.class)),
                Formacion.desde(rs.getString("formacion")), rs.getInt("duracion_dias"), inicio == null ? null : inicio.toLocalDate(),
                rs.getDate("fecha_fin").toLocalDate(), rs.getInt("dia_final"), ResultadoDelTraslado.valueOf(rs.getString("resultado")),
                rs.getString("motivo"), (Integer) rs.getObject("puntos_liga"), rs.getBigDecimal("coherencia"),
                (Integer) (rs.getObject("racha_maxima") == null ? null : rs.getInt("racha_maxima")), Formacion.desde(rs.getString("trasladado_a")),
                por == null ? null : UserId.of(por), rs.getTimestamp("creado_en").toInstant());
    }
}
