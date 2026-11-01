package com.renaser.os.onboarding.infrastructure.adapter.in.rest.terminos;

import com.renaser.os.onboarding.application.ports.in.terminos.ObtenerTerminosUseCase;
import com.renaser.os.onboarding.application.ports.in.terminos.ObtenerTerminosUseCase.TerminosAceptados;
import com.renaser.os.onboarding.application.ports.in.terminos.ObtenerTerminosUseCase.TerminosParaLaPersona;
import com.renaser.os.onboarding.domain.model.terminos.QuePideLaAceptacion;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import com.renaser.os.shared.web.security.SinInterruptor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Los términos y condiciones que le tocan a la persona (spec 027, FR-010, FR-022, FR-023): los vigentes de su formación y lo que debe hacer con ellos. La
 * aceptación en sí sigue siendo el hito de Términos de {@code POST /onboarding/milestones}, que ahora puede decir qué versión leyó.
 */
@RestController
@RequestMapping("/api/v1/onboarding/terms")
@Funcionalidad("onboarding")
public class TerminosController {

    private final ObtenerTerminosUseCase terminos;

    public TerminosController(ObtenerTerminosUseCase terminos) {
        this.terminos = terminos;
    }

    /** @param pending {@code NONE | FIRST_TIME | NEW_VERSION | FIRST_IN_FORMATION}; {@code blocks}: la app no deja usar el resto hasta aceptar */
    public record TerminosDeMiFormacionResponse(Formation formation, VersionDeTerminosResponse version, String pending, boolean blocks,
                                                Integer lastAcceptedNumber, String changeNote) {

        public record Formation(String code, String name) {
        }

        static TerminosDeMiFormacionResponse from(TerminosParaLaPersona t) {
            return new TerminosDeMiFormacionResponse(new Formation(t.formacion().codigo(), t.nombreDeLaFormacion()),
                    t.vigente().map(VersionDeTerminosResponse::from).orElse(null), pendiente(t.pide()), t.pide().bloquea(),
                    t.ultimaAceptada().orElse(null), t.nota().orElse(null));
        }

        private static String pendiente(QuePideLaAceptacion pide) {
            return pide == QuePideLaAceptacion.NADA ? "NONE" : pide.name();
        }
    }

    public record TerminosAceptadosResponse(VersionDeTerminosResponse version, Instant acceptedAt) {

        static TerminosAceptadosResponse from(TerminosAceptados t) {
            return new TerminosAceptadosResponse(VersionDeTerminosResponse.from(t.version()), t.aceptadaEn());
        }
    }

    /** Sin interruptor: la app lo lee al abrir para saber si hay que aceptar unos términos antes de dejar entrar; apagar el onboarding no puede dejar a nadie afuera. */
    @RequiresPermission(Permission.USE_APP)
    @SinInterruptor(motivo = "La app lo lee al abrir para decidir si hay términos que aceptar: apagar el onboarding no puede dejar a nadie sin entrar")
    @GetMapping
    public TerminosDeMiFormacionResponse mios(@ActorAutenticado UserId actor) {
        return TerminosDeMiFormacionResponse.from(terminos.para(actor));
    }

    /** El texto de la versión que la persona aceptó (FR-023), aunque haya una más nueva. 404 si nunca aceptó. */
    @RequiresPermission(Permission.USE_APP)
    @GetMapping("/accepted")
    public ResponseEntity<TerminosAceptadosResponse> aceptados(@ActorAutenticado UserId actor) {
        return terminos.aceptados(actor).map(TerminosAceptadosResponse::from).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
