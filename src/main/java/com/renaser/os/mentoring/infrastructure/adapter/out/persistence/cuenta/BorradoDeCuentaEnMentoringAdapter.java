package com.renaser.os.mentoring.infrastructure.adapter.out.persistence.cuenta;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.BorradoDeDatosDeCuenta;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Lo que {@code mentoring} borra de una persona cuando su cuenta se borra para siempre (D-243): sus fotos semanales de
 * huecos de horario (spec 015, V120). Son de ella sola y no guardan archivos. La foto es append-only desde el código
 * (nadie la actualiza ni la borra), pero esto no es «el código» de la función: es la baja de la cuenta.
 */
@Component
@Order(65)
class BorradoDeCuentaEnMentoringAdapter implements BorradoDeDatosDeCuenta {

    private final NamedParameterJdbcTemplate jdbc;

    BorradoDeCuentaEnMentoringAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void borrarDatosDe(UserId cuenta) {
        jdbc.update("DELETE FROM renaser.huecos_semana WHERE participante_id = :id", Map.of("id", cuenta.value()));
    }
}
