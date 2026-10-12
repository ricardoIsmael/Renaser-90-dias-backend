package com.renaser.os.users.infrastructure.adapter.out.persistence.participante;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.out.participante.BloquearActivacionProgramaPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** La fila del usuario existe también antes de crear su programa; el cerrojo sirve entre instancias. */
@Component
class BloquearActivacionProgramaJdbcAdapter implements BloquearActivacionProgramaPort {
    private final JdbcClient jdbc;

    BloquearActivacionProgramaJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void porUsuario(UserId usuarioId) {
        jdbc.sql("SELECT id FROM renaser.usuarios WHERE id = :usuario FOR UPDATE")
                .param("usuario", usuarioId.value()).query(UUID.class).optional();
    }
}
