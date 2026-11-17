package com.renaser.os.mentoring.infrastructure.adapter.in.rest;

import com.renaser.os.mentoring.application.ports.in.ConsultarDiasDelAprendizUseCase;
import com.renaser.os.mentoring.application.ports.in.ConsultarDiasDelAprendizUseCase.ConsultaDiasDelAprendiz;
import com.renaser.os.mentoring.application.ports.in.ConsultarDiasDelAprendizUseCase.DiasDelAprendiz;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code GET /api/v1/mentor/groups/{groupId}/learners/{userId}/dias?desde=&hasta=} (spec 055): el color y el porcentaje de cada
 * día del aprendiz, para los círculos de la semana y el mes de su ficha. Máximo 62 días; se recorta a [Día 1, hoy].
 *
 * <p>El permiso declarado ({@code USE_APP}) no protege nada: MENTOR pasa el interceptor sin que se le mire nada (A-1). Lo que
 * protege es el guard del servicio: acompañar el grupo HOY con la cuenta activa y que el aprendiz sea de ese grupo.
 */
@RestController
@RequestMapping("/api/v1/mentor/groups/{groupId}/learners/{userId}/dias")
@Funcionalidad("mentor.mi_grupo")
public class DiasDelAprendizController {

    private final ConsultarDiasDelAprendizUseCase consultarDias;

    public DiasDelAprendizController(ConsultarDiasDelAprendizUseCase consultarDias) {
        this.consultarDias = consultarDias;
    }

    @RequiresPermission(value = Permission.USE_APP,
            scope = "AccesoAVistasDelSemaforo: acompanante VIGENTE del grupo con cuenta activa Y el aprendiz del grupo")
    @GetMapping
    public ResponseEntity<DiasDelAprendizResponse> dias(@ActorAutenticado UserId actorId,
                                                        @PathVariable UUID groupId,
                                                        @PathVariable UUID userId,
                                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        DiasDelAprendiz dias = consultarDias.diasDe(new ConsultaDiasDelAprendiz(actorId, groupId, userId, desde, hasta));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(DiasDelAprendizResponse.from(dias));
    }

    /** @param dias por fecha: {@code estado} MEDIDO, SIN_DATOS, PAUSADO, CUENTA_SUSPENDIDA o PENDIENTE; el color y el porcentaje solo si se midió */
    public record DiasDelAprendizResponse(String zona, LocalDate diaUno, List<DiaResponse> dias) {

        static DiasDelAprendizResponse from(DiasDelAprendiz dias) {
            return new DiasDelAprendizResponse(dias.zona(), dias.diaUno(),
                    dias.dias().stream().map(DiaResponse::from).toList());
        }
    }

    public record DiaResponse(LocalDate fecha, String estado, String color, Integer porcentaje) {

        static DiaResponse from(DiaDelSemaforo dia) {
            return new DiaResponse(dia.fecha(), dia.estado().name(), dia.color().name(), dia.porcentaje());
        }
    }
}
