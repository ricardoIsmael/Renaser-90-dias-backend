package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.application.ports.in.VerComoUseCase;
import com.renaser.os.plataforma.application.ports.out.RegistrarCambioAuditadoPort;
import com.renaser.os.plataforma.application.ports.out.SesionesDeVerComoPort;
import com.renaser.os.plataforma.domain.model.auditoria.CambioAuditado;
import com.renaser.os.plataforma.domain.model.vercomo.PedidoDeVerComo;
import com.renaser.os.plataforma.domain.model.vercomo.RolElegido;
import com.renaser.os.plataforma.domain.model.vercomo.SesionDeVerComo;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.CuentaDePrueba;
import com.renaser.os.users.api.CuentasDePruebaUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * «Ver como rol» (ver {@link VerComoUseCase}; D-270). Al abrir: los cerrojos de la consola (403 / 423), el pedido
 * (400), la cuenta de prueba y su entorno (grupo, programa, onboarding), la auditoría y, al final, la sesión nueva en
 * Redis — lo último porque no es transaccional: si algo anterior falla, no queda una sesión suelta.
 */
@Service
class VerComoService implements VerComoUseCase {

    private final GuardiaDeSoporteTi guardia;
    private final CuentasDePruebaUseCase cuentas;
    private final EntornoDeLaCuentaDePrueba entorno;
    private final SesionesDeVerComoPort sesiones;
    private final RegistrarCambioAuditadoPort auditoria;
    private final IdGenerator idGenerator;
    private final Clock clock;

    @SuppressWarnings("java:S107") // colaboradores, no parámetros de negocio
    VerComoService(GuardiaDeSoporteTi guardia, CuentasDePruebaUseCase cuentas, EntornoDeLaCuentaDePrueba entorno,
                   SesionesDeVerComoPort sesiones, RegistrarCambioAuditadoPort auditoria, IdGenerator idGenerator,
                   Clock clock) {
        this.guardia = guardia;
        this.cuentas = cuentas;
        this.entorno = entorno;
        this.sesiones = sesiones;
        this.auditoria = auditoria;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public VerComoAbierto abrir(AbrirVerComoCommand command) {
        guardia.exigirConsolaAbierta(command.acceso());
        UserId soporteTi = command.acceso().actor();
        PedidoDeVerComo pedido = PedidoDeVerComo.de(command.rol(), command.rolPersonalizadoId());
        CuentaDePrueba cuenta = pedido.esDeUnRolBase() ? cuentas.deRol(soporteTi, pedido.rol())
                : cuentas.deRolPersonalizado(soporteTi, pedido.rolPersonalizadoId());
        RolElegido elegido = pedido.esDeUnRolBase() ? new RolElegido.Base(pedido.rol())
                : new RolElegido.Personalizado(cuenta.rolPersonalizado().id(), cuenta.rolPersonalizado().nombre());
        entorno.preparar(soporteTi, cuenta);
        Instant ahora = clock.now();
        auditoria.registrar(CambioAuditado.entradaAVerComo(idGenerator.newId(), soporteTi, elegido.foto(),
                command.dispositivo(), ahora));
        SesionDeVerComo sesion = SesionDeVerComo.abrir(cuenta.id(), soporteTi, elegido, ahora);
        return new VerComoAbierto(sesiones.abrir(sesion), cuenta, sesion.venceEn());
    }

    @Override
    @Transactional
    public void cerrar(CerrarVerComoCommand command) {
        SesionDeVerComo sesion = Optional.ofNullable(command.sesion()).flatMap(sesiones::leer)
                .orElseThrow(() -> new NoSuchElementException("No hay una vista «ver como» abierta con esta sesión"));
        auditoria.registrar(CambioAuditado.salidaDeVerComo(idGenerator.newId(), sesion.abiertaPor(),
                sesion.rol().foto(), command.dispositivo(), clock.now()));
        sesiones.cerrar(command.sesion());
    }
}
