package com.renaser.os.habits.infrastructure.adapter.in.rest.habitoadmin;

import com.renaser.os.habits.application.ports.in.medicion.MetaDeMedicionAdminUseCases;
import com.renaser.os.habits.application.ports.in.medicion.MetaDeMedicionAdminUseCases.FijarMetaCommand;
import com.renaser.os.habits.application.ports.in.medicion.MetaDeMedicionAdminUseCases.MetaDeMedicion;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Spec 037: la meta diaria del hábito de kilómetros en «Hábitos del programa». Solo ADMIN/ALQUIMISTA
 * ({@code MANAGE_HABIT_CATALOG}; el servicio vuelve a exigirlo). Un hábito que no mide nada responde 404.
 */
@RestController
@RequestMapping("/api/v1/admin/habits/{id}/measurement-goal")
@Funcionalidad("admin.habitos")
public class MetaDeMedicionAdminController {

    private final MetaDeMedicionAdminUseCases useCases;

    public MetaDeMedicionAdminController(MetaDeMedicionAdminUseCases useCases) {
        this.useCases = useCases;
    }

    @RequiresPermission(Permission.MANAGE_HABIT_CATALOG)
    @GetMapping
    public MetaDeMedicionResponse consultar(@ActorAutenticado UserId actor, @PathVariable UUID id) {
        return MetaDeMedicionResponse.from(useCases.consultar(actor, HabitoId.of(id)));
    }

    @RequiresPermission(Permission.MANAGE_HABIT_CATALOG)
    @PutMapping
    public MetaDeMedicionResponse fijar(@ActorAutenticado UserId actor, @PathVariable UUID id,
                                         @RequestBody @Valid FijarMetaRequest request) {
        return MetaDeMedicionResponse.from(useCases.fijar(new FijarMetaCommand(actor, HabitoId.of(id), request.meta())));
    }

    /** {@code meta} nula vuelve a la del programa. El rango lo valida el servicio con un mensaje que se lee. */
    public record FijarMetaRequest(BigDecimal meta) {
    }

    public record MetaDeMedicionResponse(String unidad, BigDecimal meta, BigDecimal porDefecto, BigDecimal minimo,
                                         BigDecimal maximo, boolean esLaPorDefecto) {

        static MetaDeMedicionResponse from(MetaDeMedicion vista) {
            return new MetaDeMedicionResponse(vista.unidad().name(), vista.meta(), vista.porDefecto(), vista.minimo(),
                    vista.maximo(), vista.esLaPorDefecto());
        }
    }
}
