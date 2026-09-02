package com.renaser.os.evidence.application.services;

import com.renaser.os.evidence.api.EstadoValidacion;
import com.renaser.os.evidence.api.RegistrarEvidenciaPort;
import com.renaser.os.evidence.application.ports.in.evidencia.AnularVeredictoUseCase;
import com.renaser.os.evidence.application.ports.in.evidencia.ConsultarEvidenciaUseCase;
import com.renaser.os.evidence.application.ports.in.evidencia.ListarEvidenciaAdminUseCase;
import com.renaser.os.evidence.application.ports.in.evidencia.ListarEvidenciaAdminUseCase.ListarEvidenciaAdminComando;
import com.renaser.os.evidence.application.ports.in.evidencia.ListarEvidenciaUseCase;
import com.renaser.os.evidence.application.ports.in.evidencia.ListarEvidenciaUseCase.ListarEvidenciaComando;
import com.renaser.os.evidence.application.ports.in.evidencia.ListarEvidenciaUseCase.PaginaEvidencias;
import com.renaser.os.evidence.application.ports.in.evidencia.ListarEvidenciaUseCase.TipoDestino;
import com.renaser.os.evidence.application.ports.in.evidencia.ProcesarColaValidacionUseCase;
import com.renaser.os.evidence.application.ports.in.evidencia.RevisarManualmenteUseCase;
import com.renaser.os.evidence.application.ports.out.evidencia.LoadEvidenciaPort;
import com.renaser.os.evidence.application.ports.out.evidencia.LoadEvidenciaPort.FiltroEvidencia;
import com.renaser.os.evidence.application.ports.out.evidencia.SaveEvidenciaPort;
import com.renaser.os.evidence.application.ports.out.ia.ResultadoValidacionIA;
import com.renaser.os.evidence.application.ports.out.ia.ValidacionIAPort;
import com.renaser.os.evidence.domain.model.evidencia.Evidencia;
import com.renaser.os.evidence.domain.model.evidencia.EvidenciaId;
import com.renaser.os.points.api.AjustarPuntosPort;
import com.renaser.os.points.api.MotivoPuntos;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Único servicio de aplicación del módulo. Implementa {@link RegistrarEvidenciaPort}
 * directamente (es, a la vez, el puerto público y el "in" de esa operación — ver su
 * javadoc) además de los casos de uso internos (consulta, revisión manual, anulación,
 * cola de validación, listados).
 */
