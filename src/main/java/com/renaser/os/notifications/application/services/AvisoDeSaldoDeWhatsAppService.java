package com.renaser.os.notifications.application.services;

import com.renaser.os.notifications.application.ports.in.whatsapp.VerAvisoDeSaldoDeWhatsAppUseCase;
import com.renaser.os.notifications.application.ports.out.whatsapp.FallasDeSaldoDeWhatsAppPort;
import com.renaser.os.notifications.domain.model.whatsapp.AvisoDeSaldoDeWhatsApp;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Set;

/** El aviso de saldo de WhatsApp (spec 054). Solo Administración y Alquimista; los demás reciben 403. Sin datos personales. */
@Service
public class AvisoDeSaldoDeWhatsAppService implements VerAvisoDeSaldoDeWhatsAppUseCase {

    private static final Set<UserRole> QUIEN_LO_VE = Set.of(UserRole.ADMIN, UserRole.ALCHEMIST);

    private final UserSummaryFinder usuarios;
    private final FallasDeSaldoDeWhatsAppPort fallas;
    private final Clock clock;

    AvisoDeSaldoDeWhatsAppService(UserSummaryFinder usuarios, FallasDeSaldoDeWhatsAppPort fallas, Clock clock) {
        this.usuarios = usuarios;
        this.fallas = fallas;
        this.clock = clock;
    }

    @Override
    public AvisoDeSaldoDeWhatsApp ver(UserId actor) {
        UserSummary quien = usuarios.findById(actor).orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + actor));
        if (!quien.status().allowsAccess() || !QUIEN_LO_VE.contains(quien.role())) {
            throw new NotAuthorizedException("Solo Administración y Alquimista ven el aviso de saldo de WhatsApp");
        }
        Instant desde = clock.now().minus(AvisoDeSaldoDeWhatsApp.VENTANA);
        return AvisoDeSaldoDeWhatsApp.evaluar(fallas.fallasPorSaldoDesde(desde, AvisoDeSaldoDeWhatsApp.PATRON_DEL_MOTIVO), fallas.ultimoQueSalioDesde(desde));
    }
}
