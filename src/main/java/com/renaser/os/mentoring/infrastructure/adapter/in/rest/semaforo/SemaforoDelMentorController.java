package com.renaser.os.mentoring.infrastructure.adapter.in.rest.semaforo;

import com.renaser.os.mentoring.application.ports.in.ConsultarSemaforoDelAprendizUseCase;
import com.renaser.os.mentoring.application.ports.in.ConsultarSemaforoDelAprendizUseCase.ConsultaDetalleDelAprendiz;
import com.renaser.os.mentoring.application.ports.in.ConsultarSemaforoDelGrupoUseCase;
import com.renaser.os.mentoring.application.ports.in.ConsultarSemaforoDelGrupoUseCase.ConsultaTablaDelGrupo;
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
 * El semáforo visto por el mentor (docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md §4.1 y §4.3):
 * <ul>
 *   <li>{@code GET /api/v1/mentor/groups/{groupId}/semaforo?semanaHasta=YYYY-MM-DD} — la tabla del
 *       grupo, con nombres.</li>
 *   <li>{@code GET /api/v1/mentor/groups/{groupId}/learners/{userId}/semaforo?semanas=8} — el
 *       detalle de un aprendiz.</li>
 * </ul>
 *
 * <p>El permiso declarado ({@code USE_APP}) no protege nada acá: a MENTOR el interceptor lo deja
 * pasar sin mirar (A-1). Lo que protege es el guard del servicio: acompañar el grupo HOY, con la
 * cuenta activa, y para el detalle que el aprendiz sea de ese grupo.
 */
@RestController
@RequestMapping("/api/v1/mentor/groups/{groupId}")
public class SemaforoDelMentorController {

    private final ConsultarSemaforoDelGrupoUseCase consultarTabla;
    private final ConsultarSemaforoDelAprendizUseCase consultarDetalle;

    public SemaforoDelMentorController(ConsultarSemaforoDelGrupoUseCase consultarTabla,
                                       ConsultarSemaforoDelAprendizUseCase consultarDetalle) {
        this.consultarTabla = consultarTabla;
        this.consultarDetalle = consultarDetalle;
    }

    @RequiresPermission(value = Permission.USE_APP,
            scope = "AccesoAVistasDelSemaforo.requireAcompananteVigente: acompanante VIGENTE del grupo con cuenta activa")
    @GetMapping("/semaforo")
    public TablaDelSemaforoResponse tabla(@ActorAutenticado UserId actorId,
                                          @PathVariable UUID groupId,
                                          @RequestParam(required = false)
                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate semanaHasta) {
        // semanaHasta null = la ventana vigente; cual es "hoy" lo decide el servicio, en la zona de
        // cada aprendiz. Un dia que no es viernes lo rechaza la consulta (400).
        return TablaDelSemaforoResponse.from(consultarTabla.tablaDe(
                new ConsultaTablaDelGrupo(actorId, groupId, semanaHasta)));
    }

    @RequiresPermission(value = Permission.USE_APP,
            scope = "AccesoAVistasDelSemaforo: acompanante VIGENTE del grupo con cuenta activa Y el aprendiz debe pertenecer a ese grupo")
    @GetMapping("/learners/{userId}/semaforo")
    public DetalleDelSemaforoResponse detalle(@ActorAutenticado UserId actorId,
                                              @PathVariable UUID groupId,
                                              @PathVariable UUID userId,
                                              @RequestParam(defaultValue = "8") int semanas) {
        return DetalleDelSemaforoResponse.from(consultarDetalle.detalleDe(
                new ConsultaDetalleDelAprendiz(actorId, groupId, userId, semanas)));
    }
}
