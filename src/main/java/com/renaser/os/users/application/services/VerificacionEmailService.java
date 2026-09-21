package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.CodigoVerificacionInvalidoException;
import com.renaser.os.shared.domain.RateLimitExceededException;
import com.renaser.os.users.application.ports.in.autenticacion.ConfirmarCodigoVerificacionEmailUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.EnviarCodigoVerificacionEmailUseCase;
import com.renaser.os.users.application.ports.out.autenticacion.CodigoVerificacionEmailPort;
import com.renaser.os.users.application.ports.out.autenticacion.EnviarEmailPort;
import com.renaser.os.users.application.ports.out.autenticacion.LimitarSolicitudesResetPort;
import com.renaser.os.users.application.ports.out.autenticacion.TokenVerificacionEmailPort;
import com.renaser.os.users.domain.model.user.Email;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Verificacion de propiedad de un email ANTES del alta de cuenta (2026-08-27,
 * docs/PLAN_INTEGRACION_FRONTEND.md — reemplaza el {@code signInWithOtp}/{@code verifyOtp} de
 * Supabase que el registro ya no puede usar desde que {@code AccountRequestService.submit}
 * genera su propio UUID). Disenado contra guias externas, no inventado (ver commit): OWASP
 * Multifactor Authentication Cheat Sheet ("apply strict attempt limits" — de ahi
 * {@link #MAX_INTENTOS}) y Forgot Password Cheat Sheet (token de un solo uso, invalidado tras
 * usarse). Spring Security tiene su propio "One-Time Token Login" (6.4+) pero exige un usuario
 * YA EXISTENTE y almacenamiento JDBC — no encaja: aca el email todavia no es nadie, y una tabla
 * nueva violaria D-40 (BD congelada). Mismo patron Redis que {@link ResetContrasenaService},
 * dos piezas separadas a proposito:
 *
 * <ul>
 *   <li>{@link CodigoVerificacionEmailPort}: el codigo de 6 digitos que una PERSONA tipea —
 *       corto, memorizable, con limite de intentos porque su espacio (1 millon) es chico.</li>
 *   <li>{@link TokenVerificacionEmailPort}: el token opaco que el CLIENTE guarda y reenvia con
 *       el resto del formulario de alta — alta entropia, no pensado para que nadie lo tipee.</li>
 * </ul>
 */
@Service
class VerificacionEmailService implements EnviarCodigoVerificacionEmailUseCase, ConfirmarCodigoVerificacionEmailUseCase {

    /** 10 minutos: Spring Security OTT usa 5 min por defecto: se da margen de usabilidad
     * (revisar el correo no es instantaneo) sin llegar a la vigencia de 30 min que usa el link
     * de activacion de cuenta (ese es para un correo que se puede mirar horas despues; este es
     * para un codigo que se tipea en el momento). */
    static final Duration VIGENCIA_CODIGO = Duration.ofMinutes(10);

    /** OWASP Multifactor Authentication Cheat Sheet: "apply strict attempt limits". Con 6
     * digitos (1 millon de combinaciones), 5 intentos deja una probabilidad de acierto por
     * fuerza bruta de 0.0005% antes de que el codigo se invalide solo. */
    static final int MAX_INTENTOS = 5;

    /** Tiempo para completar el resto del formulario de alta despues de verificar el correo. */
    static final Duration VIGENCIA_TOKEN_VERIFICACION = Duration.ofMinutes(30);

    /** Mismos umbrales que {@code ResetContrasenaService} (documentados ahi como asuncion, no
     * confirmados por producto — A-5): se repiten aca por consistencia, no por certeza nueva. */
    static final Duration VENTANA_RATE_LIMIT = Duration.ofHours(1);
    static final int LIMITE_POR_EMAIL = 5;
    static final int LIMITE_POR_IP = 20;

    /**
     * Espera obligatoria entre un envio de codigo y el siguiente, para el MISMO correo.
     *
     * <p><b>Por que existe (E-153).</b> Los limites de arriba son los estandar de la industria
     * (5 por correo y 10-20 por IP, por hora) y no habia que tocarlos. Lo que faltaba era esto:
     * sin ninguna espera, los 5 codigos de la hora se podian pedir <b>en cinco segundos</b>
     * apretando "reenviar", y la persona quedaba bloqueada una hora entera sin entender por que.
     * Paso la noche del 6 de septiembre: tres aprendices llegaron a 16, 15 y 10 pedidos
     * —muy por encima del limite— porque un fallo del alta las obligaba a reintentar, y hubo que
     * destrabarlas a mano. La recomendacion habitual es una espera de 30 segundos, que corta
     * entre el 60 y el 70 por ciento de los reenvios inutiles.
     *
     * <p>Se cuenta solo por correo y no por IP: dos personas en la misma casa o en el mismo
     * local comparten IP, y hacer esperar a una por lo que pidio la otra seria castigar a quien
     * no hizo nada. El abuso desde una IP ya lo cubre {@link #LIMITE_POR_IP}.
     */
    static final Duration ESPERA_ENTRE_ENVIOS = Duration.ofSeconds(30);

    private final CodigoVerificacionEmailPort codigoVerificacionEmailPort;
    private final TokenVerificacionEmailPort tokenVerificacionEmailPort;
    private final LimitarSolicitudesResetPort limitarSolicitudesResetPort;
    private final EnviarEmailPort enviarEmailPort;

    VerificacionEmailService(CodigoVerificacionEmailPort codigoVerificacionEmailPort,
                              TokenVerificacionEmailPort tokenVerificacionEmailPort,
                              LimitarSolicitudesResetPort limitarSolicitudesResetPort,
                              EnviarEmailPort enviarEmailPort) {
        this.codigoVerificacionEmailPort = codigoVerificacionEmailPort;
        this.tokenVerificacionEmailPort = tokenVerificacionEmailPort;
        this.limitarSolicitudesResetPort = limitarSolicitudesResetPort;
        this.enviarEmailPort = enviarEmailPort;
    }

    @Override
    public void enviar(EnviarCodigoVerificacionEmailCommand command) {
        String email = canonico(command.email());
        rejectIfRateLimitExceeded(email, command.requestIp());
        String codigo = codigoVerificacionEmailPort.generarCodigo(email, VIGENCIA_CODIGO);
        enviarEmailPort.enviarCodigoVerificacionEmail(email, codigo);
    }

    @Override
    public ResultadoVerificacion confirmar(ConfirmarCodigoVerificacionEmailCommand command) {
        // La MISMA canonizacion que enviar(), y en el mismo commit: el codigo se guarda bajo la
        // clave canonica, asi que buscarlo bajo el texto crudo no lo encontraria nunca.
        String email = canonico(command.email());
        boolean coincide = codigoVerificacionEmailPort.verificarCodigo(email, command.codigo(),
                MAX_INTENTOS);
        if (!coincide) {
            throw new CodigoVerificacionInvalidoException();
        }
        String token = tokenVerificacionEmailPort.generar(email, VIGENCIA_TOKEN_VERIFICACION);
        return new ResultadoVerificacion(token);
    }

    /**
     * Forma canonica de la direccion: la unica que este servicio puede usar para armar claves de
     * Redis y para elegir destinatario.
     *
     * <p><b>Por que (auditoria 2026-09-21).</b> Los limites de arriba dicen "para el MISMO
     * correo", pero se contaban sobre el String crudo del cuerpo HTTP. La parte de dominio de una
     * direccion es insensible a mayusculas para la entrega —los MX se resuelven por DNS, que no
     * distingue caja—, asi que {@code victima@gmail.com} y {@code victima@GMAIL.com} eran dos
     * contadores distintos en Redis y un solo buzon en el servidor de correo: cada variacion de
     * mayusculas estrenaba su cupo de {@link #LIMITE_POR_EMAIL} y se saltaba la espera de
     * {@link #ESPERA_ENTRE_ENVIOS}. El limite por buzon era, en realidad, un limite por spelling.
     *
     * <p>La regla de canonizacion no se inventa aca: es la del dominio ({@code Email}, trim +
     * minusculas), la misma que la base guarda en {@code usuarios.email} y el mismo gesto que ya
     * hace {@code AccountRequestService.submit:145}. Se llama en el primer renglon de los dos
     * puntos de entrada y desde ahi no se vuelve a mirar {@code command.email()}, que es lo que
     * garantiza que clave y destinatario sean la MISMA identidad.
     *
     * <p>El formato tambien se valida de paso —{@code Email} rechaza lo que no tenga forma de
     * direccion, y desde el arreglo de JNDI tampoco acepta ':' ni '/' en el dominio—, asi que
     * este camino ya no le entrega al adaptador SMTP un destinatario que nadie reviso: era el
     * unico de los cuatro que llaman al puerto de correo que no pasaba antes por la base. El
     * {@code IllegalArgumentException} que puede lanzar lo traduce {@code GlobalExceptionHandler}
     * a 400, igual que en {@code ConsultaEmailService.estaRegistrado}: el borde no cambia de
     * contrato, y lo que antes respondia 202 para una direccion imposible ahora responde 400.
     */
    private static String canonico(String email) {
        return new Email(email).value();
    }

    /**
     * La espera se chequea ANTES que los limites por hora, a proposito: si se hiciera al reves,
     * cada clic impaciente gastaria uno de los 5 envios de la hora antes de rebotar, y el
     * remedio terminaria provocando el bloqueo que viene a evitar.
     *
     * <p>Se implementa con el mismo contador atomico que los demas limites, con maximo 1 y
     * ventana de 30 s: el primer envio pasa, cualquiera dentro de esos 30 s rebota, y la clave
     * caduca sola. No hace falta un puerto nuevo.
     */
    private void rejectIfEsperaEntreEnviosNoCumplida(String email) {
        if (!limitarSolicitudesResetPort.registrarIntento("email-verification:espera:" + email,
                ESPERA_ENTRE_ENVIOS, 1)) {
            throw new RateLimitExceededException("Espera " + ESPERA_ENTRE_ENVIOS.toSeconds()
                    + " segundos antes de pedir otro codigo");
        }
    }

    /** {@code email} llega SIEMPRE canonizado por {@link #canonico}: ver alli por que importa. */
    private void rejectIfRateLimitExceeded(String email, String requestIp) {
        rejectIfEsperaEntreEnviosNoCumplida(email);
        if (!limitarSolicitudesResetPort.registrarIntento("email-verification:email:" + email, VENTANA_RATE_LIMIT,
                LIMITE_POR_EMAIL)) {
            throw new RateLimitExceededException("Limite de solicitudes de verificacion de correo excedido");
        }
        if (requestIp != null && !limitarSolicitudesResetPort.registrarIntento("email-verification:ip:" + requestIp,
                VENTANA_RATE_LIMIT, LIMITE_POR_IP)) {
            throw new RateLimitExceededException("Limite de solicitudes de verificacion de correo excedido");
        }
    }
}
