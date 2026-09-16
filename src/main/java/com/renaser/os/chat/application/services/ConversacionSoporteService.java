package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.conversacion.IncorporarUsuarioAlSoporteUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.RellenarConversacionesDeSoporteUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.SalirDeConversacionSoporteUseCase;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.conversacion.SaveConversacionPort;
import com.renaser.os.chat.application.ports.out.participante.AgregarParticipantePort;
import com.renaser.os.chat.application.ports.out.participante.EsParticipantePort;
import com.renaser.os.chat.application.ports.out.participante.QuitarParticipantePort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.Participante;
import com.renaser.os.chat.domain.model.conversacion.TipoConversacion;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * El chat de soporte de cada aprendiz (D-136): el aprendiz y todo el staff administrativo
 * (ADMIN/ALCHEMIST activos) adentro, creado solo cuando la persona entra al programa.
 *
 * <p><b>La unicidad la decide la base, no este servicio.</b> "Buscar y si no existe, crear" es un
 * check-then-act: dos entregas simultaneas del mismo evento lo pasan las dos. Lo que realmente
 * impide la conversacion duplicada es el UNIQUE de {@code conversaciones.clave_directa} sobre
 * {@code 'soporte:' || <uuid del aprendiz>} (V53). Aca se lee antes solo para no intentar el INSERT
 * al pedo, y se atrapa la violacion por si igual se cruzan.
 *
 * <p><b>Por que la creacion va en su propia transaccion</b> ({@link #transaccionPropia},
 * REQUIRES_NEW), mismo motivo que {@code ConversacionService.crearDirectaConAmbosParticipantes}
 * (C-10): si la violacion de unicidad ocurriera dentro de una transaccion compartida, Postgres la
 * deja abortada y cualquier consulta posterior explota con "current transaction is aborted" en vez
 * de devolver la conversacion que gano. Aislandola, perder la carrera solo deshace ESA transaccion
 * chica. Ademas es lo que hace que el relleno masivo cumpla .claude/rules/02: cada creacion
 * commitea sola, asi que un fallo a mitad del barrido no tira lo ya hecho.
 */
@Service
public class ConversacionSoporteService implements IncorporarUsuarioAlSoporteUseCase,
        RellenarConversacionesDeSoporteUseCase, SalirDeConversacionSoporteUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConversacionSoporteService.class);

    /** Quienes acompañan a TODO aprendiz desde su chat de soporte. Confirmado por el dueño del
     * proyecto: administrador o alquimista, nadie mas — ni mentor, ni lider de mentores. */
    private static final Set<UserRole> STAFF_ADMINISTRATIVO = Set.of(UserRole.ADMIN, UserRole.ALCHEMIST);

    private final LoadConversacionPort loadConversacionPort;
    private final SaveConversacionPort saveConversacionPort;
    private final AgregarParticipantePort agregarParticipantePort;
    private final QuitarParticipantePort quitarParticipantePort;
    private final EsParticipantePort esParticipantePort;
    private final UserSummaryFinder userSummaryFinder;
    private final ParticipacionProgramaFinder participacionProgramaFinder;
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final TransactionTemplate transaccionPropia;

    public ConversacionSoporteService(LoadConversacionPort loadConversacionPort,
                                       SaveConversacionPort saveConversacionPort,
                                       AgregarParticipantePort agregarParticipantePort,
                                       QuitarParticipantePort quitarParticipantePort,
                                       EsParticipantePort esParticipantePort,
                                       UserSummaryFinder userSummaryFinder,
                                       ParticipacionProgramaFinder participacionProgramaFinder,
                                       Clock clock, IdGenerator idGenerator,
                                       PlatformTransactionManager transactionManager) {
        this.loadConversacionPort = loadConversacionPort;
        this.saveConversacionPort = saveConversacionPort;
        this.agregarParticipantePort = agregarParticipantePort;
        this.quitarParticipantePort = quitarParticipantePort;
        this.esParticipantePort = esParticipantePort;
        this.userSummaryFinder = userSummaryFinder;
        this.participacionProgramaFinder = participacionProgramaFinder;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.transaccionPropia = new TransactionTemplate(transactionManager);
        this.transaccionPropia.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Una sola consulta decide todo: {@code deParticipante} trae rol, estado e inscripcion
     * juntos. Preguntarlos por separado seria tres viajes para responder una pregunta.
     */
    @Override
    public void incorporar(UserId usuarioId) {
        Optional<ParticipacionPrograma> quiza = participacionProgramaFinder.deParticipante(usuarioId);
        if (quiza.isEmpty() || quiza.get().suspendido()) {
            return;
        }
        ParticipacionPrograma participacion = quiza.get();
        if (participacion.rol() == UserRole.TRAINEE) {
            crearSoporteDelAprendizSiCorresponde(participacion);
            return;
        }
        if (STAFF_ADMINISTRATIVO.contains(participacion.rol())) {
            sumarStaffALasQueYaExisten(usuarioId);
        }
    }

    /**
     * El soporte nace cuando el aprendiz ENTRA AL PROGRAMA, no cuando se registra. Sin fila en
     * `participantes_programa` todavia no entro: una cuenta aprobada la tiene desde
     * {@code AccountRequestService.approve}, que la escribe en la misma transaccion que publica
     * {@code UsuarioRegistradoEvent}.
     */
    private void crearSoporteDelAprendizSiCorresponde(ParticipacionPrograma participacion) {
        if (!participacion.inscrito()) {
            return;
        }
        UserId aprendizId = participacion.participanteId();
        if (loadConversacionPort.porClaveDirecta(Conversacion.claveSoporteDe(aprendizId)).isPresent()) {
            return;
        }
        String nombre = nombreDelSoporteDe(aprendizId);
        crearSoporte(aprendizId, nombre, participacionProgramaFinder.usuariosActivosConRol(STAFF_ADMINISTRATIVO));
    }

    /**
     * Crea la conversacion y mete a todos sus participantes, atomico y en transaccion propia.
     *
     * @return {@code true} si la creo, {@code false} si ya existia (la creo otro primero)
     */
    private boolean crearSoporte(UserId aprendizId, String nombre, List<UserId> staff) {
        try {
            transaccionPropia.executeWithoutResult(status -> {
                Conversacion guardada = saveConversacionPort.save(Conversacion.crearSoporte(
                        ConversacionId.of(idGenerator.newId()), aprendizId, nombre, clock.now()));
                agregarParticipantePort.agregar(Participante.unirse(guardada.id(), aprendizId, clock.now()));
                for (UserId miembro : staff) {
                    agregarParticipantePort.agregar(Participante.unirse(guardada.id(), miembro, clock.now()));
                }
            });
            return true;
        } catch (DataIntegrityViolationException laCreoOtroPrimero) {
            // El UNIQUE de clave_directa hizo su trabajo: hay exactamente una y no dos.
            log.debug("[chat.soporte] el soporte de {} ya existia al intentar crearlo", aprendizId);
            return false;
        }
    }

    /** Nunca falla por el nombre: si el perfil no se pudiera leer, la conversacion igual tiene que
     * nacer — un titulo generico se corrige a mano, una conversacion que no existe no se nota. */
    private String nombreDelSoporteDe(UserId aprendizId) {
        return nombreDeSoporte(userSummaryFinder.findById(aprendizId).map(UserSummary::fullName).orElse(null));
    }

    /**
     * El titulo con el que la conversacion se lee en la bandeja. Lleva el nombre del aprendiz
     * porque quien mas la ve es el staff, y sin nombre tendria 25 filas identicas — el mismo
     * problema que ya arreglo el listado de mensajes directos.
     *
     * <p>Es una FOTO del momento en que se creo: si despues la persona se cambia el nombre, el
     * titulo no se entera. No se deriva en cada lectura porque una conversacion de grupo se
     * nombra por su columna `nombre`, igual que la GLOBAL. Queda documentado como limitacion
     * conocida en docs/MODULO_CHAT.md, no como olvido.
     */
    private static String nombreDeSoporte(String nombreCompleto) {
        return nombreCompleto == null || nombreCompleto.isBlank() ? "Soporte" : "Soporte - " + nombreCompleto;
    }

    /**
     * Un ADMIN/ALCHEMIST nuevo (o recien promovido) entra a las conversaciones de soporte que ya
     * existen: sin esto veria solo las de los aprendices que entren despues que el.
     *
     * <p>{@code agregar} es idempotente, asi que reentregar el evento no rompe nada ni pisa cuanto
     * leyo. Y a quien se fue por su cuenta no se lo vuelve a meter: el que se fue ya no es "nuevo",
     * y este camino solo corre cuando el usuario aparece o cambia de rol.
     */
    private void sumarStaffALasQueYaExisten(UserId staffId) {
        List<Conversacion> soportes = loadConversacionPort.deSoporte();
        int sumadas = 0;
        for (Conversacion soporte : soportes) {
            if (!esParticipantePort.esParticipante(soporte.id(), staffId)) {
                agregarParticipantePort.agregar(Participante.unirse(soporte.id(), staffId, clock.now()));
                sumadas++;
            }
        }
        if (sumadas > 0) {
            log.info("[chat.soporte] {} sumado a {} conversacion(es) de soporte ya existentes", staffId, sumadas);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sin N+1 (D-43): el padron, los inscritos, los soportes que ya hay y el staff se piden UNA
     * vez cada uno —cuatro consultas para todo el barrido, no cuatro por aprendiz—. Lo unico que se
     * repite por persona son los INSERT de quien todavia no tenia su conversacion.
     *
     * <p>Sin {@code @Transactional} alrededor, a proposito (.claude/rules/02): cada creacion
     * commitea sola, asi que un fallo en el aprendiz 14 no deshace los 13 anteriores, y el que
     * falla se cuenta y no detiene al resto.
     */
    @Override
    public ResultadoRelleno rellenar(UserId actorId) {
        requireActivoAdmin(actorId);
        List<UserSummary> padron = userSummaryFinder.aprendicesActivos();
        Set<UserId> inscritos = Set.copyOf(participacionProgramaFinder.participantesInscritosActivos());
        Set<String> yaTienen = clavesDeSoporteExistentes();
        List<UserId> staff = participacionProgramaFinder.usuariosActivosConRol(STAFF_ADMINISTRATIVO);

        int creadas = 0;
        int existentes = 0;
        int fallidas = 0;
        for (UserSummary aprendiz : padron) {
            if (!inscritos.contains(aprendiz.id())) {
                continue;
            }
            if (yaTienen.contains(Conversacion.claveSoporteDe(aprendiz.id()))) {
                existentes++;
                continue;
            }
            switch (intentarCrearEnElRelleno(aprendiz, staff)) {
                case CREADA -> creadas++;
                case YA_EXISTIA -> existentes++;
                case FALLO -> fallidas++;
            }
        }
        log.info("[chat.soporte] relleno: {} aprendices, {} creadas, {} ya existian, {} fallidas",
                padron.size(), creadas, existentes, fallidas);
        return new ResultadoRelleno(padron.size(), creadas, existentes, fallidas);
    }

    private Set<String> clavesDeSoporteExistentes() {
        return loadConversacionPort.deSoporte().stream()
                .map(Conversacion::claveDirecta)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Un aprendiz que falla no puede detener el barrido (.claude/rules/02). */
    private ResultadoIntento intentarCrearEnElRelleno(UserSummary aprendiz, List<UserId> staff) {
        try {
            return crearSoporte(aprendiz.id(), nombreDeSoporte(aprendiz.fullName()), staff)
                    ? ResultadoIntento.CREADA : ResultadoIntento.YA_EXISTIA;
        } catch (RuntimeException e) {
            log.warn("[chat.soporte] relleno: fallo el aprendiz {}", aprendiz.id(), e);
            return ResultadoIntento.FALLO;
        }
    }

    private enum ResultadoIntento { CREADA, YA_EXISTIA, FALLO }

    @Override
    public void salir(SalirDeConversacionSoporteCommand command) {
        requireActivo(command.actorId());
        Conversacion conversacion = loadConversacionPort.porId(command.conversacionId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Conversacion no encontrada: " + command.conversacionId()));
        if (conversacion.tipo() != TipoConversacion.SOPORTE) {
            throw new IllegalArgumentException("Solo se puede salir de una conversacion de soporte");
        }
        if (conversacion.esAprendizDeSoporte(command.actorId())) {
            throw new NotAuthorizedException("El aprendiz no puede salir de su conversacion de soporte");
        }
        if (!esParticipantePort.esParticipante(conversacion.id(), command.actorId())) {
            throw new NotAuthorizedException("No sos participante de esta conversacion");
        }
        quitarParticipantePort.quitar(conversacion.id(), command.actorId());
    }

    private UserSummary requireActivo(UserId usuarioId) {
        UserSummary usuario = userSummaryFinder.findById(usuarioId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + usuarioId));
        if (usuario.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
        return usuario;
    }

    /** Mismo criterio que {@code ConversacionService.requireActivoAdmin}: el relleno es una
     * operacion del panel de administracion, no algo que un aprendiz pueda disparar. */
    private void requireActivoAdmin(UserId usuarioId) {
        if (!requireActivo(usuarioId).role().canManageRoles()) {
            throw new NotAuthorizedException("Solo ADMIN/ALCHEMIST puede rellenar los chats de soporte");
        }
    }
}
