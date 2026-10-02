package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.cuenta;

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
 * Lo que {@code onboarding} borra o anonimiza de una persona cuando su cuenta se borra para siempre
 * (D-243): sus respuestas a todos los flujos (Ficha Inicial, Caja Renaser y los demás), sus grabaciones
 * de la V90 y sus medias, su estado y sus etapas completadas, y su mapa de acciones y protocolos.
 *
 * <p><b>La Caja Renaser (V82) no tiene tablas propias</b>: sus datos de envío son respuestas del flujo
 * {@code caja_renaser}, cada paso es una fila de {@code etapas_onboarding_completadas} con
 * {@code flujo = 'caja:<n>:<PASO>'} y las fotos son {@code medias_onboarding} a nombre del aprendiz
 * ({@code onboarding/<aprendizId>/caja/...}). Todo eso cae con lo de arriba. Donde la persona es el Admin
 * que marcó un paso de la caja de OTRO ({@code marcada_por}), el paso es del otro y pierde el autor.
 *
 * <p>Las claves de {@code medias_onboarding} las arma el servidor con el id del dueño adentro, así que
 * ninguna fila que sobreviva las nombra.
 */
@Component
@Order(90)
class BorradoDeCuentaEnOnboardingAdapter implements BorradoDeDatosDeCuenta {

    /** Hijos antes que padres: las grabaciones apuntan a la media con FK RESTRICT. */
    private static final List<String> BORRADOS = List.of(
            "DELETE FROM renaser.respuestas_onboarding WHERE usuario_id = :id",
            "DELETE FROM renaser.grabaciones_v90 WHERE usuario_id = :id",
            "DELETE FROM renaser.medias_onboarding WHERE usuario_id = :id",
            "DELETE FROM renaser.etapas_onboarding_completadas WHERE usuario_id = :id",
            "UPDATE renaser.etapas_onboarding_completadas SET marcada_por = NULL WHERE marcada_por = :id",
            "DELETE FROM renaser.estado_onboarding WHERE usuario_id = :id",
            "DELETE FROM renaser.dias_accion_mapa WHERE accion_mapa_id IN "
                    + "(SELECT id FROM renaser.acciones_mapa WHERE usuario_id = :id)",
            "DELETE FROM renaser.acciones_mapa WHERE usuario_id = :id",
            "DELETE FROM renaser.protocolos_reemplazo_mapa WHERE usuario_id = :id");

    private final NamedParameterJdbcTemplate jdbc;

    BorradoDeCuentaEnOnboardingAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<String> archivosDe(UserId cuenta) {
        return new HashSet<>(jdbc.queryForList("SELECT ruta_storage FROM renaser.medias_onboarding "
                + "WHERE usuario_id = :id AND ruta_storage IS NOT NULL", Map.of("id", cuenta.value()), String.class));
    }

    @Override
    public void borrarDatosDe(UserId cuenta) {
        BORRADOS.forEach(sql -> jdbc.update(sql, Map.of("id", cuenta.value())));
    }
}
