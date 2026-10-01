package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.application.ports.in.ConsultarFichaDeMentorUseCase;
import com.renaser.os.leadership.application.ports.in.ConsultarObservacionesUseCase;
import com.renaser.os.leadership.application.ports.in.ConsultarPadronDeMentoresUseCase;
import com.renaser.os.leadership.application.ports.in.RegistrarObservacionUseCase;
import com.renaser.os.leadership.application.ports.in.RegistrarObservacionUseCase.RegistrarObservacionCommand;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * La gestión del cuerpo de mentores por el Líder de Mentores (SDD 002; D-241): padrón, ficha y
 * observaciones.
 *
 * <p>El permiso declarado no alcanza solo: MENTOR pasa el interceptor sin que se le mire nada (A-1) y
 * MENTOR_LEAD está en modo sombra. Quien decide es {@code AccesoDeLiderazgo}: MENTOR_LEAD, ADMIN o
 * ALCHEMIST con la cuenta activa.
 */
@RestController
@RequestMapping("/api/v1/leadership/mentors")
public class LeadershipMentorsController {

    private static final String GUARD = "AccesoDeLiderazgo: MENTOR_LEAD/ADMIN/ALCHEMIST activo; el permiso no alcanza (A-1)";

    private final ConsultarPadronDeMentoresUseCase padron;
    private final ConsultarFichaDeMentorUseCase ficha;
    private final ConsultarObservacionesUseCase observaciones;
    private final RegistrarObservacionUseCase registrar;

    public LeadershipMentorsController(ConsultarPadronDeMentoresUseCase padron, ConsultarFichaDeMentorUseCase ficha,
                                       ConsultarObservacionesUseCase observaciones,
                                       RegistrarObservacionUseCase registrar) {
        this.padron = padron;
        this.ficha = ficha;
        this.observaciones = observaciones;
        this.registrar = registrar;
    }

    @RequiresPermission(value = Permission.VIEW_MENTOR_CORPS, scope = GUARD)
    @GetMapping
    public MentorRosterResponse padron(@ActorAutenticado UserId actorId) {
        return MentorRosterResponse.from(padron.padron(actorId));
    }

    @RequiresPermission(value = Permission.VIEW_MENTOR_CORPS, scope = GUARD)
    @GetMapping("/{mentorId}")
    public MentorDetailResponse ficha(@ActorAutenticado UserId actorId, @PathVariable UUID mentorId) {
        return MentorDetailResponse.from(ficha.ficha(actorId, UserId.of(mentorId)));
    }

    @RequiresPermission(value = Permission.VIEW_MENTOR_CORPS, scope = GUARD)
    @GetMapping("/{mentorId}/observations")
    public ObservationPageResponse observaciones(@ActorAutenticado UserId actorId, @PathVariable UUID mentorId,
                                                 @RequestParam(required = false) Instant before) {
        return ObservationPageResponse.from(observaciones.observaciones(actorId, UserId.of(mentorId), before));
    }

    @RequiresPermission(value = Permission.FOLLOW_UP_MENTOR, scope = GUARD + "; el destinatario tiene que ser MENTOR")
    @PostMapping("/{mentorId}/observations")
    @ResponseStatus(HttpStatus.CREATED)
    public ObservationResponse registrar(@ActorAutenticado UserId actorId, @PathVariable UUID mentorId,
                                         @Valid @RequestBody RegisterObservationRequest cuerpo) {
        return ObservationResponse.from(registrar.registrar(new RegistrarObservacionCommand(actorId,
                UserId.of(mentorId), cuerpo.type(), cuerpo.text(), cuerpo.sentByChat(), cuerpo.messageId(),
                cuerpo.operationKey())));
    }
}
