package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.users.api.EstadoDeCuentaCambiadoEvent;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.autenticacion.CerrarTodasLasSesionesUseCase;
import com.renaser.os.users.application.ports.out.eliminacion.RegistrarEliminacionPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.domain.model.user.EstadoBajaCuenta;
import com.renaser.os.users.domain.model.user.PlazoDeGracia;
import com.renaser.os.users.domain.model.user.RegistroDeEliminacion;
import com.renaser.os.users.domain.model.user.User;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * El cierre de una cuenta que la persona elimina, venga de la app o de la pagina web (D-243). Una vez
 * confirmado que es ella, todo pasa en el acto y en una transaccion:
 * <ul>
 *   <li>la cuenta queda cerrada ({@link User#cerrarParaEliminar}): no entra mas;</li>
 *   <li>se cierran TODAS sus sesiones, como al suspender;</li>
 *   <li>se publica {@link EstadoDeCuentaCambiadoEvent}, el mismo de una suspension: {@code notifications}
 *       borra sus tokens push y {@code points} pausa su semaforo;</li>
 *   <li>queda en la auditoria.</li>
 * </ul>
 * Los demas dejan de verla porque sus listados filtran con {@code users.api.CuentasCerradasFinder}.
 */
@Service
class CierreDeCuentaService {

    private final SaveUserPort saveUserPort;
    private final LoadUserPort loadUserPort;
    private final CerrarTodasLasSesionesUseCase cerrarSesiones;
    private final RegistrarEliminacionPort registrarEliminacion;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final PlazoDeGracia plazo;

    CierreDeCuentaService(SaveUserPort saveUserPort, LoadUserPort loadUserPort,
                          CerrarTodasLasSesionesUseCase cerrarSesiones, RegistrarEliminacionPort registrarEliminacion,
                          ApplicationEventPublisher events, Clock clock, PlazoDeGracia plazo) {
        this.saveUserPort = saveUserPort;
        this.loadUserPort = loadUserPort;
        this.cerrarSesiones = cerrarSesiones;
        this.registrarEliminacion = registrarEliminacion;
        this.events = events;
        this.clock = clock;
        this.plazo = plazo;
    }

    /** Idempotente: cerrar dos veces no reinicia la gracia ni duplica la auditoria. */
    @Transactional
    EstadoBajaCuenta cerrar(User cuenta, RegistroDeEliminacion.Via via) {
        if (!cuenta.bajaPendiente()) {
            exigirQueNoSeaElUltimoAdmin(cuenta);
            UserStatus antes = cuenta.status();
            cuenta.cerrarParaEliminar(clock);
            saveUserPort.save(cuenta);
            registrarEliminacion.registrar(RegistroDeEliminacion.cerrada(cuenta, via, clock.now()));
            if (antes != cuenta.status()) {
                events.publishEvent(new EstadoDeCuentaCambiadoEvent(cuenta.id(), antes, cuenta.status(), clock.now()));
            }
        }
        cerrarSesiones.cerrarTodas(cuenta.id());
        return plazo.estadoDe(cuenta.bajaSolicitadaEn(), clock.now());
    }

    int diasDeGracia() {
        return plazo.dias();
    }

    /**
     * Si la ultima cuenta ADMIN activa se cerrara, nadie podria recuperarla ni administrar nada desde la
     * app. Supuesto tecnico de D-243, a confirmar con el dueño.
     */
    private void exigirQueNoSeaElUltimoAdmin(User cuenta) {
        if (cuenta.role() == UserRole.ADMIN && cuenta.hasAccess()
                && loadUserPort.countByRoles(Set.of(UserRole.ADMIN), UserStatus.ACTIVE) <= 1) {
            throw new IllegalStateException("Eres la única cuenta Admin activa: no se puede eliminar");
        }
    }
}
