package com.renaser.os.phasecontracts.application.services;

import com.renaser.os.phasecontracts.api.ContratoFaseFinder;
import com.renaser.os.phasecontracts.application.ports.in.contrato.ConsultarContratosPendientesUseCase;
import com.renaser.os.phasecontracts.application.ports.in.contrato.ConsultarContratosUseCase;
import com.renaser.os.phasecontracts.application.ports.in.contrato.FirmarContratoUseCase;
import com.renaser.os.phasecontracts.application.ports.in.contrato.ObtenerUrlFirmaContratoUseCase;
import com.renaser.os.phasecontracts.application.ports.out.contrato.ConsultarProgresoParticipantePort;
import com.renaser.os.phasecontracts.application.ports.out.contrato.ConsultarProgresoParticipantePort.ProgresoParticipante;
import com.renaser.os.phasecontracts.application.ports.out.contrato.ConsultarProgresoParticipantePort.RolParticipante;
import com.renaser.os.phasecontracts.application.ports.out.contrato.LoadContratoPort;
import com.renaser.os.phasecontracts.application.ports.out.contrato.SaveContratoPort;
import com.renaser.os.phasecontracts.domain.model.contrato.ContratoFase;
import com.renaser.os.phasecontracts.domain.model.contrato.ContratoFaseId;
import com.renaser.os.phasecontracts.domain.model.contrato.FasePrograma;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ContratoService implements FirmarContratoUseCase, ConsultarContratosPendientesUseCase,
        ConsultarContratosUseCase, ObtenerUrlFirmaContratoUseCase, ContratoFaseFinder {

    private static final String TIPO_CONTENIDO_FIRMA = "image/svg+xml";
    private static final Duration VALIDEZ_URL_SUBIDA = Duration.ofMinutes(10);
    private static final Duration VALIDEZ_URL_LECTURA = Duration.ofMinutes(15);

    /**
     * Quien puede consultar y firmar SU PROPIO contrato de fase.
     *
     * <blockquote><b>Ampliado por el SDD 003 (ARF-16).</b> Antes firmaba solo TRAINEE y consultaba
     * TRAINEE/MENTOR. El efecto era que un ADMIN o un ALQUIMISTA que decidia hacer el programa de
     * 90 dias —que es opcional para el staff, no prohibido— recorria onboarding, mapa, objetivos y
     * habitos y se estrellaba con un 403 en el contrato de fase, sin poder avanzar ni entender por
     * que. Es la misma familia de E-169: guards que comparan contra TRAINEE literal donde la
     * pregunta real era "esta cursando".</blockquote>
     *
     * <p>Que el rol este en esta lista NO alcanza: {@link #requireProgreso} exige ademas que el
     * programa propio este ACTIVADO para todo el que no sea aprendiz. Sin esa segunda condicion,
     * un administrador que nunca curso podria firmar un contrato de una fase que no esta viviendo.
     *
     * <p>Ninguna de las dos listas habilita firmar por otra persona: los dos casos de uso operan
     * siempre sobre el actor autenticado, nunca sobre un id recibido.
     */
    private static final Set<RolParticipante> ROLES_PUEDEN_FIRMAR = Set.of(RolParticipante.TRAINEE,
            RolParticipante.MENTOR, RolParticipante.MENTOR_LEAD, RolParticipante.ADMIN,
            RolParticipante.ALCHEMIST);
    private static final Set<RolParticipante> ROLES_PUEDEN_CONSULTAR = ROLES_PUEDEN_FIRMAR;

    private final LoadContratoPort loadContratoPort;
    private final SaveContratoPort saveContratoPort;
    private final ConsultarProgresoParticipantePort progresoPort;
    private final AlmacenamientoPort almacenamientoPort;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public ContratoService(LoadContratoPort loadContratoPort, SaveContratoPort saveContratoPort,
                            ConsultarProgresoParticipantePort progresoPort, AlmacenamientoPort almacenamientoPort,
                            Clock clock, IdGenerator idGenerator) {
        this.loadContratoPort = loadContratoPort;
        this.saveContratoPort = saveContratoPort;
        this.progresoPort = progresoPort;
        this.almacenamientoPort = almacenamientoPort;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public ContratoFase firmar(FirmarContratoCommand command) {
        UserId participante = command.participanteId();
        int dia = requireProgreso(participante, ROLES_PUEDEN_FIRMAR).diaPrograma();
        List<ContratoFase> firmados = loadContratoPort.todosDeParticipante(participante);
        return command.fase() != null
                ? firmarLaFasePedida(participante, command.fase(), dia, firmados)
                : firmarSinFasePedida(participante, dia, fasesDe(firmados));
    }

    /**
     * D-216 (TRN-21 del e2e): el pedido dice que pacto firma, asi que repetirlo no firma otro. Si ese
     * pacto ya esta firmado se devuelve el que estaba (idempotente, nunca se sobreescribe). Si no, tiene
     * que ser el que toca ahora (D-193): otro ya desbloqueado es un 409 que dice cual toca. La Fase I y
     * una fase que todavia no llego las rechaza el dominio, con los 400 de siempre.
     */
    private ContratoFase firmarLaFasePedida(UserId participante, FasePrograma pedida, int dia,
                                            List<ContratoFase> firmados) {
        Optional<ContratoFase> yaFirmado = firmados.stream().filter(c -> c.fase() == pedida).findFirst();
        if (yaFirmado.isPresent()) {
            return yaFirmado.get();
        }
        FasePrograma toca = FasePrograma.faseAFirmar(dia, fasesDe(firmados));
        if (pedida != toca && pedida.firmaDesbloqueadaEnDia(dia)) {
            throw new IllegalStateException("Ahora te toca firmar el pacto de " + toca.etiqueta()
                    + ", no el de " + pedida.etiqueta());
        }
        return guardarFirma(participante, pedida, dia);
    }

    /**
     * El pedido que no dice que pacto firma (el unico que habia antes de D-216). Firma el que toca si es
     * el UNICO pendiente. Con dos o mas es ambiguo: un doble envio firmaba el primero y enseguida el
     * siguiente, este sin su firma dibujada (TRN-21), asi que se pide la fase (409). Sin pendientes, lo
     * de siempre: devuelve el de la fase en curso si ya esta firmado, o el dominio rechaza (Fase I, o
     * fase sin desbloquear).
     */
    private ContratoFase firmarSinFasePedida(UserId participante, int dia, Set<FasePrograma> firmadas) {
        List<FasePrograma> pendientes = FasePrograma.pendientes(dia, firmadas);
        if (pendientes.size() > 1) {
            throw new IllegalStateException("Tienes " + pendientes.size() + " pactos por firmar: indica cual "
                    + "firmas. Ahora toca el de " + pendientes.getFirst().etiqueta());
        }
        FasePrograma fase = pendientes.isEmpty() ? FasePrograma.paraDiaPrograma(dia) : pendientes.getFirst();
        if (pendientes.isEmpty() && fase != FasePrograma.FASE_1_RENACER) {
            Optional<ContratoFase> existente = loadContratoPort.porParticipanteYFase(participante, fase);
            if (existente.isPresent()) {
                return existente.get(); // idempotente: nunca sobreescribe (service.ts:94-99)
            }
        }
        return guardarFirma(participante, fase, dia);
    }

    /**
     * La identidad entra por el puerto IdGenerator, no la sortea el agregado (CLAUDE.MD 5.4.7).
     * {@code ContratoFase.firmar} rechaza la Fase I y una fase sin desbloquear (400).
     */
    private ContratoFase guardarFirma(UserId participante, FasePrograma fase, int dia) {
        return saveContratoPort.save(ContratoFase.firmar(ContratoFaseId.of(idGenerator.newId()), participante,
                fase, dia, clock));
    }

    @Override
    public ContratoPendiente consultarPendiente(UserId participanteId) {
        ProgresoParticipante progreso = requireProgreso(participanteId, ROLES_PUEDEN_CONSULTAR);
        FasePrograma faseAFirmar = FasePrograma.faseAFirmar(progreso.diaPrograma(), fasesFirmadas(participanteId));
        return faseAFirmar == null ? ContratoPendiente.ninguno() : ContratoPendiente.de(faseAFirmar);
    }

    @Override
    public List<ContratoConUrlLectura> consultarDeParticipante(UserId participanteId) {
        requireProgreso(participanteId, ROLES_PUEDEN_CONSULTAR);
        return loadContratoPort.todosDeParticipante(participanteId).stream()
                .map(this::conUrlLectura)
                .toList();
    }

    @Override
    public UrlFirmaContrato obtenerUrlSubida(ObtenerUrlFirmaContratoCommand command) {
        ProgresoParticipante progreso = requireProgreso(command.participanteId(), ROLES_PUEDEN_FIRMAR);
        int dia = progreso.diaPrograma();
        FasePrograma fase = FasePrograma.faseAFirmar(dia, fasesFirmadas(command.participanteId()));
        if (fase == null) {
            throw sinPactoQueFirmar(FasePrograma.paraDiaPrograma(dia), dia);
        }
        String ruta = ContratoFase.rutaFirma(command.participanteId(), fase);
        URI url = almacenamientoPort.firmarSubida(ruta, TIPO_CONTENIDO_FIRMA, VALIDEZ_URL_SUBIDA);
        return new UrlFirmaContrato(url, ContratoFase.BUCKET_DEFAULT, ruta);
    }

    @Override
    public boolean estaFirmado(UserId participanteId, int numeroFase) {
        return loadContratoPort.porParticipanteYFase(participanteId, FasePrograma.porNumero(numeroFase)).isPresent();
    }

    /**
     * Por qué no hay pacto que firmar: los mismos dos errores de siempre. O la fase en curso no se firma
     * todavía (400), o ya está firmada y no quedó ninguna anterior pendiente (409).
     */
    private static RuntimeException sinPactoQueFirmar(FasePrograma actual, int dia) {
        if (actual == FasePrograma.FASE_1_RENACER || !actual.firmaDesbloqueadaEnDia(dia)) {
            return new IllegalArgumentException("Todavia no corresponde firmar ningun pacto de fase");
        }
        return new IllegalStateException("El pacto de la fase " + actual.numero() + " ya fue firmado");
    }

    /**
     * Las fases que la persona ya firmó (D-193). Son a lo sumo cuatro filas: leerlas todas y decidir en
     * {@link FasePrograma#faseAFirmar} deja la regla en un solo lugar para pendiente, URL y firma.
     */
    private Set<FasePrograma> fasesFirmadas(UserId participanteId) {
        return fasesDe(loadContratoPort.todosDeParticipante(participanteId));
    }

    private static Set<FasePrograma> fasesDe(List<ContratoFase> firmados) {
        return firmados.stream()
                .map(ContratoFase::fase)
                .collect(Collectors.toUnmodifiableSet());
    }

    private ContratoConUrlLectura conUrlLectura(ContratoFase contrato) {
        URI url = almacenamientoPort.firmarLectura(contrato.rutaFirma(), VALIDEZ_URL_LECTURA);
        return new ContratoConUrlLectura(contrato, url);
    }

    /** SUSPENDIDO → 403 (paridad requireActiveTrainee). Rol fuera del set permitido → 403. Sin fila → 404. */
    private ProgresoParticipante requireProgreso(UserId participanteId, Set<RolParticipante> rolesPermitidos) {
        ProgresoParticipante progreso = progresoPort.deParticipante(participanteId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + participanteId));
        if (progreso.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        if (!rolesPermitidos.contains(progreso.rol())) {
            throw new NotAuthorizedException("Rol sin permiso para esta operacion: " + progreso.rol());
        }
        /* Para el aprendiz el programa es obligatorio y el reloj arranca solo; para el resto es
           opcional, y sin activarlo no hay dia de programa contra el cual medir la fase. Devolver
           el contrato igual mostraria la fase 1 a alguien que no empezo. Mismo guard que E-169
           dejo en rocks/habits/academy. */
        if (progreso.rol() != RolParticipante.TRAINEE && !progreso.programaActivado()) {
            throw new NotAuthorizedException(
                    "Tu programa personal todavia no esta activado: no hay contrato de fase que firmar");
        }
        return progreso;
    }
}
