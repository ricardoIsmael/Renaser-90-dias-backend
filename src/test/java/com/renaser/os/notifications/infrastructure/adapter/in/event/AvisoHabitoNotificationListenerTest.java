package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.habits.api.AvisoHabitoDebidoEvent;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.EntregaPush;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * E-302 / D-184: el aviso de habito respeta el recordatorio que el aprendiz configuro. Con el
 * codigo anterior el listener llamaba a {@code emitir(command)} sin entrega (push a todos los
 * dispositivos) sin importar la preferencia, y estas verificaciones fallaban.
 *
 * <p>Spec 007 (FR-012, FR-014, FR-015, FR-016): el texto dice la hora del habito en la zona del
 * programa, con tildes; el push lleva la etiqueta {@code habito-{id}-{tipo}}; y un aviso cuyo
 * momento ya paso ni se guarda ni se empuja. El reloj esta en la madrugada UTC a proposito: las
 * 21:00 de Lima son las 02:00 UTC del dia siguiente.
 */
@ExtendWith(MockitoExtension.class)
class AvisoHabitoNotificationListenerTest {

    @Mock
    private EmitirNotificacionUseCase emitirNotificacionUseCase;

    private static final UUID HABITO = UUID.fromString("0f000000-0000-4000-8000-000000000001");
    /** 20:50 del 5 en Lima: el barrido que emitio el aviso. */
    private static final Instant EMITIDO = Instant.parse("2026-09-06T01:50:00Z");
    /** 21:00 del 5 en Lima: la hora del habito. */
    private static final Instant A_LAS_21_EN_LIMA = Instant.parse("2026-09-06T02:00:00Z");

    private AvisoHabitoNotificationListener listener() {
        return new AvisoHabitoNotificationListener(emitirNotificacionUseCase, FixedClock.at(EMITIDO));
    }

    private static AvisoHabitoDebidoEvent aviso(String tipo, Boolean activo, Integer minutos) {
        return avisoDe(tipo, activo, minutos, HABITO, "BODY");
    }

    private static AvisoHabitoDebidoEvent avisoDe(String tipo, Boolean activo, Integer minutos, UUID habitoId,
                                                  String categoria) {
        return new AvisoHabitoDebidoEvent(UUID.randomUUID(), UserId.of(UUID.randomUUID()), "Meditar", tipo, 10, 10,
                UUID.randomUUID(), activo, minutos, EMITIDO, habitoId, categoria, A_LAS_21_EN_LIMA, "America/Lima",
                null);
    }

    /** Spec 008: el hábito usa el esquema por día y el aviso es de ese día de la semana (ISO). */
    private static AvisoHabitoDebidoEvent avisoDelDia(String tipo, int diaIso) {
        return new AvisoHabitoDebidoEvent(UUID.randomUUID(), UserId.of(UUID.randomUUID()), "Gimnasio", tipo, 10, 10,
                UUID.randomUUID(), true, 30, EMITIDO, HABITO, "BODY", A_LAS_21_EN_LIMA, "America/Lima", diaIso);
    }

    /** Un evento publicado antes de la spec 007: sin momento ni zona. */
    private static AvisoHabitoDebidoEvent avisoViejo(String tipo) {
        return new AvisoHabitoDebidoEvent(UUID.randomUUID(), UserId.of(UUID.randomUUID()), "Meditar", tipo, 10, 10,
                UUID.randomUUID(), null, null, EMITIDO, HABITO, "BODY", null, null, null);
    }

    private EmitirNotificacionCommand comandoEmitido() {
        ArgumentCaptor<EmitirNotificacionCommand> captor = ArgumentCaptor.forClass(EmitirNotificacionCommand.class);
        verify(emitirNotificacionUseCase).emitir(captor.capture(), any(), any(), any());
        return captor.getValue();
    }

