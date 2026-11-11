package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.espiritu.CompletarPastillaRenacerUseCase;
import com.renaser.os.habits.application.ports.in.espiritu.EntregarResumenEspirituSinRedUseCase.ResultadoEntregaSinRed;
import com.renaser.os.habits.application.ports.in.espiritu.EntregarResumenEspirituUseCase.EntregarResumenEspirituCommand;
import com.renaser.os.habits.application.ports.out.espiritu.LoadRegistroEspirituPort;
import com.renaser.os.habits.application.ports.out.espiritu.SaveRegistroEspirituPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.domain.model.espiritu.EstadoRegistroEspiritu;
import com.renaser.os.habits.domain.model.espiritu.RegistroEspiritu;
import com.renaser.os.habits.domain.model.espiritu.RegistroEspirituId;
import com.renaser.os.shared.application.EntregaSinRedDePrueba;
import com.renaser.os.shared.domain.CodigoNoAplicado;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.NoAplicadoSinRedException;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.OrigenDeLaHora;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La Pastilla Renacer entregada sin red (spec 048, issue #186). La persona entrega el lunes 9 de noviembre a las 11:00 de Lima (16:00Z,
 * antes del mediodía, el plazo) y la app la envía cuando vuelve la señal: ese mismo día a las 13:00, o el martes 10 a las 08:00.
 */
class PastillaSinRedServiceTest {

    private static final String LIMA = "America/Lima";
    /** El audio del día 3 se desbloqueó el lunes 9 a las 07:05 de Lima y su plazo es el mediodía de ese día. */
    private static final Instant DESBLOQUEO = Instant.parse("2026-11-09T12:05:00Z");
    private static final Instant MEDIODIA = Instant.parse("2026-11-09T17:00:00Z");
    private static final LocalDate LUNES = LocalDate.of(2026, 11, 9);
    /** 11:00 del lunes en Lima: dentro del plazo. */
    private static final Instant HECHO = Instant.parse("2026-11-09T16:00:00Z");
    /** 13:00 del lunes en Lima: pasado el plazo. */
    private static final Instant LLEGA_EL_MISMO_DIA = Instant.parse("2026-11-09T18:00:00Z");
    /** 08:00 del martes en Lima. */
    private static final Instant LLEGA_AL_OTRO_DIA = Instant.parse("2026-11-10T13:00:00Z");
    private static final UUID ENVIO = UUID.fromString("5f0c1f4e-2d0a-4b7e-9d77-0c9f3f6b1a10");
    private static final String RESUMEN = "Entendí que la calma se entrena";
    private static final int DIA_DEL_AUDIO = 3;

    private final LoadRegistroEspirituPort loadPort = mock(LoadRegistroEspirituPort.class);
    private final SaveRegistroEspirituPort savePort = mock(SaveRegistroEspirituPort.class);
    private final ConsultarProgresoParticipanteHabitsPort progresoPort = mock(ConsultarProgresoParticipanteHabitsPort.class);
    private final CompletarPastillaRenacerUseCase espejo = mock(CompletarPastillaRenacerUseCase.class);
    /** Sin stubbing: {@code TransactionTemplate} con {@code getTransaction() == null} corre el callback directo. */
    private final PlatformTransactionManager transacciones = mock(PlatformTransactionManager.class);

    private EntregaSinRedDePrueba entrega;
    private UserId persona;
    private RegistroEspiritu registro;

