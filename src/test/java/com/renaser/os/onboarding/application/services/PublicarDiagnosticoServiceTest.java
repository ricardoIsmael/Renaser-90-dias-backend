package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder;
import com.renaser.os.onboarding.application.ports.in.diagnostico.PublicarDiagnosticoUseCase.BorradorAdministrado;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort.ActorOnboarding;
import com.renaser.os.onboarding.application.ports.out.diagnostico.ConsultarFormacionDeLaPersonaPort;
import com.renaser.os.onboarding.application.ports.out.diagnostico.VersionesDeFichaPort;
import com.renaser.os.onboarding.application.ports.out.diagnostico.VersionesDeFichaPort.VersionPublicada;
import com.renaser.os.onboarding.domain.model.cuestionario.TipoPreguntaOnboarding;
import com.renaser.os.onboarding.domain.model.diagnostico.FichaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.NoSePuedePublicarException;
import com.renaser.os.onboarding.domain.model.diagnostico.OpcionDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.OtraPersonaPublicoException;
import com.renaser.os.onboarding.domain.model.diagnostico.PreguntaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.SeccionDelDiagnostico;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Publicar el diagnóstico (spec 026, US5): la declaración pendiente lo bloquea, las versiones no se pisan y la vista previa se escribe como la ve la persona. */
class PublicarDiagnosticoServiceTest {

    private static final UserId ADMIN = UserId.of(UUID.randomUUID());
    private static final UserId MENTOR = UserId.of(UUID.randomUUID());
    private static final Instant AHORA = Instant.parse("2026-10-31T03:00:00Z");

    private BorradorEnMemoria borrador;
    private VersionesEnMemoria versiones;
    private PublicarDiagnosticoService service;

    @BeforeEach
    void preparar() {
        ConsultarActorPort actores = mock(ConsultarActorPort.class);
        when(actores.deActor(ADMIN)).thenReturn(Optional.of(new ActorOnboarding(ADMIN, false, "Admin", true)));
        when(actores.deActor(MENTOR)).thenReturn(Optional.of(new ActorOnboarding(MENTOR, false, "Mentor", false)));
        borrador = new BorradorEnMemoria()
                .con(new SeccionDelDiagnostico(1, SeccionDelDiagnostico.CLAVE_DE_LA_PORTADA, "Diagnóstico · {formacion}", "Dura {duracion}", 0, List.of()));
        versiones = new VersionesEnMemoria();
        ConsultarFormacionDeLaPersonaPort formaciones = mock(ConsultarFormacionDeLaPersonaPort.class);
        when(formaciones.nombreDe(Formacion.ESCUELA)).thenReturn("Escuela Renaser");
        service = new PublicarDiagnosticoService(new GuardiaDelDiagnostico(actores), borrador, versiones, formaciones,
                mock(DiagnosticoDeLaPersonaFinder.class), FixedClock.at(AHORA));
    }

    private void conPreguntaYConsentimiento(String declaracion) {
        borrador.con(new SeccionDelDiagnostico(2, "s1", "Sobre ti", null, 1, List.of(
                new PreguntaDelDiagnostico(10, "diag_p1", 2, 0, "¿Cómo te llamas?", null, TipoPreguntaOnboarding.TEXTO, true, null, List.of(), null, null,
                        null, Set.of(), null, null),
                new PreguntaDelDiagnostico(11, "diag_p2", 2, 1, "Consentimiento", declaracion, TipoPreguntaOnboarding.CASILLA, true, null,
                        List.of(OpcionDelDiagnostico.de("He leído y acepto.")), null, null, null, Set.of(), null, null))));
    }

    @Test
    @DisplayName("con la declaración todavía de relleno el diagnóstico no se publica y el motivo se lee")
    void declaracionPendiente() {
        conPreguntaYConsentimiento(FichaDelDiagnostico.MARCA_DE_DECLARACION_PENDIENTE + " Aquí va el texto del dueño.");

        BorradorAdministrado vista = service.ver(ADMIN);
        assertThat(vista.motivosParaNoPublicar()).anyMatch(m -> m.contains("declaración"));
        assertThat(vista.totalPreguntas()).isEqualTo(2);
        assertThatThrownBy(() -> service.publicar(ADMIN, 0)).isInstanceOf(NoSePuedePublicarException.class)
                .satisfies(e -> assertThat(((NoSePuedePublicarException) e).motivos()).anyMatch(m -> m.contains("declaración")));
        assertThat(versiones.listar()).isEmpty();
    }

