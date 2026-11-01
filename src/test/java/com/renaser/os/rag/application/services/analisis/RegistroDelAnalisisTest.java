package com.renaser.os.rag.application.services.analisis;

import com.renaser.os.rag.api.AnalisisListoEvent;
import com.renaser.os.rag.application.ports.out.analisis.SaveAnalisisDelPeriodoPort;
import com.renaser.os.rag.domain.model.analisis.AnalisisDelPeriodo;
import com.renaser.os.rag.domain.model.analisis.AnalisisDelPeriodoId;
import com.renaser.os.rag.domain.model.analisis.ReglasDelAnalisis;
import com.renaser.os.rag.domain.model.analisis.TextoDelAnalisis;
import com.renaser.os.rag.domain.model.analisis.TipoDePeriodo;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El guardado y el aviso van juntos y en una transacción corta (spec 031): un análisis listo avisa una vez; nada más avisa. */
@ExtendWith(MockitoExtension.class)
class RegistroDelAnalisisTest {

    private static final Instant AHORA = Instant.parse("2026-10-26T06:00:00Z");
    private static final UserId ANA = UserId.of(UUID.randomUUID());

    @Mock
    private SaveAnalisisDelPeriodoPort guardar;
    @Mock
    private ApplicationEventPublisher publisher;

    private static AnalisisDelPeriodo pendiente() {
        return AnalisisDelPeriodo.pendiente(AnalisisDelPeriodoId.of(UUID.randomUUID()), ANA, Formacion.ESCUELA, TipoDePeriodo.MES, 3,
                LocalDate.of(2026, 10, 12), LocalDate.of(2026, 11, 8), FixedClock.at(AHORA));
    }

    @Test
    @DisplayName("un análisis listo que quedó guardado publica el aviso, con el id del análisis como clave y sin nada de su contenido")
    void avisaCuandoQuedaListo() {
        AnalisisDelPeriodo listo = pendiente();
        listo.completar(new TextoDelAnalisis("a", "b", "c", List.of("d")), "{}", FixedClock.at(AHORA));
        when(guardar.guardar(listo)).thenReturn(true);

        assertThat(new RegistroDelAnalisis(guardar, publisher).registrar(listo)).isTrue();

        ArgumentCaptor<AnalisisListoEvent> evento = ArgumentCaptor.forClass(AnalisisListoEvent.class);
        verify(publisher).publishEvent(evento.capture());
        assertThat(evento.getValue()).isEqualTo(new AnalisisListoEvent(listo.id().value(), ANA.value(), "MES", LocalDate.of(2026, 10, 12), AHORA));
    }

    @Test
    @DisplayName("si otro proceso ya había dejado el resultado, no se guarda ni se avisa dos veces")
    void noAvisaSiOtroGano() {
        AnalisisDelPeriodo listo = pendiente();
        listo.completar(new TextoDelAnalisis("a", "b", "c", List.of("d")), "{}", FixedClock.at(AHORA));
        when(guardar.guardar(listo)).thenReturn(false);

        assertThat(new RegistroDelAnalisis(guardar, publisher).registrar(listo)).isFalse();

        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("un intento fallido o un periodo sin datos se guardan sin avisar")
    void soloLoListoAvisa() {
        AnalisisDelPeriodo fallido = pendiente();
        fallido.fallar(ReglasDelAnalisis.POR_DEFECTO, FixedClock.at(AHORA));
        when(guardar.guardar(fallido)).thenReturn(true);

        new RegistroDelAnalisis(guardar, publisher).registrar(fallido);

        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("registrar es @Transactional: el aviso del outbox solo se entrega si se publica dentro de una transacción")
    void esTransaccional() throws NoSuchMethodException {
        assertThat(RegistroDelAnalisis.class.getDeclaredMethod("registrar", AnalisisDelPeriodo.class).getAnnotation(Transactional.class))
                .isNotNull();
    }
}
