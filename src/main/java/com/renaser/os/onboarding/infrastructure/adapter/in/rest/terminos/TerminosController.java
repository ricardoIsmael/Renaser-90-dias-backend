package com.renaser.os.onboarding.infrastructure.adapter.in.rest.terminos;

import com.renaser.os.onboarding.application.ports.in.terminos.ObtenerTerminosUseCase;
import com.renaser.os.onboarding.application.ports.in.terminos.ObtenerTerminosUseCase.AcuerdoAceptado;
import com.renaser.os.onboarding.application.ports.in.terminos.ObtenerTerminosUseCase.AcuerdoDeLaPersona;
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
import java.time.LocalDate;

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
                                                Integer lastAcceptedNumber, String changeNote, Agreement agreement) {

        public record Formation(String code, String name) {
        }

        /**
         * «Mi aceptación» del Acuerdo de transformación (spec 039), llena por el sistema: el nombre completo del perfil, el programa («Formación RENASER · 90 días») y sus
         * fechas ({@code null} si no hay un programa que prometerle). Nunca lleva el cronograma de pagos (D-315, decisión del dueño del 2026-11-02): no se muestra ni se
         * firma con los términos; la persona lo ve y lo confirma en «Mi ingreso» (spec 032).
         */
        public record Agreement(String participantName, String program, LocalDate startDate, LocalDate endDate, boolean started) {

            static Agreement from(AcuerdoDeLaPersona a) {
                return new Agreement(a.participante(), a.programa(), a.inicio(), a.fin(), a.empezado());
            }
        }

        static TerminosDeMiFormacionResponse from(TerminosParaLaPersona t) {
            return new TerminosDeMiFormacionResponse(new Formation(t.formacion().codigo(), t.nombreDeLaFormacion()),
                    t.vigente().map(VersionDeTerminosResponse::from).orElse(null), pendiente(t.pide()), t.pide().bloquea(),
                    t.ultimaAceptada().orElse(null), t.nota().orElse(null), t.acuerdo().map(Agreement::from).orElse(null));
        }

        private static String pendiente(QuePideLaAceptacion pide) {
            return switch (pide) {
                case NADA -> "NONE";
                case PRIMERA_VEZ -> "FIRST_TIME";
                case NUEVA_VERSION -> "NEW_VERSION";
                case PRIMERA_EN_LA_FORMACION -> "FIRST_IN_FORMATION";
            };
        }
    }

    /** @param signatureUrl la firma que dibujó al aceptar (el enlace vence a los 15 minutos); {@code null} si no quedó o ya no está */
    public record TerminosAceptadosResponse(VersionDeTerminosResponse version, Instant acceptedAt, String signatureUrl, AcceptedAgreement agreement) {

        static TerminosAceptadosResponse from(TerminosAceptados t) {
            return new TerminosAceptadosResponse(VersionDeTerminosResponse.from(t.version()), t.aceptadaEn(), t.firmaUrl().orElse(null),
                    t.acuerdo().map(AcceptedAgreement::from).orElse(null));
        }
    }

    /** Lo que se le mostró en «Mi aceptación» cuando aceptó (spec 039); {@code null} en lo aceptado antes. */
    public record AcceptedAgreement(String program, LocalDate startDate, LocalDate endDate) {

        static AcceptedAgreement from(AcuerdoAceptado a) {
            return new AcceptedAgreement(a.programa(), a.inicio(), a.fin());
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
