package com.renaser.os.community.infrastructure.adapter.out.persistence.sinred;

import com.renaser.os.community.application.ports.out.publicacion.PublicacionesSinRedPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** {@link PublicacionesSinRedPort} sobre {@code publicaciones_sin_red} (V189). */
@Component
class PublicacionesSinRedJdbcAdapter implements PublicacionesSinRedPort {

    private final NamedParameterJdbcTemplate jdbc;

    PublicacionesSinRedJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> publicacionDe(UserId autorId, UUID idCliente) {
        return jdbc.query("SELECT publicacion_id FROM renaser.publicaciones_sin_red WHERE autor_id = :autor AND id_cliente = :id",
                        new MapSqlParameterSource("autor", autorId.value()).addValue("id", idCliente),
                        (rs, i) -> rs.getObject("publicacion_id", UUID.class))
                .stream().findFirst();
    }

    @Override
    public void registrar(UserId autorId, UUID idCliente, UUID publicacionId, Instant hechoEn, String origenDeLaHora) {
        jdbc.update("INSERT INTO renaser.publicaciones_sin_red (autor_id, id_cliente, publicacion_id, hecho_en, origen_de_la_hora) "
                        + "VALUES (:autor, :id, :publicacion, :hecho, :origen) ON CONFLICT (autor_id, id_cliente) DO NOTHING",
                new MapSqlParameterSource("autor", autorId.value()).addValue("id", idCliente)
                        .addValue("publicacion", publicacionId).addValue("hecho", Timestamp.from(hechoEn))
                        .addValue("origen", origenDeLaHora));
    }
}
