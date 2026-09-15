package com.renaser.os.habits.infrastructure.adapter.in.rest.acompanamiento;

import com.renaser.os.habits.application.ports.in.acompanamiento.ConsultaDeAcompanante;
import com.renaser.os.habits.application.ports.in.acompanamiento.ConsultarCodigoRenaserDeAlumnoUseCase;
import com.renaser.os.habits.infrastructure.adapter.in.rest.radar.RadarHistoryPageResponse;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code GET /api/v1/mentor/groups/{groupId}/learners/{userId}/radar?cursor=<instante>}
 *
 * <p>El Codigo Renaser del alumno —las cuatro preguntas por franja horaria de los dias 1 al 7—
 * para quien lo acompaña. Misma respuesta que {@code GET /api/v1/radar/history}, el autoservicio.
 *
 * <p><b>Es lectura de texto intimo</b> ("que evito", "que siento"): hasta el 2026-09-15 solo la
 * propia persona podia leerlo, ni siquiera un admin. Se abre por pedido del dueño del proyecto y
 * por la puerta mas angosta que existe — el acompañante VIGENTE del grupo al que el alumno
 * pertenece hoy. No hay escritura ni borrado: el registro sigue siendo append-only del participante.
 */
@RestController
@RequestMapping("/api/v1/mentor/groups/{groupId}/learners/{userId}/radar")
public class CodigoRenaserDelAlumnoController {

    /**
     * Doce franjas por dia (08:00 a 19:00) durante los dias 1 al 7 = 84 registros, el Codigo
     * Renaser entero. Se pagina igual que el autoservicio, pero con la pagina del tamaño del
     * total: quien acompaña lo lee de una vez, no scrolleando de a 20. Si alguna vez hubiera mas,
     * `nextCursor` sigue estando y la app ya lo maneja.
     */
    private static final int TAMANO_PAGINA = 84;

    private final ConsultarCodigoRenaserDeAlumnoUseCase consultarCodigoRenaser;

    public CodigoRenaserDelAlumnoController(ConsultarCodigoRenaserDeAlumnoUseCase consultarCodigoRenaser) {
        this.consultarCodigoRenaser = consultarCodigoRenaser;
    }

    @RequiresPermission(value = Permission.USE_APP,
            scope = "AcompanamientoDeAlumnoService: acompanante vigente del grupo Y el alumno debe pertenecer a ese grupo")
    @GetMapping
    public RadarHistoryPageResponse codigoRenaser(@ActorAutenticado UserId actorId,
                                                   @PathVariable UUID groupId,
                                                   @PathVariable UUID userId,
                                                   @RequestParam(required = false) Instant cursor) {
        var consulta = new ConsultaDeAcompanante(actorId, groupId, UserId.of(userId));
        return RadarHistoryPageResponse.from(
                consultarCodigoRenaser.codigoRenaserDeAlumno(consulta, cursor, TAMANO_PAGINA));
    }
}
