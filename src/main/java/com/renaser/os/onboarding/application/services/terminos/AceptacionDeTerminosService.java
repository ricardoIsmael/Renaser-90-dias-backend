package com.renaser.os.onboarding.application.services.terminos;

import com.renaser.os.onboarding.application.ports.in.pagos.CronogramaParaFirmarUseCase;
import com.renaser.os.onboarding.application.ports.in.pagos.CronogramaParaFirmarUseCase.CronogramaParaFirmar;
import com.renaser.os.onboarding.application.ports.in.terminos.ObtenerTerminosUseCase.AcuerdoDeLaPersona;
import com.renaser.os.onboarding.application.ports.in.terminos.RegistrarAceptacionDeTerminosUseCase;
import com.renaser.os.onboarding.application.ports.out.media.LoadMediaPort;
import com.renaser.os.onboarding.application.ports.out.respuesta.LeerRespuestasPorClavePort;
import com.renaser.os.onboarding.application.ports.out.terminos.AceptacionesDeTerminosPort;
import com.renaser.os.onboarding.application.ports.out.terminos.AceptacionesDeTerminosPort.LoMostrado;
import com.renaser.os.onboarding.application.ports.out.terminos.TerminosPort;
import com.renaser.os.onboarding.domain.model.media.MediaOnboarding;
import com.renaser.os.onboarding.domain.model.pagos.PagoDeLaPersona;
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
 * <p><b>Spec 039:</b> la constancia guarda además lo que se le mostró en «Mi aceptación» (programa, inicio y fin) y, si firmó su cronograma de pagos junto con los
 * términos, la huella de lo que firmó; en ese caso devuelve qué confirmar en su estado de ingreso, con la misma confirmación de la spec 032. Lo que la persona vio se
 * compara con lo de ahora: si ya no coincide, no se registra nada.
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
    private final CronogramaParaFirmarUseCase cronogramas;
    private final IdGenerator ids;
    private final Clock clock;

    AceptacionDeTerminosService(TerminosPort terminos, AceptacionesDeTerminosPort aceptaciones, AlcanceDeFormacionFinder formaciones,
                                LeerRespuestasPorClavePort respuestas, LoadMediaPort medias, ArmadorDelAcuerdo acuerdos, CronogramaParaFirmarUseCase cronogramas,
                                IdGenerator ids, Clock clock) {
        this.terminos = terminos;
        this.aceptaciones = aceptaciones;
        this.formaciones = formaciones;
        this.respuestas = respuestas;
        this.medias = medias;
        this.acuerdos = acuerdos;
        this.cronogramas = cronogramas;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ResultadoDeLaAceptacion registrar(UserId persona, PedidoDeAceptacion pedido) {
        Formacion formacion = formaciones.formacionDe(persona).orElse(Formacion.POR_DEFECTO);
        Optional<VersionDeTerminos> vigente = terminos.vigente(formacion);
        if (vigente.isEmpty()) {
            return ResultadoDeLaAceptacion.sinCronograma(); // su formación no publicó términos: no hay versión de la que dejar constancia (la app usa el texto que lleva)
        }
        if (pedido.versionId() != null && !pedido.versionId().equals(vigente.get().id())) {
            throw new IllegalStateException("Los términos cambiaron mientras leías: léelos de nuevo.");
        }
        AcuerdoDeLaPersona acuerdo = acuerdos.armar(persona, formacion);
        if (pedido.inicioVisto() != null && !pedido.inicioVisto().equals(acuerdo.inicio())) {
            throw new IllegalStateException("Pasó la medianoche y tus fechas cambiaron: léelas de nuevo.");
        }
        Optional<CronogramaParaFirmar> firmado = cronogramaQueFirmo(persona, pedido);
        aceptaciones.registrar(ids.newId(), persona, vigente.get().id(), clock.now(), laFirmaDe(persona),
                new LoMostrado(acuerdo.programa(), acuerdo.inicio(), acuerdo.fin(), firmado.map(CronogramaParaFirmar::huella).orElse(null)));
        return new ResultadoDeLaAceptacion(firmado.filter(c -> !c.yaConfirmado())
                .map(c -> new ConfirmacionDelCronograma(nombreParaConfirmar(acuerdo.participante()), c.huella())));
    }

    /**
     * El cronograma que la persona firmó junto con los términos: vacío si no firmó ninguno. Si lo que firmó ya no es lo vigente (Administración lo cambió mientras
     * leía, o se lo quitó), 409 y no queda nada registrado.
     */
    private Optional<CronogramaParaFirmar> cronogramaQueFirmo(UserId persona, PedidoDeAceptacion pedido) {
        if (pedido.huellaDelCronograma() == null) {
            return Optional.empty();
        }
        return Optional.of(cronogramas.de(persona).filter(c -> c.huella().equals(pedido.huellaDelCronograma()))
                .orElseThrow(() -> new IllegalStateException("Tu cronograma cambió mientras leías: revísalo de nuevo.")));
    }

    /** El nombre completo del perfil, el mismo que se muestra como «Participante»; la confirmación de la spec 032 admite hasta 120 letras. */
    private static String nombreParaConfirmar(String participante) {
        if (participante == null || participante.isBlank()) {
            throw new IllegalStateException("Tu perfil no tiene nombre: complétalo antes de firmar tu cronograma.");
        }
        String limpio = participante.trim();
        return limpio.length() <= PagoDeLaPersona.LARGO_MAXIMO_DEL_NOMBRE ? limpio : limpio.substring(0, PagoDeLaPersona.LARGO_MAXIMO_DEL_NOMBRE).trim();
    }

    /** La firma que dibujó al aceptar: la de su respuesta a la pregunta de la firma de Términos, y solo si el archivo es suyo. */
    private Long laFirmaDe(UserId persona) {
        return respuestas.mediaDe(persona, CLAVE_FIRMA_DE_LOS_TERMINOS)
                .flatMap(mediaId -> medias.porIdYUsuario(mediaId, persona)).map(MediaOnboarding::id).orElse(null);
    }
}
