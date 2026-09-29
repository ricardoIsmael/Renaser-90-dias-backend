package com.renaser.os.habits.domain.model.horario;

import com.renaser.os.habits.domain.model.aviso.AvisoHabito;
import com.renaser.os.habits.domain.model.aviso.CalculadoraAvisosHabito;
import com.renaser.os.habits.domain.model.aviso.TipoAvisoHabito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.habits.domain.model.registro.FaseOtorgamiento;
import com.renaser.os.habits.domain.model.registro.ResultadoOtorgamiento;
import com.renaser.os.habits.domain.model.registro.VentanaEntrega;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-230 (2026-09-29). Pedido del dueño, probando: <i>"que no se limite a eso... no que despertar sea
 * si o si en la mañana... hay gente que trabaja en la noche... no debes bloquearlo"</i>.
 *
 * <p>Cualquier habito va a la hora que la persona elija, y de punta a punta: se guarda, genera su
 * aviso y paga puntos. Los relojes van a horas UTC que caen en el dia local ANTERIOR en Lima
 * (regla 02: un reloj a las 10:00 UTC esconde justo el bug del cambio de dia).
 */
class HabitoACualquierHoraTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Instant CREADO = Instant.parse("2026-09-01T12:00:00Z");
    private static final UserId PARTICIPANTE = UserId.of(UUID.randomUUID());
    private static final CalculadoraAvisosHabito AVISOS =
            new CalculadoraAvisosHabito(Duration.ofMinutes(10), Duration.ofMinutes(30));

    private static PreferenciaHorario preferencia(HabitoId habito, LocalTime disparo) {
        return PreferenciaHorario.crear(PARTICIPANTE, habito, disparo, null, CREADO);
    }

    private static HorarioHabito catalogo(HabitoId habito, LocalTime disparo, LocalTime limite) {
        return HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()), habito, 1, 90, TipoDia.TODOS, disparo,
                limite, CREADO);
    }

    private static VentanaEntrega ventana(LocalDate fecha, HorarioResuelto horario) {
        return VentanaEntrega.calcular(fecha, horario.horaDisparo(), horario.horaLimite(), LIMA, null);
    }

    @Test
    @DisplayName("Despertar a las 22:00: se guarda, avisa a las 21:50 y paga 10 puntos a las 22:05 de Lima")
    void despertarDeNoche() {
        HabitoId despertar = HabitoId.of(UUID.randomUUID());
        // Despertar no tiene horario de catalogo (V26): solo la hora que eligio la persona.
        HorarioResuelto horario = HorarioResuelto.de(null, preferencia(despertar, LocalTime.of(22, 0)));
        VentanaEntrega ventana = ventana(LocalDate.of(2026, 9, 29), horario);

        // 02:55 UTC del 30 = 21:55 del 29 en Lima.
        List<AvisoHabito> avisos = AVISOS.debidosAhora(ventana, Instant.parse("2026-09-30T02:55:00Z"));
        assertThat(avisos).extracting(AvisoHabito::tipo).containsExactly(TipoAvisoHabito.INICIO);
        assertThat(avisos.getFirst().momento()).isEqualTo(Instant.parse("2026-09-30T03:00:00Z"));

        // 03:05 UTC del 30 = 22:05 del 29 en Lima.
        ResultadoOtorgamiento puntos = ResultadoOtorgamiento.calcular(ventana.instanteAncla(),
                Instant.parse("2026-09-30T03:05:00Z"), ventana.extension());
        assertThat(puntos.puntos()).isEqualTo(ResultadoOtorgamiento.PUNTOS_COMPLETOS);
        assertThat(ventana.plazoEvidencia()).isBeforeOrEqualTo(Instant.parse("2026-09-30T05:00:00Z"));
    }

    @Test
    @DisplayName("Despertar a las 03:00: avisa a las 02:50 de Lima y paga 10 puntos a las 03:20")
    void despertarDeMadrugada() {
        HabitoId despertar = HabitoId.of(UUID.randomUUID());
        HorarioResuelto horario = HorarioResuelto.de(null, preferencia(despertar, LocalTime.of(3, 0)));
        VentanaEntrega ventana = ventana(LocalDate.of(2026, 9, 30), horario);

        assertThat(ventana.instanteInicio()).isEqualTo(Instant.parse("2026-09-30T08:00:00Z"));
        assertThat(AVISOS.debidosAhora(ventana, Instant.parse("2026-09-30T07:52:00Z")))
                .extracting(AvisoHabito::tipo).containsExactly(TipoAvisoHabito.INICIO);
        assertThat(ResultadoOtorgamiento.calcular(ventana.instanteAncla(), Instant.parse("2026-09-30T08:20:00Z"),
                ventana.extension()).puntos()).isEqualTo(ResultadoOtorgamiento.PUNTOS_COMPLETOS);
    }

    @Test
    @DisplayName("un ritual de la mañana movido a las 23:30 da puntos antes de la medianoche de Lima")
    void ritualDeMananaALas2330() {
        HabitoId ritual = HabitoId.of(UUID.randomUUID());
        HorarioResuelto horario = HorarioResuelto.de(catalogo(ritual, LocalTime.of(6, 0), null),
                preferencia(ritual, LocalTime.of(23, 30)));
        VentanaEntrega ventana = ventana(LocalDate.of(2026, 9, 29), horario);

        // 04:45 UTC del 30 = 23:45 del 29 en Lima: la extension recortada a la medianoche paga completo.
        ResultadoOtorgamiento puntos = ResultadoOtorgamiento.calcular(ventana.instanteAncla(),
                Instant.parse("2026-09-30T04:45:00Z"), ventana.extension());
        assertThat(puntos.puntos()).isEqualTo(ResultadoOtorgamiento.PUNTOS_COMPLETOS);
        assertThat(ventana.plazoEvidencia()).isEqualTo(Instant.parse("2026-09-30T05:00:00Z"));
    }

    /**
     * El bug que D-230 cierra (E-451). Pastilla Renacer trae 07:00-12:00 del catalogo; quien la
     * mueve a las 22:00 no toca el limite, y el respaldo por campo le dejaba 22:00-12:00: una
     * ventana que cruzaba la medianoche, con el ancla en las 12:00 del DIA SIGUIENTE. Con el
     * codigo viejo, {@code horaLimite} era 12:00 y el ancla 2026-09-30T17:00Z.
     */
    @Test
    @DisplayName("E-451: Pastilla Renacer (07:00-12:00) movida a las 22:00 cierra el MISMO dia, no a las 12 del siguiente")
    void unLimiteDelCatalogoNoArrastraLaVentanaAlDiaSiguiente() {
        HabitoId pastilla = HabitoId.of(UUID.randomUUID());
        HorarioResuelto horario = HorarioResuelto.de(catalogo(pastilla, LocalTime.of(7, 0), LocalTime.of(12, 0)),
                preferencia(pastilla, LocalTime.of(22, 0)));

        assertThat(horario.horaLimite()).isEqualTo(VentanaDelDia.ULTIMA_HORA_LIMITE);

        VentanaEntrega ventana = ventana(LocalDate.of(2026, 9, 29), horario);
        Instant medianocheDeLima = Instant.parse("2026-09-30T05:00:00Z");
        assertThat(ventana.instanteAncla()).isEqualTo(Instant.parse("2026-09-30T04:50:00Z"));
        assertThat(ventana.plazoEvidencia()).isBeforeOrEqualTo(medianocheDeLima);

        // 03:30 UTC del 30 = 22:30 del 29 en Lima.
        ResultadoOtorgamiento puntos = ResultadoOtorgamiento.calcular(ventana.instanteAncla(),
                Instant.parse("2026-09-30T03:30:00Z"), ventana.extension());
        assertThat(puntos.fase()).isEqualTo(FaseOtorgamiento.A_TIEMPO);
        assertThat(puntos.puntos()).isEqualTo(ResultadoOtorgamiento.PUNTOS_COMPLETOS);

        // El aviso "por vencer" sale antes de la medianoche de Lima, no al mediodia del dia siguiente.
        assertThat(AVISOS.debidosAhora(ventana, Instant.parse("2026-09-30T04:40:00Z")))
                .extracting(AvisoHabito::tipo).contains(TipoAvisoHabito.POR_VENCER);
    }

    @Test
    @DisplayName("un limite del catalogo que sigue siendo posterior al disparo se respeta tal cual")
    void unLimiteQueSigueAlDisparoNoSeToca() {
        HabitoId pastilla = HabitoId.of(UUID.randomUUID());
        HorarioResuelto horario = HorarioResuelto.de(catalogo(pastilla, LocalTime.of(7, 0), LocalTime.of(12, 0)),
                preferencia(pastilla, LocalTime.of(3, 0)));

        assertThat(horario.horaLimite()).isEqualTo(LocalTime.of(12, 0));
    }
}
