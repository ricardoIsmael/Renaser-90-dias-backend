package com.renaser.os.users.application.services;

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
 * <p>Las dos consultas son de solo lectura, sin autenticar y sin efectos: no crean nada, no
 * mandan correo, no tocan Supabase.
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
        rejectIfRateLimitExceeded(requestIp);
        // Construir el Email valida el formato ANTES de tocar la base: un correo mal formado
        // termina en 400 sin gastar una consulta, que es media defensa contra el sondeo barato.
        Email normalizado = new Email(email);
        return loadUserPort.byEmail(normalizado).isPresent()
                || loadAccountRequestPort.existePorEmail(normalizado);
    }

    @Override
    public ResultadoVerificacionDominio verificar(String email) {
        String dominio;
        try {
            dominio = new Email(email).dominio();
        } catch (IllegalArgumentException e) {
            // Aca el formato invalido NO es un error de la request: es una de las tres respuestas
            // posibles del contrato que la app ya consume.
            return ResultadoVerificacionDominio.noPuedeRecibir(MotivoNoEntregable.FORMATO);
        }

        return switch (resolverMxPort.consultar(dominio)) {
            case TIENE_MX -> ResultadoVerificacionDominio.puedeRecibir();
            case SIN_MX -> ResultadoVerificacionDominio.noPuedeRecibir(MotivoNoEntregable.SIN_MX);
            case DOMINIO_INEXISTENTE ->
                    ResultadoVerificacionDominio.noPuedeRecibir(MotivoNoEntregable.DOMINIO_INEXISTENTE);
            case INDETERMINADO -> ResultadoVerificacionDominio.noSeSabe();
        };
    }

    private void rejectIfRateLimitExceeded(String requestIp) {
        if (requestIp == null) {
            return;
        }
        if (!limitarSolicitudesPort.registrarIntento("email-check:ip:" + requestIp, VENTANA_RATE_LIMIT,
                LIMITE_CONSULTAS_POR_IP)) {
            throw new RateLimitExceededException("Demasiadas consultas de correo. Intenta mas tarde.");
        }
    }
}
