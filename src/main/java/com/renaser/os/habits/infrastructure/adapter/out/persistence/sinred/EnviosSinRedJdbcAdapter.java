package com.renaser.os.habits.infrastructure.adapter.out.persistence.sinred;

import com.renaser.os.habits.application.ports.out.sinred.EnviosSinRedPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** {@code renaser.envios_sin_red} (V188). Un envío de otra persona con el mismo id no se ve: se filtra por participante. */
@Component
class EnviosSinRedJdbcAdapter implements EnviosSinRedPort {

    private final NamedParameterJdbcTemplate jdbc;

    EnviosSinRedJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<EnvioRegistrado> buscar(UUID idCliente, UserId participanteId) {
        return jdbc.query("SELECT registro_id, motivo_sin_puntos FROM renaser.envios_sin_red "
                        + "WHERE id_cliente = :id AND participante_id = :participante",
                new MapSqlParameterSource("id", idCliente).addValue("participante", participanteId.value()),
                (rs, i) -> new EnvioRegistrado(rs.getObject("registro_id", UUID.class), rs.getString("motivo_sin_puntos")))
                .stream().findFirst();
    }

    @Override
    public void registrar(UUID idCliente, UserId participanteId, UUID registroId, String motivoSinPuntos) {
        jdbc.update("INSERT INTO renaser.envios_sin_red (id_cliente, participante_id, registro_id, motivo_sin_puntos) "
                        + "VALUES (:id, :participante, :registro, :motivo) ON CONFLICT (id_cliente) DO NOTHING",
                new MapSqlParameterSource("id", idCliente).addValue("participante", participanteId.value())
                        .addValue("registro", registroId).addValue("motivo", motivoSinPuntos));
    }
}
