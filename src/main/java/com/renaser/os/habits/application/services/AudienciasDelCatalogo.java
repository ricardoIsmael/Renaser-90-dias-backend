package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.habitoadmin.ConsultarRolesElegiblesUseCase.RolElegible;
import com.renaser.os.habits.application.ports.in.habitoadmin.HabitoDelCatalogo;
import com.renaser.os.habits.application.ports.out.audiencia.CargarAudienciasPort;
import com.renaser.os.habits.application.ports.out.audiencia.GuardarAudienciaPort;
import com.renaser.os.habits.domain.model.audiencia.Audiencia;
import com.renaser.os.habits.domain.model.audiencia.CodigoDeRol;
import com.renaser.os.habits.domain.model.audiencia.HistorialDeAudiencia;
import com.renaser.os.habits.domain.model.audiencia.VersionDeAudiencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * La audiencia de los hábitos del catálogo vista desde el panel admin (spec 001, D-264): validar los roles contra los
 * que existen, guardar una versión nueva solo si cambió, y presentar cada hábito con su última versión y quién la
 * guardó. Separado de {@link HabitoAdminService} para que el CRUD no crezca más allá de su tamaño.
 */
@Component
class AudienciasDelCatalogo {

    /** La versión base de un hábito que existía antes de tener audiencia: "para todos" desde siempre. */
    static final Instant VERSION_BASE = Instant.EPOCH;
    /**
     * Para el aviso «Rige desde mañana» del panel: la zona por defecto del padrón. Es una ayuda para quien administra;
     * la regla real la aplica {@link CatalogoAplicable} en la zona de cada persona.
     */
    static final ZoneId ZONA_DE_REFERENCIA = ZoneId.of("America/Lima");

    private final CargarAudienciasPort cargarPort;
    private final GuardarAudienciaPort guardarPort;
    private final UserSummaryFinder userSummaryFinder;
    private final Clock clock;

    AudienciasDelCatalogo(CargarAudienciasPort cargarPort, GuardarAudienciaPort guardarPort,
                          UserSummaryFinder userSummaryFinder, Clock clock) {
        this.cargarPort = cargarPort;
        this.guardarPort = guardarPort;
        this.userSummaryFinder = userSummaryFinder;
        this.clock = clock;
    }

    /** Los roles del sistema, del de menos al de más alcance (Aprendiz primero), con su nombre visible. */
    List<RolElegible> rolesElegibles() {
        List<UserRole> roles = Arrays.asList(UserRole.values());
        return roles.reversed().stream().map(r -> new RolElegible(r.name(), r.nombreVisible())).toList();
    }

    /** Un rol que no existe es un pedido inválido (400, FR-021): no se guarda nada. */
    void validar(Audiencia audiencia) {
        audiencia.roles().stream().map(CodigoDeRol::valor)
                .filter(codigo -> Arrays.stream(UserRole.values()).noneMatch(r -> r.name().equals(codigo)))
                .findFirst()
                .ifPresent(codigo -> {
                    throw new IllegalArgumentException("El rol «" + codigo + "» no existe.");
                });
    }

    /**
     * La audiencia con la que nace un hábito: rige desde la creación ({@code HistorialDeAudiencia.vigenteEl} toma la
     * primera versión). "Para todos" no necesita fila: es lo que significa no tener versiones.
     */
    void guardarAlCrear(HabitoId habitoId, Audiencia audiencia, UserId actor) {
        if (!audiencia.paraTodos()) {
            agregar(habitoId, audiencia, clock.now(), actor);
        }
    }

    /**
     * Un cambio de audiencia: rige desde el día siguiente de cada persona (vigencia estricta). Idempotente: repetir la
     * última no crea historia.
     *
     * <p>Si el hábito nunca tuvo versiones —los anteriores a esta función, o uno creado "para todos"— se anota antes
     * una versión base "para todos" desde siempre. Sin ella, la versión nueva sería la PRIMERA, y la primera rige desde
     * la creación: el cambio se aplicaría hoy mismo a quien todavía no generó su día.
     */
    void guardarSiCambio(HabitoId habitoId, Audiencia audiencia, UserId actor) {
        Optional<VersionDeAudiencia> ultima = cargarPort.deHabitos(List.of(habitoId))
                .getOrDefault(habitoId, HistorialDeAudiencia.vacio()).ultima();
        if (ultima.map(VersionDeAudiencia::audiencia).orElse(Audiencia.PARA_TODOS).equals(audiencia)) {
            return;
        }
        if (ultima.isEmpty()) {
            agregar(habitoId, Audiencia.PARA_TODOS, VERSION_BASE, actor);
        }
        agregar(habitoId, audiencia, clock.now(), actor);
    }

    private void agregar(HabitoId habitoId, Audiencia audiencia, Instant rigeDesde, UserId actor) {
        guardarPort.agregarVersion(new VersionDeAudiencia(habitoId, audiencia, rigeDesde, actor, clock.now()));
    }

    HabitoDelCatalogo presentar(Habito habito) {
        return presentar(List.of(habito)).getFirst();
    }

    /** Dos consultas para todo el listado (audiencias y nombres), nunca una por hábito. */
    List<HabitoDelCatalogo> presentar(Collection<Habito> habitos) {
        Map<HabitoId, HistorialDeAudiencia> audiencias = cargarPort.deHabitos(habitos.stream().map(Habito::id).toList());
        Map<UserId, UserSummary> autores = userSummaryFinder.findByIds(audiencias.values().stream()
                .map(HistorialDeAudiencia::ultima).flatMap(Optional::stream)
                .map(VersionDeAudiencia::cambiadoPor).distinct().toList());
        Instant ahora = clock.now();
        return habitos.stream().map(h -> {
            HistorialDeAudiencia historial = audiencias.getOrDefault(h.id(), HistorialDeAudiencia.vacio());
            Optional<VersionDeAudiencia> ultima = historial.ultima();
            String autor = ultima.map(v -> autores.get(v.cambiadoPor())).filter(Objects::nonNull)
                    .map(UserSummary::fullName).orElse(null);
            return new HabitoDelCatalogo(h, ultima, autor, historial.cambioPendienteHasta(ahora, ZONA_DE_REFERENCIA));
        }).toList();
    }
}
