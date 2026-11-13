package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.RateLimitExceededException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.CodigoPorWhatsApp;
import com.renaser.os.users.api.CodigoPorWhatsApp.NumeroParaCodigo;
import com.renaser.os.users.api.WhatsappDeLaFichaFinder;
import com.renaser.os.users.application.ports.in.autenticacion.PedirCodigoPorWhatsAppUseCase;
import com.renaser.os.users.application.ports.out.autenticacion.CodigoResetContrasenaPort;
import com.renaser.os.users.application.ports.out.autenticacion.CodigoVerificacionEmailPort;
import com.renaser.os.users.application.ports.out.autenticacion.LimitarSolicitudesResetPort;
import com.renaser.os.users.application.ports.out.autenticacion.LoadCredencialPort;
import com.renaser.os.users.application.ports.out.autenticacion.LoadCredencialPort.CredencialParaLogin;
import com.renaser.os.users.application.ports.out.whatsapp.WhatsappConfirmadoPort;
import com.renaser.os.users.domain.model.user.Email;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * El codigo de verificacion por WhatsApp (spec 043, D-325), para cuando no llega por correo (por ejemplo, un buzon lleno).
 *
 * <p><b>Es el mismo codigo del correo</b> mientras siga vigente ({@link CodigoVerificacionEmailPort#vigente}): pedirlo por WhatsApp no reinicia el
 * contador de intentos (asi no regala intentos para adivinarlo), no deja dos codigos validos a la vez y, si el correo llega tarde, los dos dicen lo
 * mismo. Si ya vencio, se genera uno nuevo con la MISMA vigencia (10 minutos) y los MISMOS 5 intentos que el del correo: se verifica por los mismos
 * endpoints de siempre ({@code /email-verification/confirm} y {@code /password/verify-code}).
 *
 * <p><b>Limites</b> (el WhatsApp cuesta y no se puede dejar como un cañon contra un numero ajeno): por origen, por numero, por correo y uno global
 * por hora. En el alta un limite se dice (429): quien pide escribio el numero y no hay nada que esconder. En la recuperacion solo el limite por
 * origen y por (origen, correo) se dice; los que dependen de la cuenta —su numero, el tope global— se saltan en silencio para no revelar nada (FR-009).
 */
@Service
class CodigoPorWhatsAppDeAccesoService implements PedirCodigoPorWhatsAppUseCase {

    static final Duration VENTANA = Duration.ofHours(1);
    /** Envios por hora desde un mismo origen (IP), sumando el alta y la recuperacion. */
    static final int LIMITE_POR_ORIGEN = 10;
    /** Envios por hora a un mismo numero: con tres ya se ve si llega o no. */
    static final int LIMITE_POR_NUMERO = 3;
    /** Envios por hora para un mismo correo (el alta) o pedidos por hora de un origen para un mismo correo (la recuperacion). */
    static final int LIMITE_POR_CORREO = 3;
    /** Espera entre dos pedidos por WhatsApp para el mismo correo, como la del correo (E-153): sin ella, los tres se gastan en tres toques. */
    static final Duration ESPERA_ENTRE_ENVIOS = Duration.ofSeconds(60);
    /** Tope de todos los codigos por WhatsApp de una hora: el freno del costo si alguien reparte el abuso entre muchos origenes. */
    static final int LIMITE_GLOBAL_POR_HORA = 300;

    static final String SIN_NUMERO = "Escribe un número de WhatsApp válido, con el código de país.";
    static final String NO_DISPONIBLE = "Por ahora no podemos enviar el código por WhatsApp. Revisa tu correo o intenta más tarde.";
    static final String NO_SALIO = "No pudimos enviar el código por WhatsApp. Intenta de nuevo en unos minutos.";
    static final String DEMASIADOS = "Ya pediste varios códigos por WhatsApp. Intenta de nuevo en una hora.";
    private static final String PREFIJO = "codigo-whatsapp:";

    private final CodigoVerificacionEmailPort codigosDelRegistro;
    private final CodigoResetContrasenaPort codigosDeRecuperacion;
    private final LimitarSolicitudesResetPort limites;
    private final LoadCredencialPort credenciales;
    private final WhatsappDeLaFichaFinder whatsappDeLaFicha;
    private final CodigoPorWhatsApp whatsapp;
    private final WhatsappConfirmadoPort whatsappConfirmado;

    CodigoPorWhatsAppDeAccesoService(CodigoVerificacionEmailPort codigosDelRegistro, CodigoResetContrasenaPort codigosDeRecuperacion,
                                     LimitarSolicitudesResetPort limites, LoadCredencialPort credenciales,
                                     WhatsappDeLaFichaFinder whatsappDeLaFicha, CodigoPorWhatsApp whatsapp,
                                     WhatsappConfirmadoPort whatsappConfirmado) {
        this.codigosDelRegistro = codigosDelRegistro;
        this.codigosDeRecuperacion = codigosDeRecuperacion;
        this.limites = limites;
        this.credenciales = credenciales;
        this.whatsappDeLaFicha = whatsappDeLaFicha;
        this.whatsapp = whatsapp;
        this.whatsappConfirmado = whatsappConfirmado;
    }

    @Override
    public boolean disponible() {
        return whatsapp.configurado();
    }

    @Override
    public String paraElRegistro(CodigoDelRegistroCommand command) {
        String email = new Email(command.email()).value();
        NumeroParaCodigo numero = whatsapp.numero(command.whatsapp()).orElseThrow(() -> new IllegalArgumentException(SIN_NUMERO));
        exigirDisponible();
        exigirCupoDelRegistro(email, numero, command.requestIp());
        String codigo = codigosDelRegistro.vigente(email)
                .orElseGet(() -> codigosDelRegistro.generarCodigo(email, VerificacionEmailService.VIGENCIA_CODIGO));
        if (!whatsapp.enviar(numero, codigo)) {
            throw new IllegalStateException(NO_SALIO);
        }
        return numero.enmascarado();
    }

    /**
     * Siempre termina igual, tenga o no cuenta el correo, tenga o no WhatsApp guardado: sin respuesta y sin esperar al proveedor (el envio va en
     * otro hilo, {@link CodigoPorWhatsApp#enviarSinEsperar}), asi tampoco lo delata lo que tarda.
     */
    @Override
    public void paraRecuperar(CodigoDeRecuperacionCommand command) {
        String email = command.email();
        exigirCupoDelOrigen(command.requestIp());
        exigirCupo(OrigenDeLaPeticion.claveConEmail(PREFIJO + "origen-email:", command.requestIp(), email), LIMITE_POR_CORREO);
        if (!whatsapp.configurado()) {
            return;
        }
        numeroGuardado(email)
                .filter(numero -> hayCupo(PREFIJO + "numero:" + numero.valor(), LIMITE_POR_NUMERO) && hayCupoGlobal())
                .ifPresent(numero -> whatsapp.enviarSinEsperar(numero, codigoDeRecuperacion(email)));
    }

    /** El WhatsApp que ya tiene la cuenta (el de su ficha, que es tambien el del alta). Sin cuenta con contraseña, o sin numero, nada. */
    private Optional<NumeroParaCodigo> numeroGuardado(String email) {
        return credenciales.porEmail(email)
                .filter(CredencialParaLogin::permiteLoginPorContrasena)
                .map(CredencialParaLogin::usuarioId)
                .map(this::whatsappDe)
                .flatMap(whatsapp::numero);
    }

    /** El WhatsApp confirmado en Yo (spec 051) si lo hay; si no, el de la ficha. */
    private String whatsappDe(UserId persona) {
        return whatsappConfirmado.de(persona).orElseGet(() -> whatsappDeLaFicha.de(Set.of(persona)).get(persona));
    }

    private String codigoDeRecuperacion(String email) {
        return codigosDeRecuperacion.vigente(email)
                .orElseGet(() -> codigosDeRecuperacion.generarCodigo(email, VerificacionEmailService.VIGENCIA_CODIGO));
    }

    private void exigirDisponible() {
        if (!whatsapp.configurado()) {
            throw new IllegalStateException(NO_DISPONIBLE);
        }
    }

    /** La espera va antes que los limites por hora: un toque impaciente no gasta un envio de la hora (E-153). */
    private void exigirCupoDelRegistro(String email, NumeroParaCodigo numero, String requestIp) {
        exigirCupoDelOrigen(requestIp);
        if (!limites.registrarIntento(PREFIJO + "espera:" + email, ESPERA_ENTRE_ENVIOS, 1)) {
            throw new RateLimitExceededException("Espera un minuto antes de pedir otro código por WhatsApp.");
        }
        exigirCupo(PREFIJO + "email:" + email, LIMITE_POR_CORREO);
        exigirCupo(PREFIJO + "numero:" + numero.valor(), LIMITE_POR_NUMERO);
        if (!hayCupoGlobal()) {
            throw new RateLimitExceededException(NO_DISPONIBLE);
        }
    }

    private void exigirCupoDelOrigen(String requestIp) {
        exigirCupo(PREFIJO + "ip:" + OrigenDeLaPeticion.de(requestIp), LIMITE_POR_ORIGEN);
    }

    private void exigirCupo(String clave, int maximo) {
        if (!hayCupo(clave, maximo)) {
            throw new RateLimitExceededException(DEMASIADOS);
        }
    }

    private boolean hayCupo(String clave, int maximo) {
        return limites.registrarIntento(clave, VENTANA, maximo);
    }

    private boolean hayCupoGlobal() {
        return hayCupo(PREFIJO + "global", LIMITE_GLOBAL_POR_HORA);
    }
}
