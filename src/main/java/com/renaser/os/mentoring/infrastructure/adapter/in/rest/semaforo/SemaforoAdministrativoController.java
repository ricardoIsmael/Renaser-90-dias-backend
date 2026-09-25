package com.renaser.os.mentoring.infrastructure.adapter.in.rest.semaforo;

import com.renaser.os.mentoring.application.ports.in.ConsultarSemaforoDelAprendizAdministrativoUseCase;
import com.renaser.os.mentoring.application.ports.in.ConsultarSemaforoDelAprendizAdministrativoUseCase.ConsultaDetalleAdministrativa;
import com.renaser.os.mentoring.application.ports.in.ConsultarSemaforoDelGrupoAdministrativoUseCase;
import com.renaser.os.mentoring.application.ports.in.ConsultarSemaforoDelGrupoAdministrativoUseCase.ConsultaTablaAdministrativa;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * El semáforo para administración (docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md §4.1 y §4.3):
 * <ul>
 *   <li>{@code GET /api/v1/admin/semaforo/groups/{groupId}?semanaHasta=YYYY-MM-DD} — la misma tabla
 *       del mentor, con nombres, de cualquier grupo.</li>
 *   <li>{@code GET /api/v1/admin/trainees/{traineeId}/semaforo?semanas=8} — el detalle de cualquier
 *       persona; cuelga de la misma ficha que {@code /admin/trainees/{id}/weekly-progress}.</li>
 * </ul>
 *
 * <p>Mismo contenido que {@link SemaforoDelMentorController}, otra puerta y otra autorización: acá
 * es de rol (ADMIN/ALCHEMIST activo) y no exige acompañar a nadie. El permiso {@code MANAGE_TRAINEES}
 * frena a TRAINEE en el interceptor, pero no a MENTOR ni —mientras siga en modo sombra— a
 * MENTOR_LEAD: los frena el guard del servicio.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class SemaforoAdministrativoController {

    private final ConsultarSemaforoDelGrupoAdministrativoUseCase consultarTabla;
    private final ConsultarSemaforoDelAprendizAdministrativoUseCase consultarDetalle;

    public SemaforoAdministrativoController(ConsultarSemaforoDelGrupoAdministrativoUseCase consultarTabla,
                                            ConsultarSemaforoDelAprendizAdministrativoUseCase consultarDetalle) {
        this.consultarTabla = consultarTabla;
        this.consultarDetalle = consultarDetalle;
    }

    @RequiresPermission(value = Permission.MANAGE_TRAINEES,
            scope = "AccesoAVistasDelSemaforo.requireAdminActivo: ADMIN/ALCHEMIST activo, sin exigir relacion con el grupo")
    @GetMapping("/semaforo/groups/{groupId}")
    public TablaDelSemaforoResponse tabla(@ActorAutenticado UserId actorId,
                                          @PathVariable UUID groupId,
                                          @RequestParam(required = false)
                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate semanaHasta) {
        return TablaDelSemaforoResponse.from(consultarTabla.tablaDe(
                new ConsultaTablaAdministrativa(actorId, groupId, semanaHasta)));
    }

    @RequiresPermission(value = Permission.MANAGE_TRAINEES,
            scope = "AccesoAVistasDelSemaforo.requireAdminActivo: ADMIN/ALCHEMIST activo, sin exigir relacion con la persona")
    @GetMapping("/trainees/{traineeId}/semaforo")
    public DetalleDelSemaforoResponse detalle(@ActorAutenticado UserId actorId,
                                              @PathVariable UUID traineeId,
                                              @RequestParam(defaultValue = "8") int semanas) {
        return DetalleDelSemaforoResponse.from(consultarDetalle.detalleDe(
                new ConsultaDetalleAdministrativa(actorId, traineeId, semanas)));
    }
}
