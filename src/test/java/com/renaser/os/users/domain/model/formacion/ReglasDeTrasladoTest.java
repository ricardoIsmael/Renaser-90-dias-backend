package com.renaser.os.users.domain.model.formacion;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.formacion.ReglasDeTraslado.Decision;
import com.renaser.os.users.domain.model.formacion.ReglasDeTraslado.Tipo;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cuándo se puede pasar a un aprendiz de formación (spec 022, Q1-A y Q8-A): al terminar, o dándolo por terminado con un motivo. */
class ReglasDeTrasladoTest {

    private static final Instant INICIO = Instant.parse("2026-01-10T15:00:00Z");
    private static final Set<Integer> NOVENTA = Set.of(90);
    private static final Set<Integer> NOVENTA_O_ANIO = Set.of(90, 365);

    private static ParticipacionPrograma enCurso() {
        return ParticipacionPrograma.activarSeguimientoPersonal(UserId.of(UUID.randomUUID()), FixedClock.at(INICIO), Formacion.ESCUELA, 90);
    }

    private static ParticipacionPrograma terminada() {
        ParticipacionPrograma p = enCurso();
        FixedClock fin = FixedClock.at(INICIO.plus(89, ChronoUnit.DAYS));
        p.sincronizarDiaDelPrograma(fin.now().atZone(p.timezone()).toLocalDate(), fin);
        assertThat(p.programaCompletado()).isTrue();
        return p;
    }

    @Test
    @DisplayName("Quien terminó se pasa sin motivo y queda su historial como «terminada»")
    void quienTerminoSePasa() {
        Decision decision = ReglasDeTraslado.decidir(terminada(), Formacion.DIPLOMADO, 365, false, null, NOVENTA_O_ANIO, true);

        assertThat(decision.tipo()).isEqualTo(Tipo.CON_HISTORIA);
        assertThat(decision.resultado()).contains(ResultadoDelTraslado.TERMINADA);
        assertThat(decision.motivo()).isNull();
    }

    @Test
    @DisplayName("Quien no terminó no se pasa a menos que se lo dé por terminado")
    void quienNoTerminoNoSePasa() {
        assertThatThrownBy(() -> ReglasDeTraslado.decidir(enCurso(), Formacion.DIPLOMADO, 90, false, null, NOVENTA_O_ANIO, true))
                .isInstanceOf(IllegalStateException.class).hasMessage(ReglasDeTraslado.TODAVIA_NO_TERMINO);
    }

    @Test
    @DisplayName("Dar por terminada la formación pide un motivo de 3 a 500 letras y queda guardado")
    void darPorTerminadaPideMotivo() {
        for (String malo : new String[]{null, "", "  ", "ab", "x".repeat(501)}) {
            assertThatThrownBy(() -> ReglasDeTraslado.decidir(enCurso(), Formacion.CERTIFICACION, 90, true, malo, NOVENTA, true))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage(ReglasDeTraslado.FALTA_EL_MOTIVO);
        }
        Decision decision = ReglasDeTraslado.decidir(enCurso(), Formacion.CERTIFICACION, 90, true, "  Cambió de programa ", NOVENTA, true);
        assertThat(decision.resultado()).contains(ResultadoDelTraslado.DADA_POR_TERMINADA);
        assertThat(decision.motivo()).isEqualTo("Cambió de programa");
    }

    @Test
    @DisplayName("Quien todavía no eligió su Día 1 se corrige sin historial (la formación estaba mal puesta)")
    void sinDiaUnoEsUnaCorreccion() {
        ParticipacionPrograma pendiente = ParticipacionPrograma.inscribirTraineeAprobado(UserId.of(UUID.randomUUID()), FixedClock.at(INICIO));

        Decision decision = ReglasDeTraslado.decidir(pendiente, Formacion.DIPLOMADO, 90, false, null, NOVENTA_O_ANIO, true);

        assertThat(decision.tipo()).isEqualTo(Tipo.CORRECCION_SIN_HISTORIA);
        assertThat(decision.resultado()).isEmpty();
    }

    @Test
    @DisplayName("No se pasa a la misma formación, ni a una apagada, ni con una duración que el destino no ofrece")
    void rechazaLoInvalido() {
        assertThatThrownBy(() -> ReglasDeTraslado.decidir(terminada(), Formacion.ESCUELA, 90, false, null, NOVENTA, true))
                .isInstanceOf(IllegalStateException.class).hasMessage(ReglasDeTraslado.MISMA_FORMACION);
        assertThatThrownBy(() -> ReglasDeTraslado.decidir(terminada(), Formacion.DIPLOMADO, 90, false, null, NOVENTA_O_ANIO, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReglasDeTraslado.decidir(terminada(), Formacion.CERTIFICACION, 365, false, null, NOVENTA, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
