package com.renaser.os.users.infrastructure.adapter.in.rest.admin;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.participante.GetTraineeDetailUseCase;
import com.renaser.os.users.application.ports.in.participante.GetTraineeDetailUseCase.GetTraineeDetailCommand;
import com.renaser.os.users.application.ports.in.participante.ListTraineesUseCase;
import com.renaser.os.users.application.ports.in.participante.ListTraineesUseCase.ListTraineesCommand;
import com.renaser.os.users.application.ports.in.participante.SetTraineeProgramDayUseCase;
import com.renaser.os.users.application.ports.in.participante.SetTraineeProgramDayUseCase.SetProgramDayCommand;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Panel admin de aprendices (gap #7 de docs/PLAN_INTEGRACION_FRONTEND.md): listar,
 * detalle, editar dia de programa. Solo ADMIN/ALCHEMIST — gate DENTRO del servicio
 * (CLAUDE.MD §5.4.6). X-Actor-Id: header TEMPORAL, ver nota de AccountRequestController.
 */
@RestController
@RequestMapping("/api/v1/admin/trainees")
public class TraineeAdminController {

    private final ListTraineesUseCase listTraineesUseCase;
    private final GetTraineeDetailUseCase getTraineeDetailUseCase;
    private final SetTraineeProgramDayUseCase setTraineeProgramDayUseCase;

    public TraineeAdminController(ListTraineesUseCase listTraineesUseCase,
                                   GetTraineeDetailUseCase getTraineeDetailUseCase,
                                   SetTraineeProgramDayUseCase setTraineeProgramDayUseCase) {
        this.listTraineesUseCase = listTraineesUseCase;
        this.getTraineeDetailUseCase = getTraineeDetailUseCase;
        this.setTraineeProgramDayUseCase = setTraineeProgramDayUseCase;
    }

    @GetMapping
    public TraineePageResponse listar(@RequestHeader("X-Actor-Id") String actorId,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        var pagina = listTraineesUseCase.listar(new ListTraineesCommand(UserId.of(actorId), page, size));
        return TraineePageResponse.from(pagina);
    }

    @GetMapping("/{id}")
    public TraineeDetailResponse detalle(@PathVariable UUID id, @RequestHeader("X-Actor-Id") String actorId) {
        var detalle = getTraineeDetailUseCase.obtener(new GetTraineeDetailCommand(UserId.of(actorId), UserId.of(id)));
        return TraineeDetailResponse.from(detalle);
    }

    @PutMapping("/{id}/program-day")
    public ResponseEntity<Void> setProgramDay(@PathVariable UUID id, @RequestHeader("X-Actor-Id") String actorId,
                                               @RequestBody @Valid SetProgramDayRequest request) {
        setTraineeProgramDayUseCase.fijarDia(new SetProgramDayCommand(UserId.of(actorId), UserId.of(id),
                request.programDay()));
        return ResponseEntity.noContent().build();
    }
}
