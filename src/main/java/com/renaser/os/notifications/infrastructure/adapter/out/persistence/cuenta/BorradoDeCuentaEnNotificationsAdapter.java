package com.renaser.os.notifications.infrastructure.adapter.out.persistence.cuenta;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.BorradoDeDatosDeCuenta;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Lo que {@code notifications} borra de una persona cuando su cuenta se borra para siempre (D-243): su
 * bandeja de avisos, sus preferencias y los tokens de sus dispositivos. Todo es de ella sola. No hay
 * archivos: {@code notificaciones.ruta_app} es un destino de navegación dentro de la app, no una clave de
 * objeto.
 */
@Component
@Order(40)
class BorradoDeCuentaEnNotificationsAdapter implements BorradoDeDatosDeCuenta {

    private static final List<String> BORRADOS = List.of(
            "DELETE FROM renaser.notificaciones WHERE usuario_id = :id",
            "DELETE FROM renaser.preferencias_notificacion WHERE usuario_id = :id",
            "DELETE FROM renaser.tokens_push WHERE usuario_id = :id");

    private final NamedParameterJdbcTemplate jdbc;

    BorradoDeCuentaEnNotificationsAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void borrarDatosDe(UserId cuenta) {
        BORRADOS.forEach(sql -> jdbc.update(sql, Map.of("id", cuenta.value())));
    }
}
