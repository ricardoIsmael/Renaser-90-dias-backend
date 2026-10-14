package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.api.RolConPersonasCambiadoEvent;
import com.renaser.os.plataforma.application.ports.in.AccesoDeConsola;
import com.renaser.os.plataforma.application.ports.in.FuncionalidadEnConsola.Actor;
import com.renaser.os.plataforma.application.ports.in.GestionarRolesEnConsolaUseCase;
import com.renaser.os.plataforma.application.ports.in.RolPersonalizadoEnConsola;
import com.renaser.os.plataforma.application.ports.out.RegistrarCambioAuditadoPort;
import com.renaser.os.plataforma.application.ports.out.TokensDeConsolaPort;
import com.renaser.os.plataforma.domain.model.auditoria.CambioAuditado;
import com.renaser.os.plataforma.domain.model.catalogo.CatalogoDeFuncionalidades;
import com.renaser.os.plataforma.domain.model.roles.PedidoDeRolPersonalizado;
import com.renaser.os.plataforma.domain.model.roles.ResumenDelCambioDeRol;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.ReautenticacionRequeridaException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.GestionarRolesPersonalizadosUseCase;
import com.renaser.os.users.api.GestionarRolesPersonalizadosUseCase.BorrarRolPersonalizadoCommand;
import com.renaser.os.users.api.GestionarRolesPersonalizadosUseCase.CambioDeRolPersonalizado;
import com.renaser.os.users.api.GestionarRolesPersonalizadosUseCase.CrearRolPersonalizadoCommand;
import com.renaser.os.users.api.GestionarRolesPersonalizadosUseCase.EditarRolPersonalizadoCommand;
import com.renaser.os.users.api.GestionarRolesPersonalizadosUseCase.ReemplazarRolPersonalizadoCommand;
import com.renaser.os.users.api.GestionarRolesPersonalizadosUseCase.ReemplazoEnBloque;
import com.renaser.os.users.api.RolPersonalizadoResumen;
import com.renaser.os.users.api.RolesPersonalizadosFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * La consola de roles personalizados (ver {@link GestionarRolesEnConsolaUseCase}; D-269). Cada escritura, en este
 * orden: Soporte TI con la consola abierta (403 / 423), el pedido válido contra el catálogo (400), el rol existe (404)
 * y, si es crítico, lo que se pueda saber sin escribir (409 por personas, destino inválido) <b>antes</b> de gastar el
 * token de reautenticación (428): así una contraseña no se pide para un cambio que de todos modos se rechazaría. Después,
 * {@code users} (que vuelve a validar todo bajo su candado), la auditoría en la misma transacción, y el aviso
 * {@code RolPersonalizadoCambiadoEvent} que recarga las copias en memoria al confirmar.
 */
@Service
class ConsolaDeRolesPersonalizadosService implements GestionarRolesEnConsolaUseCase {

    private final GuardiaDeSoporteTi guardia;
    private final GestionarRolesPersonalizadosUseCase gestion;
    private final RolesPersonalizadosFinder finder;
    private final UserSummaryFinder usuarios;
    private final RegistrarCambioAuditadoPort auditoria;
    private final TokensDeConsolaPort tokens;
    private final ApplicationEventPublisher events;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final CatalogoDeFuncionalidades catalogo = CatalogoDeFuncionalidades.inicial();

