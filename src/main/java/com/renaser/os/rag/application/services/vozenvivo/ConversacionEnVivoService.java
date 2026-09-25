package com.renaser.os.rag.application.services.vozenvivo;

import com.renaser.os.rag.application.ports.in.herramienta.EjecutarHerramientaAgenteUseCase;
import com.renaser.os.rag.application.ports.in.memoria.ConsultarMemoriaUseCase;
import com.renaser.os.rag.application.ports.in.propuesta.ConsultarPropuestasDelTurnoUseCase;
import com.renaser.os.rag.application.ports.in.voz.ConversarEnVivoUseCase;
import com.renaser.os.rag.application.ports.out.ia.ConversacionEnVivoPort;
import com.renaser.os.rag.application.ports.out.ia.ConversacionEnVivoPort.ConversacionEnVivoNoDisponibleException;
import com.renaser.os.rag.application.ports.out.ia.ConversacionEnVivoPort.SesionEnVivo;
import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort;
import com.renaser.os.rag.application.ports.out.tiempo.ProgramarTareaPeriodicaPort;
import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.conversacion.EventoDeVozEnVivo;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummaryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * La conversacion por voz en tiempo real con el acompanante (D-162, Gemini Live por detras).
 *
 * <p>Al abrir: cuenta activa con {@code USE_APP}, voz en vivo disponible, minutos del dia. Si algo
 * de eso falla se le avisa a la app con un evento y se cierra: la app vuelve al flujo anterior
 * (reconocimiento de voz del telefono, chat y voz Kore), que sigue funcionando.
 *
 * <p>Lo mismo que el chat del acompanante, a proposito: el mismo prompt con el bloque de modo voz
 * (lo arma el adaptador), las mismas herramientas ({@link EjecutarHerramientaAgenteUseCase}), las
 * mismas propuestas con boton y la misma conversacion guardada. Lo que no hay es busqueda de
 * contexto por pregunta ni memoria de turnos anteriores en el modelo: la sesion arranca antes de que
 * la persona diga nada (limite anotado en D-162).
 *
 * <p><b>Sin {@code @Transactional}</b> (C-1): una sesion dura minutos. Cada guardado es corto y
 * propio.
 */
