package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.RateLimitExceededException;
import com.renaser.os.shared.domain.TokenVerificacionEmailInvalidoException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.accountrequest.ApproveAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.CheckAccountRequestStatusUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.DeleteAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.ListAccountRequestsUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.RejectAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase;
import com.renaser.os.users.application.ports.out.accountrequest.DeleteAccountRequestPort;
import com.renaser.os.users.application.ports.out.accountrequest.LoadAccountRequestPort;
import com.renaser.os.users.application.ports.out.accountrequest.SaveAccountRequestPort;
import com.renaser.os.users.application.ports.out.autenticacion.LimitarSolicitudesResetPort;
import com.renaser.os.users.application.ports.out.autenticacion.SaveCredencialPort;
import com.renaser.os.users.application.ports.out.autenticacion.SaveIdentidadExternaPort;
import com.renaser.os.users.application.ports.out.autenticacion.TokenVerificacionEmailPort;
import com.renaser.os.users.application.ports.out.user.DeleteUserPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.application.ports.out.participante.SaveParticipacionProgramaPort;
import com.renaser.os.users.domain.model.accountrequest.AccountRequest;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import com.renaser.os.users.domain.model.accountrequest.OrigenSocial;
import com.renaser.os.users.domain.model.identidadexterna.IdentidadExterna;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import com.renaser.os.users.api.UsuarioRegistradoEvent;
import com.renaser.os.users.domain.model.user.Credencial;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.NoSuchElementException;

/**
 * Casos de uso de AccountRequest (CLAUDE.MD §4.3). Una clase por agregado agrupando
 * sus casos de uso relacionados — variante de Hombergs/estructura del profesor
 * (application/services), reconciliada con la regla de este repo de puertos chicos
 * por intencion (§5.4.8): cada metodo publico sigue siendo UN caso de uso, la clase
 * solo evita un archivo de una linea por cada uno.
 */
