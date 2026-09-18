package com.renaser.os.users.application.services;

import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.Clock;
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
    private final Clock clock;

    AvatarService(RequireActiveUserGuard requireActiveUserGuard, SaveUserPort saveUserPort,
                  AlmacenamientoPort almacenamientoPort, Clock clock) {
        this.requireActiveUserGuard = requireActiveUserGuard;
        this.saveUserPort = saveUserPort;
        this.almacenamientoPort = almacenamientoPort;
        this.clock = clock;
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
     *
     * <p><b>Y le agrega una version (2026-09-18).</b> La ruta de un avatar es SIEMPRE la misma
     * ({@code avatares/<usuarioId>}), asi que cambiar de foto reescribe el mismo objeto y la URL
     * publica queda identica a la anterior, caracter por caracter. Para cualquier cache que
     * indexe por URL —la del navegador, la de {@code expo-image}, la del CDN— eso no es una
     * imagen nueva: es la misma que ya tiene guardada. La foto se subia bien y la persona seguia
     * viendo la vieja, que es el reporte del dueno del 2026-09-18: "que se suba y cargue, porque
     * hasta ahora no funciona".
     *
     * <p>La version es el instante de la confirmacion, asi que cada cambio produce una URL
     * distinta y todos los caches fallan a la vez. No se toca el objeto ni la ruta: {@code ?v=}
     * es solo para el cache, S3 lo ignora.
     */
    @Override
    @Transactional
    public void confirmar(ConfirmarAvatarCommand command) {
        User actor = requireActiveUserGuard.of(command.actorId());
        URI url = almacenamientoPort.urlPublica(rutaDe(command.actorId().toString()));
        actor.changeAvatar(conVersion(url.toString(), clock.now().toEpochMilli()));
        saveUserPort.save(actor);
    }

    /**
     * Le agrega {@code v=<instante>} a la URL respetando la que ya trae.
     *
     * <p>El separador se elige mirando la URL y no se asume {@code ?}: {@code urlPublica} depende
     * del adaptador de almacenamiento, y uno que ya devolviera una consulta —un CDN con
     * parametros, por ejemplo— terminaria con dos {@code ?} y una URL invalida que nadie probo.
     */
    private static String conVersion(String url, long instante) {
        return url + (url.contains("?") ? "&" : "?") + "v=" + instante;
    }

    private static String rutaDe(String actorId) {
        return PREFIJO_RUTA + "/" + actorId;
    }
}
