package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.UnidadDeConteoPorIp;
import com.renaser.os.shared.domain.RateLimitExceededException;
import com.renaser.os.users.application.ports.in.accountrequest.ConsultarEmailRegistradoUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.VerificarDominioEmailUseCase;
import com.renaser.os.users.application.ports.out.accountrequest.LoadAccountRequestPort;
import com.renaser.os.users.application.ports.out.accountrequest.ResolverMxPort;
import com.renaser.os.users.application.ports.out.autenticacion.LimitarSolicitudesResetPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.user.Email;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Consultas publicas sobre un correo, ANTES de que exista cuenta o sesion: si ya esta registrado
 * y si su dominio puede recibir correo. Clase aparte de {@code AccountRequestService} por SRP:
 * aquella gobierna el ciclo de vida de una solicitud (crear, aprobar, rechazar) y esta solo lee
 * para que el formulario avise a tiempo. Mezclarlas habria llevado su constructor a 16
 * dependencias (CLAUDE.MD §5.4.8).
 *
 * <p>Las dos consultas son de solo lectura y sin autenticar: no crean nada, no mandan correo y
 * no tocan Supabase. <b>"Sin efectos" no es lo mismo que "sin coste"</b>, y confundirlos fue el
 * agujero del 2026-09-21: {@link #verificar} no escribe en ningun lado, pero <b>sale del
 * proceso</b> — consulta el DNS de un nombre que elige quien llama. Por eso las dos gastan cupo
 * por IP, con contadores distintos: el coste de cada una no es el mismo.
 */
@Service
class ConsultaEmailService implements ConsultarEmailRegistradoUseCase, VerificarDominioEmailUseCase {

    /**
     * Limite por IP de las consultas de correo. Es la pieza que el repo viejo NO pudo construir:
     * alli se concluyo que tenia que vivir en el borde (WAF) porque en serverless no habia donde
     * contar. Corriendo siempre-arriba y con Redis ya en el stack, vive aca — CLAUDE.MD §5.3.6.
     *
     * <p>El numero es MAS ALTO que el de {@code VerificacionEmailService} a proposito: alli cada
     * intento cuesta un correo real (recurso con cuota); aca cuesta una lectura por indice
     * UNIQUE. Tiene que tolerar varias personas tecleando detras de un mismo NAT sin por eso
     * dejar la enumeracion masiva gratis.
     *
     * <p><b>Asuncion, no confirmada por producto</b> — mismo criterio con el que estan
     * documentados los umbrales de {@code ResetContrasenaService} (A-5).
     */
    static final Duration VENTANA_RATE_LIMIT = Duration.ofHours(1);
    static final int LIMITE_CONSULTAS_POR_IP = 120;

    /**
     * Tope propio de {@link #verificar}, y MAS BAJO que el de arriba: el razonamiento del parrafo
     * anterior aplicado en el sentido correcto. Alli cada intento cuesta una lectura por indice
     * UNIQUE; aca cuesta una consulta DNS <b>saliente</b>, hacia un nombre que elige quien
     * pregunta, que ninguna cache absorbe (el nombre puede ser distinto en cada peticion) y que
     * retiene el hilo hasta el timeout del resolvedor. Un recurso ajeno al proceso no puede tener
     * un tope mas alto que una lectura local.
     *
     * <p>Contador aparte y no compartido con {@code email-check:ip:} a proposito: si compartieran
     * clave, gastar la cuota tecleando en el formulario dejaria sin verificacion de dominio a
     * quien esta por registrarse, que es el uso legitimo.
     *
     * <p><b>Asuncion, no confirmada por producto</b>, igual que el numero de arriba (A-5): el
     * formulario consulta el dominio al terminar de escribir el correo, no en cada tecla, asi que
     * 30 por hora deja lugar de sobra a varias personas detras de un mismo NAT.
     */
    static final int LIMITE_MX_POR_IP = 30;

    private final LoadUserPort loadUserPort;
    private final LoadAccountRequestPort loadAccountRequestPort;
    private final LimitarSolicitudesResetPort limitarSolicitudesPort;
    private final ResolverMxPort resolverMxPort;

    ConsultaEmailService(LoadUserPort loadUserPort, LoadAccountRequestPort loadAccountRequestPort,
                          LimitarSolicitudesResetPort limitarSolicitudesPort, ResolverMxPort resolverMxPort) {
        this.loadUserPort = loadUserPort;
        this.loadAccountRequestPort = loadAccountRequestPort;
        this.limitarSolicitudesPort = limitarSolicitudesPort;
        this.resolverMxPort = resolverMxPort;
    }

    @Override
    public boolean estaRegistrado(String email, String requestIp) {
        rejectIfRateLimitExceeded("email-check:ip:", requestIp, LIMITE_CONSULTAS_POR_IP);
        // Construir el Email valida el formato ANTES de tocar la base: un correo mal formado
        // termina en 400 sin gastar una consulta, que es media defensa contra el sondeo barato.
        Email normalizado = new Email(email);
        return loadUserPort.byEmail(normalizado).isPresent()
                || loadAccountRequestPort.existePorEmail(normalizado);
    }

    @Override
    public ResultadoVerificacionDominio verificar(String email, String requestIp) {
        String dominio;
        try {
            dominio = new Email(email).dominio();
        } catch (IllegalArgumentException e) {
            // Aca el formato invalido NO es un error de la request: es una de las tres respuestas
            // posibles del contrato que la app ya consume. Y no gasta cupo, porque este camino no
            // sale del proceso: se responde con un regex. Quien manda basura paga un regex; lo que
            // hay que racionar es la consulta saliente, que es la linea de abajo.
            return ResultadoVerificacionDominio.noPuedeRecibir(MotivoNoEntregable.FORMATO);
        }
        // El cupo se gasta ANTES de salir a la red y no despues: pasado el tope, el DNS no se
        // consulta ni una vez. Es el mismo orden que en estaRegistrado, donde el limite corre
        // antes de la lectura que protege.
        rejectIfRateLimitExceeded("email-mx:ip:", requestIp, LIMITE_MX_POR_IP);

        return switch (resolverMxPort.consultar(dominio)) {
            case TIENE_MX -> ResultadoVerificacionDominio.puedeRecibir();
            case SIN_MX -> ResultadoVerificacionDominio.noPuedeRecibir(MotivoNoEntregable.SIN_MX);
            case DOMINIO_INEXISTENTE ->
                    ResultadoVerificacionDominio.noPuedeRecibir(MotivoNoEntregable.DOMINIO_INEXISTENTE);
            case INDETERMINADO -> ResultadoVerificacionDominio.noSeSabe();
        };
    }

    /**
     * @param prefijoClave que contador se gasta. Son dos y separados ({@code email-check:ip:} y
     *                     {@code email-mx:ip:}) porque las dos consultas no cuestan lo mismo; ver
     *                     {@link #LIMITE_MX_POR_IP}.
     */
    private void rejectIfRateLimitExceeded(String prefijoClave, String requestIp, int maximo) {
        if (requestIp == null) {
            return;
        }
        if (!limitarSolicitudesPort.registrarIntento(prefijoClave + UnidadDeConteoPorIp.de(requestIp), VENTANA_RATE_LIMIT, maximo)) {
            throw new RateLimitExceededException("Demasiadas consultas de correo. Intenta mas tarde.");
        }
    }
}
