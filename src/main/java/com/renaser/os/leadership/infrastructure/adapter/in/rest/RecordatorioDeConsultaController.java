package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.application.ports.in.RecordarConsultaAlMentorUseCase;
import com.renaser.os.leadership.application.ports.in.RecordarConsultaAlMentorUseCase.RecordarConsultaCommand;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * {@code POST /api/v1/leadership/tickets/{ticketId}/recordar} (spec 056, FR-029): el líder le recuerda al mentor asignado una consulta sin responder, con
 * un mensaje en su chat y un aviso en su teléfono. 204 sin cuerpo; ya respondida, 409 {@code YA_RESPONDIDA}; sin mentor, 409 {@code SIN_MENTOR}; fuera de
 * sus formaciones, 403 {@code FUERA_DE_SU_ALCANCE}.
 *
 * <p>El permiso declarado no alcanza solo: MENTOR pasa el interceptor sin que se le mire nada (A-1) y MENTOR_LEAD está en modo sombra. Quien decide es
 * {@code AccesoDeLiderazgo}, como en el resto de la gestión del líder.
 */
@RestController
@RequestMapping("/api/v1/leadership/tickets")
@Funcionalidad("lider")
public class RecordatorioDeConsultaController {

    private static final String GUARD = "AccesoDeLiderazgo: MENTOR_LEAD/ADMIN/ALCHEMIST activo; el permiso no alcanza (A-1); "
            + "el aprendiz de la consulta es de una formación del líder y tiene mentor vigente";

    private final RecordarConsultaAlMentorUseCase recordar;

    public RecordatorioDeConsultaController(RecordarConsultaAlMentorUseCase recordar) {
        this.recordar = recordar;
    }

    @RequiresPermission(value = Permission.FOLLOW_UP_MENTOR, scope = GUARD)
    @PostMapping("/{ticketId}/recordar")
    public ResponseEntity<Void> recordarAlMentor(@ActorAutenticado UserId actorId, @PathVariable UUID ticketId,
                                                 @Valid @RequestBody RecordarConsultaRequest cuerpo) {
        recordar.recordar(new RecordarConsultaCommand(actorId, ticketId, cuerpo.operationKey()));
        return ResponseEntity.noContent().build();
    }
}
