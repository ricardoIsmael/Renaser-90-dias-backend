package com.renaser.os.users.infrastructure.adapter.out.persistence.whatsapp;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.out.whatsapp.NumeroEnSolicitudPendientePort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** {@code solicitudes_cuenta.telefono} de las solicitudes PENDIENTES, comparado sin separadores (spec 051). Las aprobadas ya pasaron a la Ficha. */
@Component
class NumeroEnSolicitudPendienteJdbcAdapter implements NumeroEnSolicitudPendientePort {

    private final JdbcTemplate jdbc;

    NumeroEnSolicitudPendienteJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean hayPendienteDeOtra(String numeroInternacional, UserId excepto) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM renaser.solicitudes_cuenta
                                WHERE estado = 'PENDIENTE'
                                  AND telefono IS NOT NULL
                                  AND usuario_id <> ?
                                  AND regexp_replace(telefono, '[\\s().-]', '', 'g') = ?)
                """, Boolean.class, excepto == null ? new UUID(0L, 0L) : excepto.value(), numeroInternacional));
    }
}
