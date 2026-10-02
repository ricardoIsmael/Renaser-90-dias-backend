package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.conversacion.CrearConversacionCelulaUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.CrearConversacionDirectaUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.ListarConversacionesUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.MarcarLeidoUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.RenombrarConversacionGlobalUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.UnirseAConversacionGlobalUseCase;
import com.renaser.os.chat.application.ports.in.lectura.AnunciarLecturaUseCase;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.conversacion.SaveConversacionPort;
import com.renaser.os.chat.application.ports.out.mensaje.LoadMensajePort;
import com.renaser.os.chat.application.ports.out.participante.AgregarParticipantePort;
import com.renaser.os.chat.application.ports.out.participante.ContarNoLeidosPort;
import com.renaser.os.chat.application.ports.out.participante.EsParticipantePort;
import com.renaser.os.chat.application.ports.out.participante.ListarUsuariosDeConversacionPort;
import com.renaser.os.chat.application.ports.out.participante.MarcarLeidoPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.TipoConversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.Participante;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.community.api.FotoPropiaDelGrupoFinder;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.CuentasCerradasFinder;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
public class ConversacionService implements CrearConversacionDirectaUseCase, ListarConversacionesUseCase,
        MarcarLeidoUseCase, UnirseAConversacionGlobalUseCase, CrearConversacionCelulaUseCase,
        RenombrarConversacionGlobalUseCase {

    private final LoadConversacionPort loadConversacionPort;
    private final SaveConversacionPort saveConversacionPort;
    private final AgregarParticipantePort agregarParticipantePort;
    private final EsParticipantePort esParticipantePort;
    private final AccesoAChatsDeGrupo accesoAChatsDeGrupo;
    private final MarcarLeidoPort marcarLeidoPort;
    private final AnunciarLecturaUseCase anunciarLectura;
    private final ContarNoLeidosPort contarNoLeidosPort;
    private final LoadMensajePort loadMensajePort;
    private final ListarUsuariosDeConversacionPort listarUsuariosPort;
    private final UserSummaryFinder userSummaryFinder;
    /** D-212: cuándo cambió la foto propia de cada grupo, para la ruta de su foto en la lista. */
    private final FotoPropiaDelGrupoFinder fotosDeGrupos;
    /** D-221: el nombre que se muestra de cada chat, derivado al leer. */
    private final NombresDeLosChatsService nombresDeLosChats;
    private final Clock clock;
    private final IdGenerator idGenerator;
    /**
     * Transaccion PROPIA (REQUIRES_NEW) para {@link #crearDirectaConAmbosParticipantes} — C-10
     * (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html). Mismo criterio que
     * {@code EspirituService}/{@code RegistroService}: crea una fila nueva (nunca toca una ya
     * bloqueada por la transaccion en curso), asi que aislarla no arriesga un auto-interbloqueo.
     */
    private final TransactionTemplate transaccionPropia;
    /** D-243: un chat directo con una cuenta cerrada esperando su borrado no se lista. */
    private final CuentasCerradasFinder cuentasCerradas;

    public ConversacionService(LoadConversacionPort loadConversacionPort, SaveConversacionPort saveConversacionPort,
                                AgregarParticipantePort agregarParticipantePort,
                                EsParticipantePort esParticipantePort,
                                AccesoAChatsDeGrupo accesoAChatsDeGrupo, MarcarLeidoPort marcarLeidoPort,
                                AnunciarLecturaUseCase anunciarLectura,
                                ContarNoLeidosPort contarNoLeidosPort, LoadMensajePort loadMensajePort,
                                ListarUsuariosDeConversacionPort listarUsuariosPort,
                                UserSummaryFinder userSummaryFinder, FotoPropiaDelGrupoFinder fotosDeGrupos,
                                NombresDeLosChatsService nombresDeLosChats, Clock clock, IdGenerator idGenerator, PlatformTransactionManager transactionManager,
                                CuentasCerradasFinder cuentasCerradas) {
        this.cuentasCerradas = cuentasCerradas;
        this.loadConversacionPort = loadConversacionPort;
        this.saveConversacionPort = saveConversacionPort;
        this.agregarParticipantePort = agregarParticipantePort;
        this.esParticipantePort = esParticipantePort;
        this.accesoAChatsDeGrupo = accesoAChatsDeGrupo;
        this.marcarLeidoPort = marcarLeidoPort;
        this.anunciarLectura = anunciarLectura;
        this.contarNoLeidosPort = contarNoLeidosPort;
        this.loadMensajePort = loadMensajePort;
        this.listarUsuariosPort = listarUsuariosPort;
        this.userSummaryFinder = userSummaryFinder;
        this.fotosDeGrupos = fotosDeGrupos;
        this.nombresDeLosChats = nombresDeLosChats;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.transaccionPropia = new TransactionTemplate(transactionManager);
        this.transaccionPropia.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
    }

    @Override
    @Transactional
    public Conversacion obtenerOCrear(CrearConversacionDirectaCommand command) {
        requireActivo(command.actorId());
        if (command.actorId().equals(command.otroUsuarioId())) {
            throw new IllegalArgumentException("No se puede iniciar una conversacion directa con uno mismo");
        }
        requireActivo(command.otroUsuarioId());

        String clave = Conversacion.claveDirectaDe(command.actorId(), command.otroUsuarioId());
        return loadConversacionPort.porClaveDirecta(clave)
                .orElseGet(() -> crearDirectaConAmbosParticipantes(clave, command.actorId(),
                        command.otroUsuarioId()));
    }

    /**
     * C-10 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html): {@code obtenerOCrear}
     * es un check-then-act clasico ("traeme la conversacion directa, y si no existe creala").
     * Dos llamadas casi simultaneas del mismo par de usuarios (la app movil reintenta) pueden
     * ver las dos {@code porClaveDirecta(clave)} vacio y las dos intentar el INSERT — la
     * segunda pierde contra el {@code UNIQUE(clave_directa)} y sin este arreglo el 409 de
     * {@code GlobalExceptionHandler} llega a una operacion que el cliente percibe como abrir
     * un chat, no un conflicto real.
     *
     * <p>La creacion (conversacion + sus 2 participantes, atomico) corre en su propia
     * transaccion ({@link #transaccionPropia}, REQUIRES_NEW) a proposito: si se atrapara la
     * violacion de unicidad dentro de la MISMA transaccion de {@code obtenerOCrear} (que es
     * {@code @Transactional}), Postgres ya dejo esa transaccion abortada en cuanto el INSERT
     * fallo, y el releer {@code porClaveDirecta} a continuacion explotaria con "current
     * transaction is aborted" en vez de devolver la conversacion ganadora. Aislando la
     * creacion, si pierde la carrera solo se deshace ESA transaccion chica — la de
     * {@code obtenerOCrear} sigue sana y puede releer con normalidad la fila que gano,
     * ya comprometida (Postgres bloquea el segundo INSERT hasta que el primero termina).
     */
    private Conversacion crearDirectaConAmbosParticipantes(String clave, UserId a, UserId b) {
        try {
            return transaccionPropia.execute(status -> {
                // La identidad entra por el puerto IdGenerator, no la sortea el agregado (CLAUDE.MD §5.4.7).
                Conversacion guardada = saveConversacionPort.save(
                        Conversacion.crearDirecta(ConversacionId.of(idGenerator.newId()), clave, clock.now()));
                agregarParticipantePort.agregar(Participante.unirse(guardada.id(), a, clock.now()));
                agregarParticipantePort.agregar(Participante.unirse(guardada.id(), b, clock.now()));
                return guardada;
            });
        } catch (DataIntegrityViolationException carreraDeCreacionConcurrente) {
            return loadConversacionPort.porClaveDirecta(clave).orElseThrow(() -> carreraDeCreacionConcurrente);
        }
    }

    @Override
    public List<ConversacionResumen> listar(UserId actorId) {
        requireActivo(actorId);
        List<Conversacion> abribles = lasQuePuedeAbrir(actorId);
        /* Con quien habla en cada DIRECTA, en UNA consulta. Se pide para todas y se usa solo en
           las DIRECTAS: filtrar antes obligaria a recorrer dos veces para ahorrar nada. */
        Map<ConversacionId, UserId> otros = listarUsuariosPort.otroParticipanteDeDirectas(
                abribles.stream().map(Conversacion::id).toList(), actorId);
        List<Conversacion> conversaciones = sinDirectosConCuentasCerradas(abribles, otros);
        List<ConversacionId> ids = conversaciones.stream().map(Conversacion::id).toList();
        Map<ConversacionId, Mensaje> ultimos = loadMensajePort.ultimosPorConversacion(ids);
        Map<ConversacionId, Long> noLeidos = contarNoLeidosPort.contarNoLeidos(actorId, ids);
        /* Los nombres, EN LOTE y aca. Dejarselos al cliente contra el directorio de miembros fue
           el primer intento y no servia: ese directorio exige que exista la conversacion GLOBAL y
           donde no existe responde 404, con lo que la bandeja de DMs se quedaba sin nombres por
           culpa de otra conversacion. Una bandeja de mensajes directos tiene que nombrarse sola. */
        Map<UserId, UserSummary> perfiles = userSummaryFinder.findByIds(otros.values().stream().distinct().toList());
        Map<UUID, Instant> fotosPropias = fotosPropiasDeLosGrupos(conversaciones);
        Map<ConversacionId, String> nombres = nombresDeLosChats.nombresDe(conversaciones);
        return conversaciones.stream()
                .map(c -> {
                    UserId otro = c.tipo() == TipoConversacion.DIRECTA ? otros.get(c.id()) : null;
                    UserSummary perfil = otro != null ? perfiles.get(otro) : null;
                    return new ConversacionResumen(c, ultimos.get(c.id()), noLeidos.getOrDefault(c.id(), 0L),
                            otro, perfil != null ? perfil.fullName() : null,
                            perfil != null ? perfil.avatarUrl() : null,
                            c.celulaId() != null ? fotosPropias.get(c.celulaId()) : null, nombres.get(c.id()));
                })
                .sorted(Comparator.comparing(ConversacionService::actividadDe).reversed())
                .toList();
    }

    /**
     * Saca de la lista los chats DIRECTOS cuya contraparte cerró su cuenta y espera el borrado (D-243): ya
     * no hay con quién hablar, y a los 30 días el chat se borra entero. Una consulta para toda la lista. Los
     * de grupo, el global y el soporte no cambian; una suspensión a secas tampoco.
     */
    private List<Conversacion> sinDirectosConCuentasCerradas(List<Conversacion> conversaciones,
                                                             Map<ConversacionId, UserId> otros) {
        if (otros.isEmpty()) {
            return conversaciones;
        }
        Set<UserId> cerradas = cuentasCerradas.cerradasEntre(otros.values().stream().distinct().toList());
        if (cerradas.isEmpty()) {
            return conversaciones;
        }
        return conversaciones.stream()
                .filter(c -> c.tipo() != TipoConversacion.DIRECTA || !cerradas.contains(otros.get(c.id())))
                .toList();
    }

    /**
     * Las conversaciones de la lista: las suyas (la proyección {@code participantes_conversacion}) más,
     * para el Admin, los chats de TODOS los grupos en curso (D-225), y de esas solo las que se pueden
     * abrir.
     *
     * <p><b>E-470.</b> La proyección sirve para listar rápido, pero no se entera de que el período de un
     * grupo terminó: el chat del grupo anterior se seguía listando y al abrirlo respondía 403 «Tu
     * asignacion cambio: ya no perteneces a ese grupo». Ahora cada chat de grupo pasa por la MISMA
     * regla que da ese 403 ({@link AccesoAChatsDeGrupo}), así que lo que se lista se puede abrir. No
     * se borra nada: los mensajes quedan en la base. Los DIRECTA, GLOBAL y SOPORTE no cambian.
     *
     * <p>El Admin no tiene fila en la proyección de los grupos ajenos, así que su contador de no
     * leídos de esos grupos es 0: los ve, pero no le reclaman atención como los suyos.
     */
    private List<Conversacion> lasQuePuedeAbrir(UserId actorId) {
        AccesoAChatsDeGrupo.VistaDeGrupos vista = accesoAChatsDeGrupo.vistaDe(actorId);
        Map<ConversacionId, Conversacion> todas = new LinkedHashMap<>();
        loadConversacionPort.misConversaciones(actorId).forEach(c -> todas.put(c.id(), c));
        if (!vista.gruposPorSuRol().isEmpty()) {
            loadConversacionPort.porCelulaIds(vista.gruposPorSuRol()).forEach(c -> todas.putIfAbsent(c.id(), c));
        }
        return todas.values().stream()
                .filter(c -> c.tipo() != TipoConversacion.CELULA || vista.puedeVer(c.celulaId()))
                .toList();
    }

    /**
     * D-212: cuándo cambió la foto propia de cada grupo de la lista, en UNA consulta a community (sin
     * grupos, ninguna). Los que usan la foto de Renaser no figuran.
     */
    private Map<UUID, Instant> fotosPropiasDeLosGrupos(List<Conversacion> conversaciones) {
        List<UUID> grupos = conversaciones.stream()
                .filter(c -> c.tipo() == TipoConversacion.CELULA && c.celulaId() != null)
                .map(Conversacion::celulaId)
                .distinct()
                .toList();
        return grupos.isEmpty() ? Map.of() : fotosDeGrupos.cambiadasEn(grupos);
    }

    /** Conversacion sin mensajes: ordena por su fecha de creacion. */
    private static Instant actividadDe(ConversacionResumen resumen) {
        return resumen.ultimoMensaje() != null ? resumen.ultimoMensaje().creadoEn() : resumen.conversacion().creadoEn();
    }

    /**
     * Marca leída la conversación y avisa en vivo hasta dónde leyeron todos (D-208), para que quien
     * escribió vea pasar ✓ a ✓✓.
     *
     * <p><b>Sin {@code @Transactional}, a propósito.</b> El aviso tiene que salir con la lectura YA
     * guardada, y tiene que calcular la marca de todos con lo que los demás ya guardaron: si esto fuera
     * una sola transacción, dos lecturas simultáneas calcularían cada una sin ver la otra y la última en
     * avisar podría anunciar una marca vieja para siempre. Así, la escritura se confirma en la
     * transacción del adaptador y recién después se lee y se avisa. Lo demás son lecturas.
     */
    @Override
    public void marcarLeido(MarcarLeidoCommand command) {
        requireActivo(command.actorId());
        Conversacion conversacion = requireConversacion(command.conversacionId());
        requireParticipante(conversacion, command.actorId());
        marcarLeidoPort.marcarLeido(command.conversacionId(), command.actorId(), clock.now());
        anunciarLectura.anunciar(conversacion);
    }

    @Override
    @Transactional
    public void unirse(UserId usuarioId) {
        Conversacion global = loadConversacionPort.global()
                .orElseGet(() -> saveConversacionPort.save(
                        Conversacion.crearGlobal(ConversacionId.of(idGenerator.newId()), clock.now())));
        if (!esParticipantePort.esParticipante(global.id(), usuarioId)) {
            agregarParticipantePort.agregar(Participante.unirse(global.id(), usuarioId, clock.now()));
        }
    }

    @Override
    @Transactional
    public void crearParaCelula(UUID celulaId) {
        if (loadConversacionPort.porCelulaId(celulaId).isPresent()) {
            return;
        }
        saveConversacionPort.save(
                Conversacion.crearCelula(ConversacionId.of(idGenerator.newId()), celulaId, clock.now()));
    }

    @Override
    @Transactional
    public Conversacion renombrar(RenombrarConversacionGlobalCommand command) {
        requireActivoAdmin(command.actorId());
        Conversacion global = loadConversacionPort.global()
                .orElseThrow(() -> new NoSuchElementException("La conversacion GLOBAL todavia no existe"));
        return saveConversacionPort.save(global.renombrada(command.nuevoNombre()));
    }

    /**
     * Autorizacion de una conversacion.
     *
     * <p>Para un grupo NO alcanza con {@code participantes_conversacion}: esa tabla es una
     * proyeccion, y una proyeccion vieja no se limita a mostrar de menos — concede acceso de
     * mas. Un mentor que roto el mes pasado conservaria su fila y con ella la puerta abierta al
     * chat de gente que ya no acompana. Por eso el grupo se revalida contra la pertenencia
     * vigente y la proyeccion queda para listar rapido (plan.md §6).
     *
     * <p>Los directos y el GLOBAL siguen con su politica de siempre: nadie pierde un DM porque
     * alguien roto.
     *
     * <p><b>Un SOPORTE tampoco se resuelve con la proyeccion</b> (auditoria de seguridad). Se gana
     * por ROL —el aprendiz y los ADMIN/ALCHEMIST activos, nadie mas—, no por ser una de las dos
     * partes como una DIRECTA, asi que le cabe el mismo argumento: la fila que dejo la etapa de
     * staff le sobrevivia a la baja de rol y mantenia a un ex administrador dentro del chat privado
     * de cada aprendiz. Esto es el cinturon: vale aunque la revocacion del listener todavia no haya
     * corrido, se haya perdido o se reentregue tarde. <b>La misma condicion, en el mismo orden,
     * esta en {@code MensajeService}, {@code ConversacionService}, {@code PresenciaService} y
     * {@code AutorizacionDeConversacionService}; si se toca una, se tocan las cuatro.</b>
     */
    private void requireParticipante(Conversacion conversacion, UserId usuarioId) {
        if (conversacion.tipo() == TipoConversacion.CELULA) {
            if (!accesoAChatsDeGrupo.puedeVer(conversacion.celulaId(), usuarioId)) {
                throw new NotAuthorizedException("Tu asignacion cambio: ya no perteneces a ese grupo");
            }
            return;
        }
        if (!esParticipantePort.esParticipante(conversacion.id(), usuarioId)) {
            throw new NotAuthorizedException("No eres participante de esta conversación");
        }
        if (conversacion.seGanaPorRolDeStaff(usuarioId) && !esStaffAdministrativo(usuarioId)) {
            throw new NotAuthorizedException(
                    "Tu rol cambio: ya no formas parte del staff que acompana ese chat de soporte");
        }
    }

    /**
     * El rol de AHORA, no el que dejo escrita la proyeccion. {@code canManageRoles()} es
     * exactamente {ADMIN, ALCHEMIST}, el mismo conjunto que
     * {@code ConversacionSoporteService.STAFF_ADMINISTRATIVO}. Un usuario que ya no existe no es
     * staff: falla cerrado.
     */
    private boolean esStaffAdministrativo(UserId usuarioId) {
        return userSummaryFinder.findById(usuarioId)
                .map(usuario -> usuario.role().canManageRoles())
                .orElse(false);
    }

    private Conversacion requireConversacion(ConversacionId id) {
        return loadConversacionPort.porId(id)
                .orElseThrow(() -> new NoSuchElementException("Conversacion no encontrada: " + id));
    }

    private void requireActivo(UserId usuarioId) {
        UserSummary usuario = userSummaryFinder.findById(usuarioId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + usuarioId));
        if (usuario.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
    }

    /** Igual que {@link #requireActivo} + exige ademas ADMIN/ALCHEMIST
     * ({@code UserRole.canManageRoles()}) — solo lo usa {@link #renombrar}. */
    private void requireActivoAdmin(UserId usuarioId) {
        UserSummary usuario = userSummaryFinder.findById(usuarioId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + usuarioId));
        if (usuario.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
        if (!usuario.role().canManageRoles()) {
            throw new NotAuthorizedException("Solo ADMIN/ALCHEMIST puede renombrar el chat global");
        }
    }
}
