package com.renaser.os.users.domain.model.participante;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El programa de cada persona con SU duración (spec 022): se gradúa en su último día, no siempre en el 90, y las fases se estiran. */
class ParticipacionProgramaPorFormacionTest {

    /** 10:00 en Lima. */
    private static final Instant INICIO = Instant.parse("2026-01-10T15:00:00Z");

    private static ParticipacionPrograma nueva(Formacion formacion, int duracion) {
        return ParticipacionPrograma.activarSeguimientoPersonal(UserId.of(UUID.randomUUID()), FixedClock.at(INICIO), formacion, duracion);
    }

    private static FixedClock enElDia(int dia) {
        return FixedClock.at(INICIO.plus(dia - 1L, ChronoUnit.DAYS));
    }

    private static void avanzarAlDia(ParticipacionPrograma p, int dia) {
        FixedClock reloj = enElDia(dia);
        p.sincronizarDiaDelPrograma(reloj.now().atZone(p.timezone()).toLocalDate(), reloj);
    }

    @Test
    @DisplayName("Lo de siempre: en la Escuela el día 90 gradúa y el 89 no")
    void laEscuelaGraduaEnElNoventa() {
        ParticipacionPrograma p = nueva(Formacion.ESCUELA, 90);
        avanzarAlDia(p, 89);
        assertThat(p.programaCompletado()).isFalse();
        avanzarAlDia(p, 90);
        assertThat(p.programaCompletado()).isTrue();
        assertThat(p.diaPrograma()).isEqualTo(90);
    }

    @Test
    @DisplayName("En el Diplomado de un año el día 90 NO gradúa; gradúa el 365")
    void elDiplomadoDeUnAnioGraduaEnElTrescientosSesentaYCinco() {
        ParticipacionPrograma p = nueva(Formacion.DIPLOMADO, 365);
        avanzarAlDia(p, 90);
        assertThat(p.programaCompletado()).isFalse();
        assertThat(p.diaPrograma()).isEqualTo(90);
        avanzarAlDia(p, 364);
        assertThat(p.programaCompletado()).isFalse();
        avanzarAlDia(p, 365);
        assertThat(p.programaCompletado()).isTrue();
        assertThat(p.duracionDias()).isEqualTo(365);
        assertThat(p.formacion()).isEqualTo(Formacion.DIPLOMADO);
    }

    @Test
    @DisplayName("El Diplomado de 3 meses se comporta como la Escuela: gradúa el 90")
    void elDiplomadoDeTresMeses() {
        ParticipacionPrograma p = nueva(Formacion.DIPLOMADO, 90);
        avanzarAlDia(p, 90);
        assertThat(p.programaCompletado()).isTrue();
    }

    @Test
    @DisplayName("La fase se estira con la duración: el día 90 de un año sigue en la fase 2; el 365 es la 4")
    void laFaseSeEstira() {
        ParticipacionPrograma anio = nueva(Formacion.DIPLOMADO, 365);
        avanzarAlDia(anio, 90);
        assertThat(anio.fase()).isEqualTo(FasePrograma.PHASE_2_DEVELOPMENT);
        avanzarAlDia(anio, 365);
        assertThat(anio.fase()).isEqualTo(FasePrograma.PHASE_4_ASCENSION);

        ParticipacionPrograma escuela = nueva(Formacion.ESCUELA, 90);
        avanzarAlDia(escuela, 35);
        assertThat(escuela.fase()).isEqualTo(FasePrograma.PHASE_3_ALCHEMIST_WARRIOR);
    }

    @Test
    @DisplayName("El día que se puede fijar a mano llega hasta el último día menos uno de SU duración")
    void fijarDiaSeAcotaConLaDuracion() {
        ParticipacionPrograma escuela = nueva(Formacion.ESCUELA, 90);
        escuela.fijarDia(89, FixedClock.at(INICIO));
        assertThatThrownBy(() -> escuela.fijarDia(90, FixedClock.at(INICIO))).isInstanceOf(IllegalArgumentException.class);

        ParticipacionPrograma anio = nueva(Formacion.DIPLOMADO, 365);
        anio.fijarDia(200, FixedClock.at(INICIO));
        assertThat(anio.diaPrograma()).isEqualTo(200);
        assertThatThrownBy(() -> anio.fijarDia(365, FixedClock.at(INICIO))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Trasladar reinicia el programa: nueva formación y duración, día 0, sin grupo ni mentor y sin Día 1 elegido")
    void trasladarReinicia() {
        ParticipacionPrograma p = nueva(Formacion.ESCUELA, 90);
        avanzarAlDia(p, 90);
        p.asignarMentor(UserId.of(UUID.randomUUID()), FixedClock.at(INICIO));

        p.trasladarA(Formacion.DIPLOMADO, 365, enElDia(91));

        assertThat(p.formacion()).isEqualTo(Formacion.DIPLOMADO);
        assertThat(p.duracionDias()).isEqualTo(365);
        assertThat(p.diaPrograma()).isZero();
        assertThat(p.programaCompletado()).isFalse();
        assertThat(p.estaActivado()).isFalse();
        assertThat(p.mentorId()).isNull();
        assertThat(p.celulaId()).isNull();
        assertThat(p.fase()).isEqualTo(FasePrograma.PHASE_1_REBIRTH);
    }

    @Test
    @DisplayName("Una duración fuera de 1 a 365 no se acepta")
    void duracionInvalida() {
        assertThatThrownBy(() -> nueva(Formacion.DIPLOMADO, 366)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> nueva(Formacion.ESCUELA, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Con el reloj en la madrugada UTC el día se cuenta en Lima: a las 03:30 UTC todavía es la víspera")
    void elDiaSeCuentaEnLaZonaDeLaPersona() {
        ParticipacionPrograma p = nueva(Formacion.DIPLOMADO, 365);
        // 2026-01-11 03:30 UTC = 2026-01-10 22:30 en Lima: sigue siendo el día 1.
        FixedClock madrugada = FixedClock.at(Instant.parse("2026-01-11T03:30:00Z"));
        assertThat(p.diaVigente(madrugada)).isEqualTo(1);
        // 2026-01-11 05:30 UTC = 00:30 del 11 en Lima: ya es el día 2.
        assertThat(p.diaVigente(FixedClock.at(Instant.parse("2026-01-11T05:30:00Z")))).isEqualTo(2);
    }
}
