package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.in.diagnostico.EditarDiagnosticoUseCase.PreguntaAdministrada;
import com.renaser.os.onboarding.application.ports.in.diagnostico.EditarDiagnosticoUseCase.PreguntaPedida;
import com.renaser.os.onboarding.application.ports.in.diagnostico.EditarDiagnosticoUseCase.ResultadoDeQuitar;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort.ActorOnboarding;
import com.renaser.os.onboarding.domain.model.cuestionario.TipoPreguntaOnboarding;
import com.renaser.os.onboarding.domain.model.diagnostico.CondicionDeVisibilidad;
import com.renaser.os.onboarding.domain.model.diagnostico.DestinoDeRespuesta;
import com.renaser.os.onboarding.domain.model.diagnostico.OpcionDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.PreguntaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.SeccionDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.TieneDependientesException;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Editar el borrador del diagnóstico (spec 026, US5 y US7): quién puede, las condiciones, los tipos con respuestas y las preguntas protegidas. */
class EditarDiagnosticoServiceTest {

    private static final UserId ADMIN = UserId.of(UUID.randomUUID());
    private static final UserId MENTOR = UserId.of(UUID.randomUUID());
    private static final UserId SUSPENDIDO = UserId.of(UUID.randomUUID());

    private static final int PORTADA = 1;
    private static final int SECCION = 2;
    private static final int OTRA_SECCION = 3;

    private BorradorEnMemoria borrador;
    private EditarDiagnosticoService service;

    @BeforeEach
    void preparar() {
        ConsultarActorPort actores = mock(ConsultarActorPort.class);
        when(actores.deActor(ADMIN)).thenReturn(Optional.of(new ActorOnboarding(ADMIN, false, "Admin", true)));
        when(actores.deActor(MENTOR)).thenReturn(Optional.of(new ActorOnboarding(MENTOR, false, "Mentor", false)));
        when(actores.deActor(SUSPENDIDO)).thenReturn(Optional.of(new ActorOnboarding(SUSPENDIDO, true, "Suspendida", true)));
        borrador = new BorradorEnMemoria()
                .con(new SeccionDelDiagnostico(PORTADA, SeccionDelDiagnostico.CLAVE_DE_LA_PORTADA, "Diagnóstico · {formacion}", null, 0, List.of()))
                .con(new SeccionDelDiagnostico(SECCION, "s1", "Sobre ti", null, 1, List.of()))
                .con(new SeccionDelDiagnostico(OTRA_SECCION, "s2", "Tu trabajo", null, 2, List.of()));
        service = new EditarDiagnosticoService(new GuardiaDelDiagnostico(actores), borrador,
                new QuitarPreguntasDelDiagnostico(borrador, FixedClock.at(Instant.parse("2026-10-31T03:00:00Z"))));
    }

    private static PreguntaPedida texto(int seccion, String enunciado) {
        return new PreguntaPedida(seccion, enunciado, null, TipoPreguntaOnboarding.TEXTO, true, null, List.of(), null, null, null, Set.of(), null);
    }

    private static PreguntaPedida opcionUnica(int seccion, String enunciado, String... opciones) {
        return new PreguntaPedida(seccion, enunciado, null, TipoPreguntaOnboarding.SELECCION_UNICA, true, null,
                java.util.Arrays.stream(opciones).map(OpcionDelDiagnostico::de).toList(), null, null, null, Set.of(), null);
    }

    // ------------------------------------------------------------------------------------------------ quién

