package com.renaser.os.shared.infrastructure.persistence;

import com.renaser.os.shared.application.ports.out.EnviosSinRedPort;
import com.renaser.os.shared.domain.CodigoNoAplicado;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

/** {@code renaser.envios_sin_red} (V188, V190). Un envío de otra persona con el mismo id no se ve: se filtra por participante. */
@Component
class EnviosSinRedJdbcAdapter implements EnviosSinRedPort {

    private final NamedParameterJdbcTemplate jdbc;

    EnviosSinRedJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void tomarElCandado(UUID idCliente) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(CAST(:id AS text), 0))::text",
                new MapSqlParameterSource("id", idCliente), String.class);
    }

    @Override
    public Optional<EnvioRegistrado> buscar(UUID idCliente, UserId participanteId) {
        return jdbc.query("SELECT registro_id, motivo_sin_puntos, codigo, mensaje FROM renaser.envios_sin_red "
                        + "WHERE id_cliente = :id AND participante_id = :participante",
                new MapSqlParameterSource("id", idCliente).addValue("participante", participanteId.value()),
                (rs, i) -> new EnvioRegistrado(rs.getObject("registro_id", UUID.class), rs.getString("motivo_sin_puntos"),
                        rs.getString("codigo") == null ? null : CodigoNoAplicado.valueOf(rs.getString("codigo")),
                        rs.getString("mensaje")))
                .stream().findFirst();
    }

    @Override
    public void registrar(EnvioNuevo envio) {
        jdbc.update("INSERT INTO renaser.envios_sin_red (id_cliente, participante_id, registro_id, motivo_sin_puntos, tipo, recurso, "
                        + "resultado, codigo, mensaje, hecho_en, origen_de_la_hora) "
                        + "VALUES (:id, :participante, :registro, :motivo, :tipo, :recurso, :resultado, :codigo, :mensaje, :hecho, :origen) "
                        + "ON CONFLICT (id_cliente) DO NOTHING",
                new MapSqlParameterSource("id", envio.idCliente()).addValue("participante", envio.participanteId().value())
                        .addValue("registro", envio.registroId()).addValue("motivo", envio.motivoSinPuntos())
                        .addValue("tipo", envio.tipo()).addValue("recurso", envio.recurso())
                        .addValue("resultado", envio.noAplicado() == null ? "APLICADO" : "NO_APLICADO")
                        .addValue("codigo", envio.noAplicado() == null ? null : envio.noAplicado().name())
                        .addValue("mensaje", envio.mensaje())
                        .addValue("hecho", envio.hechoEn() == null ? null : Timestamp.from(envio.hechoEn()))
                        .addValue("origen", envio.origenDeLaHora() == null ? null : envio.origenDeLaHora().name()));
    }
}