@Service
public class EvidenciaService implements RegistrarEvidenciaPort, ConsultarEvidenciaUseCase, RevisarManualmenteUseCase,
        AnularVeredictoUseCase, ProcesarColaValidacionUseCase, ListarEvidenciaUseCase, ListarEvidenciaAdminUseCase {

    private static final Logger log = LoggerFactory.getLogger(EvidenciaService.class);

    private static final int TAMANO_LOTE = 25;
    private static final int TAMANO_PAGINA = 20;

    private final LoadEvidenciaPort loadEvidenciaPort;
    private final SaveEvidenciaPort saveEvidenciaPort;
    private final ValidacionIAPort validacionIAPort;
    private final UserSummaryFinder userSummaryFinder;
    private final ParticipacionProgramaFinder participacionFinder;
    private final AjustarPuntosPort ajustarPuntosPort;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public EvidenciaService(LoadEvidenciaPort loadEvidenciaPort, SaveEvidenciaPort saveEvidenciaPort,
                             ValidacionIAPort validacionIAPort, UserSummaryFinder userSummaryFinder,
                             ParticipacionProgramaFinder participacionFinder, AjustarPuntosPort ajustarPuntosPort,
                             Clock clock, IdGenerator idGenerator) {
        this.loadEvidenciaPort = loadEvidenciaPort;
        this.saveEvidenciaPort = saveEvidenciaPort;
        this.validacionIAPort = validacionIAPort;
        this.userSummaryFinder = userSummaryFinder;
        this.participacionFinder = participacionFinder;
        this.ajustarPuntosPort = ajustarPuntosPort;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    /**
     * Defensa en profundidad (CLAUDE.MD §0.3): {@code rocks}/{@code habits} ya validan
     * que el actor no esté suspendido antes de llamar acá, pero {@code evidence} no
     * confía ciegamente en el llamador — vuelve a chequear el estado de la cuenta.
     */
    @Override
    @Transactional
    public EvidenciaRegistrada registrar(RegistrarEvidenciaComando comando) {
        requireActivo(comando.participanteId());
        // La identidad entra por el puerto IdGenerator, no la sortea el agregado (CLAUDE.MD §5.4.7).
        Evidencia evidencia = Evidencia.registrar(EvidenciaId.of(idGenerator.newId()), comando.participanteId(),
                comando.destino(), comando.tipo(), comando.bucket(), comando.rutaStorage(), comando.contenidoTexto(),
                comando.timestampExif(), comando.gpsLat(), comando.gpsLng(), comando.esPrincipal(),
                comando.subidaEn(), clock);
        Evidencia guardada = saveEvidenciaPort.save(evidencia);
        return new EvidenciaRegistrada(guardada.id().value(), guardada.estadoValidacion());
    }

    @Override
    public Evidencia porId(UserId actorId, EvidenciaId evidenciaId) {
        Evidencia evidencia = requireEvidencia(evidenciaId);
        requireDuenoOAdmin(actorId, evidencia);
        return evidencia;
    }

    @Override
    @Transactional
    public Evidencia revisar(RevisarManualmenteCommand command) {
        requireAdmin(command.actorId());
        Evidencia evidencia = requireEvidencia(command.evidenciaId());
        evidencia.revisarManualmente(command.aprobar(), command.notas());
        return saveEvidenciaPort.save(evidencia);
    }

    /**
     * Equivalente al "override" del backend viejo (ver javadoc de
     * {@link Evidencia#anularVeredicto}) — idempotente (una segunda llamada no escribe
     * nada, mismo criterio que el viejo {@code overrideEvidence}), y revierte la
     * penalización de puntos si la evidencia tenía una aplicada. {@code evidence} nunca
     * importa nada interno de {@code points}: solo {@link AjustarPuntosPort}, su API
     * pública.
     *
     * <p><b>C-13 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html), MEDIO:</b>
     * el {@code if (estadoValidacion == ANULADA_ADMIN) return} de arriba solo hace
     * idempotente la llamada SECUENCIAL (una vez que la primera ya hizo commit). Dos
     * admins (o un doble clic) que leen la MISMA fila al mismo tiempo ven ambos
     * {@code penalizacionAplicada=true} ANTES de que ninguno escriba — es un
     * check-then-act, el mismo bug que C-2 en {@code rocks}. Por eso la lectura acá es
     * con {@code PESSIMISTIC_WRITE} ({@link LoadEvidenciaPort#byIdParaEscritura}): el
     * segundo llamador espera a que el primero haga commit y entonces lee el estado YA
     * actualizado ({@code ANULADA_ADMIN}), así que su propio {@code anularVeredicto}
     * devuelve {@code false} y no vuelve a pedirle a {@code points} que revierta la
     * penalización.
     */
    @Override
    @Transactional
    public Evidencia anular(AnularVeredictoCommand command) {
        requireAdmin(command.actorId());
        Evidencia evidencia = requireEvidenciaParaEscritura(command.evidenciaId());
        if (evidencia.estadoValidacion() == EstadoValidacion.ANULADA_ADMIN) {
            return evidencia;
        }
        boolean revierteLaPenalizacion = evidencia.anularVeredicto(command.notas());
        if (revierteLaPenalizacion) {
            ajustarPuntosPort.ajustar(evidencia.participanteId(), MotivoPuntos.INVALID_EVIDENCE_REVOKED,
                    Evidencia.PENALIZACION_EVIDENCIA_INVALIDA_PUNTOS,
                    "Veredicto de evidencia invalida anulado por admin: se revierte la penalizacion");
        }
        return saveEvidenciaPort.save(evidencia);
    }

    /**
     * SIN IA en este alcance: {@link ValidacionIAPort} siempre responde
     * {@code NO_DISPONIBLE} ({@code NoOpValidacionIAAdapter}), así que cada corrida
     * incrementa {@code intentosIa} hasta el fallback a {@code REVISION_MANUAL} — ver
     * javadoc de {@link ProcesarColaValidacionUseCase}.
     *
     * <p><b>C-4 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html), ALTO:</b>
     * este método YA NO es {@code @Transactional}. La versión vieja envolvía en una sola
     * transacción la lectura con {@code FOR UPDATE SKIP LOCKED} Y hasta 25 llamadas a la
     * IA — con un proveedor real (45s calibrados para audio, CLAUDE.MD §7) eso retiene
     * una conexión de Hikari hasta 19 minutos, agotando el pool para TODA la API (mismo
     * defecto que C-1, ya corregido en {@code onboarding}/{@code rag}/{@code academy}).
     * Ahora: la lectura del lote corre en su propia transacción corta y explícita
     * ({@link com.renaser.os.evidence.infrastructure.adapter.out.persistence.EvidenciaPersistenceAdapter#pendientesLote},
     * necesaria porque el {@code FOR UPDATE} no puede correr en la transacción de solo
     * lectura que Spring Data aplicaría por defecto a un método de consulta sin
     * anotación propia); cada evidencia se procesa y persiste por separado
     * ({@link #procesarUnaAislada}), con la IA completamente afuera de cualquier
     * transacción.
     */
    @Override
    public int procesarLote() {
        List<Evidencia> pendientes = loadEvidenciaPort.pendientesLote(clock.now(), TAMANO_LOTE);
        for (Evidencia evidencia : pendientes) {
            procesarUnaAislada(evidencia);
        }
        return pendientes.size();
    }

    /**
     * Aísla cada evidencia del resto del lote (C-4, "poison pill"): antes, si la
     * evidencia 12 lanzaba una excepción (de red, por ejemplo), la transacción entera
     * revertía las 11 ya validadas y, como el orden es {@code subida_en ASC}, siempre
     * eran las mismas 25 — la cola no avanzaba nunca. Ahora un fallo aislado (de la IA o
     * al persistir) no se propaga: se loguea y el resto del lote sigue su curso. El
     * fallo de IA además cuenta como un intento fallido más (misma máquina de reintentos
     * + fallback a {@code REVISION_MANUAL} que ya tiene el dominio,
     * {@link Evidencia#registrarIntentoFallido()}) — antes {@code registrarIntentoFallido}
     * solo corría ante {@code NO_DISPONIBLE}, nunca ante una excepción.
     */
    private void procesarUnaAislada(Evidencia evidencia) {
        try {
            ResultadoValidacionIA resultado = validarSinDejarAtrapada(evidencia);
            switch (resultado) {
                case APROBADA -> evidencia.aprobarPorIa();
                case RECHAZADA -> evidencia.rechazarPorIa("Rechazada por validacion IA");
                case NO_DISPONIBLE -> evidencia.registrarIntentoFallido();
            }
            saveEvidenciaPort.save(evidencia);
        } catch (RuntimeException e) {
            log.warn("[evidence.EvidenciaService] fallo aislado procesando la evidencia {}, no afecta al resto "
                    + "del lote: {}", evidencia.id(), e.getMessage(), e);
        }
    }

    /**
     * Con el {@code NoOpValidacionIAAdapter} de hoy {@link ValidacionIAPort#validar}
     * nunca lanza. Con un proveedor real sí puede (timeout, error de red) — se trata
     * igual que {@code NO_DISPONIBLE}: la máquina de estados de {@link Evidencia} ya
     * sabe reintentar o caer a {@code REVISION_MANUAL} (CLAUDE.MD §7), no hay que
     * inventar un camino nuevo. Mismo criterio que
     * {@code onboarding.ProcesarValidacionV90Service.validarSinDejarAtrapada}.
     */
    private ResultadoValidacionIA validarSinDejarAtrapada(Evidencia evidencia) {
        try {
            return validacionIAPort.validar(evidencia);
        } catch (RuntimeException e) {
            log.warn("[evidence.EvidenciaService] la IA fallo validando la evidencia {}: {}", evidencia.id(),
                    e.getMessage(), e);
            return ResultadoValidacionIA.NO_DISPONIBLE;
        }
    }

    /**
     * Listado general (hueco #19) — ver javadoc de {@link ListarEvidenciaUseCase} para
     * el detalle de autorización por rol.
     */
    @Override
    public PaginaEvidencias listar(ListarEvidenciaComando comando) {
        UserSummary actor = requireActorActivo(comando.actorId());
        FiltroEvidencia filtro = resolverFiltroSegunRol(actor, comando.participanteId(), comando.estado(),
                comando.tipoDestino(), comando.desde(), comando.hasta());
        return paginar(loadEvidenciaPort.buscar(filtro, comando.cursor(), TAMANO_PAGINA));
    }

    /**
     * Listado del panel admin (hueco #20) — sin scoping de dueño ni de mentor, solo el
     * gate de rol (ver javadoc de {@link ListarEvidenciaAdminUseCase}).
     */
    @Override
    public PaginaEvidencias listar(ListarEvidenciaAdminComando comando) {
        requireAdmin(comando.actorId());
        FiltroEvidencia filtro = new FiltroEvidencia(comando.participanteId(), comando.estado(),
                comando.tipoDestino(), comando.desde(), comando.hasta());
        return paginar(loadEvidenciaPort.buscar(filtro, comando.cursor(), TAMANO_PAGINA));
    }

    /**
     * ADMIN/ALCHEMIST: filtro tal cual, cualquier participante. MENTOR: {@code participanteId}
     * obligatorio y debe ser el mentor asignado (E-38 de {@code docs/BITACORA_ERRORES.md}
     * es el mismo bug que esta verificación evita: "rol correcto" no es "asignado a este
     * aprendiz"). Cualquier otro rol: fuerza el filtro a la propia evidencia, y rechaza si
     * pidieron explícitamente la de otro participante.
     */
    private FiltroEvidencia resolverFiltroSegunRol(UserSummary actor, UserId participanteId, EstadoValidacion estado,
                                                    TipoDestino tipoDestino, Instant desde, Instant hasta) {
        if (actor.role() == UserRole.ADMIN || actor.role() == UserRole.ALCHEMIST) {
            return new FiltroEvidencia(participanteId, estado, tipoDestino, desde, hasta);
        }
        if (actor.role() == UserRole.MENTOR) {
            if (participanteId == null) {
                throw new NotAuthorizedException(
                        "Un mentor debe indicar participanteId: no hay listado sin acotar por aprendiz");
            }
            requireMentorAsignado(actor.id(), participanteId);
            return new FiltroEvidencia(participanteId, estado, tipoDestino, desde, hasta);
        }
        if (participanteId != null && !participanteId.equals(actor.id())) {
            throw new NotAuthorizedException("No autorizado a listar evidencia ajena");
        }
        return new FiltroEvidencia(actor.id(), estado, tipoDestino, desde, hasta);
    }

    private void requireMentorAsignado(UserId mentorId, UserId participanteId) {
        UserId asignado = participacionFinder.deParticipante(participanteId)
                .map(ParticipacionPrograma::mentorId)
                .orElse(null);
        if (!mentorId.equals(asignado)) {
            throw new NotAuthorizedException("Solo el mentor asignado a ese aprendiz puede listar su evidencia");
        }
    }

    private PaginaEvidencias paginar(List<Evidencia> filasConExtra) {
        boolean hayMas = filasConExtra.size() > TAMANO_PAGINA;
        List<Evidencia> pagina = hayMas ? filasConExtra.subList(0, TAMANO_PAGINA) : filasConExtra;
        Instant siguiente = hayMas ? pagina.get(pagina.size() - 1).creadoEn() : null;
        return new PaginaEvidencias(pagina, siguiente);
    }

    private Evidencia requireEvidencia(EvidenciaId id) {
        return loadEvidenciaPort.byId(id)
                .orElseThrow(() -> new NoSuchElementException("Evidencia no encontrada: " + id));
    }

    /**
     * Igual que {@link #requireEvidencia} pero con bloqueo pesimista (C-2 en {@code rocks},
     * C-13 acá): se usa exclusivamente en el camino que MUTA la evidencia ya resuelta
     * ({@link #anular}). El resto de los hermanos de esta clase ({@link #porId},
     * {@link #revisar}) no arriesgan un doble efecto de la misma forma — {@code revisar}
     * solo aplica sobre {@code REVISION_MANUAL} y no está en el alcance de C-13 (ver
     * {@code docs/informes/auditoria-fixes/C-4-C-13.md}).
     */
    private Evidencia requireEvidenciaParaEscritura(EvidenciaId id) {
        return loadEvidenciaPort.byIdParaEscritura(id)
                .orElseThrow(() -> new NoSuchElementException("Evidencia no encontrada: " + id));
    }

    private void requireDuenoOAdmin(UserId actorId, Evidencia evidencia) {
        if (actorId.equals(evidencia.participanteId())) {
            return;
        }
        requireAdmin(actorId);
    }

    private void requireActivo(UserId actorId) {
        requireActorActivo(actorId);
    }

    private void requireAdmin(UserId actorId) {
        UserSummary actor = requireActorActivo(actorId);
        if (actor.role() != UserRole.ADMIN && actor.role() != UserRole.ALCHEMIST) {
            throw new NotAuthorizedException("Solo ADMIN/ALCHEMIST administran evidencia ajena");
        }
    }

    private UserSummary requireActorActivo(UserId actorId) {
        UserSummary actor = requireActor(actorId);
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        return actor;
    }

    private UserSummary requireActor(UserId actorId) {
        return userSummaryFinder.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Actor no encontrado: " + actorId));
    }
}
