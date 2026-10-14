package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.application.ports.in.AccesoDeConsola;
import com.renaser.os.plataforma.application.ports.out.TokensDeConsolaPort;
import com.renaser.os.shared.domain.ConsolaBloqueadaException;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Los dos cerrojos de la consola Soporte TI, en el orden en que se prueban:
 * <ol>
 *   <li><b>{@link #exigirSoporteTi}</b>: solo una cuenta IT_SUPPORT activa (FR-025). El interceptor ya lo verifica por
 *       {@code OPERATE_PLATFORM}, estricto; se repite acá porque estos servicios también se invocan desde pruebas y
 *       oyentes, y porque la matriz falla abierta para ADMIN y ALQUIMISTA (A-1). → 403.</li>
 *   <li><b>{@link #exigirConsolaDesbloqueada}</b>: el token de {@code X-Consola-Token}, de esa persona y esa sesión,
 *       vivo; cada uso lo renueva, así que vence por inactividad (FR-024). → 423.</li>
 * </ol>
 */
@Component
class GuardiaDeSoporteTi {

    private final UserSummaryFinder userSummaryFinder;
    private final TokensDeConsolaPort tokens;
    private final Duration inactividad;

    GuardiaDeSoporteTi(UserSummaryFinder userSummaryFinder, TokensDeConsolaPort tokens,
                       @Value("${renaser.plataforma.consola.inactividad:PT15M}") Duration inactividad) {
        this.userSummaryFinder = userSummaryFinder;
        this.tokens = tokens;
        this.inactividad = inactividad;
    }

    Duration inactividad() {
        return inactividad;
    }

    void exigirSoporteTi(UserId actor) {
        boolean esSoporteTiActivo = userSummaryFinder.findById(actor)
                .filter(usuario -> usuario.role() == UserRole.IT_SUPPORT && usuario.status() == UserStatus.ACTIVE)
                .isPresent();
        if (!esSoporteTiActivo) {
            throw new NotAuthorizedException("Solo Soporte TI usa esta consola");
        }
    }

    /** Soporte TI y la consola desbloqueada: lo que exige todo endpoint de la consola salvo el desbloqueo. */
    void exigirConsolaAbierta(AccesoDeConsola acceso) {
        exigirSoporteTi(acceso.actor());
        exigirConsolaDesbloqueada(acceso);
    }

    void exigirConsolaDesbloqueada(AccesoDeConsola acceso) {
        String token = acceso.tokenDeConsola();
        if (token == null || token.isBlank()
                || !tokens.consolaVigente(token, acceso.actor(), acceso.sesion(), inactividad)) {
            throw new ConsolaBloqueadaException();
        }
    }
}
