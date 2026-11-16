package com.renaser.os.notifications.application.ports.out.whatsapp;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Lo que el aviso de saldo (spec 054) lee de {@code entregas_whatsapp}: solo momentos, nunca personas, números ni textos. */
public interface FallasDeSaldoDeWhatsAppPort {

    /** Cuándo se creó cada envío FALLIDO cuyo motivo contiene {@code patronDelMotivo} (sin distinguir mayúsculas), desde {@code desde}. */
    List<Instant> fallasPorSaldoDesde(Instant desde, String patronDelMotivo);

    /** Cuándo se creó el último envío que salió bien (ACEPTADO, ENVIADO, ENTREGADO o LEIDO) desde {@code desde}. */
    Optional<Instant> ultimoQueSalioDesde(Instant desde);
}