    @Test
    @DisplayName("publicar guarda la foto como la versión 1; sin cambios no se vuelve a publicar; con cambios es la 2")
    void publicar() {
        conPreguntaYConsentimiento("Declaro que mis respuestas son verdaderas.");

        VersionPublicada primera = service.publicar(ADMIN, 0);
        assertThat(primera.version()).isEqualTo(1);
        assertThat(primera.publicadaPor()).isEqualTo(ADMIN);
        assertThat(primera.publicadaEn()).isEqualTo(AHORA);
        assertThat(service.ver(ADMIN).hayCambiosSinPublicar()).isFalse();
        assertThatThrownBy(() -> service.publicar(ADMIN, 1)).isInstanceOf(IllegalStateException.class).hasMessageContaining("No hay cambios");

        borrador.guardarPregunta(new PreguntaDelDiagnostico(0, null, 2, 0, "¿Cuántos años tienes?", null, TipoPreguntaOnboarding.NUMERO, false, null,
                List.of(), null, null, null, Set.of(), null, null));
        assertThat(service.ver(ADMIN).hayCambiosSinPublicar()).isTrue();
        assertThat(service.publicar(ADMIN, 1).version()).isEqualTo(2);
        assertThat(service.versiones(ADMIN)).extracting(VersionPublicada::version).containsExactly(2, 1);
    }

    @Test
    @DisplayName("si otra persona publicó después de la versión que cargaste, no se pisa en silencio")
    void otraPersonaPublico() {
        conPreguntaYConsentimiento("Declaro que mis respuestas son verdaderas.");
        service.publicar(ADMIN, 0);
        borrador.guardarPregunta(new PreguntaDelDiagnostico(0, null, 2, 0, "Otra", null, TipoPreguntaOnboarding.TEXTO, false, null, List.of(), null, null,
                null, Set.of(), null, null));

        assertThatThrownBy(() -> service.publicar(ADMIN, 0)).isInstanceOf(OtraPersonaPublicoException.class)
                .satisfies(e -> assertThat(((OtraPersonaPublicoException) e).versionVigente()).isEqualTo(1));
    }

    @Test
    @DisplayName("la vista previa pone la duración y la formación en el texto y deja fuera lo de otras formaciones")
    void vistaPrevia() {
        borrador.con(new SeccionDelDiagnostico(2, "s1", "Sobre ti", null, 1, List.of(
                new PreguntaDelDiagnostico(10, "diag_p1", 2, 0, "Para todos", null, TipoPreguntaOnboarding.TEXTO, false, null, List.of(), null, null, null,
                        Set.of(), null, null),
                new PreguntaDelDiagnostico(11, "diag_p2", 2, 1, "Solo del Diplomado", null, TipoPreguntaOnboarding.TEXTO, false, null, List.of(), null, null,
                        null, Set.of(Formacion.DIPLOMADO), null, null))));

        var escuela = service.vistaPrevia(ADMIN, Formacion.ESCUELA, 90);
        assertThat(escuela.ficha().preguntas()).extracting(PreguntaDelDiagnostico::texto).containsExactly("Para todos");
        assertThat(escuela.formacionNombre()).isEqualTo("Escuela Renaser");
        assertThat(escuela.titulo()).isEqualTo("Diagnóstico · Escuela Renaser");

        var todas = service.vistaPrevia(ADMIN, null, null);
        assertThat(todas.ficha().preguntas()).hasSize(2);
    }

    @Test
    @DisplayName("solo Administración y Alquimista ven y publican")
    void soloQuienAdministra() {
        assertThatThrownBy(() -> service.ver(MENTOR)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.publicar(MENTOR, 0)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.versiones(MENTOR)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.plantilla(MENTOR)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.exportar(MENTOR)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.vistaPrevia(MENTOR, null, null)).isInstanceOf(NotAuthorizedException.class);
        assertThat(service.plantilla(ADMIN)).contains("Sección");
    }

    /** Las versiones publicadas, en memoria: append-only como la tabla. */
    private static final class VersionesEnMemoria implements VersionesDeFichaPort {
        private final List<VersionPublicada> todas = new ArrayList<>();

        @Override
        public Optional<VersionPublicada> ultima() {
            return todas.isEmpty() ? Optional.empty() : Optional.of(todas.getLast());
        }

        @Override
        public Optional<VersionPublicada> version(int version) {
            return todas.stream().filter(v -> v.version() == version).findFirst();
        }

        @Override
        public VersionPublicada publicar(FichaDelDiagnostico ficha, UserId por, Instant ahora) {
            VersionPublicada nueva = new VersionPublicada(todas.size() + 1, ficha, por, "Admin", ahora);
            todas.add(nueva);
            return nueva;
        }

        @Override
        public List<VersionPublicada> listar() {
            return todas.reversed();
        }
    }
}
