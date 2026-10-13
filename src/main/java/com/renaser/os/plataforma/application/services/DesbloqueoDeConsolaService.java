package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.application.ports.in.DesbloquearConsolaUseCase;
import com.renaser.os.plataforma.application.ports.out.LimitarDesbloqueosPort;
import com.renaser.os.plataforma.application.ports.out.RegistrarCambioAuditadoPort;
import com.renaser.os.plataforma.application.ports.out.TokensDeConsolaPort;
import com.renaser.os.plataforma.domain.model.auditoria.CambioAuditado;
import com.renaser.os.plataforma.domain.model.auditoria.TipoDeCambio;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.ContrasenaIncorrectaException;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.RateLimitExceededException;
import com.renaser.os.users.api.ConfirmacionDeContrasena;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Abre la consola con la contraseña, o la confirma para un cambio crítico (plan.md D6).
 *
 * <ul>
 *   <li>Cinco contraseñas mal en 15 minutos bloquean el desbloqueo 15 minutos (429), como el reseteo de contraseña.</li>
 *   <li>Cada intento, bien o mal, queda en la auditoría (DESBLOQUEO o REAUTENTICACION): una cuenta comprometida deja
 *       rastro (spec, «Cuenta Soporte TI comprometida»).</li>
 *   <li>El token de un cambio crítico se pide con la consola ya abierta.</li>
 * </ul>
 */
@Service
class DesbloqueoDeConsolaService implements DesbloquearConsolaUseCase {

    static final int FALLOS_MAXIMOS = 5;
    static final Duration VENTANA_DE_FALLOS = Duration.ofMinutes(15);

    private final GuardiaDeSoporteTi guardia;
    private final ConfirmacionDeContrasena confirmacion;
    private final TokensDeConsolaPort tokens;
    private final LimitarDesbloqueosPort limitador;
    private final RegistrarCambioAuditadoPort auditoria;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final Duration vigenciaDeReautenticacion;

    DesbloqueoDeConsolaService(GuardiaDeSoporteTi guardia, ConfirmacionDeContrasena confirmacion,
                               TokensDeConsolaPort tokens, LimitarDesbloqueosPort limitador,
                               RegistrarCambioAuditadoPort auditoria, IdGenerator idGenerator, Clock clock,
                               @Value("${renaser.plataforma.consola.reautenticacion:PT5M}")
                               Duration vigenciaDeReautenticacion) {
        this.guardia = guardia;
        this.confirmacion = confirmacion;
        this.tokens = tokens;
        this.limitador = limitador;
        this.auditoria = auditoria;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.vigenciaDeReautenticacion = vigenciaDeReautenticacion;
    }

    @Override
    public Desbloqueo desbloquear(DesbloquearConsolaCommand command) {
        guardia.exigirSoporteTi(command.actor());
        if (command.para() == Para.CAMBIO_CRITICO) {
            guardia.exigirConsolaDesbloqueada(command.acceso());
        }
        if (limitador.bloqueado(command.actor(), FALLOS_MAXIMOS)) {
            throw new RateLimitExceededException("Demasiados intentos. Espera 15 minutos y vuelve a probar.");
        }
        TipoDeCambio tipo = command.para() == Para.CONSOLA ? TipoDeCambio.DESBLOQUEO : TipoDeCambio.REAUTENTICACION;
        if (!confirmacion.esSuContrasena(command.actor(), command.contrasena())) {
            limitador.registrarFallo(command.actor(), VENTANA_DE_FALLOS, FALLOS_MAXIMOS);
            auditar(command, tipo, "CONTRASENA_INCORRECTA");
            throw new ContrasenaIncorrectaException();
        }
        auditar(command, tipo, "OK");
        return emitir(command);
    }

    private Desbloqueo emitir(DesbloquearConsolaCommand command) {
        if (command.para() == Para.CONSOLA) {
            String token = tokens.emitirDeConsola(command.actor(), command.acceso().sesion(), guardia.inactividad());
            return new Desbloqueo(token, clock.now().plus(guardia.inactividad()));
        }
        String token = tokens.emitirDeReautenticacion(command.actor(), command.acceso().sesion(),
                vigenciaDeReautenticacion);
        return new Desbloqueo(token, clock.now().plus(vigenciaDeReautenticacion));
    }

    private void auditar(DesbloquearConsolaCommand command, TipoDeCambio tipo, String resultado) {
        auditoria.registrar(CambioAuditado.acceso(idGenerator.newId(), tipo, command.actor(), resultado,
                command.dispositivo(), clock.now()));
    }
}
