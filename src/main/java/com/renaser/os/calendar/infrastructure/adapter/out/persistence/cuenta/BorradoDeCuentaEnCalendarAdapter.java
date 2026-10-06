package com.renaser.os.calendar.infrastructure.adapter.out.persistence.cuenta;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.BorradoDeDatosDeCuenta;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Lo que {@code calendar} borra o anonimiza de una persona cuando su cuenta se borra para siempre (D-243):
 * sus confirmaciones de asistencia y sus recordatorios programados. Los eventos que creó NO se borran: son
 * del calendario de su grupo o de todo el programa y los siguen viendo los demás; pierden el autor
 * ({@code eventos.creado_por} en NULL, lo mismo que haría la FK {@code ON DELETE SET NULL}). Por eso
 * tampoco se reporta la portada de esos eventos como archivo a borrar.
 *
 * <p>Asistencia (V93, D-256): se borran su historial de respuestas y sus marcas («vino a tal evento» es dato
 * suyo); lo que marcó o cerró al pasar lista en eventos de otros sobrevive y pierde el autor, como
 * {@code eventos.creado_por}. Las FKs hacen lo mismo; se escribe acá para que el borrado no dependa de ellas.
 */
@Component
@Order(30)
class BorradoDeCuentaEnCalendarAdapter implements BorradoDeDatosDeCuenta {

    private static final List<String> BORRADOS = List.of(
            "DELETE FROM renaser.recordatorios_evento WHERE usuario_id = :id",
            "DELETE FROM renaser.confirmaciones_evento WHERE usuario_id = :id",
            "DELETE FROM renaser.historial_confirmaciones_evento WHERE usuario_id = :id",
            "DELETE FROM renaser.asistencias_evento WHERE usuario_id = :id",
            "UPDATE renaser.asistencias_evento SET marcado_por = NULL WHERE marcado_por = :id",
            "UPDATE renaser.listas_asistencia_evento SET cerrada_por = NULL WHERE cerrada_por = :id",
            "UPDATE renaser.eventos SET creado_por = NULL WHERE creado_por = :id");

    private final NamedParameterJdbcTemplate jdbc;

    BorradoDeCuentaEnCalendarAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void borrarDatosDe(UserId cuenta) {
        BORRADOS.forEach(sql -> jdbc.update(sql, Map.of("id", cuenta.value())));
    }
}
