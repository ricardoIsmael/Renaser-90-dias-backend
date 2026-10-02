package com.renaser.os.users.infrastructure.adapter.out.persistence.eliminacion;

import com.renaser.os.users.application.ports.out.eliminacion.RegistrarEliminacionPort;
import com.renaser.os.users.domain.model.user.RegistroDeEliminacion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;

/** {@code auditoria_eliminacion_cuentas} (V90): solo INSERT, append-only. */
@Component
class RegistroDeEliminacionJdbcAdapter implements RegistrarEliminacionPort {

    private final JdbcTemplate jdbc;

    RegistroDeEliminacionJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void registrar(RegistroDeEliminacion registro) {
        jdbc.update("""
                        INSERT INTO renaser.auditoria_eliminacion_cuentas
                            (cuenta_id, rol, accion, via, actor_id, ocurrido_en)
                        VALUES (?, ?, ?, ?, ?, ?)""",
                registro.cuentaId().value(), registro.rol().name(), registro.accion().name(),
                registro.via() == null ? null : registro.via().name(),
                registro.actorId() == null ? null : registro.actorId().value(),
                Timestamp.from(registro.ocurridoEn()));
    }
}
