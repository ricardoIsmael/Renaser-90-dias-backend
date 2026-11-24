package com.renaser.os.habits.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Las cuentas de demostración que Apple y Google usan para revisar la app (D-368, issue #184). Se marcan por CONFIGURACIÓN
 * ({@code renaser.revision-tiendas.correos}, env {@code CUENTAS_DE_REVISION_DE_TIENDAS}, correos separados por comas): ningún correo ni id
 * vive en el código. Vacía por defecto, y entonces {@link #esDeRevision} responde {@code false} sin tocar la base: nadie cambia.
 *
 * <p>Los correos se resuelven a persona con {@link UserSummaryFinder#findByEmail} y el resultado se guarda {@link #VIGENCIA}: el
 * encargado de revisión puede crear la cuenta después de arrancar el servidor, y no se pregunta a la base en cada lectura de cada persona.
 */
@Component
public class CuentasDeRevisionDeTiendas {

    static final Duration VIGENCIA = Duration.ofMinutes(10);

    private final List<String> correos;
    private final UserSummaryFinder usuarios;
    private final Clock clock;

    private volatile Set<UserId> resueltas = Set.of();
    private volatile Instant resueltasEn = Instant.MIN;

    public CuentasDeRevisionDeTiendas(@Value("${renaser.revision-tiendas.correos:}") String correos,
                                       UserSummaryFinder usuarios, Clock clock) {
        this.correos = Arrays.stream(correos.split(","))
                .map(c -> c.trim().toLowerCase(Locale.ROOT))
                .filter(c -> !c.isEmpty())
                .toList();
        this.usuarios = usuarios;
        this.clock = clock;
    }

    /** {@code true} si la persona es una de las cuentas de revisión de las tiendas. Con la lista vacía, siempre {@code false}. */
    public boolean esDeRevision(UserId persona) {
        if (correos.isEmpty()) {
            return false;
        }
        return resolver().contains(persona);
    }

    private Set<UserId> resolver() {
        Instant ahora = clock.now();
        if (resueltasEn.plus(VIGENCIA).isAfter(ahora)) {
            return resueltas;
        }
        Set<UserId> ids = new HashSet<>();
        for (String correo : correos) {
            usuarios.findByEmail(correo).map(UserSummary::id).ifPresent(ids::add);
        }
        resueltas = Set.copyOf(ids);
        resueltasEn = ahora;
        return resueltas;
    }
}
