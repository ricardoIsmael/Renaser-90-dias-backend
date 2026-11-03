package com.renaser.os.onboarding.application.services.terminos;

import com.renaser.os.onboarding.application.ports.in.terminos.ObtenerTerminosUseCase.AcuerdoDeLaPersona;
import com.renaser.os.onboarding.application.ports.in.terminos.RegistrarAceptacionDeTerminosUseCase;
import com.renaser.os.onboarding.application.ports.out.media.LoadMediaPort;
import com.renaser.os.onboarding.application.ports.out.respuesta.LeerRespuestasPorClavePort;
import com.renaser.os.onboarding.application.ports.out.terminos.AceptacionesDeTerminosPort;
import com.renaser.os.onboarding.application.ports.out.terminos.AceptacionesDeTerminosPort.LoMostrado;
import com.renaser.os.onboarding.application.ports.out.terminos.TerminosPort;
import com.renaser.os.onboarding.domain.model.media.MediaOnboarding;
import com.renaser.os.onboarding.domain.model.terminos.VersionDeTerminos;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * La constancia de la aceptación (spec 027, FR-011, FR-012): la persona, la versión, la hora y la firma que dibujó. Corre DENTRO de la transacción que marca el hito
 * de Términos (la abre {@code EstadoOnboardingService.aceptar}): si no se puede dejar constancia, tampoco queda aceptado.
 *
 * <p><b>Spec 039:</b> la constancia guarda además lo que se le mostró en «Mi aceptación» (programa, inicio y fin). Lo que la persona vio se compara con lo de ahora: si
 * ya no coincide, no se registra nada. <b>D-315 (2026-11-02):</b> el cronograma de pagos no se firma con los términos —los pagos son asíncronos—, así que aquí no se lee,
 * no se compara ninguna huella y no se confirma nada: eso es de «Mi ingreso» (spec 032).
 */
@Service
class AceptacionDeTerminosService implements RegistrarAceptacionDeTerminosUseCase {

    /** {@code clave_pregunta} de la firma de los Términos en el catálogo (V10); la del Pacto es {@code signature}. */
    static final String CLAVE_FIRMA_DE_LOS_TERMINOS = "terms_signature";

    private final TerminosPort terminos;
    private final AceptacionesDeTerminosPort aceptaciones;
    private final AlcanceDeFormacionFinder formaciones;
    private final LeerRespuestasPorClavePort respuestas;
    private final LoadMediaPort medias;
    private final ArmadorDelAcuerdo acuerdos;
    private final IdGenerator ids;
    private final Clock clock;

    AceptacionDeTerminosService(TerminosPort terminos, AceptacionesDeTerminosPort aceptaciones, AlcanceDeFormacionFinder formaciones,
                                LeerRespuestasPorClavePort respuestas, LoadMediaPort medias, ArmadorDelAcuerdo acuerdos, IdGenerator ids, Clock clock) {
        this.terminos = terminos;
        this.aceptaciones = aceptaciones;
        this.formaciones = formaciones;
        this.respuestas = respuestas;
        this.medias = medias;
        this.acuerdos = acuerdos;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void registrar(UserId persona, PedidoDeAceptacion pedido) {
        Formacion formacion = formaciones.formacionDe(persona).orElse(Formacion.POR_DEFECTO);
        Optional<VersionDeTerminos> vigente = terminos.vigente(formacion);
        if (vigente.isEmpty()) {
            return; // su formación no publicó términos: no hay versión de la que dejar constancia (la app usa el texto que lleva)
        }
        if (pedido.versionId() != null && !pedido.versionId().equals(vigente.get().id())) {
            throw new IllegalStateException("Los términos cambiaron mientras leías: léelos de nuevo.");
        }
        AcuerdoDeLaPersona acuerdo = acuerdos.armar(persona, formacion);
        if (pedido.inicioVisto() != null && !pedido.inicioVisto().equals(acuerdo.inicio())) {
            throw new IllegalStateException("Pasó la medianoche y tus fechas cambiaron: léelas de nuevo.");
        }
        aceptaciones.registrar(ids.newId(), persona, vigente.get().id(), clock.now(), laFirmaDe(persona), new LoMostrado(acuerdo.programa(), acuerdo.inicio(), acuerdo.fin()));
    }

    /** La firma que dibujó al aceptar: la de su respuesta a la pregunta de la firma de Términos, y solo si el archivo es suyo. */
    private Long laFirmaDe(UserId persona) {
        return respuestas.mediaDe(persona, CLAVE_FIRMA_DE_LOS_TERMINOS)
                .flatMap(mediaId -> medias.porIdYUsuario(mediaId, persona)).map(MediaOnboarding::id).orElse(null);
    }
}
