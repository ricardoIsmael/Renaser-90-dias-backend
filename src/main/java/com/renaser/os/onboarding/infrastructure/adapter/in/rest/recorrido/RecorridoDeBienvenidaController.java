package com.renaser.os.onboarding.infrastructure.adapter.in.rest.recorrido;

import com.renaser.os.onboarding.application.ports.in.recorrido.RecorridoDeBienvenidaUseCase;
import com.renaser.os.onboarding.application.ports.in.recorrido.RecorridoDeBienvenidaUseCase.ComoTermino;
import com.renaser.os.onboarding.application.ports.in.recorrido.RecorridoDeBienvenidaUseCase.VistaDelRecorrido;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import com.renaser.os.shared.web.security.SinInterruptor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Spec 045 (D-330): el recorrido de bienvenida de la propia persona. Solo la propia persona: no hay lectura ajena.
 *
 * <p>{@code GET /api/v1/me/recorrido} dice si la app lo muestra y en qué paso va; {@code PUT} guarda el paso;
 * {@code POST /terminar} lo termina ({@code PUBLICO}, {@code LISTO} o {@code SALTO}).
 */
@RestController
@RequestMapping("/api/v1/me/recorrido")
@Funcionalidad("onboarding")
public class RecorridoDeBienvenidaController {

    private final RecorridoDeBienvenidaUseCase recorrido;

    public RecorridoDeBienvenidaController(RecorridoDeBienvenidaUseCase recorrido) {
        this.recorrido = recorrido;
    }

    @RequiresPermission(Permission.USE_APP)
    @SinInterruptor(motivo = "Hoy lo lee al entrar para decidir si muestra el recorrido; sin respuesta la app no lo muestra")
    @GetMapping
    public RecorridoResponse leer(@ActorAutenticado UserId actor) {
        return RecorridoResponse.from(recorrido.de(actor));
    }

    @RequiresPermission(Permission.USE_APP)
    @PutMapping
    public RecorridoResponse avanzar(@ActorAutenticado UserId actor, @Valid @RequestBody AvanzarRequest request) {
        return RecorridoResponse.from(recorrido.avanzar(actor, request.paso()));
    }

    @RequiresPermission(Permission.USE_APP)
    @PostMapping("/terminar")
    public RecorridoResponse terminar(@ActorAutenticado UserId actor, @Valid @RequestBody TerminarRequest request) {
        return RecorridoResponse.from(recorrido.terminar(actor, request.como()));
    }

    public record AvanzarRequest(@NotNull @Min(1) @Max(5) Integer paso) {
    }

    public record TerminarRequest(@NotNull ComoTermino como) {
    }

    public record RecorridoResponse(boolean mostrar, int paso, Instant terminadoEn, String como) {
        static RecorridoResponse from(VistaDelRecorrido vista) {
            return new RecorridoResponse(vista.mostrar(), vista.paso(), vista.terminadoEn(),
                    vista.como() == null ? null : vista.como().name());
        }
    }
}
