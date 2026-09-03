package com.renaser.os.habits.infrastructure.adapter.in.rest.radar;

import com.renaser.os.habits.application.ports.in.radar.ConsultarHistorialRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.ConsultarUltimoRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.RegistrarCheckInRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.RegistrarCheckInRadarUseCase.RegistrarCheckInRadarCommand;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Codigo Renaser. Actor resuelto desde la sesion, con respaldo por el header
 * temporal `X-Actor-Id` (D-29 de `users`, mismo patron que el resto de
 * `habits`). Autoservicio estricto: el
 * participante solo opera su propio Codigo Renaser — no hay parametro de URL
 * para pedir el de otro (D-41, docs/MODULO_HABITS.md §radar).
 *
 * <p>Rutas nuevas (D-36: la app hoy escribe directo a Supabase, no consume
 * ningun endpoint HTTP propio de esto todavia): `POST /api/v1/radar` calca la
 * ruta del contrato viejo (RC-01); `/latest` y `/history` son nuevas, una por
 * cada `select` que el cliente actual hace contra `radar_entries`
 * (radar.ts:136-158 y :357-390) — no existe un endpoint equivalente en el
 * contrato viejo (ese exponia `/today`, que el cliente actual no usa).
 */
@RestController
@RequestMapping("/api/v1/radar")
public class RadarController {

    /** radar.ts:343 (`RADAR_HISTORY_PAGE_SIZE`). */
    private static final int TAMANO_PAGINA_HISTORIAL = 20;

    private final RegistrarCheckInRadarUseCase registrarUseCase;
    private final ConsultarUltimoRadarUseCase ultimoUseCase;
    private final ConsultarHistorialRadarUseCase historialUseCase;

    public RadarController(RegistrarCheckInRadarUseCase registrarUseCase, ConsultarUltimoRadarUseCase ultimoUseCase,
                            ConsultarHistorialRadarUseCase historialUseCase) {
        this.registrarUseCase = registrarUseCase;
        this.ultimoUseCase = ultimoUseCase;
        this.historialUseCase = historialUseCase;
    }

    @RequiresPermission(Permission.FOLLOW_OWN_PROGRAM)
    @PostMapping
    public RegistroRadarResponse registrar(@ActorAutenticado UserId actor,
                                            @RequestBody @Valid RegistrarCheckInRadarRequest request) {
        var registro = registrarUseCase.registrar(new RegistrarCheckInRadarCommand(actor, actor,
                request.whatAmIDoing(), request.whatAmIThinking(), request.whatAmIFeeling(), request.energyLevel(),
                request.whatAmIAvoiding()));
        return RegistroRadarResponse.from(registro);
    }

    @RequiresPermission(Permission.FOLLOW_OWN_PROGRAM)
    @GetMapping("/latest")
    public UltimoRadarResponse ultimo(@ActorAutenticado UserId actor) {
        return ultimoUseCase.ultimo(actor, actor)
                .map(r -> new UltimoRadarResponse(r.creadoEn()))
                .orElse(new UltimoRadarResponse(null));
    }

    @RequiresPermission(Permission.FOLLOW_OWN_PROGRAM)
    @GetMapping("/history")
    public RadarHistoryPageResponse historial(@ActorAutenticado UserId actor,
                                               @RequestParam(required = false) Instant cursor) {
        var pagina = historialUseCase.historial(actor, actor, cursor, TAMANO_PAGINA_HISTORIAL);
        return RadarHistoryPageResponse.from(pagina);
    }
}
