package com.renaser.os.users.application.services;

import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.users.application.ports.in.user.ConfirmarAvatarUseCase;
import com.renaser.os.users.application.ports.in.user.SolicitarUrlAvatarUseCase;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.domain.model.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Duration;

/**
 * Avatar generico (gap #4 de docs/PLAN_INTEGRACION_FRONTEND.md), mismo patron
 * "upload-url -> PUT -> confirmar" ya establecido en `rocks`/`habits`/`onboarding`/`calendar`.
 * Clase propia (no {@code UserAccountService}) porque es un concepto separado del resto de
 * "mi perfil" — sube y confirma un archivo, no edita campos sueltos.
 */
@Service
class AvatarService implements SolicitarUrlAvatarUseCase, ConfirmarAvatarUseCase {

    /** Mismo bucket compartido que `rocks`/`habits`/`calendar` (D-34) — ver
     * RocaDiariaService.BUCKET_ROCAS, RachaService.BUCKET_DIA_SIN_CELULAR, etc. */
    static final String BUCKET_AVATARES = "renaser-files";
    private static final String PREFIJO_RUTA = "avatares";
    private static final Duration VALIDEZ_URL_SUBIDA = Duration.ofMinutes(10);
    /**
     * Limitacion conocida (ver javadoc de {@link ConfirmarAvatarUseCase}): sin un adaptador
     * de storage con URL publica permanente, se resuelve una URL de lectura firmada con la
     * validez mas larga razonable en vez de una URL que no vence nunca.
     */
    private static final Duration VALIDEZ_URL_LECTURA = Duration.ofDays(7);

    private final RequireActiveUserGuard requireActiveUserGuard;
    private final SaveUserPort saveUserPort;
    private final AlmacenamientoPort almacenamientoPort;

    AvatarService(RequireActiveUserGuard requireActiveUserGuard, SaveUserPort saveUserPort,
                  AlmacenamientoPort almacenamientoPort) {
        this.requireActiveUserGuard = requireActiveUserGuard;
        this.saveUserPort = saveUserPort;
        this.almacenamientoPort = almacenamientoPort;
    }

    @Override
    public UrlAvatar solicitarUrl(SolicitarUrlAvatarCommand command) {
        requireActiveUserGuard.of(command.actorId());
        String ruta = PREFIJO_RUTA + "/" + command.actorId();
        URI url = almacenamientoPort.firmarSubida(ruta, command.tipoContenido(), VALIDEZ_URL_SUBIDA);
        return new UrlAvatar(url, BUCKET_AVATARES, ruta);
    }

    @Override
    @Transactional
    public void confirmar(ConfirmarAvatarCommand command) {
        User actor = requireActiveUserGuard.of(command.actorId());
        URI url = almacenamientoPort.firmarLectura(command.ruta(), VALIDEZ_URL_LECTURA);
        actor.changeAvatar(url.toString());
        saveUserPort.save(actor);
    }
}