    private String etiquetaEmitida() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(emitirNotificacionUseCase).emitir(any(), any(), captor.capture(), any());
        return captor.getValue();
    }

    private Instant venceEnEmitido() {
        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(emitirNotificacionUseCase).emitir(any(), any(), any(), captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("E-645: el push de inicio caduca a la hora del habito (21:00 de Lima = 02:00 UTC del dia siguiente)")
    void inicioCaducaALaHoraDelHabito() {
        listener().on(aviso("INICIO", true, 10));

        assertThat(venceEnEmitido()).isEqualTo(A_LAS_21_EN_LIMA);
    }

    @Test
    @DisplayName("E-645: el de vencimiento caduca en el plazo de entrega que manda `habits`, no en la hora del habito")
    void vencimientoCaducaEnElPlazo() {
        Instant plazo = Instant.parse("2026-09-06T03:30:00Z");     // 22:30 en Lima
        listener().on(new AvisoHabitoDebidoEvent(UUID.randomUUID(), UserId.of(UUID.randomUUID()), "Meditar",
                "POR_VENCER", 10, 10, UUID.randomUUID(), true, 30, EMITIDO, HABITO, "BODY", plazo, "America/Lima",
                null));

        assertThat(venceEnEmitido()).isEqualTo(plazo);
    }

    @Test
    @DisplayName("E-645: con el esquema por dia caduca igual: la hora del aviso de ese dia")
    void inicioPorDiaCaducaALaHoraDelAviso() {
        listener().on(avisoDelDia("INICIO", 6));

        assertThat(venceEnEmitido()).isEqualTo(A_LAS_21_EN_LIMA);
    }

    @Test
    @DisplayName("E-645: un evento anterior a la spec 007 (sin momento) caduca en lo que faltaba, truncado al minuto")
    void eventoViejoCaducaEnLoQueFaltaba() {
        listener().on(avisoViejo("INICIO"));

        assertThat(venceEnEmitido()).isEqualTo(A_LAS_21_EN_LIMA);
    }

    @Test
    @DisplayName("E-645: el instante de caducidad es el mismo con el que se decide si el aviso todavia sirve")
    void elUltimoInstanteQueSirveEsElQueCaduca() {
        // Un segundo antes de las 21:00 el aviso sale, y sale con caducidad a las 21:00: el proveedor no lo
        // entrega ni un instante despues de la hora que el aviso dice.
        new AvisoHabitoNotificationListener(emitirNotificacionUseCase,
                FixedClock.at(A_LAS_21_EN_LIMA.minusSeconds(1))).on(aviso("INICIO", true, 10));

        assertThat(venceEnEmitido()).isEqualTo(A_LAS_21_EN_LIMA);
    }

    @Test
    @DisplayName("spec 008 R6: con el esquema por dia el push de inicio lleva la etiqueta del dia, la de la alarma local")
    void inicioPorDiaLlevaLaEtiquetaDelDia() {
        listener().on(avisoDelDia("INICIO", 6));

        assertThat(etiquetaEmitida()).isEqualTo("habito-" + HABITO + "-d6-0");
    }

    @Test
    @DisplayName("spec 008 R6: sin esquema por dia el push de inicio lleva la etiqueta de siempre")
    void inicioSinEsquemaPorDiaLlevaLaEtiquetaDeSiempre() {
        listener().on(aviso("INICIO", true, 30));

        assertThat(etiquetaEmitida()).isEqualTo("habito-" + HABITO + "-inicio");
    }

    @Test
    @DisplayName("spec 008 R6: el de vencimiento no cambia con el esquema por dia")
    void vencimientoPorDiaConservaSuEtiqueta() {
        listener().on(avisoDelDia("POR_VENCER", 6));

        assertThat(etiquetaEmitida()).isEqualTo("habito-" + HABITO + "-por-vencer");
    }

    @Test
    @DisplayName("recordatorio apagado -> fila en la bandeja, sin push")
    void apagadoSoloBandeja() {
        listener().on(aviso("INICIO", false, null));

        ArgumentCaptor<EmitirNotificacionCommand> captor = ArgumentCaptor.forClass(EmitirNotificacionCommand.class);
        verify(emitirNotificacionUseCase).emitir(captor.capture(), eq(EntregaPush.NINGUNO), any(), any());
        assertThat(captor.getValue().tipo()).isEqualTo(TipoNotificacion.RECORDATORIO_HABITO);
    }

    @Test
    @DisplayName("D-217: inicio con alarma local -> respaldo: navegador y telefonos sin alarmas confirmadas")
    void inicioConAlarmaLocal() {
        listener().on(aviso("INICIO", true, 30));

        verify(emitirNotificacionUseCase).emitir(any(), eq(EntregaPush.RESPALDO_DE_ALARMA_LOCAL), any(), any());
    }

    @Test
    @DisplayName("vencimiento con recordatorio encendido -> push a todos (el telefono no tiene alarma para eso)")
    void vencimientoATodos() {
        listener().on(aviso("POR_VENCER", true, 30));

        verify(emitirNotificacionUseCase).emitir(any(), eq(EntregaPush.TODOS), any(), any());
    }

    @Test
    @DisplayName("D-218: el aviso de inicio lleva la ruta del habito con su dimension")
    void inicioLlevaLaRutaDelHabito() {
        listener().on(aviso("INICIO", true, 0));

        assertThat(comandoEmitido().rutaApp()).isEqualTo("/habitos/" + HABITO + "?dimension=BODY");
    }

    @Test
    @DisplayName("D-218: el de vencimiento tambien abre el habito")
    void vencimientoLlevaLaRuta() {
        listener().on(aviso("POR_VENCER", null, null));

        assertThat(comandoEmitido().rutaApp()).isEqualTo("/habitos/" + HABITO + "?dimension=BODY");
    }

    @Test
    @DisplayName("D-218: sin categoria, solo el habito; evento viejo del outbox sin habito -> sin ruta, como antes")
    void rutaSinCategoriaYEventoViejo() {
        listener().on(avisoDe("INICIO", null, null, HABITO, null));
        assertThat(comandoEmitido().rutaApp()).isEqualTo("/habitos/" + HABITO);
    }

    @Test
    @DisplayName("D-218: un evento anterior a D-218 (sin habitoId) no inventa ruta")
    void eventoViejoSinRuta() {
        listener().on(avisoDe("INICIO", null, null, null, null));
        assertThat(comandoEmitido().rutaApp()).isNull();
    }

    @Test
    @DisplayName("D-218: una categoria con caracteres raros va codificada en la ruta")
    void categoriaCodificada() {
        listener().on(avisoDe("INICIO", null, null, HABITO, "VIDA Y NEGOCIO"));
        assertThat(comandoEmitido().rutaApp()).isEqualTo("/habitos/" + HABITO + "?dimension=VIDA+Y+NEGOCIO");
    }

    // ─── Spec 007: texto con la hora, etiqueta y avisos vencidos ───────────────────────────────

    @Test
    @DisplayName("FR-012/FR-016: el inicio dice la hora de Lima del habito, con tildes y tuteo")
    void textoDelInicio() {
        listener().on(aviso("INICIO", true, 10));

        EmitirNotificacionCommand comando = comandoEmitido();
        assertThat(comando.titulo()).isEqualTo("Tu hábito está por empezar");
        assertThat(comando.cuerpo()).isEqualTo("Meditar: te toca a las 21:00. En juego: 10 puntos.");
        assertThat(comando.tipo()).isEqualTo(TipoNotificacion.RECORDATORIO_HABITO);
    }

    @Test
    @DisplayName("FR-012/FR-016: el vencimiento dice a que hora vence, con tildes")
    void textoDelVencimiento() {
        listener().on(aviso("POR_VENCER", null, null));

        EmitirNotificacionCommand comando = comandoEmitido();
        assertThat(comando.titulo()).isEqualTo("Se te vence un hábito");
        assertThat(comando.cuerpo()).isEqualTo("Meditar vence a las 21:00. En juego: 10 puntos.");
    }

    @Test
    @DisplayName("FR-014: el push de un habito lleva la etiqueta habito-{id}-inicio / -por-vencer")
    void etiquetaDelPush() {
        listener().on(aviso("INICIO", true, 10));

        assertThat(etiquetaEmitida()).isEqualTo("habito-" + HABITO + "-inicio");
    }

    @Test
    @DisplayName("FR-014: la etiqueta del vencimiento es otra, para que no reemplace al aviso de inicio")
    void etiquetaDelVencimiento() {
        listener().on(aviso("POR_VENCER", null, null));

        assertThat(etiquetaEmitida()).isEqualTo("habito-" + HABITO + "-por-vencer");
    }

    @Test
    @DisplayName("FR-014: un evento viejo del outbox (sin habitoId) sale sin etiqueta, como antes")
    void sinHabitoSinEtiqueta() {
        listener().on(avisoDe("INICIO", null, null, null, null));

        assertThat(etiquetaEmitida()).isNull();
    }

    @Test
    @DisplayName("FR-015: con el momento ya pasado no se emite nada (ni bandeja ni push)")
    void avisoVencidoNoSeEmite() {
        new AvisoHabitoNotificationListener(emitirNotificacionUseCase, FixedClock.at(A_LAS_21_EN_LIMA))
                .on(aviso("INICIO", true, 10));

        verifyNoInteractions(emitirNotificacionUseCase);
    }

    @Test
    @DisplayName("FR-015: un reintento tardio del outbox, media hora despues, tampoco se emite")
    void reintentoTardioNoSeEmite() {
        new AvisoHabitoNotificationListener(emitirNotificacionUseCase,
                FixedClock.at(A_LAS_21_EN_LIMA.plusSeconds(1800))).on(aviso("POR_VENCER", true, 10));

        verifyNoInteractions(emitirNotificacionUseCase);
    }

    @Test
    @DisplayName("evento anterior a la spec 007 (sin momento): sigue con minutos y se descarta si ya empezo")
    void eventoViejoConMinutos() {
        listener().on(avisoViejo("INICIO"));

        assertThat(comandoEmitido().cuerpo()).isEqualTo("Meditar empieza en 10 min. En juego: 10 puntos.");
    }

    @Test
    @DisplayName("evento anterior a la spec 007 reentregado despues de su hora: no se emite")
    void eventoViejoVencido() {
        new AvisoHabitoNotificationListener(emitirNotificacionUseCase, FixedClock.at(A_LAS_21_EN_LIMA))
                .on(avisoViejo("INICIO"));

        verifyNoInteractions(emitirNotificacionUseCase);
    }
}
