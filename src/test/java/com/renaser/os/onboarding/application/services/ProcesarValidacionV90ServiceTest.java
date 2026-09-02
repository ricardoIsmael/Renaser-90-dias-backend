package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.out.grabacionv90.LoadGrabacionV90Port;
import com.renaser.os.onboarding.application.ports.out.grabacionv90.SaveGrabacionV90Port;
import com.renaser.os.onboarding.application.ports.out.grabacionv90.ValidacionIAPort;
import com.renaser.os.onboarding.application.ports.out.grabacionv90.ValidacionIAPort.ResultadoValidacionV90;
import com.renaser.os.onboarding.domain.model.grabacionv90.EstadoIAv90;
import com.renaser.os.onboarding.domain.model.grabacionv90.GrabacionV90;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Separado de {@link GrabacionV90ServiceTest} porque {@code procesar()} pasó a
 * {@link ProcesarValidacionV90Service}, su propia clase (E-34, evita la dependencia
 * circular con el adapter {@code @Async} — ver {@code docs/BITACORA_ERRORES.md}).
 */
@ExtendWith(MockitoExtension.class)
class ProcesarValidacionV90ServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Mock
    private LoadGrabacionV90Port loadGrabacionPort;
    @Mock
    private SaveGrabacionV90Port saveGrabacionPort;
    @Mock
    private ValidacionIAPort validacionIAPort;

    private ProcesarValidacionV90Service service;
    private UserId usuarioId;

    @BeforeEach
    void setUp() {
        service = new ProcesarValidacionV90Service(loadGrabacionPort, saveGrabacionPort, validacionIAPort, CLOCK);
        usuarioId = UserId.of(UUID.randomUUID());
    }

    private GrabacionV90 grabacionGrabadaDe(UserId propietario) {
        GrabacionV90 g = GrabacionV90.crearSlot(propietario, "FASE_1", "MENTE", (short) 0, "v90_mente_0", CLOCK);
        g.marcarGrabada(1L, null, "transcripcion", CLOCK);
        return g;
    }

    @Test
    @DisplayName("procesar(): IA aprueba -> APROBADA")
    void procesarConAprobacion() {
        GrabacionV90 g = grabacionGrabadaDe(usuarioId);
        g.procesarIntentoDeValidacion(CLOCK);
        when(loadGrabacionPort.porId(9L)).thenReturn(Optional.of(g));
        when(validacionIAPort.validar(any()))
                .thenReturn(new ResultadoValidacionV90(ResultadoValidacionV90.Estado.APROBADA, "{\"ok\":true}"));
        when(saveGrabacionPort.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        service.procesar(usuarioId, 9L);

        assertThat(g.estadoIa()).isEqualTo(EstadoIAv90.APROBADA);
        verify(saveGrabacionPort).guardar(g);
    }

    @Test
    @DisplayName("procesar(): IA no disponible -> registrarSinResultado (vuelve a PENDIENTE si quedan intentos)")
    void procesarSinResultado() {
        GrabacionV90 g = grabacionGrabadaDe(usuarioId);
        g.procesarIntentoDeValidacion(CLOCK);
        when(loadGrabacionPort.porId(9L)).thenReturn(Optional.of(g));
        when(validacionIAPort.validar(any())).thenReturn(ResultadoValidacionV90.noDisponible());
        when(saveGrabacionPort.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        service.procesar(usuarioId, 9L);

        assertThat(g.estadoIa()).isEqualTo(EstadoIAv90.PENDIENTE);
    }

    @Test
    @DisplayName("procesar(): grabacion de otro usuario (o borrada) -> se ignora, nunca guarda")
    void procesarIgnoraSiNoCoincideElUsuario() {
        UserId otro = UserId.of(UUID.randomUUID());
        GrabacionV90 deOtro = grabacionGrabadaDe(otro);
        when(loadGrabacionPort.porId(9L)).thenReturn(Optional.of(deOtro));

        service.procesar(usuarioId, 9L);

        verify(saveGrabacionPort, never()).guardar(any());
        verify(validacionIAPort, never()).validar(any());
    }

    /**
     * C-1/C-3 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html): con Gemini
     * real, {@link ValidacionIAPort#validar} puede lanzar (timeout, error de red) — a
     * diferencia del {@code NoOpValidacionIAAdapter} de hoy, que nunca lanza. Antes de este
     * cambio, {@code procesar()} no capturaba esa excepcion: se propagaba sin llegar nunca a
     * {@code saveGrabacionPort.guardar}, y la grabacion quedaba en PROCESANDO para siempre.
     * Ahora se trata igual que NO_DISPONIBLE.
     */
    @Test
    @DisplayName("procesar(): la IA lanza -> se trata como NO_DISPONIBLE, el registro no queda atrapado en PROCESANDO")
    void procesarSiLaIaLanzaNoDejaElRegistroAtrapado() {
        GrabacionV90 g = grabacionGrabadaDe(usuarioId);
        g.procesarIntentoDeValidacion(CLOCK);
        when(loadGrabacionPort.porId(9L)).thenReturn(Optional.of(g));
        when(validacionIAPort.validar(any())).thenThrow(new RuntimeException("timeout simulado de Gemini"));
        when(saveGrabacionPort.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        service.procesar(usuarioId, 9L);

        assertThat(g.estadoIa()).isNotEqualTo(EstadoIAv90.PROCESANDO);
        assertThat(g.estadoIa()).isEqualTo(EstadoIAv90.PENDIENTE);
        verify(saveGrabacionPort).guardar(g);
    }
}
