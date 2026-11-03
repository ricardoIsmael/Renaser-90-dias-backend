package com.renaser.os.onboarding.infrastructure.adapter.in.rest.terminos;

import com.renaser.os.onboarding.application.ports.in.terminos.ConsultarTerminosUseCase;
import com.renaser.os.onboarding.application.ports.in.terminos.ConsultarTerminosUseCase.ConstanciaDeUnaPersona;
import com.renaser.os.onboarding.application.ports.in.terminos.ConsultarTerminosUseCase.DetalleDeTerminos;
import com.renaser.os.onboarding.application.ports.in.terminos.ConsultarTerminosUseCase.Persona;
import com.renaser.os.onboarding.application.ports.in.terminos.ConsultarTerminosUseCase.QuienesAceptaron;
import com.renaser.os.onboarding.application.ports.in.terminos.ConsultarTerminosUseCase.ResumenDeTerminos;
import com.renaser.os.onboarding.application.ports.in.terminos.GestionarTerminosUseCase;
import com.renaser.os.onboarding.domain.model.terminos.ContenidoDeTerminos;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Administración y Alquimista editan y publican los términos y condiciones de cada formación (spec 027). El guard real es {@code GuardiaDeTerminos}
 * (ADMIN/ALCHEMIST activos): la matriz de permisos todavía deja pasar a un mentor (A-1), así que cada servicio vuelve a comprobarlo.
 */
@RestController
@RequestMapping("/api/v1/admin/terms")
@Funcionalidad("admin.personas")
public class TerminosAdminController {

    private static final String SCOPE = "el guard real es GuardiaDeTerminos: ADMIN/ALCHEMIST activos";

    private final ConsultarTerminosUseCase consultar;
    private final GestionarTerminosUseCase gestionar;

    public TerminosAdminController(ConsultarTerminosUseCase consultar, GestionarTerminosUseCase gestionar) {
        this.consultar = consultar;
        this.gestionar = gestionar;
    }

    public record ResumenResponse(String formation, String name, VersionDeTerminosResponse current, boolean hasDraft, long acceptedCount, boolean needsReview) {

        static ResumenResponse from(ResumenDeTerminos r) {
            return new ResumenResponse(r.formacion().codigo(), r.nombre(), r.vigente().map(VersionDeTerminosResponse::from).orElse(null), r.hayBorrador(),
                    r.aceptantes(), r.vigente().isEmpty());
        }
    }

    public record DetalleResponse(String formation, String name, VersionDeTerminosResponse current, VersionDeTerminosResponse draft, List<HistorialResponse> versions) {

        static DetalleResponse from(DetalleDeTerminos d) {
            return new DetalleResponse(d.formacion().codigo(), d.nombre(), d.vigente().map(VersionDeTerminosResponse::from).orElse(null),
                    d.borrador().map(VersionDeTerminosResponse::from).orElse(null),
                    d.versiones().stream().map(v -> new HistorialResponse(VersionDeTerminosResponse.from(v.version()), v.aceptantes(), v.publicadaPor())).toList());
        }
    }

    /** Una versión del historial: su texto, cuántas personas la aceptaron y quién la publicó. */
    public record HistorialResponse(VersionDeTerminosResponse version, long acceptedCount, String publishedByName) {
    }

    public record PersonaResponse(UUID id, String name) {

        static PersonaResponse from(Persona p) {
            return new PersonaResponse(p.id().value(), p.nombre());
        }
    }

    public record QuienesResponse(int number, List<PersonaResponse> accepted, List<PersonaResponse> missing) {

        static QuienesResponse from(QuienesAceptaron q) {
            return new QuienesResponse(q.numero(), q.aceptaron().stream().map(PersonaResponse::from).toList(), q.faltan().stream().map(PersonaResponse::from).toList());
        }
    }

    /** {@code program}, {@code startDate} y {@code endDate}: lo que se le mostró en «Mi aceptación» al aceptar (spec 039); nulos en lo aceptado antes. */
    public record ConstanciaResponse(String formation, int number, Instant acceptedAt, String signatureUrl, String program, LocalDate startDate, LocalDate endDate) {

