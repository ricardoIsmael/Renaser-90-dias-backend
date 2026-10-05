package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.in.media.VerFirmaDelPactoUseCase;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.media.LoadMediaPort;
import com.renaser.os.onboarding.application.ports.out.respuesta.LeerRespuestasPorClavePort;
import com.renaser.os.onboarding.domain.model.media.MediaOnboarding;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.NoSuchElementException;

/**
 * D-253: firma la lectura del PNG de la firma del Pacto, solo para su dueña.
 *
 * <p><b>De dónde sale la firma.</b> De la respuesta a la pregunta {@code signature} (V10, tipo FIRMA, flujo
 * {@code pacto}) y no de la última fila de {@code medias_onboarding}: una subida que no llegó a guardarse
 * como respuesta (la app reintenta) no es la firma que quedó sellada.
 *
 * <p><b>Por qué se mira el dueño del archivo.</b> El {@code mediaId} de la respuesta lo manda el cliente, y
 * hasta el cierre de E-528 {@code POST /onboarding/answers} no comprobaba de quién era. Hoy lo rechaza al
 * guardar, pero se sigue mirando acá (defensa en profundidad): una respuesta guardada antes del cierre puede
 * seguir apuntando a un archivo ajeno, y sin esto quien la tenga recibiría la firma de otra persona.
 *
 * <p>Sin {@code @Transactional}: son dos lecturas y firmar es cálculo local del SDK (sin ida y vuelta a S3).
 */
@Service
public class FirmaDelPactoService implements VerFirmaDelPactoUseCase {

    /** {@code clave_pregunta} de la firma del Pacto en el catálogo (V10). La de Términos es {@code terms_signature}. */
    static final String CLAVE_FIRMA_DEL_PACTO = "signature";
    /** La del {@code mediaUrl} del chat y la {@code fotoUrl} de la evidencia (D-252). */
    static final Duration VALIDEZ_URL_LECTURA = Duration.ofMinutes(15);

    private final LeerRespuestasPorClavePort respuestasPort;
    private final LoadMediaPort mediaPort;
    private final AlmacenamientoPort almacenamientoPort;
    private final ConsultarActorPort actorPort;
    private final Clock clock;

    public FirmaDelPactoService(LeerRespuestasPorClavePort respuestasPort, LoadMediaPort mediaPort,
                                AlmacenamientoPort almacenamientoPort, ConsultarActorPort actorPort, Clock clock) {
        this.respuestasPort = respuestasPort;
        this.mediaPort = mediaPort;
        this.almacenamientoPort = almacenamientoPort;
        this.actorPort = actorPort;
        this.clock = clock;
    }

    @Override
    public FirmaParaVer deActor(UserId actor) {
        requireActorActivo(actor);
        MediaOnboarding firma = respuestasPort.mediaDe(actor, CLAVE_FIRMA_DEL_PACTO)
                .flatMap(mediaPort::porId)
                .orElseThrow(FirmaDelPactoService::sinFirma);
        if (!firma.esDe(actor)) {
            throw new NotAuthorizedException("Esa firma no es tuya");
        }
        if (!firma.esFirma()) {
            throw sinFirma();
        }
        return new FirmaParaVer(almacenamientoPort.firmarLectura(firma.rutaStorage(), VALIDEZ_URL_LECTURA),
                clock.now().plus(VALIDEZ_URL_LECTURA));
    }

    private static NoSuchElementException sinFirma() {
        return new NoSuchElementException("Todavía no hay una firma del Pacto guardada");
    }

    /** SUSPENDIDO -> 403. Actor inexistente -> 404. Mismo criterio que el resto del módulo. */
    private void requireActorActivo(UserId actorId) {
        ConsultarActorPort.ActorOnboarding actor = actorPort.deActor(actorId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + actorId));
        if (actor.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
    }
}
