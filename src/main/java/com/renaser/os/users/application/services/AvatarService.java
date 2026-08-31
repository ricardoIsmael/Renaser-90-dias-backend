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
 *
 * <p><b>El avatar es el unico objeto de lectura PUBLICA del sistema (D-55).</b> La subida sigue
 * siendo una URL prefirmada de 10 minutos — lo que hace publico al objeto es la politica del
 * bucket, no este codigo —, pero la confirmacion guarda la URL PERMANENTE, sin firma ni
 * vencimiento. Hasta el 2026-08-31 guardaba una URL de lectura prefirmada por 7 dias: a la
 * semana del ultimo cambio de foto caducaba y no la firmaba nadie nunca mas, en el perfil y en
 * todas las pantallas que muestran el avatar (E-57). Firmar en cada respuesta habria arreglado
 * el vencimiento pero roto el cache de imagen del cliente — la URL cambiaria siempre —, y para
 * un activo que se ve en cada fila del muro eso cuesta mas de lo que aporta.
 */
@Service
class AvatarService implements SolicitarUrlAvatarUseCase, ConfirmarAvatarUseCase {

    /** Mismo bucket compartido que `rocks`/`habits`/`calendar` (D-34) — ver
     * RocaDiariaService.BUCKET_ROCAS, RachaService.BUCKET_DIA_SIN_CELULAR, etc. */
    static final String BUCKET_AVATARES = "renaser-files";
    /** Prefijo de lectura publica del bucket (D-55). Todo lo demas del bucket sigue privado. */
    static final String PREFIJO_RUTA = "avatares";
    private static final Duration VALIDEZ_URL_SUBIDA = Duration.ofMinutes(10);

    private final RequireActiveUserGuard requireActiveUserGuard;
    private final SaveUserPort saveUserPort;
    private final AlmacenamientoPort almacenamientoPort;

    AvatarService(RequireActiveUserGuard requireActiveUserGuard, SaveUserPort saveUserPort,
                  AlmacenamientoPort almacenamientoPort) {
        this.requireActiveUserGuard = requireActiveUserGuard;
        this.saveUserPort = saveUserPort;
        this.almacenamientoPort = almacenamientoPort;
    }

    /** La SUBIDA sigue prefirmada y corta: escribir en el bucket nunca es publico. */
    @Override
    public UrlAvatar solicitarUrl(SolicitarUrlAvatarCommand command) {
        requireActiveUserGuard.of(command.actorId());
        String ruta = rutaDe(command.actorId().toString());
        URI url = almacenamientoPort.firmarSubida(ruta, command.tipoContenido(), VALIDEZ_URL_SUBIDA);
        return new UrlAvatar(url, BUCKET_AVATARES, ruta);
    }

    /**
     * Guarda la URL PERMANENTE del objeto, no una prefirmada. La ruta se recalcula desde el
     * actor y no se toma del body: asi el usuario solo puede publicar como avatar su propio
     * objeto, aunque mande otra cosa en {@code ruta}.
     */
    @Override
    @Transactional
    public void confirmar(ConfirmarAvatarCommand command) {
        User actor = requireActiveUserGuard.of(command.actorId());
        URI url = almacenamientoPort.urlPublica(rutaDe(command.actorId().toString()));
        actor.changeAvatar(url.toString());
        saveUserPort.save(actor);
    }

    private static String rutaDe(String actorId) {
        return PREFIJO_RUTA + "/" + actorId;
    }
}
