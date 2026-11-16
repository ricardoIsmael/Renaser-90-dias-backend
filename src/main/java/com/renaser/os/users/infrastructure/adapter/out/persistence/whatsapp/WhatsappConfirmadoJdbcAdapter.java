package com.renaser.os.users.infrastructure.adapter.out.persistence.whatsapp;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.out.whatsapp.WhatsappConfirmadoPort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** {@code whatsapp_confirmados} (V192, spec 051) por SQL: una fila por cuenta y un numero en una sola cuenta (UNIQUE). */
@Component
class WhatsappConfirmadoJdbcAdapter implements WhatsappConfirmadoPort {

    private final JdbcTemplate jdbc;

    WhatsappConfirmadoJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> de(UserId cuenta) {
        return jdbc.query("SELECT numero FROM renaser.whatsapp_confirmados WHERE usuario_id = ?",
                (rs, fila) -> rs.getString(1), cuenta.value()).stream().findFirst();
    }

    @Override
    public boolean guardar(UserId cuenta, String numero) {
        try {
            jdbc.update("INSERT INTO renaser.whatsapp_confirmados (usuario_id, numero, confirmado_en) VALUES (?, ?, now()) "
                    + "ON CONFLICT (usuario_id) DO UPDATE SET numero = EXCLUDED.numero, confirmado_en = EXCLUDED.confirmado_en",
                    cuenta.value(), numero);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    @Override
    public boolean confirmadoPorOtra(String numero, UserId excepto) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM renaser.whatsapp_confirmados WHERE numero = ? AND usuario_id <> ?)", Boolean.class,
                numero, excepto == null ? new java.util.UUID(0L, 0L) : excepto.value()));
    }
}
