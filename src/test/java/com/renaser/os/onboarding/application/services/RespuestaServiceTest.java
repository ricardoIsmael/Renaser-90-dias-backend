package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.in.respuesta.GuardarRespuestaUseCase.GuardarRespuestaCommand;
import com.renaser.os.onboarding.application.ports.in.respuesta.ObtenerRespuestasUseCase.ObtenerRespuestasQuery;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort.ActorOnboarding;
import com.renaser.os.onboarding.application.ports.out.cuestionario.LoadCuestionarioPort;
import com.renaser.os.onboarding.application.ports.out.media.LoadMediaPort;
import com.renaser.os.onboarding.application.ports.out.respuesta.LoadRespuestaPort;
import com.renaser.os.onboarding.application.ports.out.respuesta.SaveRespuestaPort;
import com.renaser.os.onboarding.domain.model.cuestionario.Pregunta;
import com.renaser.os.onboarding.domain.model.cuestionario.Seccion;
import com.renaser.os.onboarding.domain.model.cuestionario.TipoPreguntaOnboarding;
import com.renaser.os.onboarding.domain.model.media.ClaseMedia;
import com.renaser.os.onboarding.domain.model.media.MediaOnboarding;
import com.renaser.os.onboarding.domain.model.respuesta.Respuesta;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RespuestaServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Mock
    private LoadCuestionarioPort loadCuestionarioPort;
    @Mock
    private LoadRespuestaPort loadRespuestaPort;
    @Mock
    private SaveRespuestaPort saveRespuestaPort;
    @Mock
    private ConsultarActorPort actorPort;

    private final MediasEnMemoria medias = new MediasEnMemoria();
    private RespuestaService service;
    private UserId usuarioId;

    @BeforeEach
    void setUp() {
        service = new RespuestaService(loadCuestionarioPort, loadRespuestaPort, saveRespuestaPort, medias, actorPort,
                CLOCK);
        usuarioId = UserId.of(UUID.randomUUID());
    }

    private void actorActivo() {
        when(actorPort.deActor(usuarioId)).thenReturn(Optional.of(new ActorOnboarding(usuarioId, false)));
    }

    private Pregunta preguntaTexto() {
        return new Pregunta(1, (short) 1, "clave", "texto", TipoPreguntaOnboarding.TEXTO, null, false, (short) 0,
                null, null, Instant.now());
    }

    @Test
    @DisplayName("guardar(): pregunta inexistente -> NoSuchElementException (404), nunca guarda")
    void guardarConPreguntaInexistente() {
        actorActivo();
        when(loadCuestionarioPort.porId(1)).thenReturn(Optional.empty());

        var comando = new GuardarRespuestaCommand(usuarioId, 1, "hola", null, null, null, null, null);

        assertThatThrownBy(() -> service.guardar(comando)).isInstanceOf(NoSuchElementException.class);
        verify(saveRespuestaPort, never()).guardar(any());
    }

    @Test
    @DisplayName("guardar(): actor suspendido -> NotAuthorizedException, nunca consulta la pregunta")
    void guardarConActorSuspendido() {
        when(actorPort.deActor(usuarioId)).thenReturn(Optional.of(new ActorOnboarding(usuarioId, true)));

        var comando = new GuardarRespuestaCommand(usuarioId, 1, "hola", null, null, null, null, null);

        assertThatThrownBy(() -> service.guardar(comando)).isInstanceOf(NotAuthorizedException.class);
        verify(loadCuestionarioPort, never()).porId(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("guardar(): tipo de valor incoherente con la pregunta -> IllegalArgumentException, propagada del dominio")
    void guardarConValorIncoherenteConElTipo() {
        actorActivo();
        when(loadCuestionarioPort.porId(1)).thenReturn(Optional.of(preguntaTexto()));

        // pregunta es TEXTO, pero se manda valorNumero -- incoherente
        var comando = new GuardarRespuestaCommand(usuarioId, 1, null, java.math.BigDecimal.TEN, null, null, null,
                null);

        assertThatThrownBy(() -> service.guardar(comando)).isInstanceOf(IllegalArgumentException.class);
        verify(saveRespuestaPort, never()).guardar(any());
    }

    @Test
    @DisplayName("guardar(): primera vez -> crea (SaveRespuestaPort.guardar con id null)")
    void guardarPrimeraVezCrea() {
        actorActivo();
        when(loadCuestionarioPort.porId(1)).thenReturn(Optional.of(preguntaTexto()));
        when(loadRespuestaPort.porUsuarioYPregunta(usuarioId, 1)).thenReturn(Optional.empty());
        when(saveRespuestaPort.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        var comando = new GuardarRespuestaCommand(usuarioId, 1, "hola", null, null, null, null, null);

        Respuesta resultado = service.guardar(comando);

        assertThat(resultado.id()).isNull();
        assertThat(resultado.valorTexto()).isEqualTo("hola");
    }

    @Test
    @DisplayName("guardar(): segunda vez sobre la misma pregunta -> actualiza la existente (mismo id), no crea otra")
    void guardarSegundaVezActualiza() {
        actorActivo();
        when(loadCuestionarioPort.porId(1)).thenReturn(Optional.of(preguntaTexto()));
        Respuesta existente = Respuesta.rehydrate(77L, usuarioId, 1, "primero", null, null, null, null, null, null,
                CLOCK.now(), CLOCK.now());
        when(loadRespuestaPort.porUsuarioYPregunta(usuarioId, 1)).thenReturn(Optional.of(existente));
        when(saveRespuestaPort.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        var comando = new GuardarRespuestaCommand(usuarioId, 1, "segundo", null, null, null, null, null);

        Respuesta resultado = service.guardar(comando);

        assertThat(resultado.id()).isEqualTo(77L);
        assertThat(resultado.valorTexto()).isEqualTo("segundo");
    }

    // ── guardar() con mediaId — el archivo tiene que ser de quien responde (E-528) ──────

    private Pregunta preguntaFirma() {
        return new Pregunta(2, (short) 1, "signature", "Firma con tu dedo", TipoPreguntaOnboarding.FIRMA, null, true,
                (short) 2, null, null, Instant.now());
    }

    @Test
    @DisplayName("E-528: firma con un archivo PROPIO -> se guarda apuntando a ese archivo")
    void guardarConMediaPropiaGuarda() {
        actorActivo();
        long propia = medias.subida(usuarioId);
        when(loadCuestionarioPort.porId(2)).thenReturn(Optional.of(preguntaFirma()));
        when(loadRespuestaPort.porUsuarioYPregunta(usuarioId, 2)).thenReturn(Optional.empty());
        when(saveRespuestaPort.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        Respuesta resultado = service.guardar(new GuardarRespuestaCommand(usuarioId, 2, null, null, null, null, null,
                propia));

        assertThat(resultado.mediaId()).isEqualTo(propia);
    }

    @Test
    @DisplayName("E-528: firma con el archivo de OTRA persona -> 404 «Ese archivo no existe o no es tuyo», nada se guarda")
    void guardarConMediaAjenaSeRechaza() {
        actorActivo();
        long deAna = medias.subida(UserId.of(UUID.randomUUID()));
        when(loadCuestionarioPort.porId(2)).thenReturn(Optional.of(preguntaFirma()));

        var comando = new GuardarRespuestaCommand(usuarioId, 2, null, null, null, null, null, deAna);

        assertThatThrownBy(() -> service.guardar(comando)).isInstanceOf(NoSuchElementException.class)
                .hasMessage(RespuestaService.MEDIA_AJENA_O_INEXISTENTE);
        verify(saveRespuestaPort, never()).guardar(any());
    }

    @Test
    @DisplayName("E-528: un id que no existe -> el MISMO rechazo que el ajeno (no deja averiguar qué ids existen)")
    void guardarConMediaInexistenteSeRechazaIgual() {
        actorActivo();
        when(loadCuestionarioPort.porId(2)).thenReturn(Optional.of(preguntaFirma()));

        var comando = new GuardarRespuestaCommand(usuarioId, 2, null, null, null, null, null, 999_999L);

        assertThatThrownBy(() -> service.guardar(comando)).isInstanceOf(NoSuchElementException.class)
                .hasMessage(RespuestaService.MEDIA_AJENA_O_INEXISTENTE);
        verify(saveRespuestaPort, never()).guardar(any());
    }

    @Test
    @DisplayName("E-528: si ya tenía su firma, cambiarla por un archivo ajeno no toca la respuesta guardada")
    void cambiarLaFirmaPorUnArchivoAjenoNoTocaLaExistente() {
        actorActivo();
        long deAna = medias.subida(UserId.of(UUID.randomUUID()));
        when(loadCuestionarioPort.porId(2)).thenReturn(Optional.of(preguntaFirma()));

        var comando = new GuardarRespuestaCommand(usuarioId, 2, null, null, null, null, null, deAna);

        assertThatThrownBy(() -> service.guardar(comando)).isInstanceOf(NoSuchElementException.class);
        verify(loadRespuestaPort, never()).porUsuarioYPregunta(any(), org.mockito.ArgumentMatchers.anyInt());
        verify(saveRespuestaPort, never()).guardar(any());
    }

    @Test
    @DisplayName("E-528: el dominio no le prohíbe un mediaId a una pregunta de texto, así que ahí también se mira el dueño")
    void mediaAjenaEnUnaPreguntaDeTextoTambienSeRechaza() {
        actorActivo();
        long deAna = medias.subida(UserId.of(UUID.randomUUID()));
        when(loadCuestionarioPort.porId(1)).thenReturn(Optional.of(preguntaTexto()));

        var comando = new GuardarRespuestaCommand(usuarioId, 1, "hola", null, null, null, null, deAna);

        assertThatThrownBy(() -> service.guardar(comando)).isInstanceOf(NoSuchElementException.class)
                .hasMessage(RespuestaService.MEDIA_AJENA_O_INEXISTENTE);
        verify(saveRespuestaPort, never()).guardar(any());
    }

    @Test
    @DisplayName("E-528: sin mediaId, igual que antes: se guarda y no se consulta ningún archivo")
    void sinMediaNoConsultaArchivos() {
        actorActivo();
        when(loadCuestionarioPort.porId(1)).thenReturn(Optional.of(preguntaTexto()));
        when(loadRespuestaPort.porUsuarioYPregunta(usuarioId, 1)).thenReturn(Optional.empty());
        when(saveRespuestaPort.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        Respuesta resultado = service.guardar(new GuardarRespuestaCommand(usuarioId, 1, "hola", null, null, null, null,
                null));

        assertThat(resultado.valorTexto()).isEqualTo("hola");
        assertThat(medias.consultas).isZero();
    }

    /**
     * Los archivos subidos, con su dueño, como los deja {@code POST /onboarding/media}. Un fake y no un mock: así
     * «ajeno» es de verdad un archivo que existe y es de otra persona, y «inexistente» uno que no está.
     */
    private static final class MediasEnMemoria implements LoadMediaPort {

        private final Map<Long, MediaOnboarding> porId = new HashMap<>();
        private int consultas;

        long subida(UserId dueno) {
            long id = porId.size() + 1L;
            porId.put(id, MediaOnboarding.rehydrate(id, dueno, "pacto", "signature", ClaseMedia.FIRMA,
                    MediaOnboarding.BUCKET_DEFAULT, "onboarding/" + dueno + "/firma/" + UUID.randomUUID(), "image/png",
                    null, null, null, CLOCK.now(), CLOCK.now()));
            return id;
        }

        @Override
        public Optional<MediaOnboarding> porId(long mediaId) {
            consultas++;
            return Optional.ofNullable(porId.get(mediaId));
        }

        @Override
        public Optional<MediaOnboarding> porIdYUsuario(long mediaId, UserId usuarioId) {
            consultas++;
            return Optional.ofNullable(porId.get(mediaId)).filter(media -> media.usuarioId().equals(usuarioId));
        }
    }

    // ── obtener() — GET /onboarding/answers ─────────────────────────────────────

    private Seccion seccion(short id, String claveSeccion) {
        return new Seccion(id, "diseno_destino", claveSeccion, "Titulo " + claveSeccion, null, (short) 0,
                Instant.now());
    }

    private Pregunta pregunta(int id, short seccionId, String clave) {
        return new Pregunta(id, seccionId, clave, "texto", TipoPreguntaOnboarding.AREA_TEXTO, null, false,
                (short) 0, null, null, Instant.now());
    }

    @Test
    @DisplayName("obtener(): actor suspendido -> NotAuthorizedException, nunca consulta el catalogo ni las respuestas")
    void obtenerConActorSuspendido() {
        when(actorPort.deActor(usuarioId)).thenReturn(Optional.of(new ActorOnboarding(usuarioId, true)));

        var query = new ObtenerRespuestasQuery(usuarioId, "diseno_destino");

        assertThatThrownBy(() -> service.obtener(query)).isInstanceOf(NotAuthorizedException.class);
        verify(loadRespuestaPort, never()).todasDeUsuario(any());
        verify(loadCuestionarioPort, never()).seccionesDeFlujo(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("obtener(): agrupa las respuestas ya guardadas por seccion, en el orden del cuestionario")
    void obtenerAgrupaPorSeccion() {
        actorActivo();
        Seccion seccion1 = seccion((short) 1, "destino_90d");
        Pregunta pregunta1 = pregunta(10, (short) 1, "master_goal_90d");
        Pregunta pregunta2 = pregunta(11, (short) 1, "otra_pregunta");
        Respuesta respuesta1 = Respuesta.rehydrate(1L, usuarioId, 10, "mi meta", null, null, null, null, null,
                CLOCK.now(), CLOCK.now(), CLOCK.now());

        when(loadRespuestaPort.todasDeUsuario(usuarioId)).thenReturn(List.of(respuesta1));
        when(loadCuestionarioPort.seccionesDeFlujo("diseno_destino")).thenReturn(List.of(seccion1));
        when(loadCuestionarioPort.preguntasDeSeccion((short) 1)).thenReturn(List.of(pregunta1, pregunta2));

        var resultado = service.obtener(new ObtenerRespuestasQuery(usuarioId, "diseno_destino"));

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).seccion()).isEqualTo(seccion1);
        // pregunta2 nunca fue respondida -> no aparece, ninguna placeholder
        assertThat(resultado.get(0).preguntas()).hasSize(1);
        assertThat(resultado.get(0).preguntas().get(0).pregunta()).isEqualTo(pregunta1);
        assertThat(resultado.get(0).preguntas().get(0).respuesta()).isEqualTo(respuesta1);
    }

    @Test
    @DisplayName("obtener(): una seccion sin ninguna pregunta respondida no aparece en el resultado")
    void obtenerOmiteSeccionesSinRespuestas() {
        actorActivo();
        Seccion seccionSinRespuestas = seccion((short) 2, "vacia");
        Pregunta preguntaSinResponder = pregunta(20, (short) 2, "sin_responder");

        when(loadRespuestaPort.todasDeUsuario(usuarioId)).thenReturn(List.of());
        when(loadCuestionarioPort.seccionesDeFlujo("diseno_destino")).thenReturn(List.of(seccionSinRespuestas));
        when(loadCuestionarioPort.preguntasDeSeccion((short) 2)).thenReturn(List.of(preguntaSinResponder));

        var resultado = service.obtener(new ObtenerRespuestasQuery(usuarioId, "diseno_destino"));

        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("obtener(): solo trae las respuestas DEL ACTOR que llama — nunca lee otro usuario")
    void obtenerSoloLeeLasRespuestasDelActorQueLlama() {
        actorActivo();
        when(loadRespuestaPort.todasDeUsuario(usuarioId)).thenReturn(List.of());
        when(loadCuestionarioPort.seccionesDeFlujo("diseno_destino")).thenReturn(List.of());

        service.obtener(new ObtenerRespuestasQuery(usuarioId, "diseno_destino"));

        verify(loadRespuestaPort).todasDeUsuario(usuarioId);
        verify(loadRespuestaPort, never()).todasDeUsuario(org.mockito.ArgumentMatchers.argThat(id -> !id.equals(usuarioId)));
    }

    // ── D-80 (2026-10-06): solo mayores de 18, con el día de Lima ───────────

    private Pregunta preguntaFechaDeNacimiento() {
        return new Pregunta(52, (short) 1, "birth_date", "Fecha de nacimiento", TipoPreguntaOnboarding.FECHA, null,
                true, (short) 0, null, null, Instant.now());
    }

    @Test
    @DisplayName("guardar(): birth_date de 17 años y 364 días -> 400 «Renaser es solo para mayores de 18 años», no guarda ni pisa")
    void guardarFechaDeNacimientoDeMenorSeRechaza() {
        actorActivo();
        when(loadCuestionarioPort.porId(52)).thenReturn(Optional.of(preguntaFechaDeNacimiento()));

        // El reloj del test es el 24/08/2026 (Lima): quien nació el 25/08/2008 cumple 18 mañana.
        var comando = new GuardarRespuestaCommand(usuarioId, 52, "2008-08-25", null, null, null, null, null);

        assertThatThrownBy(() -> service.guardar(comando))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Renaser es solo para mayores de 18 años");
        verify(loadRespuestaPort, never()).porUsuarioYPregunta(any(), org.mockito.ArgumentMatchers.anyInt());
        verify(saveRespuestaPort, never()).guardar(any());
    }

    @Test
    @DisplayName("guardar(): birth_date de 18 justos hoy -> se guarda")
    void guardarFechaDeNacimientoDeDieciochoJustos() {
        actorActivo();
        when(loadCuestionarioPort.porId(52)).thenReturn(Optional.of(preguntaFechaDeNacimiento()));
        when(loadRespuestaPort.porUsuarioYPregunta(usuarioId, 52)).thenReturn(Optional.empty());
        when(saveRespuestaPort.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        var comando = new GuardarRespuestaCommand(usuarioId, 52, "2008-08-24", null, null, null, null, null);

        assertThat(service.guardar(comando).valorTexto()).isEqualTo("2008-08-24");
    }

    @Test
    @DisplayName("guardar(): la regla de edad es solo para birth_date — otra pregunta FECHA con una fecha reciente se guarda")
    void otraPreguntaFechaNoMiraLaEdad() {
        actorActivo();
        Pregunta otraFecha = new Pregunta(60, (short) 1, "fecha_inicio_negocio", "Inicio", TipoPreguntaOnboarding.FECHA,
                null, false, (short) 0, null, null, Instant.now());
        when(loadCuestionarioPort.porId(60)).thenReturn(Optional.of(otraFecha));
        when(loadRespuestaPort.porUsuarioYPregunta(usuarioId, 60)).thenReturn(Optional.empty());
        when(saveRespuestaPort.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        var comando = new GuardarRespuestaCommand(usuarioId, 60, "2026-01-10", null, null, null, null, null);

        assertThat(service.guardar(comando).valorTexto()).isEqualTo("2026-01-10");
    }
}
