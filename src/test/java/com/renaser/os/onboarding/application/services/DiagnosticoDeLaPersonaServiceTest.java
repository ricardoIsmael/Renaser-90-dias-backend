package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder.LecturaDelDiagnostico;
import com.renaser.os.onboarding.application.ports.in.diagnostico.DiagnosticoDeLaPersonaUseCase.DiagnosticoAbierto;
import com.renaser.os.onboarding.application.ports.in.diagnostico.DiagnosticoDeLaPersonaUseCase.ResultadoDeTerminar;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort.ActorOnboarding;
import com.renaser.os.onboarding.application.ports.out.diagnostico.BorradorDelDiagnosticoPort;
import com.renaser.os.onboarding.application.ports.out.diagnostico.ConsultarFormacionDeLaPersonaPort;
import com.renaser.os.onboarding.application.ports.out.diagnostico.ConsultarFormacionDeLaPersonaPort.FormacionDeLaPersona;
import com.renaser.os.onboarding.application.ports.out.diagnostico.VersionesDeFichaPort;
import com.renaser.os.onboarding.domain.model.diagnostico.VersionPublicada;
import com.renaser.os.onboarding.application.ports.out.estado.LoadEstadoOnboardingPort;
import com.renaser.os.onboarding.application.ports.out.estado.SaveEstadoOnboardingPort;
import com.renaser.os.onboarding.application.ports.out.ficha.RespuestasDelDiagnosticoPort;
import com.renaser.os.onboarding.application.ports.out.mapa.EtapaOnboardingPort;
import com.renaser.os.onboarding.application.ports.out.respuesta.LoadRespuestaPort;
import com.renaser.os.onboarding.application.ports.out.respuesta.SaveRespuestaPort;
import com.renaser.os.onboarding.domain.model.cuestionario.TipoPreguntaOnboarding;
import com.renaser.os.onboarding.domain.model.diagnostico.DestinoDeRespuesta;
import com.renaser.os.onboarding.domain.model.diagnostico.Diagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.DiagnosticoIncompletoException;
import com.renaser.os.onboarding.domain.model.diagnostico.FichaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.OpcionDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.PreguntaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.RespuestaEntrante;
import com.renaser.os.onboarding.domain.model.diagnostico.SeccionDelDiagnostico;
import com.renaser.os.onboarding.domain.model.estado.EstadoOnboarding;
import com.renaser.os.onboarding.domain.model.respuesta.Respuesta;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiagnosticoDeLaPersonaServiceTest {

    private static final FixedClock RELOJ = FixedClock.at(Instant.parse("2026-10-31T03:00:00Z"));
    private static final UserId ADMIN = UserId.of(UUID.randomUUID());

    @Mock private LoadEstadoOnboardingPort estados;
    @Mock private SaveEstadoOnboardingPort guardarEstado;
    @Mock private ConsultarActorPort actores;
    @Mock private VersionesDeFichaPort versiones;
    @Mock private BorradorDelDiagnosticoPort borrador;
    @Mock private LoadRespuestaPort respuestas;
    @Mock private SaveRespuestaPort guardarRespuesta;
    @Mock private RespuestasDelDiagnosticoPort hayRespuestas;
    @Mock private EtapaOnboardingPort etapas;
    @Mock private ConsultarFormacionDeLaPersonaPort formaciones;

    private DiagnosticoDeLaPersonaService servicio;
    private LecturaDelDiagnosticoService lecturas;
    private UserId persona;
    private final List<Respuesta> guardadas = new ArrayList<>();

    // 1 nombre (precarga), 2 prioridad, 3 objetivo de Cuerpo (500), 4 solo del Diplomado
    private final PreguntaDelDiagnostico nombre = new PreguntaDelDiagnostico(1, "diag_p1", 1, 0, "¿Cuál es tu nombre completo?", null, TipoPreguntaOnboarding.TEXTO,
            true, null, List.of(), null, null, null, Set.of(), PreguntaDelDiagnostico.PRECARGA_NOMBRE, null);
    private final PreguntaDelDiagnostico prioridad = new PreguntaDelDiagnostico(2, "diag_p2", 1, 1, "¿Tu prioridad en {duracion}?", null,
            TipoPreguntaOnboarding.SELECCION_UNICA, true, null, List.of(new OpcionDelDiagnostico("CUERPO", "Salud."), new OpcionDelDiagnostico("TRABAJO", "Negocios.")),
            null, null, DestinoDeRespuesta.PRIORIDAD_PRINCIPAL, Set.of(), null, null);
    private final PreguntaDelDiagnostico objetivo = new PreguntaDelDiagnostico(3, "diag_p3", 1, 2, "¿Qué quieres conseguir en tu salud?", null,
            TipoPreguntaOnboarding.AREA_TEXTO, true, null, List.of(), null, null, DestinoDeRespuesta.OBJETIVO_CUERPO, Set.of(), null, null);
    private final PreguntaDelDiagnostico soloDiplomado = new PreguntaDelDiagnostico(4, "diag_p4", 1, 3, "Pregunta del Diplomado", null, TipoPreguntaOnboarding.TEXTO,
            true, null, List.of(), null, null, null, Set.of(Formacion.DIPLOMADO), null, null);

    @BeforeEach
    void setUp() {
        FichaVigenteDeLaPersona vigente = new FichaVigenteDeLaPersona(versiones, formaciones, respuestas, hayRespuestas, etapas);
        lecturas = new LecturaDelDiagnosticoService(estados, actores, borrador, etapas, formaciones, vigente);
        servicio = new DiagnosticoDeLaPersonaService(estados, guardarEstado, actores, versiones, guardarRespuesta, etapas, formaciones, vigente, lecturas,
                RELOJ);
        persona = UserId.of(UUID.randomUUID());
    }

    private FichaDelDiagnostico ficha() {
        SeccionDelDiagnostico portada = new SeccionDelDiagnostico(9, "portada", "Diagnóstico · {formacion}", "Tiempo estimado: 15 minutos", 0, List.of());
        return new FichaDelDiagnostico(List.of(portada, new SeccionDelDiagnostico(1, "s1", "Primera", null, 1,
                List.of(nombre, prioridad, objetivo, soloDiplomado))));
    }

    private void personaActiva() {
        when(actores.deActor(persona)).thenReturn(Optional.of(new ActorOnboarding(persona, false, "María Pérez", false)));
    }

    private EstadoOnboarding conDiagnostico(Integer version) {
        EstadoOnboarding estado = EstadoOnboarding.conDiagnostico(persona, ADMIN, RELOJ);
        if (version != null) {
            estado.fijarVersionDelDiagnostico(version, RELOJ);
        }
        when(estados.deUsuario(persona)).thenReturn(Optional.of(estado));
        return estado;
    }

    private void versionPublicada(int version) {
        VersionPublicada v = new VersionPublicada(version, ficha(), ADMIN, "Ana", RELOJ.now());
        lenient(v);
        when(versiones.version(version)).thenReturn(Optional.of(v));
    }

    private void lenient(VersionPublicada v) {
        org.mockito.Mockito.lenient().when(versiones.ultima()).thenReturn(Optional.of(v));
    }

    private void formacion(Formacion f, String nombre, int dias) {
        when(formaciones.de(persona)).thenReturn(Optional.of(new FormacionDeLaPersona(f, nombre, dias)));
    }

    private void respuestasGuardadas(Respuesta... rs) {
        guardadas.clear();
        guardadas.addAll(List.of(rs));
        when(respuestas.todasDeUsuario(persona)).thenReturn(guardadas);
    }

    // ---------------------------------------------------------------------------------------------- abrir

    @Test
    @DisplayName("abrir(): sin diagnóstico asignado -> 404")
    void sinDiagnosticoAsignado() {
        personaActiva();
        when(estados.deUsuario(persona)).thenReturn(Optional.of(EstadoOnboarding.iniciar(persona, RELOJ)));

        assertThatThrownBy(() -> servicio.abrir(persona)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("abrir(): la primera vez fija la versión vigente; los textos llevan el plazo y la formación de la persona; el nombre viene precargado")
    void abreConLaVersionVigente() {
        personaActiva();
        conDiagnostico(null);
        when(etapas.flujosCompletados(persona)).thenReturn(Set.of());
        versionPublicada(3);
        formacion(Formacion.DIPLOMADO, "Diplomado Renaser", 365);
        respuestasGuardadas();
        when(hayRespuestas.hayRespuestas(persona)).thenReturn(false);

        DiagnosticoAbierto abierto = servicio.abrir(persona);

        assertThat(abierto.version()).isEqualTo(3);
        assertThat(abierto.estado()).isEqualTo("PENDIENTE");
        assertThat(abierto.titulo()).isEqualTo("Diagnóstico · Diplomado Renaser");
        assertThat(abierto.duracionTexto()).isEqualTo("1 año");
        assertThat(abierto.ficha().pregunta(2).orElseThrow().texto()).isEqualTo("¿Tu prioridad en 1 año?");
        assertThat(abierto.ficha().pregunta(4)).isPresent();
        assertThat(abierto.precargas()).containsEntry(1, "María Pérez");
        verify(guardarEstado).guardar(any());
    }

    @Test
    @DisplayName("abrir(): quien ya empezó sigue con SU versión aunque haya una nueva; y la Escuela no ve la pregunta del Diplomado")
    void sigueConSuVersion() {
        personaActiva();
        conDiagnostico(2);
        when(etapas.flujosCompletados(persona)).thenReturn(Set.of());
        versionPublicada(2);
        formacion(Formacion.ESCUELA, "Escuela Renaser", 90);
        respuestasGuardadas(respuesta(1, "María"));
        when(hayRespuestas.hayRespuestas(persona)).thenReturn(true);

        DiagnosticoAbierto abierto = servicio.abrir(persona);

        assertThat(abierto.version()).isEqualTo(2);
        assertThat(abierto.estado()).isEqualTo("EN_CURSO");
        assertThat(abierto.ficha().pregunta(4)).isEmpty();
        assertThat(abierto.precargas()).doesNotContainKey(1);
        verify(guardarEstado, never()).guardar(any());
        verify(versiones, never()).ultima();
    }

    @Test
    @DisplayName("abrir(): sin versión publicada -> 404; ya terminado -> 409")
    void sinVersionOTerminado() {
        personaActiva();
        conDiagnostico(null);
        when(etapas.flujosCompletados(persona)).thenReturn(Set.of());
        when(versiones.ultima()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.abrir(persona)).isInstanceOf(NoSuchElementException.class).hasMessageContaining("Todavía no hay");

        when(etapas.flujosCompletados(persona)).thenReturn(Set.of(Diagnostico.FLUJO));
        assertThatThrownBy(() -> servicio.abrir(persona)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("una cuenta suspendida no abre nada (403)")
    void suspendida() {
        when(actores.deActor(persona)).thenReturn(Optional.of(new ActorOnboarding(persona, true)));
        assertThatThrownBy(() -> servicio.abrir(persona)).isInstanceOf(NotAuthorizedException.class);
    }

    // ---------------------------------------------------------------------------------------------- guardar

    @Test
    @DisplayName("guardar(): valida cada respuesta contra su pregunta, guarda las que vienen y borra las que llegan vacías")
    void guardaYBorra() {
        personaActiva();
        conDiagnostico(1);
        versionPublicada(1);
        when(etapas.flujosCompletados(persona)).thenReturn(Set.of());
        formacion(Formacion.ESCUELA, "Escuela Renaser", 90);
        respuestasGuardadas(respuesta(3, "Viejo objetivo"));
        when(guardarRespuesta.guardar(any())).thenAnswer(i -> i.getArgument(0));

        int n = servicio.guardar(persona, List.of(
                new RespuestaEntrante(1, "María Pérez", null, null, null, null),
                new RespuestaEntrante(2, "TRABAJO", null, null, null, null),
                new RespuestaEntrante(3, null, null, null, null, null)));

        assertThat(n).isEqualTo(2);
        verify(guardarRespuesta).eliminar(persona, 3);
    }

    @Test
    @DisplayName("guardar(): una respuesta inválida, una pregunta ajena o un diagnóstico terminado se rechazan sin guardar")
    void rechazos() {
        personaActiva();
        conDiagnostico(1);
        versionPublicada(1);
        formacion(Formacion.ESCUELA, "Escuela Renaser", 90);
        when(etapas.flujosCompletados(persona)).thenReturn(Set.of());
        respuestasGuardadas();

        assertThatThrownBy(() -> servicio.guardar(persona, List.of(new RespuestaEntrante(2, "NADA", null, null, null, null))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.guardar(persona, List.of(new RespuestaEntrante(3, "a".repeat(501), null, null, null, null))))
                .hasMessageContaining("500");
        assertThatThrownBy(() -> servicio.guardar(persona, List.of(new RespuestaEntrante(4, "x", null, null, null, null))))
                .isInstanceOf(NoSuchElementException.class);

        when(etapas.flujosCompletados(persona)).thenReturn(Set.of(Diagnostico.FLUJO));
        assertThatThrownBy(() -> servicio.guardar(persona, List.of())).isInstanceOf(IllegalStateException.class);
        verify(guardarRespuesta, never()).guardar(any());
    }

    // ---------------------------------------------------------------------------------------------- terminar

    @Test
    @DisplayName("terminar(): con obligatorias sin responder falla con sus ids y no marca nada")
    void terminarIncompleto() {
        personaActiva();
        conDiagnostico(1);
        versionPublicada(1);
        formacion(Formacion.ESCUELA, "Escuela Renaser", 90);
        when(etapas.flujosCompletados(persona)).thenReturn(Set.of());
        respuestasGuardadas(respuesta(1, "María"));

        assertThatThrownBy(() -> servicio.terminar(persona)).isInstanceOfSatisfying(DiagnosticoIncompletoException.class,
                e -> assertThat(e.faltan()).containsExactly(2, 3));
        verify(etapas, never()).marcarCompletada(any(), any());
    }

    @Test
    @DisplayName("terminar(): completo, marca la etapa y entrega los objetivos con la prioridad primero")
    void terminarCompleto() {
        personaActiva();
        conDiagnostico(1);
        versionPublicada(1);
        formacion(Formacion.ESCUELA, "Escuela Renaser", 90);
        when(etapas.flujosCompletados(persona)).thenReturn(Set.of());
        respuestasGuardadas(respuesta(1, "María"), respuesta(2, "CUERPO"), respuesta(3, "Entrenar cuatro días por semana"));

        ResultadoDeTerminar r = servicio.terminar(persona);

        assertThat(r.estado()).isEqualTo("COMPLETADO");
        assertThat(r.prioridad()).isEqualTo("CUERPO");
        assertThat(r.objetivos()).extracting(o -> o.eje()).containsExactly("CUERPO");
        verify(etapas).marcarCompletada(persona, Diagnostico.FLUJO);
    }

    @Test
    @DisplayName("terminar() de nuevo es idempotente: devuelve lo mismo sin volver a marcar")
    void terminarDosVeces() {
        personaActiva();
        conDiagnostico(1);
        versionPublicada(1);
        formacion(Formacion.ESCUELA, "Escuela Renaser", 90);
        when(etapas.flujosCompletados(persona)).thenReturn(Set.of(Diagnostico.FLUJO));
        respuestasGuardadas(respuesta(1, "María"), respuesta(2, "CUERPO"), respuesta(3, "Entrenar"));

        assertThat(servicio.terminar(persona).objetivos()).hasSize(1);
        verify(etapas, never()).marcarCompletada(any(), any());
    }

    // ------------------------------------------------------------------------------------ lo que ven los demás

    @Test
    @DisplayName("lecturaDe(): la ficha estándar sale sin secciones y sin fallar")
    void lecturaDeUnaFichaEstandar() {
        when(actores.deActor(persona)).thenReturn(Optional.of(new ActorOnboarding(persona, false, "María Pérez", false)));
        when(formaciones.de(persona)).thenReturn(Optional.empty());
        when(estados.deUsuario(persona)).thenReturn(Optional.empty());

        LecturaDelDiagnostico lectura = lecturas.lecturaDe(persona);

        assertThat(lectura.ficha()).isEqualTo("ESTANDAR");
        assertThat(lectura.estado()).isEqualTo("NO_APLICA");
        assertThat(lectura.secciones()).isEmpty();
    }

    @Test
    @DisplayName("lecturaDe(): cada pregunta con el texto de SU versión y su respuesta en palabras; las retiradas, marcadas")
    void lecturaConRespuestas() {
        when(actores.deActor(persona)).thenReturn(Optional.of(new ActorOnboarding(persona, false, "María Pérez", false)));
        conDiagnostico(1);
        versionPublicada(1);
        formacion(Formacion.ESCUELA, "Escuela Renaser", 90);
        when(etapas.flujosCompletados(persona)).thenReturn(Set.of(Diagnostico.FLUJO));
        when(etapas.completadaEn(persona, Diagnostico.FLUJO)).thenReturn(Optional.of(RELOJ.now()));
        respuestasGuardadas(respuesta(1, "María"), respuesta(2, "TRABAJO"));
        when(borrador.retiradas()).thenReturn(List.of(objetivo.retirada(RELOJ.now())));

        LecturaDelDiagnostico lectura = lecturas.lecturaDe(persona);

        assertThat(lectura.estado()).isEqualTo("COMPLETADO");
        var preguntas = lectura.secciones().get(0).preguntas();
        assertThat(preguntas.get(1).texto()).isEqualTo("¿Tu prioridad en 90 días?");
        assertThat(preguntas.get(1).respuesta().texto()).isEqualTo("Negocios.");
        assertThat(preguntas.get(2).retirada()).isTrue();
        assertThat(preguntas.get(2).respuesta()).isNull();
    }

    @Test
    @DisplayName("tratoDe() lee la pregunta del trato del diagnóstico; sin diagnóstico, null para que se use la ficha estándar")
    void trato() {
        var pregunta = new PreguntaDelDiagnostico(7, "diag_ptrato", 1, 0, "¿Cómo prefieres que te hablemos?", null, TipoPreguntaOnboarding.SELECCION_UNICA, true, null,
                List.of(new OpcionDelDiagnostico("MASCULINO", "Él"), new OpcionDelDiagnostico("FEMENINO", "Ella")), null, null,
                DestinoDeRespuesta.TRATO_DE_LA_PERSONA, Set.of(), null, null);
        when(versiones.version(1)).thenReturn(Optional.of(new VersionPublicada(1, new FichaDelDiagnostico(List.of(
                new SeccionDelDiagnostico(1, "s1", "Primera", null, 0, List.of(pregunta)))), ADMIN, "Ana", RELOJ.now())));
        conDiagnostico(1);
        when(formaciones.de(persona)).thenReturn(Optional.empty());
        respuestasGuardadas(respuesta(7, "FEMENINO"));

        assertThat(lecturas.tratoDe(persona)).isEqualTo("FEMENINO");

        when(estados.deUsuario(persona)).thenReturn(Optional.empty());
        assertThat(lecturas.tratoDe(persona)).isNull();
    }

    private static Respuesta respuesta(int preguntaId, String texto) {
        return Respuesta.crear(TipoPreguntaOnboarding.TEXTO, UserId.of(UUID.randomUUID()), preguntaId, texto, (BigDecimal) null, null, null, null, null, RELOJ);
    }
}