@Service
public class ConversacionEnVivoService implements ConversarEnVivoUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConversacionEnVivoService.class);

    static final String MENSAJE_NO_DISPONIBLE = "La conversacion por voz en vivo no esta disponible en este momento.";
    static final String MENSAJE_NO_AUTORIZADO = "Tu cuenta no puede usar la conversacion por voz.";
    /** Cada cuanto se cobran los segundos hablados y se revisa la cuota. */
    static final Duration INTERVALO_DE_COBRO = Duration.ofSeconds(5);

    private final UserSummaryFinder userSummaryFinder;
    private final ConversacionEnVivoPort conversacionPort;
    private final ConsultarSituacionDelAprendizPort situacionPort;
    /** D-167: la misma memoria que el chat escrito; se lee una vez, al abrir la sesion. */
    private final ConsultarMemoriaUseCase memoriaUseCase;
    private final SesionDeVozEnVivo.Colaboradores colaboradores;

    public ConversacionEnVivoService(UserSummaryFinder userSummaryFinder, ConversacionEnVivoPort conversacionPort,
                                     ConsultarSituacionDelAprendizPort situacionPort,
                                     ConsultarMemoriaUseCase memoriaUseCase,
                                     EjecutarHerramientaAgenteUseCase herramientas,
                                     ConsultarPropuestasDelTurnoUseCase propuestas, TurnosDeVozEnVivo turnos,
                                     TiempoDeVozEnVivo tiempo, ProgramarTareaPeriodicaPort programador, Clock clock) {
        this.userSummaryFinder = userSummaryFinder;
        this.conversacionPort = conversacionPort;
        this.situacionPort = situacionPort;
        this.memoriaUseCase = memoriaUseCase;
        this.colaboradores = new SesionDeVozEnVivo.Colaboradores(herramientas, propuestas, turnos, tiempo,
                programador, clock, INTERVALO_DE_COBRO);
    }

    /**
     * Mismo criterio que {@code PermissionEnforcementInterceptor} para {@code USE_APP}, que no
     * alcanza a este endpoint porque un WebSocket no es un metodo de controller: la cuenta tiene que
     * existir, estar activa y su rol tener el permiso.
     */
    @Override
    public boolean puedeConversar(UserId actorId) {
        return userSummaryFinder.findById(actorId)
                .filter(usuario -> usuario.status() == UserStatus.ACTIVE)
                .filter(usuario -> usuario.role().can(Permission.USE_APP))
                .isPresent();
    }

    @Override
    public ConversacionEnVivo iniciar(UserId actorId, SalidaDeVozEnVivo salida) {
        if (!puedeConversar(actorId)) {
            return rechazar(salida, new EventoDeVozEnVivo.Error(MENSAJE_NO_AUTORIZADO), MotivoDeCierre.ERROR);
        }
        if (!conversacionPort.disponible()) {
            return rechazar(salida, new EventoDeVozEnVivo.Error(MENSAJE_NO_DISPONIBLE), MotivoDeCierre.NO_DISPONIBLE);
        }
        Duration restante;
        try {
            restante = colaboradores.tiempo().restanteHoy(actorId);
        } catch (RuntimeException e) {
            // Sin poder leer la cuota no se abre: son minutos de un servicio pago, y la app tiene a
            // donde volver (el flujo anterior). Mejor cerrado que una sesion sin limite.
            log.warn("No se pudo leer la cuota de la voz en vivo ({})", e.getClass().getSimpleName());
            return rechazar(salida, new EventoDeVozEnVivo.Error(MENSAJE_NO_DISPONIBLE), MotivoDeCierre.NO_DISPONIBLE);
        }
        if (restante.isZero()) {
            return rechazar(salida, new EventoDeVozEnVivo.CuotaAgotada(), MotivoDeCierre.CUOTA_AGOTADA);
        }
        return abrir(actorId, salida, restante);
    }

    private ConversacionEnVivo abrir(UserId actorId, SalidaDeVozEnVivo salida, Duration restante) {
        SesionDeVozEnVivo sesion = new SesionDeVozEnVivo(actorId, salida, colaboradores);
        try {
            colaboradores.turnos().asegurarConversacion(actorId);
            SesionEnVivo abierta = conversacionPort.abrir(apertura(actorId), sesion);
            sesion.arrancar(abierta, restante);
            return sesion;
        } catch (ConversacionEnVivoNoDisponibleException e) {
            log.warn("No se pudo abrir la voz en vivo: {}", e.getMessage());
            return rechazar(salida, new EventoDeVozEnVivo.Error(MENSAJE_NO_DISPONIBLE), MotivoDeCierre.NO_DISPONIBLE);
        } catch (RuntimeException e) {
            log.warn("Fallo al abrir la voz en vivo ({})", e.getClass().getSimpleName());
            return rechazar(salida, new EventoDeVozEnVivo.Error(MENSAJE_NO_DISPONIBLE), MotivoDeCierre.NO_DISPONIBLE);
        }
    }

    private ConversacionEnVivoPort.Apertura apertura(UserId actorId) {
        return new ConversacionEnVivoPort.Apertura(situacionPort.de(actorId).orElse(null),
                colaboradores.herramientas().disponibles(AgenteConversacional.COMPANION),
                memoriaUseCase.paraConversar(actorId).orElse(null));
    }

    private static ConversacionEnVivo rechazar(SalidaDeVozEnVivo salida, EventoDeVozEnVivo aviso,
                                               MotivoDeCierre motivo) {
        salida.evento(aviso);
        salida.cerrar(motivo);
        return SinConversacion.INSTANCIA;
    }

    /** Lo que recibe el transporte cuando no se abrio nada: ignora todo. */
    private enum SinConversacion implements ConversacionEnVivo {
        INSTANCIA;

        @Override
        public void recibirAudio(byte[] pcm16kHz) {
            // no hay con quien hablar
        }

        @Override
        public void terminar() {
            // ya estaba cerrada
        }
    }
}
