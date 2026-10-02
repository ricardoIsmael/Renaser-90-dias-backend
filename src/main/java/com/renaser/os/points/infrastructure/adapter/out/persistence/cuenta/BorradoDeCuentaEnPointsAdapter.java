package com.renaser.os.points.infrastructure.adapter.out.persistence.cuenta;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.BorradoDeDatosDeCuenta;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Lo que {@code points} borra de una persona cuando su cuenta se borra para siempre (D-243): sus filas en
 * los snapshots del ranking, su puntaje y sus ajustes de liga, su historial de coherencia y su semáforo
 * (días, semanas y pausas). {@code verificacion_puntos_liga} es una VIEW y no guarda nada.
 *
 * <p>Sacarla del snapshot del ranking deja un hueco en las posiciones de esa fecha; no se renumera acá
 * porque el snapshot de hoy se regenera solo, y la lectura ya renumera al filtrar cuentas cerradas
 * ({@code RankingService.consultar}).
 */
@Component
@Order(130)
class BorradoDeCuentaEnPointsAdapter implements BorradoDeDatosDeCuenta {

    private static final List<String> BORRADOS = List.of(
            "DELETE FROM renaser.ranking_aprendices WHERE participante_id = :id",
            "DELETE FROM renaser.puntajes_participante WHERE participante_id = :id",
            "DELETE FROM renaser.ajustes_puntos_liga WHERE participante_id = :id",
            "DELETE FROM renaser.historial_coherencia WHERE participante_id = :id",
            "DELETE FROM renaser.semaforo_dias WHERE participante_id = :id",
            "DELETE FROM renaser.semaforo_pausas WHERE usuario_id = :id",
            "DELETE FROM renaser.semaforo_semanas WHERE participante_id = :id");

    private final NamedParameterJdbcTemplate jdbc;

    BorradoDeCuentaEnPointsAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void borrarDatosDe(UserId cuenta) {
        BORRADOS.forEach(sql -> jdbc.update(sql, Map.of("id", cuenta.value())));
    }
}