    @SuppressWarnings("java:S107") // colaboradores, no parámetros de negocio
    ConsolaDeRolesPersonalizadosService(GuardiaDeSoporteTi guardia, GestionarRolesPersonalizadosUseCase gestion,
                                        RolesPersonalizadosFinder finder, UserSummaryFinder usuarios,
                                        RegistrarCambioAuditadoPort auditoria, TokensDeConsolaPort tokens,
                                        ApplicationEventPublisher events, IdGenerator idGenerator, Clock clock) {
        this.guardia = guardia;
        this.gestion = gestion;
        this.finder = finder;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.tokens = tokens;
        this.events = events;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RolPersonalizadoEnConsola> listar(AccesoDeConsola acceso) {
        guardia.exigirConsolaAbierta(acceso);
        List<RolPersonalizadoResumen> todos = finder.todos();
        Map<UserId, UserSummary> nombres = usuarios.findByIds(todos.stream()
                .map(RolPersonalizadoResumen::actualizadoPor).filter(Objects::nonNull).distinct().toList());
        return todos.stream().map(rol -> vista(rol, nombres)).toList();
    }

    @Override
    @Transactional
    public RolPersonalizadoEnConsola crear(CrearRolEnConsolaCommand command) {
        guardia.exigirConsolaAbierta(command.acceso());
        String motivo = PedidoDeRolPersonalizado.motivoValido(command.motivo());
        UserRole base = PedidoDeRolPersonalizado.rolBaseValido(command.rolBase());
        CambioDeRolPersonalizado cambio = gestion.crear(new CrearRolPersonalizadoCommand(command.acceso().actor(),
                command.nombre(), command.descripcion(), base,
                PedidoDeRolPersonalizado.clavesValidas(command.funcionalidades(), base, catalogo)));
        auditar(command.acceso(), cambio.despues().clave(), null, foto(cambio.despues()), motivo,
                command.dispositivo());
        return vista(cambio.despues());
    }

    @Override
    @Transactional
    public RolPersonalizadoEnConsola editar(EditarRolEnConsolaCommand command) {
        guardia.exigirConsolaAbierta(command.acceso());
        String motivo = PedidoDeRolPersonalizado.motivoValido(command.motivo());
        if (command.version() == null || command.activo() == null) {
            throw new IllegalArgumentException("Faltan la versión que estabas mirando o si el rol queda activo");
        }
        RolPersonalizadoResumen actual = requerir(command.id());
        UserRole base = command.rolBase() == null ? actual.rolBase()
                : PedidoDeRolPersonalizado.rolBaseValido(command.rolBase());
        CambioDeRolPersonalizado cambio = gestion.editar(new EditarRolPersonalizadoCommand(command.acceso().actor(),
                actual.id(), command.nombre(), command.descripcion(), command.rolBase() == null ? null : base,
                PedidoDeRolPersonalizado.clavesValidas(command.funcionalidades(), base, catalogo), command.activo(),
                command.version()));
        auditar(command.acceso(), actual.clave(), foto(cambio.antes()), foto(cambio.despues()), motivo,
                command.dispositivo());
        ResumenDelCambioDeRol.deUnaEdicion(paraElAviso(cambio.antes()), paraElAviso(cambio.despues()),
                        cambio.antes().personas())
                .ifPresent(resumen -> avisar(cambio.despues(), cambio.antes().personas(), resumen, command.acceso(),
                        motivo));
        return vista(cambio.despues());
    }

    @Override
    @Transactional
    public void borrar(BorrarRolEnConsolaCommand command) {
        guardia.exigirConsolaAbierta(command.acceso());
        String motivo = PedidoDeRolPersonalizado.motivoValido(command.motivo());
        RolPersonalizadoResumen actual = requerir(command.id());
        if (actual.personas() > 0) {
            throw new IllegalStateException("«" + actual.nombre() + "» tiene " + actual.personas() + " persona(s): "
                    + "no se borra. Desactívalo o reemplázalo en bloque.");
        }
        exigirReautenticacion(command.acceso(), command.reautenticacion());
        CambioDeRolPersonalizado cambio = gestion.borrar(new BorrarRolPersonalizadoCommand(command.acceso().actor(),
                actual.id()));
        auditar(command.acceso(), actual.clave(), foto(cambio.antes()), null, motivo, command.dispositivo());
    }

    @Override
    @Transactional
    public int reemplazar(ReemplazarRolEnConsolaCommand command) {
        guardia.exigirConsolaAbierta(command.acceso());
        String motivo = PedidoDeRolPersonalizado.motivoValido(command.motivo());
        RolPersonalizadoResumen origen = requerir(command.id());
        if (command.destinoId() != null) {
            exigirDestinoValido(origen, finder.porId(command.destinoId())
                    .orElseThrow(() -> new IllegalArgumentException("El rol de destino no existe")));
        }
        exigirReautenticacion(command.acceso(), command.reautenticacion());
        ReemplazoEnBloque reemplazo = gestion.reemplazarEnBloque(new ReemplazarRolPersonalizadoCommand(
                command.acceso().actor(), origen.id(), command.destinoId()));
        auditar(command.acceso(), origen.clave(), fotoConPersonas(reemplazo.origen(), reemplazo.origen().personas()),
                fotoDelReemplazo(reemplazo), motivo, command.dispositivo());
        ResumenDelCambioDeRol.deUnReemplazo(reemplazo.destino() == null ? null : reemplazo.destino().nombre(),
                        origen.rolBase(), reemplazo.personasMovidas())
                .ifPresent(resumen -> avisar(origen, reemplazo.personasMovidas(), resumen, command.acceso(), motivo));
        return reemplazo.personasMovidas();
    }

    private RolPersonalizadoResumen requerir(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("Falta el rol");
        }
        return finder.porId(id).orElseThrow(() -> new NoSuchElementException("Rol personalizado no encontrado: "
                + id));
    }

