package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ConflictoDeRolPersonalizadoException;
import com.renaser.os.users.api.GestionarRolesPersonalizadosUseCase;
import com.renaser.os.users.api.RolPersonalizadoCambiadoEvent;
import com.renaser.os.users.application.ports.out.rolpersonalizado.CargarRolesPersonalizadosPort;
import com.renaser.os.users.application.ports.out.rolpersonalizado.GuardarRolesPersonalizadosPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.rolpersonalizado.ReglasDeRolesPersonalizados;
import com.renaser.os.users.domain.model.rolpersonalizado.RolPersonalizado;
import com.renaser.os.users.domain.model.rolpersonalizado.RolPersonalizado.Datos;
import com.renaser.os.users.domain.model.user.User;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * La gestión de roles personalizados (ver {@link GestionarRolesPersonalizadosUseCase}; D-269). Cada escritura, en este
 * orden: Soporte TI activo (403), candado de escrituras, el rol existe (404), versión (409), las reglas del agregado y
 * las que miran otras filas (400/409), guardar y publicar {@link RolPersonalizadoCambiadoEvent} en la misma
 * transacción.
 *
 * <p>Que cada clave exista en el catálogo y que el rol base la use lo valida quien llama ({@code plataforma}): acá
 * solo se exige la forma de una clave.
 */
@Service
class RolesPersonalizadosService implements GestionarRolesPersonalizadosUseCase {

    private final CargarRolesPersonalizadosPort cargar;
    private final GuardarRolesPersonalizadosPort guardar;
    private final LoadUserPort loadUserPort;
    private final ApplicationEventPublisher events;
    private final IdGenerator idGenerator;
    private final Clock clock;

    RolesPersonalizadosService(CargarRolesPersonalizadosPort cargar, GuardarRolesPersonalizadosPort guardar,
                               LoadUserPort loadUserPort, ApplicationEventPublisher events, IdGenerator idGenerator,
                               Clock clock) {
        this.cargar = cargar;
        this.guardar = guardar;
        this.loadUserPort = loadUserPort;
        this.events = events;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CambioDeRolPersonalizado crear(CrearRolPersonalizadoCommand command) {
        exigirSoporteTi(command.actor());
        guardar.bloquearCambios();
        List<RolPersonalizado> existentes = cargar.todos();
        RolPersonalizado nuevo = RolPersonalizado.crear(idGenerator.newId(), new Datos(command.nombre(),
                command.descripcion(), command.rolBase(), command.funcionalidades()), command.actor(), clock.now());
        ReglasDeRolesPersonalizados.exigirNombreLibre(nuevo, existentes, null);
        ReglasDeRolesPersonalizados.exigirCupo(existentes, null);
        guardar.insertar(nuevo);
        publicar(nuevo.id());
        return new CambioDeRolPersonalizado(null, ResumenDeRolPersonalizado.de(nuevo, 0));
    }

    @Override
    @Transactional
    public CambioDeRolPersonalizado editar(EditarRolPersonalizadoCommand command) {
        exigirSoporteTi(command.actor());
        guardar.bloquearCambios();
        RolPersonalizado actual = requerir(command.id());
        if (actual.version() != command.version()) {
            throw ConflictoDeRolPersonalizadoException.versionCambiada();
        }
        long personas = cargar.personasCon(actual.id());
        RolPersonalizado editado = actual.editar(new Datos(command.nombre(), command.descripcion(), command.rolBase(),
                command.funcionalidades()), command.activo(), personas, command.actor(), clock.now());
        List<RolPersonalizado> existentes = cargar.todos();
        ReglasDeRolesPersonalizados.exigirNombreLibre(editado, existentes, actual.id());
        if (editado.activo() && !actual.activo()) {
            ReglasDeRolesPersonalizados.exigirCupo(existentes, actual.id());
        }
        if (!guardar.actualizar(editado, actual.version())) {
            throw ConflictoDeRolPersonalizadoException.versionCambiada();
        }
        publicar(editado.id());
        return new CambioDeRolPersonalizado(ResumenDeRolPersonalizado.de(actual, personas),
                ResumenDeRolPersonalizado.de(editado, personas));
    }

    @Override
    @Transactional
    public CambioDeRolPersonalizado borrar(BorrarRolPersonalizadoCommand command) {
        exigirSoporteTi(command.actor());
        guardar.bloquearCambios();
        RolPersonalizado actual = requerir(command.id());
        actual.exigirQueSePuedaBorrar(cargar.personasCon(actual.id()));
        guardar.borrar(actual.id());
        publicar(actual.id());
        return new CambioDeRolPersonalizado(ResumenDeRolPersonalizado.de(actual, 0), null);
    }

    @Override
    @Transactional
    public ReemplazoEnBloque reemplazarEnBloque(ReemplazarRolPersonalizadoCommand command) {
        exigirSoporteTi(command.actor());
        guardar.bloquearCambios();
        RolPersonalizado origen = requerir(command.id());
        RolPersonalizado destino = command.destinoId() == null ? null : cargar.porId(command.destinoId())
                .orElseThrow(() -> new IllegalArgumentException("El rol de destino no existe"));
        if (destino != null) {
            ReglasDeRolesPersonalizados.exigirDestinoValido(origen, destino);
        }
        long antes = cargar.personasCon(origen.id());
        int movidas = guardar.reemplazarAsignaciones(origen.id(), destino == null ? null : destino.id());
        publicar(origen.id());
        return new ReemplazoEnBloque(ResumenDeRolPersonalizado.de(origen, antes),
                destino == null ? null : ResumenDeRolPersonalizado.de(destino, cargar.personasCon(destino.id())),
                movidas);
    }

    private RolPersonalizado requerir(UUID id) {
        return cargar.porId(id).orElseThrow(() -> new NoSuchElementException("Rol personalizado no encontrado: " + id));
    }

    /**
     * La consola ya lo verificó; se repite acá porque este caso de uso es público ({@code users.api}) y la matriz de
     * permisos falla abierta para ADMIN y ALQUIMISTA (A-1). Mismo criterio que {@code GuardiaDeSoporteTi}.
     */
    private void exigirSoporteTi(UserId actor) {
        boolean esSoporteTiActivo = loadUserPort.byId(actor).filter(User::esSoporteTi).filter(User::hasAccess)
                .isPresent();
        if (!esSoporteTiActivo) {
            throw new NotAuthorizedException("Solo Soporte TI gestiona los roles personalizados");
        }
    }

    private void publicar(UUID rolId) {
        events.publishEvent(new RolPersonalizadoCambiadoEvent(rolId, clock.now()));
    }
}
