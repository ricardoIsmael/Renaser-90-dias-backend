package com.renaser.os.users.infrastructure.adapter.out.persistence.formacion;

import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.infrastructure.persistence.FiltroDeFormacionSql;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.application.ports.out.formacion.CargarPertenenciaPort;
import com.renaser.os.users.domain.model.formacion.ReglaDeAlcance;
import com.renaser.os.users.domain.model.formacion.ReglaDeAlcance.Asignacion;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Una lectura por PK a {@code usuarios} + {@code participantes_programa}; y solo si es personal, una segunda a {@code formaciones_personal}. Es lo que
 * paga cada pedido que filtra por formación (spec 022: «una consulta por PK por pedido»).
 */
@Component
class PertenenciaJdbcAdapter implements CargarPertenenciaPort {

    private static final String PERTENENCIA = """
            SELECT u.rol::text AS rol, pp.formacion, COALESCE(pp.duracion_dias, 90) AS duracion_dias,
                   (pp.programa_activado_en IS NOT NULL) AS activado
            FROM renaser.usuarios u
            LEFT JOIN renaser.participantes_programa pp ON pp.usuario_id = u.id
            WHERE u.id = :id
            """;

    private static final String ASIGNADAS = """
            SELECT formacion, activa FROM renaser.formaciones_personal WHERE usuario_id = :id ORDER BY formacion
            """;

    private static final String DENTRO_DEL_ALCANCE = """
            SELECT u.id
            FROM renaser.usuarios u
            LEFT JOIN renaser.participantes_programa pp ON pp.usuario_id = u.id
            WHERE u.id IN (:ids)
              AND CASE WHEN u.rol::text = 'APRENDIZ'
                       THEN COALESCE(pp.formacion, 'ESCUELA') IN (:formaciones)
                       ELSE pp.formacion IN (:formaciones)
                            OR EXISTS (SELECT 1 FROM renaser.formaciones_personal fp
                                       WHERE fp.usuario_id = u.id AND fp.formacion IN (:formaciones))
                  END
            """;

    private final JdbcClient jdbc;

    PertenenciaJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Pertenencia> de(UserId usuario) {
        return jdbc.sql(PERTENENCIA).param("id", usuario.value())
                .query((rs, n) -> {
                    UserRole rol = RolDeLaBase.aRol(rs.getString("rol"));
                    String formacion = rs.getString("formacion");
                    return new Pertenencia(rol, formacion == null ? null : Formacion.desde(formacion),
                            rs.getInt("duracion_dias"), rs.getBoolean("activado"), asignadasSiEsPersonal(rol, usuario));
                })
                .optional();
    }

    @Override
    public Set<UserId> dentroDelAlcance(Collection<UserId> personas, AlcanceDeFormacion alcance) {
        return jdbc.sql(DENTRO_DEL_ALCANCE)
                .param("ids", personas.stream().map(UserId::value).toList())
                .param("formaciones", FiltroDeFormacionSql.codigos(alcance))
                .query(UUID.class).list().stream().map(UserId::of).collect(Collectors.toUnmodifiableSet());
    }

    private List<Asignacion> asignadasSiEsPersonal(UserRole rol, UserId usuario) {
        if (!ReglaDeAlcance.esPersonalConFormaciones(rol)) {
            return List.of();
        }
        return jdbc.sql(ASIGNADAS).param("id", usuario.value())
                .query((rs, n) -> new Asignacion(Formacion.desde(rs.getString("formacion")), rs.getBoolean("activa")))
                .list();
    }
}
