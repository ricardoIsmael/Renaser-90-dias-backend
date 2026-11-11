package com.renaser.os.habits.infrastructure.adapter.in.rest.radar;

import com.renaser.os.habits.application.ports.in.radar.ConfigurarMiRadarSinRedUseCase;
import com.renaser.os.habits.application.ports.in.radar.ConfigurarMiRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.ConfigurarMiRadarUseCase.ConfigurarMiRadarCommand;
import com.renaser.os.habits.application.ports.in.radar.ConsultarHistorialRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.ConsultarMiRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.ConsultarUltimoRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.RegistrarCheckInRadarSinRedUseCase;
import com.renaser.os.habits.application.ports.in.radar.RegistrarCheckInRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.RegistrarCheckInRadarUseCase.RegistrarCheckInRadarCommand;
import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
 *
 * <p>Spec 012 (D-286) y 019 (D-292): `GET`/`PUT /api/v1/radar/mi-radar`, Mi Radar de la propia persona desde el dia 8
 * (encendido y, por dia de la semana, modo, veces y «Sin Radar»; las horas salen del Despertar y el Dormir). Mismo
 * permiso y mismo autoservicio estricto que el resto.
 */
@RestController
@RequestMapping("/api/v1/radar")
@Funcionalidad("radar")
public class RadarController {

    /** Era 20 (radar.ts:343); desde el dia 8 un dia puede tener hasta 21 registros (03-23) y Mi Radar arma «Hoy» con la primera pagina: 84 como el mentor y el lider. */
    private static final int TAMANO_PAGINA_HISTORIAL = 84;

    private final RegistrarCheckInRadarUseCase registrarUseCase;
    private final ConsultarUltimoRadarUseCase ultimoUseCase;
    private final ConsultarHistorialRadarUseCase historialUseCase;
    private final ConsultarMiRadarUseCase consultarMiRadarUseCase;
    private final ConfigurarMiRadarUseCase configurarMiRadarUseCase;
    private final RegistrarCheckInRadarSinRedUseCase registrarSinRedUseCase;
    private final ConfigurarMiRadarSinRedUseCase configurarMiRadarSinRedUseCase;

    public RadarController(RegistrarCheckInRadarUseCase registrarUseCase, ConsultarUltimoRadarUseCase ultimoUseCase,
                            ConsultarHistorialRadarUseCase historialUseCase,
                            ConsultarMiRadarUseCase consultarMiRadarUseCase,
                            ConfigurarMiRadarUseCase configurarMiRadarUseCase,
                            RegistrarCheckInRadarSinRedUseCase registrarSinRedUseCase,
                            ConfigurarMiRadarSinRedUseCase configurarMiRadarSinRedUseCase) {
        this.registrarSinRedUseCase = registrarSinRedUseCase;
        this.configurarMiRadarSinRedUseCase = configurarMiRadarSinRedUseCase;
        this.registrarUseCase = registrarUseCase;
        this.ultimoUseCase = ultimoUseCase;
        this.historialUseCase = historialUseCase;
        this.consultarMiRadarUseCase = consultarMiRadarUseCase;
        this.configurarMiRadarUseCase = configurarMiRadarUseCase;
    }

    @RequiresPermission(Permission.FOLLOW_OWN_PROGRAM)
    @PostMapping
    public RegistroRadarResponse registrar(@ActorAutenticado UserId actor, HechoSinRed sinRed,
                                            @RequestBody @Valid RegistrarCheckInRadarRequest request) {
        var comando = new RegistrarCheckInRadarCommand(actor, actor,
                request.whatAmIDoing(), request.whatAmIThinking(), request.whatAmIFeeling(), request.energyLevel(),
                request.whatAmIAvoiding(), request.fueDictado());
        // Spec 048 (FR-019): con X-Id-Cliente y X-Hecho-En es lo hecho sin red, con su hora real; sin ellas, lo de siempre.
        var registro = sinRed == null ? registrarUseCase.registrar(comando) : registrarSinRedUseCase.registrar(comando, sinRed);
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

    @RequiresPermission(Permission.FOLLOW_OWN_PROGRAM)
    @GetMapping("/mi-radar")
    public MiRadarResponse miRadar(@ActorAutenticado UserId actor) {
        return MiRadarResponse.from(consultarMiRadarUseCase.consultar(actor, actor));
    }

    @RequiresPermission(Permission.FOLLOW_OWN_PROGRAM)
    @PutMapping("/mi-radar")
    public MiRadarResponse configurarMiRadar(@ActorAutenticado UserId actor, HechoSinRed sinRed,
                                              @RequestBody @Valid GuardarMiRadarRequest request) {
        var comando = new ConfigurarMiRadarCommand(actor, actor, request.encendido(), request.cambios());
        return MiRadarResponse.from(sinRed == null ? configurarMiRadarUseCase.configurar(comando)
                : configurarMiRadarSinRedUseCase.configurar(comando, sinRed));
    }
}