@Service
public class AccountRequestService implements SubmitAccountRequestUseCase, ApproveAccountRequestUseCase,
        RejectAccountRequestUseCase, ListAccountRequestsUseCase, DeleteAccountRequestUseCase,
        CheckAccountRequestStatusUseCase {

    private static final int RATE_LIMIT_PER_HOUR = 60;

    /**
     * C-16 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html): antes este limite
     * se chequeaba con un {@code COUNT} de Postgres y DESPUES se insertaba -- check-then-act,
     * ninguna de las dos operaciones era atomica entre si, y varios envios concurrentes desde
     * la misma IP podian pasar el chequeo a la vez y superar el limite. {@code registrarIntento}
     * hace el chequeo-y-registro en UNA sola operacion atomica (INCR) contra Redis, mismo puerto
     * que ya reutilizan {@code VerificacionEmailService} y {@code ConsultaEmailService} para sus
     * propios limites por IP (CLAUDE.MD §5.3.6: tiene que seguir siendo real entre instancias,
     * por eso Redis compartido y no un contador en memoria).
     */
    private static final Duration VENTANA_RATE_LIMIT_IP = Duration.ofHours(1);

    // Ya no hay token ni correo de activacion (2026-08-27): la contrasena se elige en el alta,
    // asi que aprobar no necesita mandar nada. Quien olvide su clave usa el flujo normal de
    // "olvide mi contrasena" (ResetContrasenaService), que es exactamente para eso.

    private final LoadAccountRequestPort loadAccountRequestPort;
    private final SaveAccountRequestPort saveAccountRequestPort;
    private final DeleteAccountRequestPort deleteAccountRequestPort;
    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final DeleteUserPort deleteUserPort;
    private final SaveCredencialPort saveCredencialPort;
    private final SaveIdentidadExternaPort saveIdentidadExternaPort;
    private final PasswordEncoder passwordEncoder;
    private final SaveParticipacionProgramaPort saveParticipacionProgramaPort;
    private final TokenVerificacionEmailPort tokenVerificacionEmailPort;
    private final LimitarSolicitudesResetPort limitarSolicitudesResetPort;
    private final RequireActiveUserGuard requireActiveUserGuard;
    private final RequireAdminGuard requireAdminGuard;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public AccountRequestService(LoadAccountRequestPort loadAccountRequestPort,
                                  SaveAccountRequestPort saveAccountRequestPort,
                                  DeleteAccountRequestPort deleteAccountRequestPort,
                                  LoadUserPort loadUserPort, SaveUserPort saveUserPort,
                                  DeleteUserPort deleteUserPort, SaveCredencialPort saveCredencialPort,
                                  SaveIdentidadExternaPort saveIdentidadExternaPort,
                                  PasswordEncoder passwordEncoder,
                                  SaveParticipacionProgramaPort saveParticipacionProgramaPort,
                                  TokenVerificacionEmailPort tokenVerificacionEmailPort,
                                  LimitarSolicitudesResetPort limitarSolicitudesResetPort,
                                  RequireActiveUserGuard requireActiveUserGuard, RequireAdminGuard requireAdminGuard,
                                  ApplicationEventPublisher events, Clock clock, IdGenerator idGenerator) {
        this.loadAccountRequestPort = loadAccountRequestPort;
        this.saveAccountRequestPort = saveAccountRequestPort;
        this.deleteAccountRequestPort = deleteAccountRequestPort;
        this.loadUserPort = loadUserPort;
        this.saveUserPort = saveUserPort;
        this.deleteUserPort = deleteUserPort;
        this.saveCredencialPort = saveCredencialPort;
        this.saveIdentidadExternaPort = saveIdentidadExternaPort;
        this.passwordEncoder = passwordEncoder;
        this.saveParticipacionProgramaPort = saveParticipacionProgramaPort;
        this.tokenVerificacionEmailPort = tokenVerificacionEmailPort;
        this.limitarSolicitudesResetPort = limitarSolicitudesResetPort;
        this.requireActiveUserGuard = requireActiveUserGuard;
        this.requireAdminGuard = requireAdminGuard;
        this.events = events;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    /**
     * Genera el UUID del solicitante acá adentro (2026-08-27, D-49): hasta ahora este id lo
     * creaba Supabase Auth del lado del cliente y viajaba en el request — desde que Renaser OS
     * emite y valida su propia identidad de punta a punta, no hay ninguna razón para depender
     * de un tercero para algo tan simple como un UUID nuevo. Ese UUID sale del puerto
     * {@link com.renaser.os.shared.domain.IdGenerator} (D-59), el mismo camino que el id de la
     * solicitud: {@code domain/} no sortea identidad, la recibe ya armada (CLAUDE.MD §5.4.7).
     *
     * <p>Exige y consume {@code verificationToken} (2026-08-27): el reemplazo propio del email
     * de un solo uso que antes emitia Supabase Auth. Se valida ANTES de tocar nada mas — si el
     * token no existe, ya vencio, o certifica un email distinto al del comando (alguien
     * verifico un correo e intento usarlo para dar de alta otro), la solicitud ni se arma.
     *
     * <p><b>C-16 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html):</b> el correo
     * se chequea contra lo que YA existe ({@link #rejectIfEmailYaRegistrado}) ANTES de consumir
     * {@code verificationToken} — antes ese chequeo llegaba recien con el UNIQUE de Postgres,
     * despues de haber gastado un token de un solo uso en un intento que iba a fallar igual.
     */
    @Override
    @Transactional
    public AccountRequestId submit(SubmitAccountRequestCommand command) {
        rejectIfRateLimitExceeded(command.requestIp());
        Email email = new Email(command.email());
        rejectIfEmailYaRegistrado(email);
        requireEmailVerificado(command.email(), command.verificationToken());

        User usuario = User.registrarPendienteAprobacion(UserId.of(idGenerator.newId()), email,
                command.fullName());
        saveUserPort.save(usuario);
        guardarCredencialSiEligioContrasena(usuario, command.contrasena());

        AccountRequest request = AccountRequest.submit(AccountRequestId.of(idGenerator.newId()), usuario.id(),
                email, command.fullName(), command.phone(), command.city(), command.requestIp(),
                origenSocialDe(command), clock);
        return saveAccountRequestPort.save(request).id();
    }

    /**
     * C-16: el token de verificacion de email es de un solo uso ({@link TokenVerificacionEmailPort#consumir}
     * lo borra al leerlo) — gastarlo en un intento que de todos modos iba a chocar contra el
     * UNIQUE de {@code usuarios.email}/{@code solicitudes_cuenta.email} obligaba a la persona a
     * reverificar su correo de cero solo para volver a intentar con un correo que, para empezar,
     * ya no estaba disponible. Este chequeo no elimina la carrera con un segundo {@code submit}
     * EXACTAMENTE simultaneo para el mismo correo (esa la sigue resolviendo el UNIQUE de la base,
     * como siempre — ver C-17 para el camino social) pero cubre el caso comun, no concurrente:
     * un correo que ya tenia cuenta o solicitud desde antes.
     */
    private void rejectIfEmailYaRegistrado(Email email) {
        if (loadUserPort.byEmail(email).isPresent() || loadAccountRequestPort.existePorEmail(email)) {
            throw new IllegalStateException("Ya existe una cuenta o solicitud con este correo");
        }
    }

    /**
     * El alta por formulario no tiene origen social (null); la que abre el login social lleva la
     * identidad ya verificada contra el proveedor. El comando garantiza que los dos campos vienen
     * juntos o ninguno, asi que aca alcanza con mirar uno.
     */
    private static OrigenSocial origenSocialDe(SubmitAccountRequestCommand command) {
        return command.proveedor() == null ? null
                : new OrigenSocial(command.proveedor(), command.sujetoProveedor());
    }

    /**
     * El alta por formulario trae contrasena; la de proveedor social no (entra por Google/Apple
     * y {@code usuarios.hash_contrasena} queda null, que es justo para lo que esa columna era
     * nullable). La credencial se escribe aparte del usuario a proposito: {@code
     * SaveCredencialPort} solo toca las dos columnas de contrasena, asi el hash nunca pasa por
     * {@code UserJpaEntity} y no puede salir por una respuesta HTTP ni por un log
     * (docs/MODULO_AUTH.md §2.2).
     */
    private void guardarCredencialSiEligioContrasena(User usuario, String contrasenaEnClaro) {
        if (contrasenaEnClaro == null) {
            return;
        }
        saveCredencialPort.guardar(usuario.id(),
                new Credencial(passwordEncoder.encode(contrasenaEnClaro), clock.now()));
    }

    private void requireEmailVerificado(String email, String verificationToken) {
        String emailVerificado = tokenVerificacionEmailPort.consumir(verificationToken).orElse(null);
        if (emailVerificado == null || !emailVerificado.equalsIgnoreCase(email)) {
            throw new TokenVerificacionEmailInvalidoException();
        }
    }

    @Override
    @Transactional
    public void approve(ApproveAccountRequestCommand command) {
        AccountRequest request = requireRequest(command.accountRequestId());
        User actor = requireActiveUserGuard.of(command.actorId());

        // El usuario YA existe desde el alta, en estado INACTIVE y con su contrasena elegida
        // (ver SubmitAccountRequestUseCase): aprobar solo le da acceso. Antes se creaba aca,
        // lo que obligaba a un segundo correo de activacion para que fijara su clave — dos
        // envios en el camino critico y nadie sabia por que no llegaba el segundo.
        User usuario = loadUserPort.byId(request.usuarioId())
                .orElseThrow(() -> new IllegalStateException(
                        "La solicitud no tiene usuario asociado: " + request.usuarioId()));
        usuario.aprobar();
        saveUserPort.save(usuario);
        vincularIdentidadSocialSiCorresponde(request);

        // Invariante de participantes_programa (baseline V1, comentario de la tabla): el
        // programa de 90 dias es obligatorio para TRAINEE y la fila se crea al aprobar la
        // cuenta, en la MISMA transaccion — el alta fuerza el rol TRAINEE, asi que todo alta
        // por este camino la necesita. Porte de
        // onboarding/service.ts::createTraineePendingActivation (repo viejo): el reloj del
        // programa arranca pausado, lo activa despues el primer login + Ficha + Terminos.
        saveParticipacionProgramaPort.save(ParticipacionPrograma.inscribirTraineeAprobado(usuario.id(), clock));
        request.approve(actor, usuario.id(), clock);
        saveAccountRequestPort.save(request);

        events.publishEvent(new UsuarioRegistradoEvent(usuario.id(), clock.now()));
    }

    /**
     * Cierra el circulo de A-7 (docs/MODULO_AUTH.md §6.7): si el alta la abrio un proveedor
     * social, aca — y recien aca, porque la FK de {@code identidades_externas.usuario_id} exige
     * que la fila de {@code usuarios} exista — se escribe el vinculo
     * {@code (proveedor, sujeto) -> usuarioId}. En la MISMA transaccion que activa al usuario:
     * si el vinculo falla, la aprobacion entera se deshace, y nunca queda un usuario aprobado
     * que no pueda volver a entrar por donde entro.
     *
     * <p>{@code emailProveedor} se guarda solo como dato informativo para mostrar en pantalla
     * "vinculada a juan@..." — nunca se usa para resolver identidad (§2.2).
     */
    private void vincularIdentidadSocialSiCorresponde(AccountRequest request) {
        OrigenSocial origen = request.origenSocial();
        if (origen == null) {
            return;
        }
        saveIdentidadExternaPort.guardar(IdentidadExterna.vincular(origen.proveedor(),
                origen.sujetoProveedor(), request.usuarioId(), request.email().value(), clock));
    }

    @Override
    @Transactional
    public void reject(RejectAccountRequestCommand command) {
        AccountRequest request = requireRequest(command.accountRequestId());
        User actor = requireActiveUserGuard.of(command.actorId());

        request.reject(actor, command.reason(), clock);
        saveAccountRequestPort.save(request);

        // Borrar el usuario es OBLIGATORIO desde que el alta lo crea (2026-08-27): sin esto,
        // una solicitud rechazada dejaria la fila en `usuarios` ocupando el correo para
        // siempre (el indice UNIQUE de usuarios.email), y ademas conservando una credencial
        // de alguien que no es usuario. Es el mismo squatting de correos que el repo viejo
        // resolvia con `deleteUser` en Supabase (PROPOSAL_ACCOUNT_REQUESTS.md punto 3), ahora
        // sobre nuestra propia tabla. Idempotente: si no existe, no falla (E-44).
        deleteUserPort.deleteById(request.usuarioId());
    }

    /**
     * C-16: reemplaza el {@code COUNT} de Postgres (check-then-act) por el mismo limitador
     * atomico de Redis que ya usan {@code VerificacionEmailService} y {@code ConsultaEmailService}
     * para sus propios limites por IP (mismo criterio documentado ahi: "el limite tiene que
     * seguir siendo real entre instancias", CLAUDE.MD §5.3.6). {@code registrarIntento} hace el
     * chequeo-y-registro en una sola llamada (INCR), asi que no queda una ventana entre "leer
     * cuantos van" y "escribir uno mas" donde varios envios concurrentes desde la misma IP
     * puedan colarse todos a la vez por encima del limite.
     *
     * <p>{@code loadAccountRequestPort.countSubmittedFromIpSince} queda sin uso en este servicio
     * tras este cambio (se deja el puerto y su adaptador tal cual, sin tocarlos — ver informe).
     */
    private void rejectIfRateLimitExceeded(String requestIp) {
        if (requestIp == null) {
            return;
        }
        if (!limitarSolicitudesResetPort.registrarIntento("account-request:ip:" + requestIp, VENTANA_RATE_LIMIT_IP,
                RATE_LIMIT_PER_HOUR)) {
            throw new RateLimitExceededException("Limite de solicitudes por hora excedido para IP " + requestIp);
        }
    }

    private AccountRequest requireRequest(AccountRequestId id) {
        return loadAccountRequestPort.byId(id)
                .orElseThrow(() -> new NoSuchElementException("Solicitud no encontrada: " + id));
    }

    // Se elimino compensateSupabaseUserOnRollback (2026-08-27): compensaba la creacion de un
    // usuario en Supabase Auth durante approve(), y approve() ya no crea a nadie. La creacion
    // vive ahora en submit(), dentro de UNA sola transaccion contra NUESTRO Postgres — si algo
    // falla, el rollback deshace usuario, credencial y solicitud junto, sin compensacion manual
    // (CLAUDE.MD §9.1: el rollback gratis es justamente lo que da el monolito).

    // Se elimino deleteSupabaseUserAfterCommit (2026-08-31): registraba un afterCommit que
    // llamaba a SupabaseAdminAuthPort.deleteUser para liberar el correo en Supabase Auth.
    // Desde que la identidad es propia (docs/MODULO_AUTH.md, 2026-08-26) no hay ningun sistema
    // externo que compensar: el borrado real es el deleteUserPort.deleteById de arriba, contra
    // nuestro Postgres y dentro de la misma transaccion. El puerto quedaba servido por un
    // adaptador NoOp que solo loguea, asi que retirarlo no cambia ningun comportamiento.

    /** Panel admin de solicitudes de cuenta (gap #9). Listado, sin un recurso previo por
     * id que proteger: el gate de admin va primero. */
    @Override
    public PaginaAccountRequests listar(ListAccountRequestsCommand command) {
        requireAdminGuard.requireAdminActivo(command.actorId());
        var contenido = loadAccountRequestPort.pagina(command.statusFilter(), command.page(), command.size());
        long total = loadAccountRequestPort.contar(command.statusFilter());
        return new PaginaAccountRequests(contenido, total, command.page(), command.size());
    }

    /** El recurso se carga PRIMERO (404 si no existe), el gate de admin va DESPUES
     * (docs/BITACORA_ERRORES.md E-42), fail-closed via {@link RequireAdminGuard}. */
    @Override
    @Transactional
    public void eliminar(DeleteAccountRequestUseCase.DeleteAccountRequestCommand command) {
        requireRequest(command.requestId());
        requireAdminGuard.requireAdminActivo(command.actorId());
        deleteAccountRequestPort.deleteById(command.requestId());
    }

    /** PUBLIC_ENDPOINT (gap #9) — ver javadoc de {@link CheckAccountRequestStatusUseCase}. */
    @Override
    public AccountRequestStatusView consultar(AccountRequestId requestId) {
        AccountRequest request = requireRequest(requestId);
        return new AccountRequestStatusView(request.status(), request.rejectionReason());
    }
}
