package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.EstadoDeCuentaCambiadoEvent;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.eliminacion.AdministrarEliminacionDeCuentasUseCase;
import com.renaser.os.users.application.ports.out.eliminacion.RegistrarEliminacionPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.domain.model.user.RegistroDeEliminacion;
import com.renaser.os.users.domain.model.user.ReglaDeEliminacionPorAdmin;
import com.renaser.os.users.domain.model.user.User;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/**
 * Administracion → persona → «Eliminar cuenta» y «Recuperar cuenta» (D-243). Orden de las
 * comprobaciones como en el resto del panel (E-42): primero la cuenta objetivo (404), despues el actor
 * (403), y recien despues lo que se escribio (400). Asi un actor sin permiso nunca distingue por la
 * respuesta si la cuenta existe.
 */
@Service
class EliminacionPorAdministracionService implements AdministrarEliminacionDeCuentasUseCase {

    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final RequireAdminGuard requireAdminGuard;
    private final BorradoDefinitivoService borrado;
    private final RegistrarEliminacionPort registrarEliminacion;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    EliminacionPorAdministracionService(LoadUserPort loadUserPort, SaveUserPort saveUserPort,
                                        RequireAdminGuard requireAdminGuard, BorradoDefinitivoService borrado,
                                        RegistrarEliminacionPort registrarEliminacion,
                                        ApplicationEventPublisher events, Clock clock) {
        this.loadUserPort = loadUserPort;
        this.saveUserPort = saveUserPort;
        this.requireAdminGuard = requireAdminGuard;
        this.borrado = borrado;
        this.registrarEliminacion = registrarEliminacion;
        this.events = events;
        this.clock = clock;
    }

    /** Sin {@code @Transactional}: el borrado abre la suya y antes borra archivos del bucket. */
    @Override
    public void eliminar(UserId actorId, UserId cuentaId, String correoEscrito) {
        User cuenta = requireCuenta(cuentaId);
        User actor = loadUserPort.byId(actorId).orElse(null);
        ReglaDeEliminacionPorAdmin.exigirPermitida(actor, cuenta);
        ReglaDeEliminacionPorAdmin.exigirCorreoConfirmado(cuenta, correoEscrito);
        borrado.borrar(cuentaId, RegistroDeEliminacion.Accion.ELIMINADA_POR_ADMIN, actorId);
    }

    /**
     * Devuelve la cuenta como estaba antes de cerrarse, y se publica el cambio de estado: {@code chat}
     * le completa los chats de su grupo al reactivarse (D-224). Lo que se borro de verdad no vuelve:
     * aca no se borro nada, solo se cerro.
     */
    @Override
    @Transactional
    public void recuperar(UserId actorId, UserId cuentaId) {
        User cuenta = requireCuenta(cuentaId);
        requireAdminGuard.requireAdminActivo(actorId);
        UserStatus antes = cuenta.status();
        cuenta.recuperarDeEliminacion();
        saveUserPort.save(cuenta);
        registrarEliminacion.registrar(RegistroDeEliminacion.de(cuenta, RegistroDeEliminacion.Accion.RECUPERADA,
                actorId, clock.now()));
        if (antes != cuenta.status()) {
            events.publishEvent(new EstadoDeCuentaCambiadoEvent(cuenta.id(), antes, cuenta.status(), clock.now()));
        }
    }

    private User requireCuenta(UserId cuentaId) {
        return loadUserPort.byId(cuentaId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + cuentaId));
    }
}
