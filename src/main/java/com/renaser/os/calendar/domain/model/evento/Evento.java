package com.renaser.os.calendar.domain.model.evento;

import com.renaser.os.calendar.domain.model.meet.AccesoSalaMeet;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Un evento del calendario (tabla {@code eventos} + sus tablas hijas). El formulario de
 * "Crear/Editar evento" del panel admin siempre reenvia el evento COMPLETO (nunca un PATCH
 * parcial) — igual que el repo viejo (CreateEventInput/UpdateEventInput comparten forma en
 * schema.ts) — por eso {@link #actualizar} reemplaza el estado entero en vez de exponer
 * setters de campo suelto.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class Evento {

    /** z.string().max(30) en schema.ts — bastante mas corto que otros modulos, es intencional (tarjeta del calendario). */
    private static final int MAX_TITULO = 30;
    private static final int MAX_DESCRIPCION = 300;
    private static final int MAX_UBICACION = 600;
    /** SEG-13 (E-364): el unico mensaje del rechazo, para que el cliente sepa exactamente que corregir. */
    static final String LINK_NO_WEB = "El link tiene que empezar con https:// o http://";
    /** MAX_REGLAS_POR_EVENTO del repo viejo (reminders.ts) — regla de negocio confirmada; el CHECK de
     * la tabla ({@code orden BETWEEN 1 AND 10}) solo da margen de crecimiento futuro, no reemplaza esta regla. */
    public static final int MAX_REGLAS_RECORDATORIO = 5;

    private final EventoId id;
    private String titulo;
    private String descripcion;
    private String portadaRuta;
    private Instant iniciaEn;
    private Integer duracionMinutos;
    private ZoneId timezone;
    private TipoUbicacion tipoUbicacion;
    private String valorUbicacion;
    private TipoAudiencia tipoAudiencia;
    private Integer nivelMinimoId;
    private String cursoId;
    private UUID celulaDestinoId;
    private EstadoEvento estado;
    private final TipoEvento tipoEvento;
    private boolean notificarAlCrear;
    private boolean recordarPorEmail;
    private boolean recordatoriosPersonalizados;
    private Recurrencia recurrencia;
    private Set<RolUsuario> rolesDestino;
    private List<ReglaRecordatorio> reglasRecordatorio;
    private final UserId creadoPor;
    private final Instant creadoEn;
    private Instant actualizadoEn;
    /**
     * La formación del evento (V137, spec 024): su audiencia —todos, nivel, curso, roles, grupo— se resuelve DENTRO de ella. {@code final}: un evento no
     * cambia de formación (para repetirlo en otra se copia).
     */
    private final Formacion formacion;
    /**
     * Spec 040 (V169): si este evento pide que su enlace de Meet se cree solo. {@code null} = no lo pide (el evento no usa Meet, o su link se
     * pegó a mano); con valor, el evento es {@code MEET} y su enlace lo pone el servidor ({@link #fijarEnlaceDeMeet}). Con valor es SIEMPRE
     * {@link AccesoSalaMeet#ABIERTA}: la sala de Renaser no se elige con permiso (decisión del dueño del 2026-11-05, «mucha configuración
     * arruina la experiencia»). Cualquier otro valor que llegue —un dato guardado antes de esa decisión o un llamador viejo— se toma como
     * abierta, así el servidor nunca pide {@code TRUSTED} a Google.
     */
    private AccesoSalaMeet accesoSalaMeet;

    /**
     * El {@code id} entra por parametro, no se genera aca: la identidad viene del puerto
     * {@code IdGenerator} que inyecta el caso de uso ({@code EventoService.crear}). Asi
     * {@code crear} es referencialmente transparente y un test puede fijar el id que espera,
     * en vez de tener que caer a {@link #rehydrate} para lograrlo.
     */
    public static Evento crear(EventoId id, String titulo, String descripcion, Instant iniciaEn,
                                Integer duracionMinutos, ZoneId timezone, TipoUbicacion tipoUbicacion,
                                String valorUbicacion, TipoAudiencia tipoAudiencia, Integer nivelMinimoId,
                                String cursoId, UUID celulaDestinoId, TipoEvento tipoEvento,
                                boolean notificarAlCrear, boolean recordarPorEmail,
                                boolean recordatoriosPersonalizados, Recurrencia recurrencia,
                                Set<RolUsuario> rolesDestino, List<ReglaRecordatorio> reglasRecordatorio,
                                UserId creadoPor, Clock clock) {
        return crear(id, titulo, descripcion, iniciaEn, duracionMinutos, timezone, tipoUbicacion, valorUbicacion,
                tipoAudiencia, nivelMinimoId, cursoId, celulaDestinoId, tipoEvento, notificarAlCrear, recordarPorEmail,
                recordatoriosPersonalizados, recurrencia, rolesDestino, reglasRecordatorio, creadoPor, clock,
                Formacion.POR_DEFECTO);
    }

    /** Igual que la anterior, en la formación dada (spec 024): Administración la elige siempre al crear. */
    public static Evento crear(EventoId id, String titulo, String descripcion, Instant iniciaEn,
                                Integer duracionMinutos, ZoneId timezone, TipoUbicacion tipoUbicacion,
                                String valorUbicacion, TipoAudiencia tipoAudiencia, Integer nivelMinimoId,
                                String cursoId, UUID celulaDestinoId, TipoEvento tipoEvento,
                                boolean notificarAlCrear, boolean recordarPorEmail,
                                boolean recordatoriosPersonalizados, Recurrencia recurrencia,
                                Set<RolUsuario> rolesDestino, List<ReglaRecordatorio> reglasRecordatorio,
                                UserId creadoPor, Clock clock, Formacion formacion) {
        return crear(id, titulo, descripcion, iniciaEn, duracionMinutos, timezone, tipoUbicacion, valorUbicacion,
                tipoAudiencia, nivelMinimoId, cursoId, celulaDestinoId, tipoEvento, notificarAlCrear, recordarPorEmail,
                recordatoriosPersonalizados, recurrencia, rolesDestino, reglasRecordatorio, creadoPor, clock, formacion,
                null);
    }

    /**
     * Igual que la anterior, pidiendo que el enlace de Meet se cree solo (spec 040): con {@code accesoSalaMeet} el evento es {@code MEET} y su
     * enlace todavía no existe ({@code valorUbicacion} nulo); lo pone el servidor cuando Google crea la sala. {@code null} = el evento no lo pide.
     */
    public static Evento crear(EventoId id, String titulo, String descripcion, Instant iniciaEn,
                                Integer duracionMinutos, ZoneId timezone, TipoUbicacion tipoUbicacion,
                                String valorUbicacion, TipoAudiencia tipoAudiencia, Integer nivelMinimoId,
                                String cursoId, UUID celulaDestinoId, TipoEvento tipoEvento,
                                boolean notificarAlCrear, boolean recordarPorEmail,
                                boolean recordatoriosPersonalizados, Recurrencia recurrencia,
                                Set<RolUsuario> rolesDestino, List<ReglaRecordatorio> reglasRecordatorio,
                                UserId creadoPor, Clock clock, Formacion formacion, AccesoSalaMeet accesoSalaMeet) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(formacion, "formacion es obligatoria");
        Instant ahora = clock.now();
        Evento evento = new Evento(id, null, null, null, null, null, null, null, null, null,
                null, null, null, EstadoEvento.PUBLICADO, requireNonNullEvento(tipoEvento), false, false,
                false, null, Set.<RolUsuario>of(), List.<ReglaRecordatorio>of(), creadoPor, ahora, ahora, formacion, null);
        evento.aplicarCambios(titulo, descripcion, iniciaEn, duracionMinutos, timezone, tipoUbicacion, valorUbicacion,
                tipoAudiencia, nivelMinimoId, cursoId, celulaDestinoId, notificarAlCrear, recordarPorEmail,
                recordatoriosPersonalizados, recurrencia, rolesDestino, reglasRecordatorio, accesoSalaMeet);
        evento.actualizadoEn = ahora;
        return evento;
    }

    /** Reenvio completo del formulario de edicion — reemplaza todo salvo id/tipoEvento/creadoPor/creadoEn/estado. */
    public void actualizar(String titulo, String descripcion, Instant iniciaEn, Integer duracionMinutos,
                            ZoneId timezone, TipoUbicacion tipoUbicacion, String valorUbicacion,
                            TipoAudiencia tipoAudiencia, Integer nivelMinimoId, String cursoId, UUID celulaDestinoId,
                            boolean notificarAlCrear, boolean recordarPorEmail, boolean recordatoriosPersonalizados,
                            Recurrencia recurrencia, Set<RolUsuario> rolesDestino,
                            List<ReglaRecordatorio> reglasRecordatorio, Clock clock) {
        actualizar(titulo, descripcion, iniciaEn, duracionMinutos, timezone, tipoUbicacion, valorUbicacion,
                tipoAudiencia, nivelMinimoId, cursoId, celulaDestinoId, notificarAlCrear, recordarPorEmail,
                recordatoriosPersonalizados, recurrencia, rolesDestino, reglasRecordatorio, clock, null);
    }

    /** Igual que la anterior, pidiendo (o dejando de pedir) que el enlace de Meet se cree solo (spec 040). */
    public void actualizar(String titulo, String descripcion, Instant iniciaEn, Integer duracionMinutos,
                            ZoneId timezone, TipoUbicacion tipoUbicacion, String valorUbicacion,
                            TipoAudiencia tipoAudiencia, Integer nivelMinimoId, String cursoId, UUID celulaDestinoId,
                            boolean notificarAlCrear, boolean recordarPorEmail, boolean recordatoriosPersonalizados,
                            Recurrencia recurrencia, Set<RolUsuario> rolesDestino,
                            List<ReglaRecordatorio> reglasRecordatorio, Clock clock, AccesoSalaMeet accesoSalaMeet) {
        aplicarCambios(titulo, descripcion, iniciaEn, duracionMinutos, timezone, tipoUbicacion, valorUbicacion,
                tipoAudiencia, nivelMinimoId, cursoId, celulaDestinoId, notificarAlCrear, recordarPorEmail,
                recordatoriosPersonalizados, recurrencia, rolesDestino, reglasRecordatorio, accesoSalaMeet);
        this.actualizadoEn = clock.now();
    }

    /** Solo para el adaptador de persistencia: reconstruye un evento ya existente sin re-validar. */
    public static Evento rehydrate(EventoId id, String titulo, String descripcion, String portadaRuta,
                                    Instant iniciaEn, Integer duracionMinutos, ZoneId timezone,
                                    TipoUbicacion tipoUbicacion, String valorUbicacion, TipoAudiencia tipoAudiencia,
                                    Integer nivelMinimoId, String cursoId, UUID celulaDestinoId, EstadoEvento estado,
                                    TipoEvento tipoEvento, boolean notificarAlCrear, boolean recordarPorEmail,
                                    boolean recordatoriosPersonalizados, Recurrencia recurrencia,
                                    Set<RolUsuario> rolesDestino, List<ReglaRecordatorio> reglasRecordatorio,
                                    UserId creadoPor, Instant creadoEn, Instant actualizadoEn) {
        return rehydrate(id, titulo, descripcion, portadaRuta, iniciaEn, duracionMinutos, timezone, tipoUbicacion,
                valorUbicacion, tipoAudiencia, nivelMinimoId, cursoId, celulaDestinoId, estado, tipoEvento,
                notificarAlCrear, recordarPorEmail, recordatoriosPersonalizados, recurrencia, rolesDestino,
                reglasRecordatorio, creadoPor, creadoEn, actualizadoEn, Formacion.POR_DEFECTO);
    }

    /** Solo para el adaptador de persistencia, con la formación guardada en la fila. */
    public static Evento rehydrate(EventoId id, String titulo, String descripcion, String portadaRuta,
                                    Instant iniciaEn, Integer duracionMinutos, ZoneId timezone,
                                    TipoUbicacion tipoUbicacion, String valorUbicacion, TipoAudiencia tipoAudiencia,
                                    Integer nivelMinimoId, String cursoId, UUID celulaDestinoId, EstadoEvento estado,
                                    TipoEvento tipoEvento, boolean notificarAlCrear, boolean recordarPorEmail,
                                    boolean recordatoriosPersonalizados, Recurrencia recurrencia,
                                    Set<RolUsuario> rolesDestino, List<ReglaRecordatorio> reglasRecordatorio,
                                    UserId creadoPor, Instant creadoEn, Instant actualizadoEn, Formacion formacion) {
        return rehydrate(id, titulo, descripcion, portadaRuta, iniciaEn, duracionMinutos, timezone, tipoUbicacion,
                valorUbicacion, tipoAudiencia, nivelMinimoId, cursoId, celulaDestinoId, estado, tipoEvento,
                notificarAlCrear, recordarPorEmail, recordatoriosPersonalizados, recurrencia, rolesDestino,
                reglasRecordatorio, creadoPor, creadoEn, actualizadoEn, formacion, null);
    }

    /** Solo para el adaptador de persistencia, con la formación y el acceso de la sala automática (spec 040). */
    public static Evento rehydrate(EventoId id, String titulo, String descripcion, String portadaRuta,
                                    Instant iniciaEn, Integer duracionMinutos, ZoneId timezone,
                                    TipoUbicacion tipoUbicacion, String valorUbicacion, TipoAudiencia tipoAudiencia,
                                    Integer nivelMinimoId, String cursoId, UUID celulaDestinoId, EstadoEvento estado,
                                    TipoEvento tipoEvento, boolean notificarAlCrear, boolean recordarPorEmail,
                                    boolean recordatoriosPersonalizados, Recurrencia recurrencia,
                                    Set<RolUsuario> rolesDestino, List<ReglaRecordatorio> reglasRecordatorio,
                                    UserId creadoPor, Instant creadoEn, Instant actualizadoEn, Formacion formacion,
                                    AccesoSalaMeet accesoSalaMeet) {
        return new Evento(id, titulo, descripcion, portadaRuta, iniciaEn, duracionMinutos, timezone, tipoUbicacion,
                valorUbicacion, tipoAudiencia, nivelMinimoId, cursoId, celulaDestinoId, estado, tipoEvento,
                notificarAlCrear, recordarPorEmail, recordatoriosPersonalizados, recurrencia,
                rolesDestino == null || rolesDestino.isEmpty() ? Set.of() : EnumSet.copyOf(rolesDestino),
                reglasRecordatorio == null ? List.of() : List.copyOf(reglasRecordatorio), creadoPor, creadoEn,
                actualizadoEn, formacion, accesoSalaMeet);
    }

    public void fijarPortada(String ruta) {
        this.portadaRuta = ruta;
    }

    /** Si el servidor, y no una persona, pone el enlace de Meet de este evento (spec 040, FR-001). */
    public boolean usaSalaDeMeetAutomatica() {
        return accesoSalaMeet != null;
    }

    /**
     * El enlace de la sala que creó Google. Solo un evento que pide la sala automática lo recibe, y debe ser un link web válido (la misma regla
     * que un link pegado a mano, {@link #requireLinkWeb}); {@code null} lo quita. Es lo que el resto del sistema —listas, «Unirme»,
     * recordatorios— lee como cualquier link de Meet (FR-003).
     */
    public void fijarEnlaceDeMeet(String enlace) {
        if (!usaSalaDeMeetAutomatica()) {
            throw new IllegalStateException("Este evento no pide el enlace de Meet automático.");
        }
        if (enlace != null) {
            requireLinkWeb(enlace);
        }
        this.valorUbicacion = enlace;
    }

    public void cancelar(Clock clock) {
        this.estado = EstadoEvento.CANCELADO;
        this.actualizadoEn = clock.now();
    }

    public boolean esRecurrente() {
        return recurrencia != null;
    }

    /** reglasEfectivas() del repo viejo: null (aca, `!recordatoriosPersonalizados`) hereda las del TIPO; con el
     * flag en true rigen las propias, aunque sean 0 (el admin decidio que este evento no avisa). */
    public List<ReglaRecordatorio> reglasRecordatorioEfectivas() {
        return recordatoriosPersonalizados ? reglasRecordatorio : ReglasPorTipoEvento.recordatoriosPorDefecto(tipoEvento);
    }

    private void aplicarCambios(String titulo, String descripcion, Instant iniciaEn, Integer duracionMinutos,
                                 ZoneId timezone, TipoUbicacion tipoUbicacion, String valorUbicacion,
                                 TipoAudiencia tipoAudiencia, Integer nivelMinimoId, String cursoId,
                                 UUID celulaDestinoId, boolean notificarAlCrear, boolean recordarPorEmail,
                                 boolean recordatoriosPersonalizados, Recurrencia recurrencia,
                                 Set<RolUsuario> rolesDestino, List<ReglaRecordatorio> reglasRecordatorio,
                                 AccesoSalaMeet accesoSalaMeet) {
        requireAudienciaCoherente(tipoAudiencia, nivelMinimoId, cursoId, celulaDestinoId, rolesDestino);
        requireUbicacionCoherente(tipoUbicacion, valorUbicacion, accesoSalaMeet != null);
        requireReglasValidas(recordatoriosPersonalizados, reglasRecordatorio);
        if (recurrencia != null && recurrencia.hasta() != null && !recurrencia.hasta().isAfter(iniciaEn)) {
            throw new IllegalArgumentException("recurrencia.hasta debe ser posterior a iniciaEn");
        }

        this.titulo = requireTitulo(titulo);
        this.descripcion = requireMax(descripcion, MAX_DESCRIPCION, "descripcion");
        this.iniciaEn = Objects.requireNonNull(iniciaEn, "iniciaEn es obligatorio");
        this.duracionMinutos = requirePositivoONulo(duracionMinutos, "duracionMinutos");
        this.timezone = Objects.requireNonNull(timezone, "timezone es obligatoria");
        this.tipoUbicacion = Objects.requireNonNull(tipoUbicacion, "tipoUbicacion es obligatorio");
        this.valorUbicacion = requireMax(valorUbicacion, MAX_UBICACION, "valorUbicacion");
        this.tipoAudiencia = tipoAudiencia;
        this.nivelMinimoId = nivelMinimoId;
        this.cursoId = cursoId;
        this.celulaDestinoId = celulaDestinoId;
        this.notificarAlCrear = notificarAlCrear;
        this.recordarPorEmail = recordarPorEmail;
        this.recordatoriosPersonalizados = recordatoriosPersonalizados;
        this.recurrencia = recurrencia;
        this.rolesDestino = rolesDestino == null || rolesDestino.isEmpty()
                ? Set.of() : EnumSet.copyOf(rolesDestino);
        this.reglasRecordatorio = reglasRecordatorio == null ? List.of() : List.copyOf(reglasRecordatorio);
        this.accesoSalaMeet = accesoSalaMeet == null ? null : AccesoSalaMeet.ABIERTA;
    }

    private static void requireAudienciaCoherente(TipoAudiencia tipoAudiencia, Integer nivelMinimoId, String cursoId,
                                                    UUID celulaDestinoId, Set<RolUsuario> rolesDestino) {
        Objects.requireNonNull(tipoAudiencia, "tipoAudiencia es obligatorio");
        boolean requiereNivel = tipoAudiencia == TipoAudiencia.NIVEL_MINIMO;
        boolean requiereCurso = tipoAudiencia == TipoAudiencia.CURSO;
        boolean requiereCelula = tipoAudiencia == TipoAudiencia.CELULA;
        boolean requiereRoles = tipoAudiencia == TipoAudiencia.ROLES;

        if (requiereNivel != (nivelMinimoId != null)) {
            throw new IllegalArgumentException("audiencia_coherente: nivelMinimoId requerido solo para NIVEL_MINIMO");
        }
        if (requiereCurso != (cursoId != null)) {
            throw new IllegalArgumentException("audiencia_coherente: cursoId requerido solo para CURSO");
        }
        if (requiereCelula != (celulaDestinoId != null)) {
            throw new IllegalArgumentException("audiencia_coherente: celulaDestinoId requerido solo para CELULA");
        }
        boolean tieneRoles = rolesDestino != null && !rolesDestino.isEmpty();
        if (requiereRoles != tieneRoles) {
            throw new IllegalArgumentException("targetRoles es obligatorio para ROLES y vacio en cualquier otro caso");
        }
    }

    private static void requireUbicacionCoherente(TipoUbicacion tipo, String valor, boolean enlaceDeMeetAutomatico) {
        boolean blank = valor == null || valor.isBlank();
        if (enlaceDeMeetAutomatico) {
            requireUbicacionDeMeetAutomatico(tipo, blank ? null : valor);
            return;
        }
        switch (tipo) {
            case ZOOM, MEET, ENLACE -> {
                if (blank) {
                    throw new IllegalArgumentException("La URL es obligatoria para este tipo de ubicación");
                }
                requireLinkWeb(valor);
            }
            case DIRECCION -> {
                if (blank) {
                    throw new IllegalArgumentException("La dirección es obligatoria");
                }
            }
            case LLAMADA_INTERNA, WEBINAR -> {
                if (!blank) {
                    throw new IllegalArgumentException("valorUbicacion debe ser nulo para " + tipo);
                }
            }
        }
    }

    /** Spec 040: con la sala automática el evento es de Meet y su enlace, si ya existe, es uno web válido; todavía puede no existir. */
    private static void requireUbicacionDeMeetAutomatico(TipoUbicacion tipo, String valor) {
        if (tipo != TipoUbicacion.MEET) {
            throw new IllegalArgumentException("Con el enlace de Meet automático, el evento tiene que ser de Meet.");
        }
        if (valor != null) {
            requireLinkWeb(valor);
        }
    }

    /**
     * SEG-13 (E-364, 2026-09-27): el link de un Meet, un Zoom o un Drive se abre al tocar «Unirme», en la app
     * y en la web, asi que solo se acepta una direccion web con servidor: {@code https} o {@code http}.
     * Antes no se miraba nada y el servidor guardo {@code javascript:alert(1)}; tambien quedan afuera
     * {@code data:}, {@code intent:}, {@code file:} y un texto sin esquema. La app ya exigia {@code https}
     * en su formulario, pero la regla tiene que estar donde no se la puede saltear.
     *
     * <p>Los eventos que ya tienen un link asi se siguen leyendo ({@link #rehydrate} no valida); al editarlos
     * hay que corregirlo.
     */
    private static void requireLinkWeb(String valor) {
        URI link;
        try {
            link = new URI(valor.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(LINK_NO_WEB);
        }
        String esquema = link.getScheme();
        boolean web = "https".equalsIgnoreCase(esquema) || "http".equalsIgnoreCase(esquema);
        if (!web || link.getHost() == null || link.getHost().isBlank()) {
            throw new IllegalArgumentException(LINK_NO_WEB);
        }
    }

    private static void requireReglasValidas(boolean personalizados, List<ReglaRecordatorio> reglas) {
        if (!personalizados || reglas == null) {
            return;
        }
        if (reglas.size() > MAX_REGLAS_RECORDATORIO) {
            throw new IllegalArgumentException("Maximo " + MAX_REGLAS_RECORDATORIO + " recordatorios por evento");
        }
        Set<String> vistas = new java.util.HashSet<>();
        for (ReglaRecordatorio regla : reglas) {
            if (!vistas.add(regla.claveDuplicado())) {
                throw new IllegalArgumentException("Hay dos recordatorios iguales — cada uno debe ser distinto");
            }
        }
    }

    private static String requireTitulo(String titulo) {
        if (titulo == null || titulo.isBlank()) {
            throw new IllegalArgumentException("El titulo es obligatorio");
        }
        String limpio = titulo.trim();
        if (limpio.length() > MAX_TITULO) {
            throw new IllegalArgumentException("titulo supera " + MAX_TITULO + " caracteres");
        }
        return limpio;
    }

    private static String requireMax(String valor, int max, String campo) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        if (limpio.length() > max) {
            throw new IllegalArgumentException(campo + " supera " + max + " caracteres");
        }
        return limpio.isBlank() ? null : limpio;
    }

    private static Integer requirePositivoONulo(Integer valor, String campo) {
        if (valor != null && valor <= 0) {
            throw new IllegalArgumentException(campo + " debe ser positivo");
        }
        return valor;
    }

    private static TipoEvento requireNonNullEvento(TipoEvento tipoEvento) {
        return Objects.requireNonNull(tipoEvento, "tipoEvento es obligatorio");
    }

    @Override
    public String toString() {
        return "Evento[" + id + ", " + titulo + ", " + tipoEvento + "]";
    }
}
