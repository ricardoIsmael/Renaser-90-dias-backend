package com.renaser.os.community.infrastructure.adapter.in.rest.cohorte;

import com.renaser.os.community.application.ports.in.acompanamiento.ConfigurarMentoriaUseCase;
import com.renaser.os.community.application.ports.in.acompanamiento.ConfigurarMentoriaUseCase.ReconfigurarPolitica;
import com.renaser.os.community.application.ports.in.acompanamiento.ConfigurarMentoriaUseCase.ReemplazarGuias;
import com.renaser.os.community.application.ports.in.acompanamiento.ConfigurarMentoriaUseCase.ReferenciaDeUsuario;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.community.infrastructure.adapter.in.rest.cohorte.MentoringPolicyResponse.ReceptionGuidesResponse;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Configuración operativa de una cohorte: cupo, cadencia, zona, umbral de aviso y guías de
 * recepción.
 *
 * <p>Exige {@code MANAGE_COHORTS}, el mismo permiso que ya gobierna el resto de la administración
 * de cohortes. Un mentor común no configura nada acá: acompañar no es administrar, y
 * MENTOR_LEAD tampoco pasa a serlo por su nombre (contracts.md).
 */
@RestController
@RequestMapping("/api/v1/admin/cohorts/{cohortId}")
public class MentoringPolicyController {

    private final ConfigurarMentoriaUseCase configurarMentoria;

    public MentoringPolicyController(ConfigurarMentoriaUseCase configurarMentoria) {
        this.configurarMentoria = configurarMentoria;
    }

    @RequiresPermission(Permission.MANAGE_COHORTS)
    @GetMapping("/mentoring-policy")
    public MentoringPolicyResponse consultar(@ActorAutenticado UserId actorId, @PathVariable UUID cohortId) {
        return MentoringPolicyResponse.from(configurarMentoria.consultar(actorId, CohorteId.of(cohortId)));
    }

    @RequiresPermission(Permission.MANAGE_COHORTS)
    @PatchMapping("/mentoring-policy")
    public MentoringPolicyResponse reconfigurar(@ActorAutenticado UserId actorId, @PathVariable UUID cohortId,
                                                 @Valid @RequestBody MentoringPolicyRequest cuerpo) {
        return MentoringPolicyResponse.from(configurarMentoria.reconfigurar(new ReconfigurarPolitica(
                actorId, CohorteId.of(cohortId), cuerpo.capacity(), cuerpo.rotation(), cuerpo.timezone(),
                cuerpo.transferDay(), cuerpo.inactivityDays(), cuerpo.expectedVersion())));
    }

    /** Reemplazo atómico: la lista que llega es la que queda. */
    @RequiresPermission(Permission.MANAGE_COHORTS)
    @PutMapping("/reception/guides")
    public ReceptionGuidesResponse reemplazarGuias(@ActorAutenticado UserId actorId, @PathVariable UUID cohortId,
                                                    @Valid @RequestBody ReceptionGuidesRequest cuerpo) {
        return ReceptionGuidesResponse.from(configurarMentoria.reemplazarGuias(new ReemplazarGuias(
                actorId, CohorteId.of(cohortId), cuerpo.receptionCellId(),
                cuerpo.guides().stream().map(g -> new ReferenciaDeUsuario(g.userId(), g.email())).toList())));
    }
}