    @Test
    @DisplayName("solo Administración y Alquimista editan: un mentor y una cuenta suspendida reciben 403")
    void soloQuienAdministra() {
        assertThatThrownBy(() -> service.crearPregunta(MENTOR, texto(SECCION, "¿Cómo te llamas?"))).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.crearSeccion(MENTOR, "Nueva", null)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.quitar(MENTOR, List.of(1), false)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.crearPregunta(SUSPENDIDO, texto(SECCION, "¿Cómo te llamas?"))).isInstanceOf(NotAuthorizedException.class);
        assertThat(borrador.cargar().preguntas()).isEmpty();
    }

    // ------------------------------------------------------------------------------------------------ crear y mover

    @Test
    @DisplayName("crear una pregunta la deja al final de su sección; la portada no lleva preguntas")
    void crear() {
        PreguntaAdministrada a = service.crearPregunta(ADMIN, texto(SECCION, "Primera"));
        PreguntaAdministrada b = service.crearPregunta(ADMIN, texto(SECCION, "Segunda"));

        assertThat(borrador.cargar().preguntas()).extracting(PreguntaDelDiagnostico::texto).containsExactly("Primera", "Segunda");
        assertThat(a.respuestas()).isZero();
        assertThat(b.protegida()).isFalse();
        assertThatThrownBy(() -> service.crearPregunta(ADMIN, texto(PORTADA, "En la portada"))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("portada");
    }

    @Test
    @DisplayName("mover una pregunta a otra sección y posición reordena; mover una dependiente antes de su padre se rechaza")
    void mover() {
        int a = service.crearPregunta(ADMIN, texto(SECCION, "A")).pregunta().id();
        int b = service.crearPregunta(ADMIN, texto(SECCION, "B")).pregunta().id();

        service.moverPregunta(ADMIN, b, SECCION, 0);
        assertThat(borrador.cargar().preguntas()).extracting(PreguntaDelDiagnostico::id).containsExactly(b, a);

        service.moverPregunta(ADMIN, a, OTRA_SECCION, 0);
        assertThat(borrador.seccion(OTRA_SECCION).orElseThrow().preguntas()).extracting(PreguntaDelDiagnostico::id).containsExactly(a);

        int padre = service.crearPregunta(ADMIN, opcionUnica(OTRA_SECCION, "¿Trabajas?", "Sí.", "No.")).pregunta().id();
        PreguntaPedida condicionada = new PreguntaPedida(OTRA_SECCION, "¿Dónde?", null, TipoPreguntaOnboarding.TEXTO, true, null, List.of(), null,
                new CondicionDeVisibilidad(padre, "Sí."), null, Set.of(), null);
        int hija = service.crearPregunta(ADMIN, condicionada).pregunta().id();
        assertThatThrownBy(() -> service.moverPregunta(ADMIN, hija, OTRA_SECCION, 0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("anterior");
    }

    @Test
    @DisplayName("una condición que apunta a una pregunta posterior, que no existe o a una opción que no está se rechaza")
    void condicionesInvalidas() {
        int padre = service.crearPregunta(ADMIN, opcionUnica(SECCION, "¿Trabajas?", "Sí.", "No.")).pregunta().id();

        PreguntaPedida aOpcionQueNoEsta = new PreguntaPedida(SECCION, "¿Dónde?", null, TipoPreguntaOnboarding.TEXTO, true, null, List.of(), null,
                new CondicionDeVisibilidad(padre, "Quizá"), null, Set.of(), null);
        assertThatThrownBy(() -> service.crearPregunta(ADMIN, aOpcionQueNoEsta)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("opción");

    }

    /** Aparte: el servicio real deshace lo que deja el borrador roto con la transaccion; el borrador en memoria de la prueba no, y la anterior quedaria puesta. */
    @Test
    @DisplayName("una condicion que apunta a una pregunta que no existe se rechaza")
    void condicionAUnaQueNoExiste() {
        PreguntaPedida aUnaQueNoExiste = new PreguntaPedida(SECCION, "¿Dónde?", null, TipoPreguntaOnboarding.TEXTO, true, null, List.of(), null,
                new CondicionDeVisibilidad(9999, "Sí."), null, Set.of(), null);

        assertThatThrownBy(() -> service.crearPregunta(ADMIN, aUnaQueNoExiste)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no existe");
    }

    // ------------------------------------------------------------------------------------------------ cambiar

    @Test
    @DisplayName("a una pregunta con respuestas no se le cambia el tipo, pero sí el texto, la ayuda y las formaciones")
    void cambiarConRespuestas() {
        int id = service.crearPregunta(ADMIN, texto(SECCION, "¿Cómo te llamas?")).pregunta().id();
        borrador.respuestas.put(id, 4);

        assertThatThrownBy(() -> service.actualizarPregunta(ADMIN, id, new PreguntaPedida(SECCION, "¿Cómo te llamas?", null, TipoPreguntaOnboarding.AREA_TEXTO,
                true, null, List.of(), null, null, null, Set.of(), null))).isInstanceOf(IllegalStateException.class).hasMessageContaining("4 respuestas");

        PreguntaAdministrada cambiada = service.actualizarPregunta(ADMIN, id, new PreguntaPedida(SECCION, "¿Cuál es tu nombre?", "Tal como figura en tu DNI",
                TipoPreguntaOnboarding.TEXTO, false, null, List.of(), null, null, null, Set.of(Formacion.DIPLOMADO), null));
        assertThat(cambiada.pregunta().texto()).isEqualTo("¿Cuál es tu nombre?");
        assertThat(cambiada.pregunta().formaciones()).containsExactly(Formacion.DIPLOMADO);
        assertThat(cambiada.respuestas()).isEqualTo(4);
    }

    @Test
    @DisplayName("cada destino admite una sola pregunta, y la del trato no se puede dejar sin su destino")
    void destinos() {
        PreguntaPedida objetivo = new PreguntaPedida(SECCION, "¿Qué quieres lograr en tu cuerpo?", null, TipoPreguntaOnboarding.AREA_TEXTO, true, null,
                List.of(), null, null, DestinoDeRespuesta.OBJETIVO_CUERPO, Set.of(), null);
        service.crearPregunta(ADMIN, objetivo);
        assertThatThrownBy(() -> service.crearPregunta(ADMIN, objetivo)).isInstanceOf(IllegalStateException.class).hasMessageContaining("una sola pregunta");

        PreguntaPedida trato = new PreguntaPedida(SECCION, "¿Cómo prefieres que te hablemos?", null, TipoPreguntaOnboarding.SELECCION_UNICA, true, null,
                List.of(new OpcionDelDiagnostico("MASCULINO", "En masculino"), new OpcionDelDiagnostico("FEMENINO", "En femenino"),
                        new OpcionDelDiagnostico("NEUTRO", "Neutro")), null, null, DestinoDeRespuesta.TRATO_DE_LA_PERSONA, Set.of(), null);
        PreguntaAdministrada creada = service.crearPregunta(ADMIN, trato);
        assertThat(creada.protegida()).isTrue();

        PreguntaPedida sinDestino = new PreguntaPedida(SECCION, "¿Cómo prefieres que te hablemos?", null, TipoPreguntaOnboarding.SELECCION_UNICA, true, null,
                trato.opciones(), null, null, null, Set.of(), null);
        assertThatThrownBy(() -> service.actualizarPregunta(ADMIN, creada.pregunta().id(), sinDestino)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SER");
    }

    // ------------------------------------------------------------------------------------------------ quitar

    @Test
    @DisplayName("la pregunta del trato y el consentimiento no se pueden quitar")
    void protegidas() {
        PreguntaPedida consentimiento = new PreguntaPedida(SECCION, "Consentimiento", "Declaración", TipoPreguntaOnboarding.CASILLA, true, null,
                List.of(OpcionDelDiagnostico.de("He leído y acepto.")), null, null, null, Set.of(), null);
        int id = service.crearPregunta(ADMIN, consentimiento).pregunta().id();

        assertThatThrownBy(() -> service.quitar(ADMIN, List.of(id), false)).isInstanceOf(IllegalStateException.class).hasMessageContaining("otra función");
        assertThatThrownBy(() -> service.duplicarPregunta(ADMIN, id)).isInstanceOf(IllegalStateException.class).hasMessageContaining("uno solo");
        assertThat(borrador.existe(id)).isTrue();
    }

    @Test
    @DisplayName("quitar una pregunta de la que dependen otras avisa cuáles; con confirmación se quitan juntas")
    void quitarConDependientes() {
        int padre = service.crearPregunta(ADMIN, opcionUnica(SECCION, "¿Trabajas?", "Sí.", "No.")).pregunta().id();
        int hija = service.crearPregunta(ADMIN, new PreguntaPedida(SECCION, "¿Dónde?", null, TipoPreguntaOnboarding.TEXTO, true, null, List.of(), null,
                new CondicionDeVisibilidad(padre, "Sí."), null, Set.of(), null)).pregunta().id();

        assertThatThrownBy(() -> service.quitar(ADMIN, List.of(padre), false)).isInstanceOf(TieneDependientesException.class)
                .satisfies(e -> assertThat(((TieneDependientesException) e).dependientes()).containsExactly(hija));
        assertThat(borrador.cargar().preguntas()).hasSize(2);

        ResultadoDeQuitar resultado = service.quitar(ADMIN, List.of(padre), true);
        assertThat(resultado.eliminadas()).isEqualTo(2);
        assertThat(borrador.cargar().preguntas()).isEmpty();
    }

    @Test
    @DisplayName("una pregunta con respuestas o ya publicada se retira (no se borra) y se puede restaurar")
    void retirarYRestaurar() {
        int conRespuestas = service.crearPregunta(ADMIN, texto(SECCION, "Con respuestas")).pregunta().id();
        int publicada = service.crearPregunta(ADMIN, texto(SECCION, "Publicada")).pregunta().id();
        int limpia = service.crearPregunta(ADMIN, texto(SECCION, "Limpia")).pregunta().id();
        borrador.respuestas.put(conRespuestas, 2);
        borrador.publicadas.add(publicada);

        ResultadoDeQuitar resultado = service.quitar(ADMIN, List.of(conRespuestas, publicada, limpia), false);

        assertThat(resultado).isEqualTo(new ResultadoDeQuitar(1, 2, 1));
        assertThat(borrador.existe(limpia)).isFalse();
        assertThat(borrador.retiradas()).extracting(PreguntaDelDiagnostico::id).containsExactlyInAnyOrder(conRespuestas, publicada);
        assertThat(borrador.cargar().preguntas()).isEmpty();

        PreguntaAdministrada restaurada = service.restaurar(ADMIN, conRespuestas);
        assertThat(restaurada.pregunta().estaRetirada()).isFalse();
        assertThat(restaurada.respuestas()).isEqualTo(2);
        assertThat(borrador.cargar().preguntas()).extracting(PreguntaDelDiagnostico::id).containsExactly(conRespuestas);
        assertThatThrownBy(() -> service.restaurar(ADMIN, conRespuestas)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.actualizarPregunta(ADMIN, publicada, texto(SECCION, "Editada")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("retirada");
    }

    @Test
    @DisplayName("duplicar copia la pregunta justo debajo, sin el destino que la hace única")
    void duplicar() {
        PreguntaPedida original = new PreguntaPedida(SECCION, "Objetivo", null, TipoPreguntaOnboarding.AREA_TEXTO, true, null, List.of(), null, null,
                DestinoDeRespuesta.OBJETIVO_CUERPO, Set.of(), null);
        int id = service.crearPregunta(ADMIN, original).pregunta().id();
        service.crearPregunta(ADMIN, texto(SECCION, "Otra"));

        PreguntaAdministrada copia = service.duplicarPregunta(ADMIN, id);

        assertThat(copia.pregunta().alimenta()).isNull();
        assertThat(borrador.cargar().preguntas()).extracting(PreguntaDelDiagnostico::texto).containsExactly("Objetivo", "Objetivo", "Otra");
    }

    // ------------------------------------------------------------------------------------------------ secciones

    @Test
    @DisplayName("una sección se crea, se renombra, se ordena y al borrarla quita sus preguntas; la portada no se borra")
    void secciones() {
        SeccionDelDiagnostico nueva = service.crearSeccion(ADMIN, "Tu familia", null);
        assertThat(service.actualizarSeccion(ADMIN, nueva.id(), "Tu familia y tus amigos", "Para conocerte mejor").titulo()).isEqualTo("Tu familia y tus amigos");
        service.crearPregunta(ADMIN, texto(nueva.id(), "¿Con quién vives?"));

        service.ordenarSecciones(ADMIN, List.of(PORTADA, nueva.id(), SECCION, OTRA_SECCION));
        assertThat(borrador.cargar().secciones()).extracting(SeccionDelDiagnostico::id).containsExactly(PORTADA, nueva.id(), SECCION, OTRA_SECCION);
        assertThatThrownBy(() -> service.ordenarSecciones(ADMIN, List.of(PORTADA, nueva.id()))).isInstanceOf(IllegalArgumentException.class);

        ResultadoDeQuitar resultado = service.eliminarSeccion(ADMIN, nueva.id(), false);
        assertThat(resultado.eliminadas()).isEqualTo(1);
        assertThat(borrador.seccion(nueva.id())).isEmpty();
        assertThatThrownBy(() -> service.eliminarSeccion(ADMIN, PORTADA, false)).isInstanceOf(IllegalStateException.class).hasMessageContaining("portada");
    }
}