    @BeforeEach
    void preparar() {
        persona = UserId.of(UUID.randomUUID());
        registro = RegistroEspiritu.desbloquear(RegistroEspirituId.of(UUID.randomUUID()), persona, DIA_DEL_AUDIO, DESBLOQUEO,
                MEDIODIA, DESBLOQUEO);
        when(progresoPort.deParticipante(persona)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(9, LIMA, RolParticipante.TRAINEE, false, true)));
        when(loadPort.porParticipanteYDia(persona, DIA_DEL_AUDIO)).thenAnswer(inv -> Optional.of(registro));
        when(savePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void terminar() {
        if (entrega != null) {
            entrega.terminar();
        }
    }

    private PastillaSinRedService servicioConLlegada(Instant llegada) {
        FixedClock reloj = FixedClock.at(llegada);
        entrega = EntregaSinRedDePrueba.nueva(reloj);
        return new PastillaSinRedService(progresoPort, loadPort, savePort, espejo, entrega.puerto(), reloj, transacciones);
    }

    private EntregarResumenEspirituCommand comando() {
        return new EntregarResumenEspirituCommand(persona, DIA_DEL_AUDIO, RESUMEN);
    }

    private static HechoSinRed envio(UUID id, Instant hechoEn) {
        return new HechoSinRed(hechoEn, id, OrigenDeLaHora.RELOJ_DEL_SISTEMA);
    }

    private void exigirNoAplicado(PastillaSinRedService servicio, HechoSinRed sinRed, CodigoNoAplicado esperado) {
        assertThatThrownBy(() -> servicio.entregar(comando(), sinRed)).isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                e -> assertThat(e.codigo()).isEqualTo(esperado));
        verify(savePort, never()).save(any());
        verify(espejo, never()).completarSinRed(any(), any(), any(), any(), anyBoolean());
        assertThat(entrega.noAplicado(sinRed.idCliente())).contains(esperado);
    }

    @Test
    @DisplayName("la entregada a las 11:00 que llega a las 13:00 cuenta a tiempo: ENTREGADO con su hora real, y el hábito se cierra con ella")
    void llegaTardePeroSeHizoATiempo() { // spec 048 FR-010
        var servicio = servicioConLlegada(LLEGA_EL_MISMO_DIA);

        ResultadoEntregaSinRed resultado = servicio.entregar(comando(), envio(ENVIO, HECHO));

        assertThat(resultado).isEqualTo(new ResultadoEntregaSinRed(true, null));
        assertThat(registro.estado()).isEqualTo(EstadoRegistroEspiritu.ENTREGADO);
        assertThat(registro.entregadoEn()).isEqualTo(HECHO);
        assertThat(registro.resumenTexto()).isEqualTo(RESUMEN);
        verify(savePort).save(registro);
        verify(espejo).completarSinRed(persona, RESUMEN, LUNES, HECHO, true);
        var rastro = entrega.registro(ENVIO).orElseThrow();
        assertThat(rastro.tipo()).isEqualTo("ENTREGAR_PASTILLA");
        assertThat(rastro.recurso()).isEqualTo("pastilla:3");
        assertThat(rastro.hechoEn()).isEqualTo(HECHO);
        assertThat(rastro.registroId()).isEqualTo(registro.id().value());
    }

    @Test
    @DisplayName("la que llega al día siguiente, con el audio ya PERDIDO por la lectura perezosa, queda a tiempo (hoy: 409 «…: PERDIDO»)")
    void llegaAlOtroDiaYElServidorYaLaDabaPorPerdida() { // spec 048 FR-010
        registro.marcarPerdido(LLEGA_AL_OTRO_DIA.minus(Duration.ofMinutes(5)));
        var servicio = servicioConLlegada(LLEGA_AL_OTRO_DIA);

        ResultadoEntregaSinRed resultado = servicio.entregar(comando(), envio(ENVIO, HECHO));

        assertThat(resultado.aTiempo()).isTrue();
        assertThat(registro.estado()).isEqualTo(EstadoRegistroEspiritu.ENTREGADO);
        assertThat(registro.entregadoEn()).isEqualTo(HECHO);
        verify(espejo).completarSinRed(persona, RESUMEN, LUNES, HECHO, true);
    }

    @Test
    @DisplayName("hecha después del mediodía es tardía como con red: el texto queda y el estado no cambia; el hábito sí se cierra")
    void tardiaSigueSiendoTardia() { // spec 048 FR-010
        Instant tarde = Instant.parse("2026-11-09T17:30:00Z");
        var servicio = servicioConLlegada(LLEGA_AL_OTRO_DIA);

        ResultadoEntregaSinRed resultado = servicio.entregar(comando(), envio(ENVIO, tarde));

        assertThat(resultado.aTiempo()).isFalse();
        assertThat(registro.estado()).isEqualTo(EstadoRegistroEspiritu.PENDIENTE);
        assertThat(registro.resumenTexto()).isEqualTo(RESUMEN);
        verify(espejo).completarSinRed(persona, RESUMEN, LUNES, tarde, true);
    }

