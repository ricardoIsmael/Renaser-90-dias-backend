package com.renaser.os.notifications.infrastructure.adapter.out.persistence.whatsapp;

import com.renaser.os.notifications.application.ports.out.whatsapp.FallasDeSaldoDeWhatsAppPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Lo que el aviso de saldo (spec 054) lee de {@code entregas_whatsapp} con {@link JdbcClient}: solo la columna {@code creado_en}, con su índice. */
@Component
class FallasDeSaldoDeWhatsAppJdbcAdapter implements FallasDeSaldoDeWhatsAppPort {

    private final JdbcClient jdbc;

    FallasDeSaldoDeWhatsAppJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** {@code position(...)} y no {@code LIKE}: el patrón es un texto, no una plantilla con comodines. */
    @Override
    public List<Instant> fallasPorSaldoDesde(Instant desde, String patronDelMotivo) {
        return jdbc.sql("""
                        SELECT creado_en FROM renaser.entregas_whatsapp
                        WHERE estado = 'FALLIDO' AND creado_en >= :desde AND position(lower(:patron) IN lower(motivo)) > 0
                        ORDER BY creado_en
                        """)
                .param("desde", Timestamp.from(desde)).param("patron", patronDelMotivo)
                .query((rs, n) -> rs.getTimestamp("creado_en").toInstant()).list();
    }

    @Override
    public Optional<Instant> ultimoQueSalioDesde(Instant desde) {
        return jdbc.sql("""
                        SELECT max(creado_en) FROM renaser.entregas_whatsapp
                        WHERE estado IN ('ACEPTADO', 'ENVIADO', 'ENTREGADO', 'LEIDO') AND creado_en >= :desde
                        """)
                .param("desde", Timestamp.from(desde))
                .query((rs, n) -> Optional.ofNullable(rs.getTimestamp(1)).map(Timestamp::toInstant)).single();
    }
}
