package com.renaser.os.habits.domain.model.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RepeticionDeSemanaTest {

    private final HabitoId despertar = HabitoId.of(UUID.randomUUID());
    private final HabitoId gimnasio = HabitoId.of(UUID.randomUUID());
    private final HabitoId viejo = HabitoId.of(UUID.randomUUID());
    private final SemanaCalendaria modelo = SemanaCalendaria.desde(LocalDate.of(2026, 10, 26));
    private final SemanaCalendaria destino = modelo.siguiente();
    /** Hoy es el miercoles 28 de octubre: la modelo tiene dias pasados, el destino es todo futuro. */
    private final LocalDate hoy = LocalDate.of(2026, 10, 28);
    private final Set<HabitoId> aplicables = Set.of(despertar, gimnasio);

    private FechaPropia sabado(HabitoId habito, int hora) {
        return new FechaPropia(habito, LocalDate.of(2026, 10, 31), true, LocalTime.of(hora, 0), null, null, null);
    }

    @Test
    void copiaLaFilaDeLaModeloALaMismaFechaDeLaSiguiente() {
        var plan = RepeticionDeSemana.planear(modelo, destino, List.of(sabado(despertar, 9)), List.of(), aplicables, hoy);

        assertThat(plan.sinCambios()).isFalse();
        assertThat(plan.escrituras()).containsExactly(sabado(despertar, 9).enLaFecha(LocalDate.of(2026, 11, 7)));
        assertThat(plan.reemplazaLoPropio()).isFalse();
        assertThat(plan.borrados()).isEmpty();
    }

    @Test
    void repetirDosVecesDejaLoMismoYLaSegundaNoTieneNadaQueEscribir() {
        var filas = List.of(sabado(despertar, 9));
        var primera = RepeticionDeSemana.planear(modelo, destino, filas, List.of(), aplicables, hoy);

        var yaEscrito = primera.escrituras();
        var segunda = RepeticionDeSemana.planear(modelo, destino, filas, yaEscrito, aplicables, hoy);

        assertThat(segunda.sinCambios()).isTrue();
    }

    @Test
    void siElDestinoYaTieneOtroCambioSeAvisaQueSeReemplaza() {
        var destinoPropio = sabado(despertar, 10).enLaFecha(LocalDate.of(2026, 11, 7));

        var plan = RepeticionDeSemana.planear(modelo, destino, List.of(sabado(despertar, 9)), List.of(destinoPropio), aplicables, hoy);

        assertThat(plan.reemplazaLoPropio()).isTrue();
        assertThat(plan.cambios()).singleElement().satisfies(c -> assertThat(c.reemplaza()).isTrue());
    }

    @Test
    void unaFechaDelDestinoSinContraparteEnLaModeloVuelveAlPlanDeSiempre() {
        var destinoPropio = sabado(gimnasio, 10).enLaFecha(LocalDate.of(2026, 11, 7));

        var plan = RepeticionDeSemana.planear(modelo, destino, List.of(sabado(despertar, 9)), List.of(destinoPropio), aplicables, hoy);

        assertThat(plan.borrados()).singleElement().satisfies(c -> {
            assertThat(c.habitoId()).isEqualTo(gimnasio);
            assertThat(c.fecha()).isEqualTo(LocalDate.of(2026, 11, 7));
        });
        assertThat(plan.reemplazaLoPropio()).isTrue();
    }

    @Test
    void soloSeEscribenFechasPosterioresAHoy() {
        // La modelo es la semana en curso y el destino tambien tiene un dia ya pasado cuando se repite sobre si misma una semana vieja.
        var destinoPasado = SemanaCalendaria.desde(LocalDate.of(2026, 10, 26));
        var plan = RepeticionDeSemana.planear(modelo.siguiente(), destinoPasado,
                List.of(new FechaPropia(despertar, LocalDate.of(2026, 11, 2), true, LocalTime.of(9, 0), null, null, null),
                        new FechaPropia(despertar, LocalDate.of(2026, 11, 7), true, LocalTime.of(9, 0), null, null, null)),
                List.of(), aplicables, hoy);

        assertThat(plan.escrituras()).extracting(FechaPropia::fecha)
                .containsExactly(LocalDate.of(2026, 10, 31));
    }

    @Test
    void unHabitoQueYaNoLeCorrespondeSeDejaAfueraSinFallarElResto() {
        var plan = RepeticionDeSemana.planear(modelo, destino, List.of(sabado(viejo, 9), sabado(despertar, 9)), List.of(), aplicables, hoy);

        assertThat(plan.excluidos()).containsExactly(viejo);
        assertThat(plan.escrituras()).extracting(FechaPropia::habitoId).containsExactly(despertar);
    }

    @Test
    void laHuellaCambiaSiCambiaLaModeloYSoloMiraLosHabitosQueSeRepiten() {
        var a = RepeticionDeSemana.huella(List.of(sabado(despertar, 9)), aplicables);
        var b = RepeticionDeSemana.huella(List.of(sabado(despertar, 10)), aplicables);
        var conUnoAjeno = RepeticionDeSemana.huella(List.of(sabado(despertar, 9), sabado(viejo, 8)), aplicables);

        assertThat(a).hasSize(16).isNotEqualTo(b).isEqualTo(conUnoAjeno);
        assertThat(RepeticionDeSemana.huella(List.of(sabado(despertar, 9)), aplicables)).isEqualTo(a);
    }

    @Test
    void sinFilasEnLaModeloNoHayNadaQueOfrecer() {
        assertThat(RepeticionDeSemana.planear(modelo, destino, List.of(), List.of(), aplicables, hoy).sinCambios()).isTrue();
    }
}
