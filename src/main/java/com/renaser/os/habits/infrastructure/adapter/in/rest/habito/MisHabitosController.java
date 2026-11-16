package com.renaser.os.habits.infrastructure.adapter.in.rest.habito;

import com.renaser.os.habits.application.ports.in.habito.ConsultarMisHabitosUseCase;
import com.renaser.os.habits.domain.model.categoria.CodigoDeCategoria;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.application.ports.in.habito.CrearHabitoPersonalUseCase;
import com.renaser.os.habits.application.ports.in.habito.HabitoPersonalSinRedUseCase;
import com.renaser.os.habits.application.ports.in.habito.CrearHabitoPersonalUseCase.CrearHabitoPersonalCommand;
import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Autoservicio: catalogo SISTEMA activo + habitos PERSONAL propios del actor, sin filtrar por
 * dia — a diferencia de {@code GET /habit-tracks/today}, que solo trae los tracks de hoy. El
 * {@code POST} (§3, docs/informes/habits-eleccion-y-personales.md) da de alta un habito PROPIO.
 */
@RestController
@RequestMapping("/api/v1/habits")
@Funcionalidad("habitos")
public class MisHabitosController {

    private final ConsultarMisHabitosUseCase consultarUseCase;
    private final CrearHabitoPersonalUseCase crearUseCase;
    private final HabitoPersonalSinRedUseCase sinRedUseCase;

    public MisHabitosController(ConsultarMisHabitosUseCase consultarUseCase,
                                 CrearHabitoPersonalUseCase crearUseCase, HabitoPersonalSinRedUseCase sinRedUseCase) {
        this.sinRedUseCase = sinRedUseCase;
        this.consultarUseCase = consultarUseCase;
        this.crearUseCase = crearUseCase;
    }

    // Sin @RequiresPermission a proposito: MisHabitosService.consultar NO ejecuta ningun guard
    // (una cuenta SUSPENDED sigue leyendo su catalogo) — anotarlo afirmaria que algo lo hace
    // cumplir, y no es cierto. Ver EndpointAuthorizationDeclarationTest.HANDLERS_SIN_CLASIFICAR.
    @GetMapping
    public List<MiHabitoResponse> listar(@ActorAutenticado UserId actor) {
        return consultarUseCase.consultarConRachas(actor).stream().map(MiHabitoResponse::from).toList();
    }

    @RequiresPermission(Permission.USE_APP)
    @PostMapping
    public ResponseEntity<MiHabitoResponse> crear(@ActorAutenticado UserId actor, HechoSinRed sinRed,
                                                    @RequestBody @Valid CreatePersonalHabitRequest request) {
        var comando = new CrearHabitoPersonalCommand(actor, request.title(), request.habitType().toDomain(),
                CodigoDeCategoria.aClave(request.category()), request.template(), request.goalLabel(), request.iconKey(),
                request.triggerTime(), request.limitTime(), request.activeWeekdays());
        // Spec 048 (FR-019): con X-Id-Cliente y X-Hecho-En es idempotente por envío; sin ellas, lo de siempre.
        var habito = sinRed == null ? crearUseCase.crear(comando) : sinRedUseCase.crear(comando, sinRed);
        // El horario del catalogo sigue siendo TODOS; los dias que corre de verdad son los que
        // eligio el aprendiz, y el comando ya resolvio el default de "todos" cuando no manda nada.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(MiHabitoResponse.from(habito, comando.diasActivos()));
    }
}
