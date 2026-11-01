package com.renaser.os.habits.infrastructure.adapter.out.persistence.categoria;

import com.renaser.os.habits.application.ports.out.categoria.RegistrarCambioDelCatalogoPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.util.Map;

/** {@code auditoria_catalogo_habitos} (V135): solo INSERT. La base ademas rechaza UPDATE y DELETE con un trigger. */
@Component
class AuditoriaDelCatalogoJdbcAdapter implements RegistrarCambioDelCatalogoPort {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;

    AuditoriaDelCatalogoJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void registrar(CambioDelCatalogo cambio) {
        jdbc.sql("""
                        INSERT INTO renaser.auditoria_catalogo_habitos (id, formacion, entidad, clave, accion, actor_id, detalle, creado_en)
                        VALUES (gen_random_uuid(), :formacion, :entidad, :clave, :accion, :actor, CAST(:detalle AS jsonb), :en)
                        """)
                .param("formacion", cambio.formacion().codigo()).param("entidad", cambio.entidad().name())
                .param("clave", cambio.clave()).param("accion", cambio.accion().name())
                .param("actor", cambio.actor().value()).param("detalle", json(cambio.detalle()))
                .param("en", Timestamp.from(cambio.cuando())).update();
    }

    private static String json(Map<String, String> detalle) {
        return detalle == null || detalle.isEmpty() ? null : JSON.writeValueAsString(detalle);
    }
}
