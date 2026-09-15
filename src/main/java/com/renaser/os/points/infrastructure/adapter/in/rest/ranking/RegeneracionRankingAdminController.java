package com.renaser.os.points.infrastructure.adapter.in.rest.ranking;

import com.renaser.os.points.application.ports.in.ranking.RegenerarSnapshotsRankingUseCase;
import com.renaser.os.points.application.ports.in.ranking.RegenerarSnapshotsRankingUseCase.RegenerarSnapshotsCommand;
import com.renaser.os.points.application.ports.in.ranking.RegenerarSnapshotsRankingUseCase.ResultadoRegeneracion;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * {@code POST /api/v1/admin/ranking/snapshots?date=YYYY-MM-DD}
 *
 * <p>Rehace el corte del ranking sin esperar al cron de las 05:05 UTC. Existe porque ese cron
 * corre una vez al dia y solo si el backend esta arriba a esa hora: si estuvo caido, o si acaba
 * de cambiar como se calcula algo que el ranking ordena —la coherencia, D-128—, la tabla del dia
 * queda vieja y no hay forma de arreglarla hasta mañana.
 *
 * <p>Sin {@code date} regenera el corte de HOY, que es el caso normal. Se admite una fecha para
 * poder rehacer el corte de un dia que quedo mal, sin inventar uno nuevo.
 *
 * <p>El permiso declarado es {@link Permission#ADJUST_POINTS} —"solo ADMIN/ALCHEMIST activos"— y
 * se reusa a proposito en vez de agregar uno nuevo: es el mismo subsistema y el mismo operador
 * que el ajuste manual de puntos, y quien puede tener cada permiso es una decision del dueño, no
 * algo que se invente al escribir un endpoint. Quien lo hace cumplir de verdad es el servicio.
 */
@RestController
@RequestMapping("/api/v1/admin/ranking/snapshots")
public class RegeneracionRankingAdminController {

    private final RegenerarSnapshotsRankingUseCase regenerarUseCase;

    public RegeneracionRankingAdminController(RegenerarSnapshotsRankingUseCase regenerarUseCase) {
        this.regenerarUseCase = regenerarUseCase;
    }

    @RequiresPermission(value = Permission.ADJUST_POINTS,
            scope = "RegeneracionRankingService: solo ADMIN/ALCHEMIST activos")
    @PostMapping
    public ResultadoRegeneracion regenerar(@ActorAutenticado UserId actorId,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return regenerarUseCase.regenerar(new RegenerarSnapshotsCommand(actorId, date));
    }
}
