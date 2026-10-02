package com.renaser.os.support.infrastructure.adapter.out.persistence.cuenta;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.BorradoDeDatosDeCuenta;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lo que {@code support} borra o anonimiza de una persona cuando su cuenta se borra para siempre (D-243):
 * sus tickets de soporte (con su adjunto) y sus tickets al mentor. Donde respondió como mentor el ticket
 * de OTRA persona ({@code tickets_mentor.respondido_por}), el ticket es de la otra persona y sobrevive sin
 * autor de la respuesta.
 *
 * <p>El adjunto no se comparte: la clave la arma el servidor con el id del dueño y
 * {@code TicketSoporteService.exigirAdjuntoPropio} rechaza una ajena, así que ninguna fila que sobreviva
 * la puede nombrar y no hace falta responder {@link #archivosEnUsoTrasBorrar}.
 */
@Component
@Order(50)
class BorradoDeCuentaEnSupportAdapter implements BorradoDeDatosDeCuenta {

    private static final List<String> BORRADOS = List.of(
            "DELETE FROM renaser.tickets_soporte WHERE usuario_id = :id",
            "DELETE FROM renaser.tickets_mentor WHERE participante_id = :id",
            "UPDATE renaser.tickets_mentor SET respondido_por = NULL WHERE respondido_por = :id");

    private final NamedParameterJdbcTemplate jdbc;

    BorradoDeCuentaEnSupportAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<String> archivosDe(UserId cuenta) {
        return new HashSet<>(jdbc.queryForList("SELECT adjunto_ruta FROM renaser.tickets_soporte "
                + "WHERE usuario_id = :id AND adjunto_ruta IS NOT NULL", Map.of("id", cuenta.value()), String.class));
    }

    @Override
    public void borrarDatosDe(UserId cuenta) {
        BORRADOS.forEach(sql -> jdbc.update(sql, Map.of("id", cuenta.value())));
    }
}
