package com.renaser.os.rocks.infrastructure.adapter.out.persistence.cuenta;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.BorradoDeDatosDeCuenta;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Lo que {@code rocks} borra de una persona cuando su cuenta se borra para siempre (D-243): sus eventos del
 * Verdugo, sus objetivos diarios con sus acciones, y sus objetivos maestros con sus desgloses semanales y
 * mensuales. Todo es de ella sola y no hay archivos (las fotos de un objetivo son {@code evidencias}).
 *
 * <p>Va antes que {@code habits}: {@code eventos_verdugo} apunta a {@code registros_habito}.
 */
@Component
@Order(120)
class BorradoDeCuentaEnRocksAdapter implements BorradoDeDatosDeCuenta {

    /** Hijos antes que padres: las diarias apuntan a las semanales y estas a las maestras. */
    private static final List<String> BORRADOS = List.of(
            "DELETE FROM renaser.eventos_verdugo WHERE participante_id = :id",
            "DELETE FROM renaser.acciones_diarias WHERE roca_diaria_id IN "
                    + "(SELECT id FROM renaser.rocas_diarias WHERE participante_id = :id)",
            "DELETE FROM renaser.rocas_diarias WHERE participante_id = :id",
            "DELETE FROM renaser.rocas_semanales WHERE roca_maestra_id IN "
                    + "(SELECT id FROM renaser.rocas_maestras WHERE participante_id = :id)",
            "DELETE FROM renaser.rocas_mensuales WHERE roca_maestra_id IN "
                    + "(SELECT id FROM renaser.rocas_maestras WHERE participante_id = :id)",
            "DELETE FROM renaser.rocas_maestras WHERE participante_id = :id");

    private final NamedParameterJdbcTemplate jdbc;

    BorradoDeCuentaEnRocksAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void borrarDatosDe(UserId cuenta) {
        BORRADOS.forEach(sql -> jdbc.update(sql, Map.of("id", cuenta.value())));
    }
}