        static ConstanciaResponse from(ConstanciaDeUnaPersona c) {
            return new ConstanciaResponse(c.formacion().codigo(), c.numero(), c.aceptadaEn(), c.firmaUrl(), c.programa(), c.inicio(), c.fin());
        }
    }

    /** @param baseNumber la versión vigente que quien edita tenía a la vista ({@code null} si no había ninguna) */
    public record GuardarBorradorRequest(@NotNull String title, String introduction, @NotNull List<String> clauses, @NotNull String checkboxText,
                                         Integer baseNumber) {
    }

    public record PublicarRequest(String note, boolean asksNewAcceptance, Integer baseNumber) {
    }

    @RequiresPermission(value = Permission.MANAGE_ROLES, scope = SCOPE)
    @GetMapping
    public List<ResumenResponse> resumen(@ActorAutenticado UserId actor) {
        return consultar.resumen(actor).stream().map(ResumenResponse::from).toList();
    }

    @RequiresPermission(value = Permission.MANAGE_ROLES, scope = SCOPE)
    @GetMapping("/people/{id}")
    public List<ConstanciaResponse> constancia(@PathVariable UUID id, @ActorAutenticado UserId actor) {
        return consultar.constanciaDe(actor, UserId.of(id)).stream().map(ConstanciaResponse::from).toList();
    }

    @RequiresPermission(value = Permission.MANAGE_ROLES, scope = SCOPE)
    @GetMapping("/{formation}")
    public DetalleResponse detalle(@PathVariable String formation, @ActorAutenticado UserId actor) {
        return DetalleResponse.from(consultar.detalle(actor, Formacion.desde(formation)));
    }

    @RequiresPermission(value = Permission.MANAGE_ROLES, scope = SCOPE)
    @GetMapping("/{formation}/versions/{number}")
    public VersionDeTerminosResponse version(@PathVariable String formation, @PathVariable int number, @ActorAutenticado UserId actor) {
        return VersionDeTerminosResponse.from(consultar.version(actor, Formacion.desde(formation), number));
    }

    @RequiresPermission(value = Permission.MANAGE_ROLES, scope = SCOPE)
    @GetMapping("/{formation}/versions/{number}/acceptances")
    public QuienesResponse quienes(@PathVariable String formation, @PathVariable int number, @ActorAutenticado UserId actor) {
        return QuienesResponse.from(consultar.quienes(actor, Formacion.desde(formation), number));
    }

    @RequiresPermission(value = Permission.MANAGE_ROLES, scope = SCOPE)
    @PutMapping("/{formation}/draft")
    public VersionDeTerminosResponse guardarBorrador(@PathVariable String formation, @ActorAutenticado UserId actor,
                                                     @RequestBody @Valid GuardarBorradorRequest request) {
        var contenido = new ContenidoDeTerminos(request.title(), request.introduction(), request.clauses(), request.checkboxText());
        return VersionDeTerminosResponse.from(gestionar.guardarBorrador(actor, Formacion.desde(formation), contenido, request.baseNumber()));
    }

    @RequiresPermission(value = Permission.MANAGE_ROLES, scope = SCOPE)
    @DeleteMapping("/{formation}/draft")
    public ResponseEntity<Void> descartarBorrador(@PathVariable String formation, @ActorAutenticado UserId actor) {
        gestionar.descartarBorrador(actor, Formacion.desde(formation));
        return ResponseEntity.noContent().build();
    }

    @RequiresPermission(value = Permission.MANAGE_ROLES, scope = SCOPE)
    @PostMapping("/{formation}/versions/{number}/restore")
    public VersionDeTerminosResponse restaurar(@PathVariable String formation, @PathVariable int number, @ActorAutenticado UserId actor) {
        return VersionDeTerminosResponse.from(gestionar.restaurarComoBorrador(actor, Formacion.desde(formation), number));
    }

    @RequiresPermission(value = Permission.MANAGE_ROLES, scope = SCOPE)
    @PostMapping("/{formation}/publish")
    public VersionDeTerminosResponse publicar(@PathVariable String formation, @ActorAutenticado UserId actor, @RequestBody @Valid PublicarRequest request) {
        return VersionDeTerminosResponse.from(
                gestionar.publicar(actor, Formacion.desde(formation), request.note(), request.asksNewAcceptance(), request.baseNumber()));
    }
}
