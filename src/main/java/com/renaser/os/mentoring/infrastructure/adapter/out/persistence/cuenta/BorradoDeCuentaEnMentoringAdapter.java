package com.renaser.os.mentoring.infrastructure.adapter.out.persistence.cuenta;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.BorradoDeDatosDeCuenta;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Lo que {@code mentoring} borra de una persona cuando su cuenta se borra para siempre (D-243): sus fotos semanales de
 * huecos de horario (spec 015, V120) y las notas privadas sobre ella o escritas por ella (spec 055, V198). Son de ella sola y no guardan archivos. La foto es append-only desde el código
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
        Map<String, Object> id = Map.of("id", cuenta.value());
        jdbc.update("DELETE FROM renaser.huecos_semana WHERE participante_id = :id", id);
        // Spec 055 (V198): las notas sobre ella y las que escribió, con su historia. La FK del autor es RESTRICT: sin esto la baja fallaría.
        jdbc.update("DELETE FROM renaser.notas_del_mentor_versiones WHERE nota_id IN "
                + "(SELECT id FROM renaser.notas_del_mentor WHERE aprendiz_id = :id OR autor_id = :id)", id);
        jdbc.update("DELETE FROM renaser.notas_del_mentor WHERE aprendiz_id = :id OR autor_id = :id", id);
    }
}