    @Test
    @DisplayName("regla 02: hecha a las 21:40 de Lima (02:40Z del día siguiente) es del lunes, no del martes: se aplica, tardía")
    void laMadrugadaUtcEsElDiaLocalAnterior() { // spec 048 FR-010
        Instant nocheDeLima = Instant.parse("2026-11-10T02:40:00Z");
        var servicio = servicioConLlegada(LLEGA_AL_OTRO_DIA);

        ResultadoEntregaSinRed resultado = servicio.entregar(comando(), envio(ENVIO, nocheDeLima));

        assertThat(resultado.aTiempo()).isFalse();
        verify(espejo).completarSinRed(persona, RESUMEN, LUNES, nocheDeLima, true);
    }

    @Test
    @DisplayName("D29: hecha el martes (el día de ese audio ya cerró) no se aplica: FUERA_DE_SU_DIA, sin tocar nada")
    void elDiaYaCerro() { // spec 048 FR-016
        var servicio = servicioConLlegada(LLEGA_AL_OTRO_DIA);

        exigirNoAplicado(servicio, envio(ENVIO, Instant.parse("2026-11-10T12:00:00Z")), CodigoNoAplicado.FUERA_DE_SU_DIA);
        assertThat(registro.estado()).isEqualTo(EstadoRegistroEspiritu.PENDIENTE);
        assertThat(registro.resumenTexto()).isNull();
    }

    @Test
    @DisplayName("un día sin audio desbloqueado es RECURSO_YA_NO_EXISTE (409 con código), no un 404 que la cola reintentaría para siempre")
    void sinRegistroDeEseDia() { // spec 048 FR-016
        when(loadPort.porParticipanteYDia(persona, DIA_DEL_AUDIO)).thenReturn(Optional.empty());

        exigirNoAplicado(servicioConLlegada(LLEGA_EL_MISMO_DIA), envio(ENVIO, HECHO), CodigoNoAplicado.RECURSO_YA_NO_EXISTE);
    }

    @Test
    @DisplayName("D13: si ese día ya estaba entregado antes de lo que hiciste es YA_ESTABA_HECHO; si se entregó después, gana lo más nuevo")
    void yaEntregadaDesdeOtroLado() { // spec 048 FR-016
        registro.entregar("desde otro teléfono", HECHO.minus(Duration.ofMinutes(30)));
        exigirNoAplicado(servicioConLlegada(LLEGA_EL_MISMO_DIA), envio(ENVIO, HECHO), CodigoNoAplicado.YA_ESTABA_HECHO);
        assertThat(registro.resumenTexto()).isEqualTo("desde otro teléfono");

        RegistroEspiritu otra = RegistroEspiritu.desbloquear(RegistroEspirituId.of(UUID.randomUUID()), persona, DIA_DEL_AUDIO,
                DESBLOQUEO, MEDIODIA, DESBLOQUEO);
        otra.entregar("más nueva", HECHO.plus(Duration.ofMinutes(30)));
        registro = otra;
        exigirNoAplicado(servicioConLlegada(LLEGA_EL_MISMO_DIA), envio(UUID.randomUUID(), HECHO),
                CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR);
    }

    @Test
    @DisplayName("el mismo envío dos veces se registra una sola vez y los dos responden lo mismo; el hábito se cierra una vez")
    void reintentoEsUnaSolaEntrega() { // spec 048 FR-008
        var servicio = servicioConLlegada(LLEGA_AL_OTRO_DIA);

        var primero = servicio.entregar(comando(), envio(ENVIO, HECHO));
        var segundo = servicio.entregar(comando(), envio(ENVIO, HECHO));

        assertThat(segundo).isEqualTo(primero);
        verify(savePort, times(1)).save(any());
        verify(espejo, times(1)).completarSinRed(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("un envío que no se aplicó responde igual al repetirse (el mismo 409) y sigue sin tocar nada")
    void reintentoDeLoQueNoSeAplico() { // spec 048 FR-008
        var servicio = servicioConLlegada(LLEGA_AL_OTRO_DIA);
        HechoSinRed cerrado = envio(ENVIO, Instant.parse("2026-11-10T12:00:00Z"));
        assertThatThrownBy(() -> servicio.entregar(comando(), cerrado)).isInstanceOf(NoAplicadoSinRedException.class);

        assertThatThrownBy(() -> servicio.entregar(comando(), cerrado)).isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.FUERA_DE_SU_DIA));
        verify(savePort, never()).save(any());
    }

