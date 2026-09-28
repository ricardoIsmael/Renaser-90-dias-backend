package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocadiaria;

import com.renaser.os.rocks.application.ports.in.rocadiaria.ConsultarRocasAgendadasUseCase;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las acciones agendadas de hoy hasta el domingo (D-217), para que la app arme sus alarmas. Controller
 * aparte de {@code RocaDiariaController} para no sumarle otro caso de uso al constructor; la ruta cuelga
 * del mismo {@code /api/v1/rocks}.
 */
@RestController
@RequestMapping("/api/v1/rocks")
public class RocasAgendadasController {

    private final ConsultarRocasAgendadasUseCase agendadasUseCase;

    public RocasAgendadasController(ConsultarRocasAgendadasUseCase agendadasUseCase) {
        this.agendadasUseCase = agendadasUseCase;
    }

    @RequiresPermission(Permission.FOLLOW_OWN_PROGRAM)
    @GetMapping("/upcoming")
    public RocasAgendadasResponse agendadas(@ActorAutenticado UserId actor) {
        return RocasAgendadasResponse.from(agendadasUseCase.agendadas(actor));
    }
}