    /** Lo mismo que vuelve a exigir {@code users}, adelantado para que un destino inválido sea 400 y no pida contraseña. */
    private static void exigirDestinoValido(RolPersonalizadoResumen origen, RolPersonalizadoResumen destino) {
        if (destino.id().equals(origen.id()) || !destino.activo() || destino.rolBase() != origen.rolBase()) {
            throw new IllegalArgumentException("El destino del reemplazo tiene que ser otro rol activo que herede de "
                    + origen.rolBase().nombreVisible());
        }
    }

    /** El token de un solo uso de {@code X-Reautenticacion}, de esta persona y esta sesión (428 sin él). */
    private void exigirReautenticacion(AccesoDeConsola acceso, String token) {
        if (token == null || token.isBlank() || !tokens.gastarReautenticacion(token, acceso.actor(), acceso.sesion())) {
            throw new ReautenticacionRequeridaException();
        }
    }

    /**
     * El aviso a Administración de un cambio que toca a personas (D-269): sale por el outbox en esta misma transacción,
     * y {@code notifications} lo manda por push a los ADMIN y ALQUIMISTA activos después del commit.
     */
    private void avisar(RolPersonalizadoResumen rol, long personas, String resumen, AccesoDeConsola acceso,
                        String motivo) {
        events.publishEvent(new RolConPersonasCambiadoEvent(rol.id(), rol.nombre(), personas, resumen, acceso.actor(),
                motivo, clock.now()));
    }

    private static ResumenDelCambioDeRol.Foto paraElAviso(RolPersonalizadoResumen rol) {
        return new ResumenDelCambioDeRol.Foto(rol.rolBase(), rol.activo(), rol.funcionalidades());
    }

    private void auditar(AccesoDeConsola acceso, String clave, Map<String, Object> antes, Map<String, Object> despues,
                         String motivo, String dispositivo) {
        auditoria.registrar(CambioAuditado.deRolPersonalizado(idGenerator.newId(), acceso.actor(), clave, antes,
                despues, motivo, dispositivo, clock.now()));
    }

    /** La foto del contrato (§4.5): {@code { nombre, rolBase, activo, funcionalidades }}. */
    private static Map<String, Object> foto(RolPersonalizadoResumen rol) {
        if (rol == null) {
            return null;
        }
        Map<String, Object> foto = new LinkedHashMap<>();
        foto.put("nombre", rol.nombre());
        foto.put("rolBase", rol.rolBase().name());
        foto.put("activo", rol.activo());
        foto.put("funcionalidades", rol.funcionalidades().stream().sorted().toList());
        return foto;
    }

    private static Map<String, Object> fotoConPersonas(RolPersonalizadoResumen rol, long personas) {
        Map<String, Object> foto = foto(rol);
        foto.put("personas", personas);
        return foto;
    }

    /** Después de un reemplazo: el origen sin personas, a qué rol pasaron ({@code null}: solo la base) y cuántas. */
    private static Map<String, Object> fotoDelReemplazo(ReemplazoEnBloque reemplazo) {
        Map<String, Object> foto = fotoConPersonas(reemplazo.origen(), 0);
        Map<String, Object> destino = null;
        if (reemplazo.destino() != null) {
            destino = new LinkedHashMap<>();
            destino.put("id", reemplazo.destino().id().toString());
            destino.put("nombre", reemplazo.destino().nombre());
        }
        foto.put("reemplazadoPor", destino);
        foto.put("personasMovidas", reemplazo.personasMovidas());
        return foto;
    }

    private RolPersonalizadoEnConsola vista(RolPersonalizadoResumen rol) {
        return vista(rol, rol.actualizadoPor() == null ? Map.of() : usuarios.findByIds(List.of(rol.actualizadoPor())));
    }

    private RolPersonalizadoEnConsola vista(RolPersonalizadoResumen rol, Map<UserId, UserSummary> nombres) {
        Actor actor = rol.actualizadoPor() == null ? null : new Actor(rol.actualizadoPor(),
                nombres.containsKey(rol.actualizadoPor()) ? nombres.get(rol.actualizadoPor()).fullName() : null);
        List<String> claves = rol.funcionalidades().stream().filter(catalogo::existe).sorted().toList();
        return new RolPersonalizadoEnConsola(rol.id(), rol.clave(), rol.nombre(), rol.descripcion(), rol.rolBase(),
                rol.activo(), claves, rol.personas(), rol.version(), actor, rol.actualizadoEn());
    }
}
