package com.renaser.os.habits.domain.model.horario;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HorarioHabitoTest {

    private static final Instant AHORA = Instant.parse("2026-08-26T10:00:00Z");

    @Test
    void actualizarRangoCambiaDiaInicioDiaFinYTipoDia() {
        HorarioHabito horario = HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()),
                HabitoId.of(UUID.randomUUID()), 1, 10, TipoDia.DISCIPLINA, LocalTime.of(6, 0), LocalTime.of(9, 0),
                AHORA);
        Instant despues = AHORA.plusSeconds(60);

        horario.actualizarRango(5, 20, TipoDia.TODOS, despues);

        assertThat(horario.diaInicio()).isEqualTo(5);
        assertThat(horario.diaFin()).isEqualTo(20);
        assertThat(horario.tipoDia()).isEqualTo(TipoDia.TODOS);
        assertThat(horario.actualizadoEn()).isEqualTo(despues);
    }

    @Test
    void actualizarRangoAceptaDiaFinNuloParaDejarloAbierto() {
        HorarioHabito horario = HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()),
                HabitoId.of(UUID.randomUUID()), 1, 10, TipoDia.DISCIPLINA, null, null, AHORA);

        horario.actualizarRango(1, null, TipoDia.DISCIPLINA, AHORA.plusSeconds(1));

        assertThat(horario.diaFin()).isNull();
    }

    @Test
    void actualizarRangoConDiaInicioFueraDeRangoFalla() {
        HorarioHabito horario = HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()),
                HabitoId.of(UUID.randomUUID()), 1, null, TipoDia.DISCIPLINA, null, null, AHORA);

        assertThatThrownBy(() -> horario.actualizarRango(0, null, TipoDia.DISCIPLINA, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
        // Spec 023 (V136): el programa más largo es el de 365 días (Diplomado de 1 año); un hábito propio nace con el día de la persona.
        assertThatThrownBy(() -> horario.actualizarRango(HorarioHabito.ULTIMO_DIA_POSIBLE + 1, null, TipoDia.DISCIPLINA, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unHabitoPropioPuedeNacerPasadoElDia90DeUnProgramaDeUnAnio() {
        HorarioHabito horario = HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()),
                HabitoId.of(UUID.randomUUID()), 200, null, TipoDia.TODOS, null, null, AHORA);

        assertThat(horario.diaInicio()).isEqualTo(200);
        horario.actualizarRango(365, null, TipoDia.TODOS, AHORA);
        assertThat(horario.diaInicio()).isEqualTo(365);
    }

    @Test
    void copiarParaOtroHabitoConservaElRangoYLasHorasConOtraIdentidad() {
        HorarioHabito original = HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()), HabitoId.of(UUID.randomUUID()), 8, 40,
                TipoDia.DISCIPLINA, java.time.LocalTime.of(6, 0), java.time.LocalTime.of(7, 0), AHORA);
        HabitoId otroHabito = HabitoId.of(UUID.randomUUID());

        HorarioHabito copia = original.copiarPara(HorarioHabitoId.of(UUID.randomUUID()), otroHabito, AHORA.plusSeconds(5));

        assertThat(copia.id()).isNotEqualTo(original.id());
        assertThat(copia.habitoId()).isEqualTo(otroHabito);
        assertThat(copia.diaInicio()).isEqualTo(8);
        assertThat(copia.diaFin()).isEqualTo(40);
        assertThat(copia.tipoDia()).isEqualTo(TipoDia.DISCIPLINA);
        assertThat(copia.horaDisparo()).isEqualTo(java.time.LocalTime.of(6, 0));
        assertThat(copia.creadoEn()).isEqualTo(AHORA.plusSeconds(5));
    }

    @Test
    void actualizarRangoConDiaFinAnteriorADiaInicioFalla() {
        HorarioHabito horario = HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()),
                HabitoId.of(UUID.randomUUID()), 10, null, TipoDia.DISCIPLINA, null, null, AHORA);

        assertThatThrownBy(() -> horario.actualizarRango(10, 5, TipoDia.DISCIPLINA, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actualizarRangoConTipoDiaNuloFalla() {
        HorarioHabito horario = HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()),
                HabitoId.of(UUID.randomUUID()), 1, null, TipoDia.DISCIPLINA, null, null, AHORA);

        assertThatThrownBy(() -> horario.actualizarRango(1, null, null, AHORA))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void actualizarHorasSiguenPudiendoLimpiarseANull() {
        HorarioHabito horario = HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()),
                HabitoId.of(UUID.randomUUID()), 1, null, TipoDia.DISCIPLINA, LocalTime.of(6, 0), LocalTime.of(9, 0),
                AHORA);

        horario.actualizarHoras(null, null, AHORA.plusSeconds(1));

        assertThat(horario.horaDisparo()).isNull();
        assertThat(horario.horaLimite()).isNull();
    }

    // ---- la ventana cabe dentro del dia (D-122, 2026-09-08) ----
    //
    // Estos tres decian ...Falla y esperaban IllegalArgumentException. Cambiaron con D-122: un
    // cierre que se pasa de la medianoche ya no traba al aprendiz que trabaja de noche, se acomoda
    // al ultimo instante util del dia. Lo que SI sigue fallando es arrancar despues de las 23:40,
    // que es la decision equivalente tomada del otro lado (ver `noSePuedeArrancarDespuesDeLas2340`).

    @Test
    void crearConCierrePasadaLaMedianocheLoAcomodaAlFinalDelDia() {
        HorarioHabito horario = HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()),
                HabitoId.of(UUID.randomUUID()), 1, null, TipoDia.TODOS, LocalTime.of(22, 0), LocalTime.of(6, 0),
                AHORA);

        assertThat(horario.horaDisparo()).isEqualTo(LocalTime.of(22, 0));
        assertThat(horario.horaLimite()).isEqualTo(LocalTime.of(23, 50));
    }

    @Test
    void crearConCierreIgualAlArranqueLoAcomoda() {
        LocalTime misma = LocalTime.of(8, 0);
        HorarioHabito horario = HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()),
                HabitoId.of(UUID.randomUUID()), 1, null, TipoDia.TODOS, misma, misma, AHORA);

        assertThat(horario.horaLimite()).isEqualTo(LocalTime.of(23, 50));
    }

    @Test
    void noSePuedeArrancarDespuesDeLas2340() {
        assertThatThrownBy(() -> HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()),
                HabitoId.of(UUID.randomUUID()), 1, null, TipoDia.TODOS, LocalTime.of(23, 45), null, AHORA))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("23:40");
    }

    @Test
    void crearConSoloHoraDisparoNoFalla() {
        HorarioHabito horario = HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()),
                HabitoId.of(UUID.randomUUID()), 1, null, TipoDia.TODOS, LocalTime.of(6, 0), null, AHORA);

        assertThat(horario.horaDisparo()).isEqualTo(LocalTime.of(6, 0));
        assertThat(horario.horaLimite()).isNull();
    }

    @Test
    void actualizarHorasConCierrePasadaLaMedianocheLoAcomoda() {
        HorarioHabito horario = HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()),
                HabitoId.of(UUID.randomUUID()), 1, null, TipoDia.DISCIPLINA, LocalTime.of(6, 0), LocalTime.of(9, 0),
                AHORA);

        horario.actualizarHoras(LocalTime.of(20, 0), LocalTime.of(5, 0), AHORA.plusSeconds(1));

        assertThat(horario.horaDisparo()).isEqualTo(LocalTime.of(20, 0));
        assertThat(horario.horaLimite()).isEqualTo(LocalTime.of(23, 50));
    }

    /**
     * Lo que el test anterior cubria ademas del rechazo: que un intento invalido no deje el
     * agregado mutado a medias. Se conserva con el caso que SIGUE fallando tras D-122.
     */
    @Test
    void unArranqueFueraDeRangoNoDejaElHorarioMutadoAMedias() {
        HorarioHabito horario = HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()),
                HabitoId.of(UUID.randomUUID()), 1, null, TipoDia.DISCIPLINA, LocalTime.of(6, 0), LocalTime.of(9, 0),
                AHORA);

        assertThatThrownBy(() -> horario.actualizarHoras(LocalTime.of(23, 45), LocalTime.of(23, 50),
                AHORA.plusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        // El estado previo no debe quedar mutado a mitad de camino por el intento fallido.
        assertThat(horario.horaDisparo()).isEqualTo(LocalTime.of(6, 0));
        assertThat(horario.horaLimite()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void todoElProgramaRigeDesdeElDia1TodosLosDiasSinFinNiCierre() {
        HorarioHabito horario = HorarioHabito.todoElPrograma(HorarioHabitoId.of(UUID.randomUUID()),
                HabitoId.of(UUID.randomUUID()), LocalTime.of(6, 30), AHORA);

        assertThat(horario.diaInicio()).isEqualTo(1);
        assertThat(horario.diaFin()).isNull();
        assertThat(horario.tipoDia()).isEqualTo(TipoDia.TODOS);
        assertThat(horario.horaDisparo()).isEqualTo(LocalTime.of(6, 30));
        assertThat(horario.horaLimite()).isNull();
        for (int dia : new int[] {1, 45, 90}) {
            assertThat(horario.aplicaEnDia(dia, TipoDia.DISCIPLINA)).as("día " + dia).isTrue();
            assertThat(horario.aplicaEnDia(dia, TipoDia.DOMINGO)).as("domingo del día " + dia).isTrue();
        }
    }

    @Test
    void todoElProgramaSinHoraEsTodoElDia() {
        HorarioHabito horario = HorarioHabito.todoElPrograma(HorarioHabitoId.of(UUID.randomUUID()),
                HabitoId.of(UUID.randomUUID()), null, AHORA);

        assertThat(horario.horaDisparo()).isNull();
        assertThat(horario.horaLimite()).isNull();
    }

    @Test
    void todoElProgramaConHoraDespuesDeLas2340Falla() {
        assertThatThrownBy(() -> HorarioHabito.todoElPrograma(HorarioHabitoId.of(UUID.randomUUID()),
                HabitoId.of(UUID.randomUUID()), LocalTime.of(23, 41), AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
