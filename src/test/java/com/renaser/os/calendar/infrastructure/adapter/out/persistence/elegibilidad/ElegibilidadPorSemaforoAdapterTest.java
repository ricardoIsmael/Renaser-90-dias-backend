package com.renaser.os.calendar.infrastructure.adapter.out.persistence.elegibilidad;

import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.SemaforoFinder;
import com.renaser.os.points.api.VentanaDelSemaforo;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * D-293 (cierra la pregunta abierta de D-213): la Mentoria del Alquimista la ve un aprendiz solo si su semaforo
 * vigente esta en VERDE. El adaptador le pregunta el color a {@link SemaforoFinder} (el contrato publico de
 * {@code points}, el mismo semaforo que Hoy le muestra) y no calcula nada. Contra el adaptador anterior
 * ({@code ElegibilidadSegunAudienciaAdapter}, siempre {@code true}) fallan todos los casos que no son verde.
 *
 * <p>El personal nunca llega a este puerto: lo deja pasar quien llama. Esa mitad de la regla se prueba por cada rol en
 * {@code MentoriaConSemaforoVerdePorRolTest}.
 */
@ExtendWith(MockitoExtension.class)
class ElegibilidadPorSemaforoAdapterTest {

    private static final LocalDate AYER = LocalDate.of(2026, 10, 29);

    @Mock
    private SemaforoFinder semaforo;

    private ElegibilidadPorSemaforoAdapter adapter;
    private final UserId aprendiz = UserId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        adapter = new ElegibilidadPorSemaforoAdapter(semaforo);
    }

    @ParameterizedTest(name = "semaforo {0} -> elegible: {1}")
    @CsvSource({"VERDE,true", "AMARILLO,false", "ROJO,false", "SIN_DATOS,false"})
    @DisplayName("D-293: solo el verde ve la Mentoria del Alquimista; amarillo, rojo y sin datos no")
    void soloElVerdeDejaPasar(ColorSemaforo color, boolean elegible) {
        when(semaforo.vigenteDe(anyCollection())).thenReturn(Map.of(aprendiz, ventana(color)));

        assertThat(adapter.esElegible(aprendiz, TipoEvento.MENTORIA_ALQUIMISTA)).isEqualTo(elegible);
    }

    @Test
    @DisplayName("D-293: un aprendiz sin semaforo (no activo su programa) no ve la Mentoria: no se inventa un verde")
    void sinSemaforoNoPasa() {
        when(semaforo.vigenteDe(anyCollection())).thenReturn(Map.of());

        assertThat(adapter.esElegible(aprendiz, TipoEvento.MENTORIA_ALQUIMISTA)).isFalse();
    }

    @Test
    @DisplayName("D-293: los umbrales son los del semaforo (80 es verde, 79,9 es amarillo); el adaptador no los repite")
    void usaElColorQueDiceElSemaforo() {
        UserId justoVerde = UserId.of(UUID.randomUUID());
        UserId casiVerde = UserId.of(UUID.randomUUID());
        when(semaforo.vigenteDe(anyCollection())).thenReturn(Map.of(
                justoVerde, ventana(ColorSemaforo.delPorcentaje(new BigDecimal("80.0")), new BigDecimal("80.0")),
                casiVerde, ventana(ColorSemaforo.delPorcentaje(new BigDecimal("79.9")), new BigDecimal("79.9"))));

        assertThat(adapter.elegiblesEntre(List.of(justoVerde, casiVerde), TipoEvento.MENTORIA_ALQUIMISTA))
                .containsExactly(justoVerde);
    }

    @Test
    @DisplayName("D-293: con varios aprendices se lee el semaforo una sola vez y pasan los verdes")
    void elLoteSeLeeUnaVez() {
        UserId verde = UserId.of(UUID.randomUUID());
        UserId amarillo = UserId.of(UUID.randomUUID());
        UserId rojo = UserId.of(UUID.randomUUID());
        UserId sinDatos = UserId.of(UUID.randomUUID());
        UserId sinSemaforo = UserId.of(UUID.randomUUID());
        List<UserId> todos = List.of(verde, amarillo, rojo, sinDatos, sinSemaforo);
        when(semaforo.vigenteDe(todos)).thenReturn(Map.of(verde, ventana(ColorSemaforo.VERDE),
                amarillo, ventana(ColorSemaforo.AMARILLO), rojo, ventana(ColorSemaforo.ROJO),
                sinDatos, ventana(ColorSemaforo.SIN_DATOS)));

        assertThat(adapter.elegiblesEntre(todos, TipoEvento.MENTORIA_ALQUIMISTA)).containsExactly(verde);
        verify(semaforo, times(1)).vigenteDe(todos);
    }

    @Test
    @DisplayName("D-293: sin aprendices no se consulta nada")
    void sinAprendicesNoConsulta() {
        assertThat(adapter.elegiblesEntre(List.of(), TipoEvento.MENTORIA_ALQUIMISTA)).isEmpty();

        verifyNoInteractions(semaforo);
    }

    @ParameterizedTest(name = "{0} no agrega criterio: pasa un aprendiz sin semaforo y no se consulta")
    @EnumSource(value = TipoEvento.class, names = "MENTORIA_ALQUIMISTA", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("D-293: los demas tipos de evento no cambian, tenga el aprendiz el color que tenga")
    void losDemasTiposNoCambian(TipoEvento otroTipo) {
        assertThat(adapter.esElegible(aprendiz, otroTipo)).isTrue();
        assertThat(adapter.elegiblesEntre(Set.of(aprendiz), otroTipo)).containsExactly(aprendiz);

        verify(semaforo, never()).vigenteDe(anyCollection());
    }

    private static VentanaDelSemaforo ventana(ColorSemaforo color) {
        BigDecimal porcentaje = switch (color) {
            case VERDE -> BigDecimal.valueOf(90);
            case AMARILLO -> BigDecimal.valueOf(70);
            case ROJO -> BigDecimal.valueOf(40);
            case SIN_DATOS -> null;
        };
        return ventana(color, porcentaje);
    }

    private static VentanaDelSemaforo ventana(ColorSemaforo color, BigDecimal porcentaje) {
        return new VentanaDelSemaforo(AYER.minusDays(6), AYER, porcentaje, color, porcentaje == null ? 0 : 7, false,
                List.of());
    }
}
