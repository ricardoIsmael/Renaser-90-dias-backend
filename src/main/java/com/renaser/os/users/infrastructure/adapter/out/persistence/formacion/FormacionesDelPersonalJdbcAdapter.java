package com.renaser.os.users.infrastructure.adapter.out.persistence.formacion;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.out.formacion.FormacionesDelPersonalPort;
import com.renaser.os.users.domain.model.formacion.ReglaDeAlcance.Asignacion;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/** {@code formaciones_personal} (V126). Una sola activa por persona: la garantiza el índice único parcial, así que se desmarca antes de marcar. */
@Component
class FormacionesDelPersonalJdbcAdapter implements FormacionesDelPersonalPort {

    private final JdbcClient jdbc;

    FormacionesDelPersonalJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Asignacion> de(UserId persona) {
        return jdbc.sql("SELECT formacion, activa FROM renaser.formaciones_personal WHERE usuario_id = :id ORDER BY formacion")
                .param("id", persona.value())
                .query((rs, n) -> new Asignacion(Formacion.desde(rs.getString("formacion")), rs.getBoolean("activa")))
                .list();
    }

    @Override
    public void reemplazar(UserId persona, Set<Formacion> formaciones, Formacion activa, UserId asignadaPor) {
        jdbc.sql("UPDATE renaser.formaciones_personal SET activa = false WHERE usuario_id = :id AND activa")
                .param("id", persona.value()).update();
        jdbc.sql("DELETE FROM renaser.formaciones_personal WHERE usuario_id = :id AND NOT (formacion IN (:formaciones))")
                .param("id", persona.value()).param("formaciones", codigos(formaciones)).update();
        for (Formacion formacion : formaciones) {
            jdbc.sql("""
                    INSERT INTO renaser.formaciones_personal (usuario_id, formacion, activa, asignada_por)
                    VALUES (:id, :formacion, false, :por) ON CONFLICT (usuario_id, formacion) DO NOTHING
                    """)
                    .param("id", persona.value()).param("formacion", formacion.codigo())
                    .param("por", asignadaPor == null ? null : asignadaPor.value()).update();
        }
        marcarActiva(persona, activa);
    }

    @Override
    public void marcarActiva(UserId persona, Formacion formacion) {
        jdbc.sql("UPDATE renaser.formaciones_personal SET activa = false WHERE usuario_id = :id AND activa")
                .param("id", persona.value()).update();
        jdbc.sql("UPDATE renaser.formaciones_personal SET activa = true WHERE usuario_id = :id AND formacion = :formacion")
                .param("id", persona.value()).param("formacion", formacion.codigo()).update();
    }

    @Override
    public void asignarSiNoTieneNinguna(UserId persona, Formacion formacion) {
        jdbc.sql("""
                INSERT INTO renaser.formaciones_personal (usuario_id, formacion, activa)
                SELECT :id, :formacion, true
                WHERE NOT EXISTS (SELECT 1 FROM renaser.formaciones_personal WHERE usuario_id = :id)
                """)
                .param("id", persona.value()).param("formacion", formacion.codigo()).update();
    }

    @Override
    public void quitarTodas(UserId persona) {
        jdbc.sql("DELETE FROM renaser.formaciones_personal WHERE usuario_id = :id").param("id", persona.value()).update();
    }

    private static List<String> codigos(Set<Formacion> formaciones) {
        return formaciones.isEmpty() ? List.of("-") : formaciones.stream().map(Formacion::codigo).toList();
    }
}
