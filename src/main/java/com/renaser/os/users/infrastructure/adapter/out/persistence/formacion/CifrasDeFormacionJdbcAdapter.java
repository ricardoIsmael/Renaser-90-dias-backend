package com.renaser.os.users.infrastructure.adapter.out.persistence.formacion;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.users.api.CifrasDeFormacionFinder.CifrasDePersonas;
import com.renaser.os.users.application.ports.out.formacion.CifrasDeFormacionPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Las cifras del centro «Formaciones». Mismo criterio que el listado de Personas ({@code ConsultarResumenParticipacionPersistenceAdapter}): cuentas reales
 * (sin las de prueba) de cualquier estado, aprendices por su participación y personal por sus asignaciones.
 */
@Component
class CifrasDeFormacionJdbcAdapter implements CifrasDeFormacionPort {

    private static final String APRENDICES = """
            SELECT COUNT(*) AS aprendices,
                   COUNT(*) FILTER (WHERE pp.programa_activado_en IS NULL) AS por_empezar,
                   COUNT(*) FILTER (WHERE pp.programa_activado_en IS NOT NULL AND NOT pp.programa_completado) AS en_curso,
                   COUNT(*) FILTER (WHERE pp.programa_completado) AS terminaron,
                   COUNT(*) FILTER (WHERE pp.celula_id IS NULL) AS sin_grupo
            FROM renaser.participantes_programa pp
            JOIN renaser.usuarios u ON u.id = pp.usuario_id
            WHERE u.rol::text = 'APRENDIZ' AND NOT u.es_cuenta_de_prueba AND pp.formacion = :formacion
            """;

    private static final String PERSONAL = """
            SELECT COUNT(*) FROM renaser.formaciones_personal fp
            JOIN renaser.usuarios u ON u.id = fp.usuario_id
            WHERE NOT u.es_cuenta_de_prueba AND fp.formacion = :formacion
            """;

    private static final String SIN_FORMACION = """
            SELECT COUNT(*) FROM renaser.usuarios u
            WHERE u.rol::text IN ('MENTOR', 'GUIA_APRENDIZ', 'LIDER_MENTORES') AND u.estado::text = 'ACTIVO' AND NOT u.es_cuenta_de_prueba
              AND NOT EXISTS (SELECT 1 FROM renaser.formaciones_personal fp WHERE fp.usuario_id = u.id)
            """;

    private final JdbcClient jdbc;

    CifrasDeFormacionJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CifrasDePersonas de(Formacion formacion) {
        int personal = jdbc.sql(PERSONAL).param("formacion", formacion.codigo()).query(Integer.class).single();
        return jdbc.sql(APRENDICES).param("formacion", formacion.codigo())
                .query((rs, n) -> new CifrasDePersonas(rs.getInt("aprendices"), rs.getInt("por_empezar"), rs.getInt("en_curso"),
                        rs.getInt("terminaron"), rs.getInt("sin_grupo"), personal))
                .single();
    }

    @Override
    public int personalSinFormacion() {
        return jdbc.sql(SIN_FORMACION).query(Integer.class).single();
    }
}
