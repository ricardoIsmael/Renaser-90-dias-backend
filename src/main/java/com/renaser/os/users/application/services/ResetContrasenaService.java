package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.CodigoVerificacionInvalidoException;
import com.renaser.os.shared.domain.RateLimitExceededException;
import com.renaser.os.shared.domain.TokenResetInvalidoException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.autenticacion.CerrarTodasLasSesionesUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.ConfirmarResetContrasenaUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.SolicitarCodigoResetContrasenaUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.SolicitarResetContrasenaUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.VerificarCodigoResetContrasenaUseCase;
import com.renaser.os.users.application.ports.out.autenticacion.CodigoResetContrasenaPort;
import com.renaser.os.users.application.ports.out.autenticacion.EnviarEmailPort;
import com.renaser.os.users.application.ports.out.autenticacion.LimitarSolicitudesResetPort;
import com.renaser.os.users.application.ports.out.autenticacion.LoadCredencialPort;
import com.renaser.os.users.application.ports.out.autenticacion.LoadCredencialPort.CredencialParaLogin;
import com.renaser.os.users.application.ports.out.autenticacion.SaveCredencialPort;
import com.renaser.os.users.application.ports.out.autenticacion.TokenResetContrasenaPort;
import com.renaser.os.users.domain.model.user.Credencial;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;

/**
 * Reseteo de contrasena (docs/MODULO_AUTH.md §7.5 y §7.6, §2.2). No hay tabla de tokens (BD
 * congelada, D-40): el token vive en Redis con TTL de 30 min via
 * {@link TokenResetContrasenaPort}, que ya garantiza la atomicidad del "un solo uso".
 *
 * <p>Dos formas de OBTENER ese token, un solo lugar donde se CONSUME ({@link #confirmar}):
 *
 * <ul>
 *   <li><b>Por link</b> (§7.5, 2026-08-26): {@link #solicitar} manda un correo con el token
 *       adentro de una URL. Pensado para un frontend web que todavia no existe.</li>
 *   <li><b>Por codigo</b> (§7.6, D-102, 2026-09-04): {@link #solicitarCodigo} manda un codigo
 *       de 6 digitos y {@link #verificarCodigo} lo canjea por el token. Es el que usa la app:
 *       la persona no sale a un navegador. Vigencia y limite de intentos son LOS MISMOS que
 *       el codigo del alta ({@link VerificacionEmailService}) — se referencian, no se copian,
 *       para que la regla tenga un solo dueno.</li>
 * </ul>
 */
