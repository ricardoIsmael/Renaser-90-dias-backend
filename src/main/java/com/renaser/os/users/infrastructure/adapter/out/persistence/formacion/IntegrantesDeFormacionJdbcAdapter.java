package com.renaser.os.users.infrastructure.adapter.out.persistence.formacion;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.IntegrantesDeFormacionFinder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Los integrantes de una formación: sus aprendices activos ({@code participantes_programa}) y su personal asignado ({@code formaciones_personal}). */
@Component
class IntegrantesDeFormacionJdbcAdapter implements IntegrantesDeFormacionFinder {

    private static final String INTEGRANTES = """
            SELECT u.id FROM renaser.usuarios u JOIN renaser.participantes_programa pp ON pp.usuario_id = u.id
            WHERE pp.formacion = :f AND u.rol::text = 'APRENDIZ' AND u.estado::text = 'ACTIVO' AND NOT u.es_cuenta_de_prueba
            UNION
            SELECT u.id FROM renaser.usuarios u JOIN renaser.formaciones_personal fp ON fp.usuario_id = u.id
            WHERE fp.formacion = :f AND u.estado::text = 'ACTIVO' AND NOT u.es_cuenta_de_prueba
            """;

    private final JdbcClient jdbc;

    IntegrantesDeFormacionJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<UserId> deLaFormacion(Formacion formacion) {
        return jdbc.sql(INTEGRANTES).param("f", formacion.codigo()).query((rs, n) -> UserId.of(rs.getObject("id", UUID.class))).list().stream()
                .collect(Collectors.toSet());
    }
}
