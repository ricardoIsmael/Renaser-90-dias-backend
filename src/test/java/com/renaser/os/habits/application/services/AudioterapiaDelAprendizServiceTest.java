package com.renaser.os.habits.application.services;

import com.renaser.os.evidence.api.TipoEvidencia;
import com.renaser.os.habits.api.AudioterapiaDelAprendizPort.AudioterapiaDeHoy;
import com.renaser.os.habits.application.ports.in.audioterapia.ConsultarAudioterapiaSemanalUseCase;
import com.renaser.os.habits.application.ports.in.audioterapia.ConsultarAudioterapiaSemanalUseCase.AudioDeLaSemana;
import com.renaser.os.habits.application.ports.in.audioterapia.ConsultarAudioterapiaSemanalUseCase.EsperandoContenido;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase.TrackDelDiaConCatalogo;
import com.renaser.os.habits.application.ports.in.registro.SubirEvidenciaRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.SubirEvidenciaRegistroUseCase.SubirEvidenciaRegistroCommand;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La Audioterapia vista desde otro modulo (D-171). Se entrega como la app: evidencia de TEXTO y
 * completar, nunca por la Pastilla Renacer; y se valida todo antes de subir nada.
 */
class AudioterapiaDelAprendizServiceTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final Instant AHORA = Instant.parse("2026-09-26T03:00:00Z");
    private static final LocalDate VIERNES = LocalDate.of(2026, 9, 25);
    private static final String TEXTO = "¿Qué sentiste después de escuchar este audio?\nCalma";

    private final ConsultarAudioterapiaSemanalUseCase audioterapia = mock(ConsultarAudioterapiaSemanalUseCase.class);
    private final ConsultarTracksDelDiaConCatalogoUseCase tracks = mock(ConsultarTracksDelDiaConCatalogoUseCase.class);
    private final SubirEvidenciaRegistroUseCase subir = mock(SubirEvidenciaRegistroUseCase.class);
    private final CompletarRegistroUseCase completar = mock(CompletarRegistroUseCase.class);
    private final AudioterapiaDelAprendizService service = new AudioterapiaDelAprendizService(audioterapia, tracks,
            subir, completar);

    private static RegistroHabito registro() {
        return RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), APRENDIZ,
                HabitoId.of(UUID.randomUUID()), VIERNES, 20, TipoDia.DISCIPLINA, false, AHORA);
    }

    private static TrackDelDiaConCatalogo track(RegistroHabito registro, String clave) {
        return new TrackDelDiaConCatalogo(registro, "Audioterapia", TipoHabito.JOURNALING, null, null, null, null,
                false, false, clave);
    }

    private void conAudio() {
        when(audioterapia.consultar(APRENDIZ)).thenReturn(new AudioDeLaSemana(3, "Perdonar", "https://x", 22));
    }

    @Test
    @DisplayName("de hoy: el audio de la semana y el registro de hoy de AUDIO_THERAPY_WEEKLY, no otro")
    void deHoy() {
        conAudio();
        RegistroHabito audio = registro();
        when(tracks.consultarHoyDe(APRENDIZ)).thenReturn(List.of(track(registro(), "PASTILLA_RENACER"),
                track(audio, "AUDIO_THERAPY_WEEKLY")));

        assertThat(service.deHoyDe(APRENDIZ))
                .isEqualTo(new AudioterapiaDeHoy(3, "Perdonar", 22, audio.id().value(), "PENDIENTE"));
    }

    @Test
    @DisplayName("de hoy: sin audio esta semana y sin registro hoy, todo en null")
    void deHoySinNada() {
        when(audioterapia.consultar(APRENDIZ)).thenReturn(new EsperandoContenido());
        when(tracks.consultarHoyDe(APRENDIZ)).thenReturn(List.of());

        assertThat(service.deHoyDe(APRENDIZ)).isEqualTo(new AudioterapiaDeHoy(null, null, null, null, null));
    }

    @Test
    @DisplayName("entregar: primero la evidencia de TEXTO y despues completar, sobre el registro de hoy")
    void entrega() {
        conAudio();
        RegistroHabito audio = registro();
        when(tracks.consultarHoyDe(APRENDIZ)).thenReturn(List.of(track(audio, "AUDIO_THERAPY_WEEKLY")));
        RegistroHabito completado = registro();
        completado.completar(10, null, null, null, AHORA);
        when(completar.completar(any())).thenReturn(completado);

        assertThat(service.entregarRespuestas(APRENDIZ, audio.id().value(), TEXTO)).isEqualTo(10);

        var orden = inOrder(subir, completar);
        ArgumentCaptor<SubirEvidenciaRegistroCommand> evidencia = ArgumentCaptor.forClass(
                SubirEvidenciaRegistroCommand.class);
        orden.verify(subir).subir(evidencia.capture());
        orden.verify(completar).completar(new CompletarRegistroCommand(APRENDIZ, audio.id(), null, null));
        assertThat(evidencia.getValue().tipo()).isEqualTo(TipoEvidencia.TEXTO);
        assertThat(evidencia.getValue().contenidoTexto()).isEqualTo(TEXTO);
        assertThat(evidencia.getValue().registroId()).isEqualTo(audio.id());
    }

    @Test
    @DisplayName("entregar: un registro que ya no es el de hoy (paso la medianoche) no sube nada")
    void otroDia() {
        conAudio();
        when(tracks.consultarHoyDe(APRENDIZ)).thenReturn(List.of(track(registro(), "AUDIO_THERAPY_WEEKLY")));

        assertThatThrownBy(() -> service.entregarRespuestas(APRENDIZ, UUID.randomUUID(), TEXTO))
                .isInstanceOf(NoSuchElementException.class);
        verify(subir, never()).subir(any());
        verify(completar, never()).completar(any());
    }

    @Test
    @DisplayName("entregar: ya completada no sube una segunda evidencia")
    void yaCompletada() {
        conAudio();
        RegistroHabito audio = registro();
        audio.completar(10, null, null, null, AHORA);
        when(tracks.consultarHoyDe(APRENDIZ)).thenReturn(List.of(track(audio, "AUDIO_THERAPY_WEEKLY")));

        assertThatThrownBy(() -> service.entregarRespuestas(APRENDIZ, audio.id().value(), TEXTO))
                .isInstanceOf(IllegalStateException.class);
        verify(subir, never()).subir(any());
    }

    @Test
    @DisplayName("entregar: sin audio esta semana no hay nada que entregar")
    void sinAudio() {
        when(audioterapia.consultar(APRENDIZ)).thenReturn(new EsperandoContenido());

        assertThatThrownBy(() -> service.entregarRespuestas(APRENDIZ, UUID.randomUUID(), TEXTO))
                .isInstanceOf(NoSuchElementException.class);
        verify(subir, never()).subir(any());
    }
}
