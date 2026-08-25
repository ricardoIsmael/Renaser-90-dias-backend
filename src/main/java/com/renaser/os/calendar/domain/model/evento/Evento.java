package com.renaser.os.calendar.domain.model.evento;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

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

    public static Evento crear(String titulo, String descripcion, Instant iniciaEn, Integer duracionMinutos,
                                ZoneId timezone, TipoUbicacion tipoUbicacion, String valorUbicacion,
                                TipoAudiencia tipoAudiencia, Integer nivelMinimoId, String cursoId,
                                UUID celulaDestinoId, TipoEvento tipoEvento, boolean notificarAlCrear,
                                boolean recordarPorEmail, boolean recordatoriosPersonalizados,
                                Recurrencia recurrencia, Set<RolUsuario> rolesDestino,
                                List<ReglaRecordatorio> reglasRecordatorio, UserId creadoPor, Clock clock) {
        Instant ahora = clock.now();
        Evento evento = new Evento(EventoId.newId(), null, null, null, null, null, null, null, null, null,
                null, null, null, EstadoEvento.PUBLICADO, requireNonNullEvento(tipoEvento), false, false,
                false, null, Set.<RolUsuario>of(), List.<ReglaRecordatorio>of(), creadoPor, ahora, ahora);
        evento.aplicarCambios(titulo, descripcion, iniciaEn, duracionMinutos, timezone, tipoUbicacion, valorUbicacion,
                tipoAudiencia, nivelMinimoId, cursoId, celulaDestinoId, notificarAlCrear, recordarPorEmail,
                recordatoriosPersonalizados, recurrencia, rolesDestino, reglasRecordatorio);
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
        aplicarCambios(titulo, descripcion, iniciaEn, duracionMinutos, timezone, tipoUbicacion, valorUbicacion,
                tipoAudiencia, nivelMinimoId, cursoId, celulaDestinoId, notificarAlCrear, recordarPorEmail,
                recordatoriosPersonalizados, recurrencia, rolesDestino, reglasRecordatorio);
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
        return new Evento(id, titulo, descripcion, portadaRuta, iniciaEn, duracionMinutos, timezone, tipoUbicacion,
                valorUbicacion, tipoAudiencia, nivelMinimoId, cursoId, celulaDestinoId, estado, tipoEvento,
                notificarAlCrear, recordarPorEmail, recordatoriosPersonalizados, recurrencia,
                rolesDestino == null || rolesDestino.isEmpty() ? Set.of() : EnumSet.copyOf(rolesDestino),
                reglasRecordatorio == null ? List.of() : List.copyOf(reglasRecordatorio), creadoPor, creadoEn,
                actualizadoEn);
    }

    public void fijarPortada(String ruta) {
        this.portadaRuta = ruta;
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
                                 Set<RolUsuario> rolesDestino, List<ReglaRecordatorio> reglasRecordatorio) {
        requireAudienciaCoherente(tipoAudiencia, nivelMinimoId, cursoId, celulaDestinoId, rolesDestino);
        requireUbicacionCoherente(tipoUbicacion, valorUbicacion);
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

    private static void requireUbicacionCoherente(TipoUbicacion tipo, String valor) {
        boolean blank = valor == null || valor.isBlank();
        switch (tipo) {
            case ZOOM, MEET, ENLACE -> {
                if (blank) {
                    throw new IllegalArgumentException("La URL es obligatoria para este tipo de ubicacion");
                }
            }
            case DIRECCION -> {
                if (blank) {
                    throw new IllegalArgumentException("La direccion es obligatoria");
                }
            }
            case LLAMADA_INTERNA, WEBINAR -> {
                if (!blank) {
                    throw new IllegalArgumentException("valorUbicacion debe ser nulo para " + tipo);
                }
            }
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
