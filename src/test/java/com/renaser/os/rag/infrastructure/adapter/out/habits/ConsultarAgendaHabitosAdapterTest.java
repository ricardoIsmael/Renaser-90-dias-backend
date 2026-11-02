package com.renaser.os.rag.infrastructure.adapter.out.habits;

import com.renaser.os.habits.api.AgendaDelDiaFinder;
import com.renaser.os.habits.api.CategoriasDeLaFormacionFinder;
import com.renaser.os.habits.api.CategoriasDeLaFormacionFinder.CategoriaResumen;
import com.renaser.os.habits.api.HabitoEnJuegoResumen;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort.HabitoDelDia;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Spec 038: lo que SER lee de los habitos de hoy lleva el nombre de la categoria de la formacion de la persona, no solo las cuatro de la
 * Escuela. Antes una categoria propia del Diplomado o la Certificacion salia como «Sin dimension», y SER no sabia a que categoria ofrecer un
 * habito nuevo. Una prueba por formacion (Escuela, Diplomado, Certificacion), parametrizada.
 */
class ConsultarAgendaHabitosAdapterTest {

    private static final UserId PERSONA = UserId.of(UUID.randomUUID());
    private static final UUID REGISTRO = UUID.randomUUID();
    private static final UUID HABITO = UUID.randomUUID();
    private static final LocalDate DIA = LocalDate.of(2026, 11, 3);

    private final AgendaDelDiaFinder agenda = mock(AgendaDelDiaFinder.class);
    private final CategoriasDeLaFormacionFinder categorias = mock(CategoriasDeLaFormacionFinder.class);
    private final ConsultarAgendaHabitosAdapter adapter = new ConsultarAgendaHabitosAdapter(agenda, categorias);

    static Stream<Arguments> formaciones() {
        return Stream.of(
                Arguments.of("ESCUELA", List.of(new CategoriaResumen("CUERPO", "Cuerpo"), new CategoriaResumen("CONSCIENCIA", "Emociones")),
                        "CONSCIENCIA", "Emociones"),
                Arguments.of("DIPLOMADO", List.of(new CategoriaResumen("DIPLOMADO_ESTUDIO_PROFUNDO", "Estudio profundo")),
                        "DIPLOMADO_ESTUDIO_PROFUNDO", "Estudio profundo"),
                Arguments.of("CERTIFICACION", List.of(new CategoriaResumen("CERTIFICACION_MENTORIA", "Mentoría")),
                        "CERTIFICACION_MENTORIA", "Mentoría"));
    }

    private static HabitoEnJuegoResumen habito(String categoriaClave) {
        return new HabitoEnJuegoResumen(REGISTRO, "Leer", "PENDIENTE", 10, 10, null, false, List.of(), null, null, LocalTime.of(7, 0),
                categoriaClave, HABITO, DIA);
    }

    // spec 038 FR-011
    @ParameterizedTest(name = "{0}")
    @MethodSource("formaciones")
    @DisplayName("spec 038: el habito lleva el nombre de la categoria de SU formacion, y su habito y su dia para apagar las alarmas")
    void rotulaConLaCategoriaDeSuFormacion(String formacion, List<CategoriaResumen> deSuFormacion, String clave, String nombre) {
        when(categorias.activasDe(PERSONA)).thenReturn(deSuFormacion);
        when(agenda.deHoyDe(PERSONA)).thenReturn(List.of(habito(clave)));

        HabitoDelDia resultado = adapter.deHoyDe(PERSONA).getFirst();

        assertThat(resultado.dimension()).as(formacion).isEqualTo(nombre);
        assertThat(resultado.habitoId()).isEqualTo(HABITO);
        assertThat(resultado.fecha()).isEqualTo(DIA);
    }

    @Test
    @DisplayName("spec 038: una categoria propia que ya no esta activa no se inventa un nombre; las cuatro de la Escuela conservan el suyo")
    void sinNombreNoSeInventa() {
        when(categorias.activasDe(PERSONA)).thenReturn(List.of(new CategoriaResumen("DIPLOMADO_ESTUDIO_PROFUNDO", "Estudio profundo")));
        when(agenda.deHoyDe(PERSONA)).thenReturn(List.of(habito("DIPLOMADO_DESACTIVADA"), habito("MENTE"), habito(null)));

        assertThat(adapter.deHoyDe(PERSONA)).extracting(HabitoDelDia::dimension).containsExactly(null, "Mente", null);
    }

    @Test
    @DisplayName("spec 038: si no se pueden leer las categorias, los habitos salen igual con las cuatro de siempre")
    void sinCategoriasLeidas() {
        when(categorias.activasDe(PERSONA)).thenThrow(new IllegalStateException("caida"));
        when(agenda.deHoyDe(PERSONA)).thenReturn(List.of(habito("CONSCIENCIA")));

        assertThat(adapter.deHoyDe(PERSONA)).extracting(HabitoDelDia::dimension).containsExactly("Emociones");
    }

    // spec 037 (T021 de la 038): la medicion de `habits` llega a SER con su meta, lo registrado hoy y la pista de los pasos conectados
    @Test
    @DisplayName("spec 037: la medicion del habito de km pasa a SER tal cual, y los demas habitos no llevan medicion")
    void llevaLaMedicion() {
        var km = new HabitoEnJuegoResumen(REGISTRO, "KILOMETROS DIARIOS", "PENDIENTE", 10, 10, null, true, List.of(), "DAILY_KM", null,
                LocalTime.of(17, 0), "CUERPO", HABITO, DIA, new com.renaser.os.habits.api.MedicionDelHabito("KILOMETROS",
                new java.math.BigDecimal("5.00"), new java.math.BigDecimal("5.40"), "HEALTH_CONNECT", "Galaxy Watch", true));
        when(categorias.activasDe(PERSONA)).thenReturn(List.of());
        when(agenda.deHoyDe(PERSONA)).thenReturn(List.of(km, habito("MENTE")));

        List<HabitoDelDia> habitos = adapter.deHoyDe(PERSONA);

        assertThat(habitos.get(0).mideKilometros()).isTrue();
        assertThat(habitos.get(0).seMideSolo()).isTrue();
        assertThat(habitos.get(0).medicion().meta()).isEqualByComparingTo("5");
        assertThat(habitos.get(0).medicion().valorDelDia()).isEqualByComparingTo("5.4");
        assertThat(habitos.get(0).medicion().origenDelDia()).isEqualTo("HEALTH_CONNECT");
        assertThat(habitos.get(0).medicion().fuenteDelDia()).isEqualTo("Galaxy Watch");
        assertThat(habitos.get(1).medicion()).isNull();
        assertThat(habitos.get(1).mideKilometros()).isFalse();
        assertThat(habitos.get(1).seMideSolo()).isFalse();
    }
}