    @Test
    @DisplayName("una hora del futuro no adelanta nada: se cuenta con la hora de llegada y el hábito queda sin puntos (HORA_FUTURA)")
    void horaFutura() { // spec 048 FR-015
        Instant ahora = Instant.parse("2026-11-09T15:00:00Z");
        var servicio = servicioConLlegada(ahora);

        ResultadoEntregaSinRed resultado = servicio.entregar(comando(), envio(ENVIO, ahora.plus(Duration.ofMinutes(10))));

        assertThat(resultado).isEqualTo(new ResultadoEntregaSinRed(true, "HORA_FUTURA"));
        assertThat(registro.entregadoEn()).isEqualTo(ahora);
        verify(espejo).completarSinRed(persona, RESUMEN, LUNES, ahora, false);
        assertThat(entrega.registro(ENVIO).orElseThrow().motivoSinPuntos()).isEqualTo("HORA_FUTURA");
    }

    @Test
    @DisplayName("llegar a más de 48 h solo quita los puntos del hábito: la Pastilla se registra a tiempo (nada rebota)")
    void masDeCuarentaYOchoHoras() { // spec 048 FR-015
        Instant jueves = Instant.parse("2026-11-12T13:00:00Z");
        var servicio = servicioConLlegada(jueves);

        ResultadoEntregaSinRed resultado = servicio.entregar(comando(), envio(ENVIO, HECHO));

        assertThat(resultado).isEqualTo(new ResultadoEntregaSinRed(true, "LLEGO_DESPUES_DE_48_H"));
        assertThat(registro.estado()).isEqualTo(EstadoRegistroEspiritu.ENTREGADO);
        verify(espejo).completarSinRed(persona, RESUMEN, LUNES, HECHO, false);
    }

    @Test
    @DisplayName("si el hábito no se puede cerrar, la Pastilla igual queda entregada (el espejo es best-effort, como con red)")
    void unFalloDelHabitoNoDeshaceLaEntrega() {
        doThrow(new IllegalStateException("el hábito falló")).when(espejo).completarSinRed(any(), any(), any(), any(), anyBoolean());
        var servicio = servicioConLlegada(LLEGA_EL_MISMO_DIA);

        assertThatCode(() -> servicio.entregar(comando(), envio(ENVIO, HECHO))).doesNotThrowAnyException();

        assertThat(registro.estado()).isEqualTo(EstadoRegistroEspiritu.ENTREGADO);
        assertThat(entrega.registro(ENVIO)).isPresent();
    }

    @Test
    @DisplayName("una cuenta suspendida o un rol que no es aprendiz es 403 también con las cabeceras")
    void suspendidaOSinProgramaNoEntrega() {
        var servicio = servicioConLlegada(LLEGA_EL_MISMO_DIA);
        when(progresoPort.deParticipante(persona)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(9, LIMA, RolParticipante.TRAINEE, true, true)));
        assertThatThrownBy(() -> servicio.entregar(comando(), envio(ENVIO, HECHO))).isInstanceOf(NotAuthorizedException.class);

        when(progresoPort.deParticipante(persona)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(9, LIMA, RolParticipante.ADMIN, false, false)));
        assertThatThrownBy(() -> servicio.entregar(comando(), envio(UUID.randomUUID(), HECHO))).isInstanceOf(NotAuthorizedException.class);

        verify(savePort, never()).save(any());
        verify(espejo, never()).completarSinRed(any(), eq(RESUMEN), any(), any(), anyBoolean());
    }
}