@Service
public class ResetContrasenaService implements SolicitarResetContrasenaUseCase, ConfirmarResetContrasenaUseCase,
        SolicitarCodigoResetContrasenaUseCase, VerificarCodigoResetContrasenaUseCase {

    /** 30 minutos, fijado en docs/MODULO_AUTH.md §2.2 ("es un token de un solo uso que vive 30 minutos"). */
    static final Duration VIGENCIA_TOKEN = Duration.ofMinutes(30);

    /**
     * Umbrales de la ventana de una hora. No estan especificados en CLAUDE.MD/MODULO_AUTH.md
     * (que solo piden "rate limit por email y por IP", sin numeros) — se asumen estos valores
     * por analogia con el unico precedente del repo (60/hora por IP en
     * {@code AccountRequestService}), quedan documentados como decision a confirmar.
     */
    static final Duration VENTANA_RATE_LIMIT = Duration.ofHours(1);

    /**
     * Cinco pedidos por hora contra el MISMO correo DESDE EL MISMO ORIGEN (2026-09-21). El
     * numero es el de siempre; lo que cambio es que ya no cuelga del correo a secas, asi que
     * un tercero no puede gastarle el cupo a nadie. Ver {@link OrigenDeLaPeticion}.
     */
    static final int LIMITE_POR_EMAIL_Y_ORIGEN = 5;

    static final int LIMITE_POR_IP = 20;

    /**
     * Tope de CORREOS ENVIADOS a una misma casilla por hora, no de peticiones recibidas
     * (2026-09-21). Es el unico contador del flujo que sigue colgando solo del correo, y por
     * eso es el unico que NO rechaza nada: si esta agotado, {@link #solicitar} y
     * {@link #solicitarCodigo} se saltean el envio y devuelven lo mismo que devuelven siempre
     * —202, exista o no la cuenta—, que es justo el contrato de no-enumeracion que estos dos
     * endpoints ya tenian. Existe porque cada pedido le mete un correo a una casilla ajena, y
     * sin ningun tope la recuperacion seria un canon para bombardear un buzon con nuestro
     * propio servidor de mail.
     *
     * <p>Sube de 5 a 30 porque cambio de trabajo: 5 era el corte duro de la puerta (y por eso
     * cerrarla costaba cinco peticiones), 30 es cuanto mail se tolera hacia una casilla antes
     * de frenar el grifo. Con el guard por origen delante, un solo origen puede empujarlo a lo
     * sumo {@link #LIMITE_POR_IP} veces, y contra un correo concreto solo
     * {@link #LIMITE_POR_EMAIL_Y_ORIGEN}: llegar a 30 pide seis origenes distintos.
     */
    static final int LIMITE_DE_ENVIOS_POR_EMAIL = 30;

    /** Mismo texto para los dos topes que rechazan, por lo mismo que en el login. */
    private static final String MENSAJE_LIMITE = "Limite de solicitudes de reseteo de contrasena excedido";

    private final LoadCredencialPort loadCredencialPort;
    private final SaveCredencialPort saveCredencialPort;
    private final TokenResetContrasenaPort tokenResetContrasenaPort;
    private final CodigoResetContrasenaPort codigoResetContrasenaPort;
    private final LimitarSolicitudesResetPort limitarSolicitudesResetPort;
    private final EnviarEmailPort enviarEmailPort;
    private final CerrarTodasLasSesionesUseCase cerrarTodasLasSesionesUseCase;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public ResetContrasenaService(LoadCredencialPort loadCredencialPort, SaveCredencialPort saveCredencialPort,
                                   TokenResetContrasenaPort tokenResetContrasenaPort,
                                   CodigoResetContrasenaPort codigoResetContrasenaPort,
                                   LimitarSolicitudesResetPort limitarSolicitudesResetPort,
                                   EnviarEmailPort enviarEmailPort,
                                   CerrarTodasLasSesionesUseCase cerrarTodasLasSesionesUseCase,
                                   PasswordEncoder passwordEncoder, Clock clock) {
        this.loadCredencialPort = loadCredencialPort;
        this.saveCredencialPort = saveCredencialPort;
        this.tokenResetContrasenaPort = tokenResetContrasenaPort;
        this.codigoResetContrasenaPort = codigoResetContrasenaPort;
        this.limitarSolicitudesResetPort = limitarSolicitudesResetPort;
        this.enviarEmailPort = enviarEmailPort;
        this.cerrarTodasLasSesionesUseCase = cerrarTodasLasSesionesUseCase;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    public void solicitar(SolicitarResetContrasenaCommand command) {
        rejectIfRateLimitExceeded(command.email(), command.requestIp());

        // Sin `else`: si no hay cuenta, o la cuenta no tiene contrasena (solo entra por
        // proveedor social), o el buzon ya recibio demasiado correo nuestro en esta hora, el
        // metodo simplemente no hace nada mas — misma no-enumeracion que el login
        // (CLAUDE.MD §5.3.3): la respuesta es 202 en los cuatro casos.
        cuentaConContrasena(command.email()).ifPresent(credencial -> {
            if (hayCupoDeEnvioA(command.email())) {
                emitirTokenYEnviarCorreo(credencial.usuarioId(), command.email());
            }
        });
    }

    /**
     * Mismos contadores que {@link #solicitar} (claves {@code ip:}/{@code origen-email:} y el
     * tope de envios {@code email-envios:}), a proposito: para el limite, pedir un link o pedir
     * un codigo es la misma accion — "alguien quiere recuperar esta cuenta" — y separarlos
     * duplicaria el cupo.
     */
    @Override
    public void solicitarCodigo(SolicitarCodigoResetContrasenaCommand command) {
        rejectIfRateLimitExceeded(command.email(), command.requestIp());

        cuentaConContrasena(command.email()).ifPresent(credencial -> {
            if (hayCupoDeEnvioA(command.email())) {
                emitirCodigoYEnviarCorreo(command.email());
            }
        });
    }

    /**
     * Se vuelve a buscar la cuenta DESPUES de consumir el codigo, no antes: si se buscara
     * primero y no existiera, habria que decidir si gastar un intento o no — y cualquiera de
     * las dos opciones deja una diferencia observable entre "correo sin cuenta" y "codigo
     * equivocado". Consumiendo primero, los dos caminos cuestan lo mismo y fallan igual.
     */
    @Override
    public ResultadoVerificacionReset verificarCodigo(VerificarCodigoResetContrasenaCommand command) {
        boolean coincide = codigoResetContrasenaPort.verificarCodigo(command.email(), command.codigo(),
                VerificacionEmailService.MAX_INTENTOS);
        if (!coincide) {
            throw new CodigoVerificacionInvalidoException();
        }
        UserId usuarioId = cuentaConContrasena(command.email())
                .map(CredencialParaLogin::usuarioId)
                .orElseThrow(CodigoVerificacionInvalidoException::new);
        String token = tokenResetContrasenaPort.generar(usuarioId, VIGENCIA_TOKEN);
        return new ResultadoVerificacionReset(token);
    }

    @Override
    @Transactional
    public void confirmar(ConfirmarResetContrasenaCommand command) {
        UserId usuarioId = tokenResetContrasenaPort.consumir(command.token())
                .orElseThrow(TokenResetInvalidoException::new);

        Credencial nueva = new Credencial(passwordEncoder.encode(command.contrasenaNueva()), clock.now());
        saveCredencialPort.guardar(usuarioId, nueva);

        // Razon de negocio completa de este paso (CLAUDE.MD, docs/MODULO_AUTH.md §4.1): cambiar
        // la contrasena por sospecha de robo debe invalidar cualquier sesion que el atacante ya
        // tenga abierta, no solo impedir logins futuros.
        cerrarTodasLasSesionesUseCase.cerrarTodas(usuarioId);
    }

    private Optional<CredencialParaLogin> cuentaConContrasena(String email) {
        return loadCredencialPort.porEmail(email).filter(CredencialParaLogin::permiteLoginPorContrasena);
    }

    private void emitirTokenYEnviarCorreo(UserId usuarioId, String email) {
        String token = tokenResetContrasenaPort.generar(usuarioId, VIGENCIA_TOKEN);
        enviarEmailPort.enviarResetContrasena(email, token);
    }

    private void emitirCodigoYEnviarCorreo(String email) {
        String codigo = codigoResetContrasenaPort.generarCodigo(email, VerificacionEmailService.VIGENCIA_CODIGO);
        enviarEmailPort.enviarCodigoResetContrasena(email, codigo);
    }

    /**
     * Se consume solo cuando de verdad va a salir un correo: el tope cuenta mail enviado, no
     * peticiones. Un correo sin cuenta nunca lo toca, y eso no se nota desde afuera porque la
     * respuesta es 202 igual.
     */
    private boolean hayCupoDeEnvioA(String email) {
        return limitarSolicitudesResetPort.registrarIntento("email-envios:" + email, VENTANA_RATE_LIMIT,
                LIMITE_DE_ENVIOS_POR_EMAIL);
    }

    /**
     * Los dos topes que rechazan cuelgan del origen de la peticion (2026-09-21). El de la
     * pareja (origen, correo) reemplaza al viejo {@code email:}, que colgaba del correo a
     * secas: con aquel, cinco peticiones anonimas con el correo de otra persona le cerraban la
     * recuperacion el resto de la hora —las dos puertas a la vez, porque {@link #solicitar} y
     * {@link #solicitarCodigo} comparten contador—, y eso convertia la unica salida de emergencia
     * en algo que un tercero podia clausurar. Ahora esa salida no se puede cerrar desde afuera,
     * que es lo que sostiene la decision del login de no tener ningun tope global por correo
     * (ver {@code AutenticacionService.requireDentroDelLimite}).
     *
     * <p>El guard por origen va primero, por lo mismo que en el login: un origen sin cupo no
     * debe poder seguir creando una clave nueva por cada correo que toque.
     */
    private void rejectIfRateLimitExceeded(String email, String requestIp) {
        if (!limitarSolicitudesResetPort.registrarIntento("ip:" + OrigenDeLaPeticion.de(requestIp),
                VENTANA_RATE_LIMIT, LIMITE_POR_IP)) {
            throw new RateLimitExceededException(MENSAJE_LIMITE);
        }
        if (!limitarSolicitudesResetPort.registrarIntento(
                OrigenDeLaPeticion.claveConEmail("origen-email:", requestIp, email),
                VENTANA_RATE_LIMIT, LIMITE_POR_EMAIL_Y_ORIGEN)) {
            throw new RateLimitExceededException(MENSAJE_LIMITE);
        }
    }
}
