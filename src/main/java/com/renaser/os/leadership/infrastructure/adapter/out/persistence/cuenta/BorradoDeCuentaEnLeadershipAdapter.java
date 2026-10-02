package com.renaser.os.leadership.infrastructure.adapter.out.persistence.cuenta;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.BorradoDeDatosDeCuenta;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Lo que {@code leadership} borra de una persona cuando su cuenta se borra para siempre (D-243): las
 * observaciones del Líder de Mentores SOBRE ella ({@code mentor_id}) y también las que ella ESCRIBIÓ
 * ({@code autor_id}).
 *
 * <p><b>Por qué se borran las que escribió y no solo pierden el autor.</b> Decisión de D-243: son texto
 * suyo, y {@code autor_id} es {@code NOT NULL} con FK {@code ON DELETE RESTRICT} (V89) que la app del Líder
 * lee como obligatorio. Volverlo nullable para conservarlas cambiaba el contrato de esa pantalla; borrarlas
 * acá, antes del DELETE de {@code usuarios}, hace que la FK nunca se dispare (V90 lo explica).
 */
@Component
@Order(60)
class BorradoDeCuentaEnLeadershipAdapter implements BorradoDeDatosDeCuenta {

    private final NamedParameterJdbcTemplate jdbc;

    BorradoDeCuentaEnLeadershipAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void borrarDatosDe(UserId cuenta) {
        jdbc.update("DELETE FROM renaser.observaciones_mentor WHERE mentor_id = :id OR autor_id = :id",
                Map.of("id", cuenta.value()));
    }
}
