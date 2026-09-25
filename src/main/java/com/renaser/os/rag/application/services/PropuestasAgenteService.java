package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.in.propuesta.ConsultarPropuestasDelTurnoUseCase;
import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.in.propuesta.ResolverPropuestaUseCase;
import com.renaser.os.rag.application.ports.out.propuesta.LoadPropuestaAccionPort;
import com.renaser.os.rag.application.ports.out.propuesta.PropuestaModificadaEnParaleloException;
import com.renaser.os.rag.application.ports.out.propuesta.SavePropuestaAccionPort;
import com.renaser.os.rag.application.services.herramientas.AccionConfirmable;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.rag.domain.model.propuesta.PropuestaAccion;
import com.renaser.os.rag.domain.model.propuesta.PropuestaAccionId;
import com.renaser.os.rag.domain.model.propuesta.PropuestaNoDisponibleException;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Propuestas del acompanante (fase 2, D-153): el modelo propone, la persona confirma con un boton.
 *
 * <p><b>Sin {@code @Transactional} a proposito (C-1).</b> {@link #confirmar} ejecuta una
 * {@link AccionConfirmable} que llama a otros modulos; envolverla en una transaccion retendria una
 * conexion todo ese tiempo y, peor, haria que la marca CONFIRMADA recien se viera al final — con lo
 * que un doble toque la ejecutaria dos veces. Cada {@code save} es su propia transaccion corta.
 *
 * <p><b>Como se ejecuta una sola vez.</b> La transicion PENDIENTE -> CONFIRMADA se guarda ANTES de
 * ejecutar, con bloqueo optimista: de dos toques que leyeron la misma version, solo uno la escribe;
 * el otro recibe {@link PropuestaModificadaEnParaleloException}, relee y devuelve lo que haya sin
 * ejecutar nada. Un toque que llega despues encuentra el resultado guardado y lo devuelve igual.
 */
@Service
public class PropuestasAgenteService
        implements ProponerAccionUseCase, ResolverPropuestaUseCase, ConsultarPropuestasDelTurnoUseCase {

    static final String MENSAJE_EN_EJECUCION = "Ya estoy aplicando este cambio; en un momento lo vas a ver.";
    static final String MENSAJE_ALTERADA = "No pude verificar esta propuesta. Pidele al acompanante que te la "
            + "vuelva a ofrecer.";
    static final String MENSAJE_SIN_ACCION = "Esta accion ya no esta disponible desde el chat.";
    static final String MENSAJE_ERROR_INESPERADO = "No pude aplicar el cambio por un error nuestro. Intentalo "
            + "desde la app.";

    private static final Logger log = LoggerFactory.getLogger(PropuestasAgenteService.class);

    private final LoadPropuestaAccionPort loadPort;
    private final SavePropuestaAccionPort savePort;
    private final List<AccionConfirmable> acciones;
    private final UserSummaryFinder userSummaryFinder;
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final Duration vigencia;

    public PropuestasAgenteService(LoadPropuestaAccionPort loadPort, SavePropuestaAccionPort savePort,
                                   List<AccionConfirmable> acciones, UserSummaryFinder userSummaryFinder,
                                   Clock clock, IdGenerator idGenerator,
                                   @Value("${renaser.ia.acompanante.propuesta-vigencia:PT10M}") Duration vigencia) {
        this.loadPort = loadPort;
        this.savePort = savePort;
        this.acciones = List.copyOf(acciones);
        this.userSummaryFinder = userSummaryFinder;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.vigencia = vigencia;
    }

    @Override
    public PropuestaCreada proponer(UserId actorId, InvocacionHerramienta invocacion, String resumen) {
        PropuestaAccion propuesta = PropuestaAccion.crear(PropuestaAccionId.of(idGenerator.newId()), actorId,
                invocacion, resumen, clock.now(), vigencia);
        return aCreada(savePort.save(propuesta));
    }

    @Override
    public List<PropuestaCreada> pendientesCreadasDesde(UserId actorId, Instant desde) {
        Instant ahora = clock.now();
        return loadPort.pendientesCreadasDesde(actorId, desde).stream()
                .filter(propuesta -> !propuesta.estaVencidaEn(ahora))
                .map(PropuestasAgenteService::aCreada)
                .toList();
    }

    /**
     * Orden de las guardas: dueno y cuenta activa (403), lo ya resuelto (idempotente), vencida o
     * cancelada (409, lo decide el dominio), integridad y accion disponible (FALLIDA legible), y
     * recien ahi se reclama y se ejecuta.
     */
    @Override
    public ResultadoHerramienta confirmar(UserId actorId, UUID propuestaId) {
        PropuestaAccion propuesta = propiaDeActorActivo(actorId, propuestaId);
        Optional<ResultadoHerramienta> yaResuelta = resolucionPrevia(propuesta);
        if (yaResuelta.isPresent()) {
            return yaResuelta.get();
        }
        propuesta.confirmar(clock.now());
        if (!propuesta.argumentosIntegros()) {
            log.warn("Propuesta {} con argumentos que no coinciden con su huella: no se ejecuta", propuesta.id());
            return fallar(propuesta, MENSAJE_ALTERADA);
        }
        Optional<AccionConfirmable> accion = accionPara(propuesta);
        if (accion.isEmpty()) {
            log.warn("Propuesta {} de la herramienta '{}' sin accion que la ejecute", propuesta.id(),
                    propuesta.invocacion().nombre());
            return fallar(propuesta, MENSAJE_SIN_ACCION);
        }
        return reclamarYEjecutar(propuesta, accion.get());
    }

    @Override
    public void cancelar(UserId actorId, UUID propuestaId) {
        PropuestaAccion propuesta = propiaDeActorActivo(actorId, propuestaId);
        propuesta.cancelar(clock.now());
        try {
            savePort.save(propuesta);
        } catch (PropuestaModificadaEnParaleloException e) {
            // Otro toque la resolvio primero. Si fue otra cancelacion, da igual; si fue una
            // confirmacion, la relectura lo dice con el mismo mensaje que un cancelar tardio.
            releer(propuesta).cancelar(clock.now());
        }
    }

    /** Guarda CONFIRMADA; solo quien lo logra ejecuta. El que pierde la carrera no ejecuta nada. */
    private ResultadoHerramienta reclamarYEjecutar(PropuestaAccion propuesta, AccionConfirmable accion) {
        PropuestaAccion reclamada;
        try {
            reclamada = savePort.save(propuesta);
        } catch (PropuestaModificadaEnParaleloException e) {
            return resultadoDelQueGano(propuesta);
        }
        ResultadoHerramienta resultado = ejecutar(reclamada, accion);
        registrar(reclamada, resultado);
        return resultado;
    }

    /** Una falla inesperada de la accion no puede dejar la propuesta colgada en ejecucion para siempre. */
    private ResultadoHerramienta ejecutar(PropuestaAccion propuesta, AccionConfirmable accion) {
        try {
            return accion.aplicar(propuesta.participanteId(), propuesta.invocacion());
        } catch (RuntimeException e) {
            log.error("La accion de la propuesta {} fallo de forma inesperada", propuesta.id(), e);
            return ResultadoHerramienta.fallo(MENSAJE_ERROR_INESPERADO);
        }
    }

    private void registrar(PropuestaAccion propuesta, ResultadoHerramienta resultado) {
        switch (resultado) {
            case ResultadoHerramienta.Exito exito -> propuesta.registrarResultado(exito.contenido());
            case ResultadoHerramienta.Fallo fallo -> propuesta.marcarFallida(fallo.motivo(), clock.now());
        }
        savePort.save(propuesta);
    }

    private ResultadoHerramienta fallar(PropuestaAccion propuesta, String motivo) {
        propuesta.marcarFallida(motivo, clock.now());
        try {
            savePort.save(propuesta);
        } catch (PropuestaModificadaEnParaleloException e) {
            return resultadoDelQueGano(propuesta);
        }
        return ResultadoHerramienta.fallo(motivo);
    }

    /**
     * Otro toque escribio primero. Lo que haya dejado es la respuesta: su resultado, "en
     * ejecucion", o —si fue una cancelacion— el mismo 409 que un confirmar tardio.
     */
    private ResultadoHerramienta resultadoDelQueGano(PropuestaAccion propuesta) {
        return resolucionPrevia(releer(propuesta)).orElseThrow(() -> new PropuestaNoDisponibleException(
                "Esta propuesta ya fue cancelada; no se puede confirmar."));
    }

    /**
     * Idempotencia: lo que ya termino devuelve su resultado guardado, y lo que esta en ejecucion
     * avisa sin volver a ejecutar. Vacio = sigue pendiente (o cancelada, que la rechaza el dominio).
     */
    private static Optional<ResultadoHerramienta> resolucionPrevia(PropuestaAccion propuesta) {
        if (propuesta.enEjecucion()) {
            return Optional.of(ResultadoHerramienta.exito(MENSAJE_EN_EJECUCION));
        }
        return propuesta.resultadoRegistrado();
    }

    private Optional<AccionConfirmable> accionPara(PropuestaAccion propuesta) {
        String herramienta = propuesta.invocacion().nombre();
        return acciones.stream().filter(accion -> accion.herramienta().equals(herramienta)).findFirst();
    }

    private PropuestaAccion propiaDeActorActivo(UserId actorId, UUID propuestaId) {
        requireActivo(actorId);
        PropuestaAccion propuesta = loadPort.porId(PropuestaAccionId.of(propuestaId))
                .orElseThrow(() -> new NoSuchElementException("No encontre esa propuesta"));
        if (!propuesta.perteneceA(actorId)) {
            throw new NotAuthorizedException("Esa propuesta no es tuya");
        }
        return propuesta;
    }

    private PropuestaAccion releer(PropuestaAccion propuesta) {
        return loadPort.porId(propuesta.id())
                .orElseThrow(() -> new NoSuchElementException("No encontre esa propuesta"));
    }

    /** Mismo criterio que {@code ConversacionRenasiaService.requireActivo}: suspendido es 403. */
    private void requireActivo(UserId actorId) {
        UserSummary usuario = userSummaryFinder.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + actorId));
        if (usuario.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
    }

    private static PropuestaCreada aCreada(PropuestaAccion propuesta) {
        return new PropuestaCreada(propuesta.id().value(), propuesta.resumen(), propuesta.venceEn());
    }
}
