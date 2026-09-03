package com.renaser.os.habits.infrastructure.adapter.in.rest.habito;

import com.renaser.os.habits.application.ports.in.habito.ConsultarMisHabitosUseCase;
import com.renaser.os.habits.application.ports.in.habito.CrearHabitoPersonalUseCase;
import com.renaser.os.habits.application.ports.in.habito.CrearHabitoPersonalUseCase.CrearHabitoPersonalCommand;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
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
public class MisHabitosController {

    private final ConsultarMisHabitosUseCase consultarUseCase;
    private final CrearHabitoPersonalUseCase crearUseCase;

    public MisHabitosController(ConsultarMisHabitosUseCase consultarUseCase,
                                 CrearHabitoPersonalUseCase crearUseCase) {
        this.consultarUseCase = consultarUseCase;
        this.crearUseCase = crearUseCase;
    }

    // Sin @RequiresPermission a proposito: MisHabitosService.consultar NO ejecuta ningun guard
    // (una cuenta SUSPENDED sigue leyendo su catalogo) — anotarlo afirmaria que algo lo hace
    // cumplir, y no es cierto. Ver EndpointAuthorizationDeclarationTest.HANDLERS_SIN_CLASIFICAR.
    @GetMapping
    public List<MiHabitoResponse> listar(@ActorAutenticado UserId actor) {
        return consultarUseCase.consultar(actor).stream().map(MiHabitoResponse::from).toList();
    }

    @RequiresPermission(Permission.USE_APP)
    @PostMapping
    public ResponseEntity<MiHabitoResponse> crear(@ActorAutenticado UserId actor,
                                                    @RequestBody @Valid CreatePersonalHabitRequest request) {
        var habito = crearUseCase.crear(new CrearHabitoPersonalCommand(actor, request.title(),
                request.habitType().toDomain(), request.category().toClave(), request.template(),
                request.goalLabel(), request.triggerTime(), request.limitTime()));
        return ResponseEntity.status(HttpStatus.CREATED).body(MiHabitoResponse.from(habito));
    }
}
