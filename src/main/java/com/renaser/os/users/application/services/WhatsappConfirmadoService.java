package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.RateLimitExceededException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.domain.WhatsappDeLaCuentaException;
import com.renaser.os.shared.domain.WhatsappDeLaCuentaException.Motivo;
import com.renaser.os.users.api.CodigoPorWhatsApp;
import com.renaser.os.users.api.CodigoPorWhatsApp.NumeroParaCodigo;
import com.renaser.os.users.application.ports.in.whatsapp.WhatsappConfirmadoUseCase;
import com.renaser.os.users.application.ports.out.autenticacion.LimitarSolicitudesResetPort;
import com.renaser.os.users.application.ports.out.whatsapp.CodigoDeWhatsappDeLaCuentaPort;
import com.renaser.os.users.application.ports.out.whatsapp.WhatsappConfirmadoPort;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Yo → «WhatsApp» (spec 051): agregar o cambiar el WhatsApp de la cuenta, confirmado con un codigo que llega a ese numero. Sin
 * {@code @Transactional}: mandar el WhatsApp no va dentro de una transaccion; guardar es una sola sentencia.
 *
 * <p><b>Limites</b> (el WhatsApp cuesta y no puede ser un cañon contra un numero ajeno): una espera de 60 s y 5 codigos por hora por cuenta, y
 * <b>los mismos contadores por origen (10/h), por numero (3/h) y global (300/h) del codigo de acceso</b> (spec 043, mismas claves): un numero
 * recibe como maximo 3 codigos por hora sumando los dos usos. Confirmar cuenta 10 intentos por hora y cuenta, ademas de los 5 por codigo.
 *
 * <p><b>Por que no hay un «ese numero ya esta en uso» al pedir el codigo</b>: seria un oraculo para saber que numeros tienen cuenta. Se dice al
 * confirmar, cuando quien pide ya demostro que controla el numero.
 */
@Service
class WhatsappConfirmadoService implements WhatsappConfirmadoUseCase {

    static final Duration VENTANA = Duration.ofHours(1);
    static final Duration ESPERA_ENTRE_CODIGOS = Duration.ofSeconds(60);
    static final int CODIGOS_POR_HORA_Y_CUENTA = 5;
    static final int INTENTOS_DE_CONFIRMAR_POR_HORA = 10;
    /** Los mismos topes (y las mismas claves) que {@code CodigoPorWhatsAppDeAccesoService}: el costo es del numero y del conjunto, no del uso. */
    static final int LIMITE_POR_ORIGEN = 10;
    static final int LIMITE_POR_NUMERO = 3;
    static final int LIMITE_GLOBAL_POR_HORA = 300;
    private static final String PREFIJO_DEL_ACCESO = "codigo-whatsapp:";
    private static final String PREFIJO = "whatsapp-cuenta:";
    static final String DEMASIADOS = "Ya pediste varios códigos por WhatsApp. Intenta de nuevo en una hora.";

    private final RequireActiveUserGuard activa;
    private final CodigoPorWhatsApp whatsapp;
    private final CodigoDeWhatsappDeLaCuentaPort codigos;
    private final WhatsappConfirmadoPort confirmados;
    private final LimitarSolicitudesResetPort limites;

    WhatsappConfirmadoService(RequireActiveUserGuard activa, CodigoPorWhatsApp whatsapp, CodigoDeWhatsappDeLaCuentaPort codigos,
                              WhatsappConfirmadoPort confirmados, LimitarSolicitudesResetPort limites) {
        this.activa = activa;
        this.whatsapp = whatsapp;
        this.codigos = codigos;
        this.confirmados = confirmados;
        this.limites = limites;
    }

    @Override
    public Estado estado(UserId cuenta) {
        activa.of(cuenta);
        Optional<String> enmascarado = confirmados.de(cuenta).flatMap(whatsapp::numero).map(NumeroParaCodigo::enmascarado);
        return new Estado(whatsapp.configurado(), enmascarado);
    }

    @Override
    public String pedirCodigo(PedirCodigoCommand command) {
        activa.of(command.cuenta());
        NumeroParaCodigo numero = numeroValido(command.whatsapp());
        if (!whatsapp.configurado()) {
            throw new WhatsappDeLaCuentaException(Motivo.CANAL_NO_DISPONIBLE);
        }
        if (confirmados.de(command.cuenta()).filter(numero.valor()::equals).isPresent()) {
            throw new WhatsappDeLaCuentaException(Motivo.YA_CONFIRMADO);
        }
        exigirCupoDeEnviar(command.cuenta(), numero, command.requestIp());
        String codigo = codigos.generarCodigo(command.cuenta(), numero.valor(), VerificacionEmailService.VIGENCIA_CODIGO);
        if (!whatsapp.enviar(numero, codigo)) {
            throw new WhatsappDeLaCuentaException(Motivo.CODIGO_NO_SALIO);
        }
        return numero.enmascarado();
    }

    @Override
    public String confirmar(ConfirmarCommand command) {
        activa.of(command.cuenta());
        NumeroParaCodigo numero = numeroValido(command.whatsapp());
        exigirCupo(PREFIJO + "intentos:" + command.cuenta(), INTENTOS_DE_CONFIRMAR_POR_HORA);
        String escrito = command.codigo() == null ? "" : command.codigo().trim();
        if (escrito.isEmpty()
                || !codigos.verificarCodigo(command.cuenta(), numero.valor(), escrito, VerificacionEmailService.MAX_INTENTOS)) {
            throw new WhatsappDeLaCuentaException(Motivo.CODIGO_INCORRECTO);
        }
        if (!confirmados.guardar(command.cuenta(), numero.valor())) {
            throw new WhatsappDeLaCuentaException(Motivo.NUMERO_EN_USO);
        }
        return numero.enmascarado();
    }

    private NumeroParaCodigo numeroValido(String texto) {
        return whatsapp.numero(texto).orElseThrow(() -> new WhatsappDeLaCuentaException(Motivo.NUMERO_INVALIDO));
    }

    /** La espera va antes que los limites por hora: un toque impaciente no gasta un envio de la hora (E-153). */
    private void exigirCupoDeEnviar(UserId cuenta, NumeroParaCodigo numero, String requestIp) {
        exigirCupo(PREFIJO_DEL_ACCESO + "ip:" + OrigenDeLaPeticion.de(requestIp), LIMITE_POR_ORIGEN);
        if (!limites.registrarIntento(PREFIJO + "espera:" + cuenta, ESPERA_ENTRE_CODIGOS, 1)) {
            throw new RateLimitExceededException("Espera un minuto antes de pedir otro código por WhatsApp.");
        }
        exigirCupo(PREFIJO + "codigos:" + cuenta, CODIGOS_POR_HORA_Y_CUENTA);
        exigirCupo(PREFIJO_DEL_ACCESO + "numero:" + numero.valor(), LIMITE_POR_NUMERO);
        if (!limites.registrarIntento(PREFIJO_DEL_ACCESO + "global", VENTANA, LIMITE_GLOBAL_POR_HORA)) {
            throw new WhatsappDeLaCuentaException(Motivo.CANAL_NO_DISPONIBLE);
        }
    }

    private void exigirCupo(String clave, int maximo) {
        if (!limites.registrarIntento(clave, VENTANA, maximo)) {
            throw new RateLimitExceededException(DEMASIADOS);
        }
    }
}
