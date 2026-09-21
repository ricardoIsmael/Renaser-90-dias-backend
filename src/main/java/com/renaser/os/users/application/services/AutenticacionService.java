package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.CredencialesInvalidasException;
import com.renaser.os.shared.domain.RateLimitExceededException;
import com.renaser.os.users.application.ports.out.autenticacion.LimitarSolicitudesResetPort;
import java.time.Duration;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionUseCase;
import com.renaser.os.users.application.ports.out.autenticacion.LoadCredencialPort;
import com.renaser.os.users.application.ports.out.autenticacion.LoadCredencialPort.CredencialParaLogin;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.user.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AutenticacionService implements IniciarSesionUseCase {

    /**
     * Hash valido de una contrasena que nadie usa, para comparar contra algo real cuando el
     * email no existe (ver {@link #iniciarSesion}). Cualquier hash BCrypt sirve — no hace falta
     * que corresponda a una contrasena en particular, solo que {@code passwordEncoder.matches}
     * tenga que hacer el trabajo criptografico completo.
     */
    private static final String HASH_SENUELO =
            "{bcrypt}$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5vTMBRfXHRT6QK0OEQmnFhFq6HZ0O";

    /**
     * Misma ventana que el resto de los limites del modulo (reset de contrasena, verificacion
     * de email, alta de solicitudes), para no tener cuatro criterios distintos conviviendo.
     *
     * <p>Los topes son mas altos que los de esos flujos, y a proposito: pedir un reseteo es
     * algo que se hace una vez cada mucho, mientras que iniciar sesion es rutina. Cinco
     * intentos por hora dejaria afuera a alguien que simplemente se equivoco de contrasena un
     * par de veces. Diez sigue haciendo inviable adivinar una contrasena, que es lo que hay
     * que impedir.
     *
     * <p><b>Son numeros elegidos por quien escribio esto, no confirmados por el dueno del
     * proyecto.</b> Si resultan molestos en uso real, se suben; lo que no se puede es no tener
     * ninguno, que era el estado hasta ahora.
     */
    static final Duration VENTANA_RATE_LIMIT = Duration.ofHours(1);

    /**
     * Diez intentos por hora contra el MISMO correo DESDE EL MISMO ORIGEN (2026-09-21). Hasta
     * ahora este tope colgaba del correo a secas, y por eso se podia gastar desde afuera: el
     * numero no cambio, lo que cambio es quien lo paga. El razonamiento completo, y la regla
     * general que deja esto asi, estan en {@link OrigenDeLaPeticion}.
     */
    static final int LIMITE_POR_EMAIL_Y_ORIGEN = 10;

    static final int LIMITE_POR_IP = 50;

    /**
     * El mismo texto para los dos topes: decir cual de los dos se alcanzo le confirmaria a
     * quien sondea que ese correo existe y esta siendo defendido.
     */
    private static final String MENSAJE_LIMITE = "Demasiados intentos. Espera unos minutos.";

    private final LoadCredencialPort loadCredencialPort;
    private final LoadUserPort loadUserPort;
    private final PasswordEncoder passwordEncoder;
    private final LimitarSolicitudesResetPort limitarIntentosPort;

    public AutenticacionService(LoadCredencialPort loadCredencialPort, LoadUserPort loadUserPort,
                                 PasswordEncoder passwordEncoder,
                                 LimitarSolicitudesResetPort limitarIntentosPort) {
        this.loadCredencialPort = loadCredencialPort;
        this.loadUserPort = loadUserPort;
        this.passwordEncoder = passwordEncoder;
        this.limitarIntentosPort = limitarIntentosPort;
    }

    /**
     * Compara contra un hash señuelo aunque el email no exista o la cuenta no tenga contrasena,
     * para que {@code passwordEncoder.matches} corra siempre el mismo trabajo de BCrypt — sin
     * esto, el tiempo de respuesta delata si un email esta registrado (responder rapido cuando
     * no existe, lento cuando si) antes de que el mensaje de error llegue a decir nada.
     */
    @Override
    public User iniciarSesion(IniciarSesionCommand command) {
        requireDentroDelLimite(command);
        Optional<CredencialParaLogin> credencial = loadCredencialPort.porEmail(command.email());
        String hashParaComparar = credencial.filter(CredencialParaLogin::permiteLoginPorContrasena)
                .map(CredencialParaLogin::hash)
                .orElse(HASH_SENUELO);
        boolean coincide = passwordEncoder.matches(command.contrasena(), hashParaComparar);

        boolean loginValido = credencial.isPresent() && credencial.get().cuentaHabilitada()
                && credencial.get().permiteLoginPorContrasena() && coincide;
        if (!loginValido) {
            throw new CredencialesInvalidasException();
        }
        return loadUserPort.byId(credencial.get().usuarioId())
                .orElseThrow(() -> new IllegalStateException(
                        "Credencial sin usuario correspondiente: " + credencial.get().usuarioId()));
    }

    /**
     * Se cuenta ANTES de comparar la contrasena, y se cuenta TODO intento, no solo los
     * fallidos. Contar solo los fallidos deja abierta la puerta que se quiere cerrar: quien
     * prueba contrasenas al azar acierta o no, y si acierta ya entro — el limite tiene que
     * gastarse con el intento, no con su resultado.
     *
     * <p><b>Contra QUIEN se cuenta (2026-09-21).</b> Los dos topes cuelgan ahora del origen de
     * la peticion. El de la pareja (origen, correo) reemplaza al viejo {@code login:email:},
     * que colgaba del correo a secas: con aquel, diez peticiones anonimas con el correo de otra
     * persona la dejaban afuera de su propia cuenta el resto de la hora, porque el tope se
     * agotaba y el rechazo salia antes de que se llegara a comparar ningun hash. Contra un
     * mismo origen el freno es exactamente el de antes —el intento once desde ahi rebota— pero
     * el cupo de cada quien ya no lo puede gastar un tercero.
     *
     * <p><b>Y por que ya no hay ningun tope global por correo.</b> Se evaluo dejarlo con un
     * umbral mas alto, para frenar un ataque repartido entre muchos origenes. No se puede
     * elegir ese umbral: como cada origen aporta hasta {@link #LIMITE_POR_IP} por ventana, un
     * tope global de T se agota con T/50 origenes —seis para 300— y vuelve a ser la palanca de
     * expulsion que se esta sacando. Y al reves, un tope global que la duena pudiera saltearse
     * con su contrasena correcta no frena ni un intento: una contrasena equivocada se rechaza
     * igual que siempre, y una acertada ya entro. Frenar el ataque repartido pide un desafio de
     * posesion del correo, no un contador; el modulo ya tiene uno y esta enfrente —
     * {@code POST /auth/password/forgot} manda un codigo de 6 digitos a la casilla—, y
     * {@code ResetContrasenaService} lo deja abierto por construccion: ningun tercero puede
     * cerrarlo. Esa es la salida de quien se quedo afuera, y por eso no hay que inventar otra.
     *
     * <p>El orden importa: el guard por origen va PRIMERO. Si fuera al reves, un origen que ya
     * agoto sus 50 por hora seguiria creando una clave nueva en Redis por cada correo que
     * tocara, aunque cada peticion le devolviera 429.
     */
    private void requireDentroDelLimite(IniciarSesionCommand command) {
        if (!limitarIntentosPort.registrarIntento("login:ip:" + OrigenDeLaPeticion.de(command.requestIp()),
                VENTANA_RATE_LIMIT, LIMITE_POR_IP)) {
            throw new RateLimitExceededException(MENSAJE_LIMITE);
        }
        if (!limitarIntentosPort.registrarIntento(
                OrigenDeLaPeticion.claveConEmail("login:origen-email:", command.requestIp(), command.email()),
                VENTANA_RATE_LIMIT, LIMITE_POR_EMAIL_Y_ORIGEN)) {
            throw new RateLimitExceededException(MENSAJE_LIMITE);
        }
    }
}
