package com.renaser.os.rag.application.services.memoria;

import com.renaser.os.rag.application.ports.out.conversacion.LoadMensajeRenasiaPort;
import com.renaser.os.rag.application.ports.out.memoria.CompactarConversacionPort;
import com.renaser.os.rag.application.ports.out.memoria.EjecutarEnSegundoPlanoPort;
import com.renaser.os.rag.application.ports.out.memoria.MemoriaDeRenasiaPort;
import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasiaId;
import com.renaser.os.rag.domain.model.memoria.CategoriaDeRecuerdo;
import com.renaser.os.rag.domain.model.memoria.Compactacion;
import com.renaser.os.rag.domain.model.memoria.MemoriaDeRenasia;
import com.renaser.os.rag.domain.model.memoria.Recuerdo;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MemoriaDeRenasiaServiceTest {

    private static final UserId ACTOR = UserId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final Instant INICIO = Instant.parse("2026-09-20T15:00:00Z");
    private static final Instant AHORA = Instant.parse("2026-09-25T15:00:00Z");

    private final MemoriaDeRenasiaPort memoriaPort = mock(MemoriaDeRenasiaPort.class);
    private final LoadMensajeRenasiaPort mensajesPort = mock(LoadMensajeRenasiaPort.class);
    private final CompactarConversacionPort compactarPort = mock(CompactarConversacionPort.class);
    /** Guarda lo que se manda a segundo plano sin correrlo: la prueba decide cuando. */
    private final List<Runnable> enSegundoPlano = new ArrayList<>();
    private final EjecutarEnSegundoPlanoPort segundoPlano = enSegundoPlano::add;

    private MemoriaDeRenasiaService service;

    @BeforeEach
    void setUp() {
        service = conMemoria(true);
        when(memoriaPort.de(ACTOR)).thenReturn(MemoriaDeRenasia.vacia());
        when(memoriaPort.reemplazar(any(), any(), any())).thenReturn(true);
        when(compactarPort.compactar(any())).thenReturn(new Compactacion("Armaron su rutina de la manana.",
                Map.of(CategoriaDeRecuerdo.CONTEXTO_DE_VIDA, List.of("Trabaja de noche"))));
    }

    private MemoriaDeRenasiaService conMemoria(boolean activa) {
        return new MemoriaDeRenasiaService(memoriaPort, mensajesPort, compactarPort, segundoPlano,
                UUID::randomUUID, FixedClock.at(AHORA), activa);
    }

    /** {@code cuantos} mensajes, uno por minuto desde {@code desde}, como los da el puerto: del mas nuevo al mas viejo. */
    private void conMensajes(Instant desde, int cuantos) {
        List<MensajeRenasia> mensajes = new ArrayList<>(IntStream.range(0, cuantos)
                .mapToObj(i -> MensajeRenasia.escribirDeUsuario(MensajeRenasiaId.of(UUID.randomUUID()), ACTOR,
                        AgenteConversacional.COMPANION, "mensaje " + i, desde.plus(Duration.ofMinutes(i))))
                .toList());
        Collections.reverse(mensajes);
        when(mensajesPort.pagina(ACTOR, AgenteConversacional.COMPANION, null,
                MemoriaDeRenasiaService.VENTANA_DE_LECTURA)).thenReturn(mensajes);
    }

    private static Instant minuto(int i) {
        return INICIO.plus(Duration.ofMinutes(i));
    }

    @Test
    @DisplayName("apagada: no lee la memoria ni compacta, y el chat conversa como antes")
    void apagada() {
        service = conMemoria(false);

        assertThat(service.paraConversar(ACTOR)).isEmpty();
        service.compactarEnSegundoPlano(ACTOR);

        assertThat(enSegundoPlano).isEmpty();
        verifyNoInteractions(memoriaPort, mensajesPort, compactarPort);
    }

    @Test
    @DisplayName("si la memoria no se puede leer, se conversa igual sin ella")
    void lecturaQueFalla() {
        when(memoriaPort.de(ACTOR)).thenThrow(new IllegalStateException("base caida"));

        assertThat(service.paraConversar(ACTOR)).isEmpty();
    }

    @Test
    @DisplayName("encendida, entra al prompt aunque todavia no sepa nada: el prompt lo dice")
    void encendidaSinNada() {
        assertThat(service.paraConversar(ACTOR)).contains(MemoriaDeRenasia.vacia());
    }

    @Test
    @DisplayName("con menos de 20 mensajes nuevos no se llama al modelo")
    void pocosMensajes() {
        conMensajes(INICIO, MemoriaDeRenasiaService.UMBRAL_PARA_COMPACTAR - 1);

        service.compactarSiHaceFalta(ACTOR);

        verifyNoInteractions(compactarPort);
        verify(memoriaPort, never()).reemplazar(any(), any(), any());
    }

    @Test
    @DisplayName("compacta todos menos los ultimos 10, del mas viejo al mas nuevo, y marca hasta donde llego")
    void compactaTodosMenosLosUltimosDiez() {
        conMensajes(INICIO, 25);

        service.compactarSiHaceFalta(ACTOR);

        var entrada = ArgumentCaptor.forClass(CompactarConversacionPort.Entrada.class);
        verify(compactarPort).compactar(entrada.capture());
        assertThat(entrada.getValue().mensajes()).extracting(MensajeRenasia::contenido)
                .containsExactlyElementsOf(IntStream.range(0, 15).mapToObj(i -> "mensaje " + i).toList());
        MemoriaDeRenasia nueva = guardada(MemoriaDeRenasia.vacia());
        assertThat(nueva.resumen()).contains("Armaron su rutina de la manana.");
        assertThat(nueva.compactadoHasta()).isEqualTo(minuto(14));
        assertThat(nueva.recuerdos()).singleElement().satisfies(recuerdo -> {
            assertThat(recuerdo.categoria()).isEqualTo(CategoriaDeRecuerdo.CONTEXTO_DE_VIDA);
            assertThat(recuerdo.texto()).isEqualTo("Trabaja de noche");
            assertThat(recuerdo.creadoEn()).isEqualTo(AHORA);
        });
    }

    private MemoriaDeRenasia guardada(MemoriaDeRenasia antes) {
        var nueva = ArgumentCaptor.forClass(MemoriaDeRenasia.class);
        verify(memoriaPort).reemplazar(eq(ACTOR), eq(antes), nueva.capture());
        return nueva.getValue();
    }

    @Test
    @DisplayName("solo cuenta lo que llego despues de la ultima compactacion")
    void soloLoNuevo() {
        when(memoriaPort.de(ACTOR)).thenReturn(new MemoriaDeRenasia(List.of(), Optional.of("Antes."), minuto(9)));
        conMensajes(INICIO, 29);

        service.compactarSiHaceFalta(ACTOR);

        // Del minuto 10 al 28 son 19 nuevos: no alcanza.
        verifyNoInteractions(compactarPort);
    }

    @Test
    @DisplayName("un recuerdo que sigue igual conserva su id y su fecha: la persona lo puede estar mirando para borrarlo")
    void elQueSigueIgualConservaSuId() {
        var trabaja = new Recuerdo(UUID.randomUUID(), CategoriaDeRecuerdo.CONTEXTO_DE_VIDA, "Trabaja de noche", INICIO);
        var actual = new MemoriaDeRenasia(List.of(trabaja), Optional.empty(), minuto(-1));
        when(memoriaPort.de(ACTOR)).thenReturn(actual);
        conMensajes(INICIO, 20);

        service.compactarSiHaceFalta(ACTOR);

        assertThat(guardada(actual).recuerdos()).containsExactly(trabaja);
    }

    @Test
    @DisplayName("un resumen inservible del modelo conserva el anterior, y se avanza igual: no se recompacta lo mismo")
    void resumenInservible() {
        when(compactarPort.compactar(any())).thenReturn(new Compactacion("Hablo de su crisis.", Map.of()));
        var actual = new MemoriaDeRenasia(List.of(), Optional.of("Antes."), minuto(-1));
        when(memoriaPort.de(ACTOR)).thenReturn(actual);
        conMensajes(INICIO, 20);

        service.compactarSiHaceFalta(ACTOR);

        assertThat(guardada(actual)).isEqualTo(new MemoriaDeRenasia(List.of(), Optional.of("Antes."), minuto(9)));
    }

    @Test
    @DisplayName("si la persona borro algo mientras se compactaba, no se pisa: se reintenta en otro turno")
    void cambioMientrasCompactaba() {
        when(memoriaPort.reemplazar(any(), any(), any())).thenReturn(false);
        conMensajes(INICIO, 20);

        service.compactarSiHaceFalta(ACTOR);
        service.compactarEnSegundoPlano(ACTOR);

        assertThat(enSegundoPlano).hasSize(1);
    }

    @Test
    @DisplayName("borrar todo olvida hasta este momento: lo conversado antes no vuelve a compactarse")
    void borrarTodoHastaAhora() {
        service.borrarTodo(ACTOR);

        verify(memoriaPort).olvidarTodo(ACTOR, AHORA);
    }

    @Test
    @DisplayName("una compactacion a la vez por persona; al terminar, aunque falle, se puede volver a pedir")
    void unaALaVez() {
        conMensajes(INICIO, 20);
        when(compactarPort.compactar(any())).thenThrow(new IllegalStateException("modelo caido"));

        service.compactarEnSegundoPlano(ACTOR);
        service.compactarEnSegundoPlano(ACTOR);
        assertThat(enSegundoPlano).hasSize(1);

        enSegundoPlano.getFirst().run();
        service.compactarEnSegundoPlano(ACTOR);

        assertThat(enSegundoPlano).hasSize(2);
        verify(memoriaPort, never()).reemplazar(any(), any(), any());
    }

    @Test
    @DisplayName("cada persona compacta lo suyo: la de otra no espera")
    void otraPersonaNoEspera() {
        var otra = UserId.of(UUID.fromString("22222222-2222-2222-2222-222222222222"));

        service.compactarEnSegundoPlano(ACTOR);
        service.compactarEnSegundoPlano(otra);

        assertThat(enSegundoPlano).hasSize(2);
        verify(compactarPort, times(0)).compactar(any());
    }

    @Test
    @DisplayName("borrar un recuerdo que no es suyo (o que no existe) es un 404, no un borrado silencioso")
    void borrarAjeno() {
        UUID ajeno = UUID.randomUUID();
        when(memoriaPort.olvidarRecuerdo(ACTOR, ajeno)).thenReturn(false);

        assertThatThrownBy(() -> service.borrarRecuerdo(ACTOR, ajeno)).isInstanceOf(NoSuchElementException.class);
    }
}
