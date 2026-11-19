package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.application.ports.in.AvisarAlMentorUseCase;
import com.renaser.os.leadership.application.ports.in.AvisarAlMentorUseCase.AvisarAlMentorCommand;
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
 * {@code POST /api/v1/leadership/people/{personId}/avisar-al-mentor} (spec 056, FR-019): el líder le avisa al mentor vigente de una persona, con un mensaje en
 * su chat y un aviso en su teléfono. 204 sin cuerpo; sin mentor, 409 {@code SIN_MENTOR}; fuera de sus formaciones, 403 {@code FUERA_DE_SU_ALCANCE}.
 *
 * <p>El permiso declarado no alcanza solo: MENTOR pasa el interceptor sin que se le mire nada (A-1) y MENTOR_LEAD está en modo sombra. Quien decide es
 * {@code AccesoDeLiderazgo}, como en el resto de la gestión del líder.
 */
@RestController
@RequestMapping("/api/v1/leadership/people")
@Funcionalidad("lider")
public class AvisoAlMentorController {

    private static final String GUARD = "AccesoDeLiderazgo: MENTOR_LEAD/ADMIN/ALCHEMIST activo; el permiso no alcanza (A-1); "
            + "la persona es de una formación del líder y tiene mentor vigente";

    private final AvisarAlMentorUseCase avisar;

    public AvisoAlMentorController(AvisarAlMentorUseCase avisar) {
        this.avisar = avisar;
    }

    @RequiresPermission(value = Permission.FOLLOW_UP_MENTOR, scope = GUARD)
    @PostMapping("/{personId}/avisar-al-mentor")
    public ResponseEntity<Void> avisarAlMentor(@ActorAutenticado UserId actorId, @PathVariable UUID personId,
                                               @Valid @RequestBody AvisarAlMentorRequest cuerpo) {
        avisar.avisar(new AvisarAlMentorCommand(actorId, UserId.of(personId), cuerpo.texto(), cuerpo.operationKey()));
        return ResponseEntity.noContent().build();
    }
}
