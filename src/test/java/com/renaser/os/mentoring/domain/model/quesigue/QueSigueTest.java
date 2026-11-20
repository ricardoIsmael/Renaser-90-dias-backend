package com.renaser.os.mentoring.domain.model.quesigue;

import com.renaser.os.mentoring.domain.model.quesigue.QueSigue.Accion;
import com.renaser.os.mentoring.domain.model.quesigue.QueSigue.AprendizQueBaja;
import com.renaser.os.mentoring.domain.model.quesigue.QueSigue.Candidato;
import com.renaser.os.mentoring.domain.model.quesigue.QueSigue.Tipo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Las reglas fijas de «Qué sigue» y «Quién baja el promedio» (spec 057, FR-011 y FR-012). */
class QueSigueTest {

    private static final YearMonth OCTUBRE = YearMonth.of(2026, 10);
    private static final UUID GRUPO = UUID.randomUUID();

    private static Candidato candidato(String nombre, String porcentaje, int entregadas, int esperadas) {
        return new Candidato(UUID.randomUUID(), GRUPO, nombre, new BigDecimal(porcentaje), entregadas, esperadas);
    }

    private static AprendizQueBaja baja(String nombre, String porcentaje, int entregadas, int esperadas) {
        return new AprendizQueBaja(UUID.randomUUID(), GRUPO, nombre, new BigDecimal(porcentaje), entregadas, esperadas);
    }

    private static List<Tipo> tipos(List<Accion> acciones) {
        return acciones.stream().map(Accion::tipo).toList();
    }

    @Test
    @DisplayName("spec 057 FR-012: Quién baja deja a los de 80 % o más, ordena del menor al mayor y corta en 5")
    void quienBaja() { // spec 057 FR-012
        List<Candidato> todos = new ArrayList<>(List.of(candidato("Justo en 80", "80", 8, 10), candidato("Bien", "95", 19, 20)));
        for (int i = 0; i < 7; i++) {
            todos.add(candidato("Baja " + (70 - i), String.valueOf(70 - i), 7, 10));
        }

        List<AprendizQueBaja> lista = QueSigue.quienBaja(todos);

        assertThat(lista).extracting(AprendizQueBaja::nombre).containsExactly("Baja 64", "Baja 65", "Baja 66", "Baja 67", "Baja 68");
    }

    @Test
    @DisplayName("spec 057 FR-012: quien no tiene nada esperado no se evalúa ni baja el promedio")
    void sinEsperadasNoEntra() { // spec 057 FR-012
        assertThat(QueSigue.quienBaja(List.of(candidato("Nuevo", "0", 0, 0)))).isEmpty();
    }

    @Test
    @DisplayName("spec 057 FR-011: a los de menos de 60 % se les escribe (hasta 2), con «Entregó E de S este mes»")
    void escribirALosDeRojo() { // spec 057 FR-011
        List<AprendizQueBaja> lista = List.of(baja("Ana", "20", 1, 5), baja("Luis", "40", 2, 5), baja("Eva", "55", 5, 9), baja("Teo", "70", 7, 10));

        List<Accion> acciones = QueSigue.acciones(new BigDecimal("50"), null, OCTUBRE, 0, lista);

        assertThat(tipos(acciones)).containsExactly(Tipo.ESCRIBIR, Tipo.ESCRIBIR);
        assertThat(acciones.get(0).titulo()).isEqualTo("Escríbele a Ana");
        assertThat(acciones.get(0).motivo()).isEqualTo("Entregó 1 de 5 este mes");
        assertThat(acciones.get(0).aprendizId()).isEqualTo(lista.get(0).aprendizId());
        assertThat(acciones.get(1).titulo()).isEqualTo("Escríbele a Luis");
    }

    @Test
    @DisplayName("spec 057 FR-011: bajar 5 puntos o más desde el mes anterior sugiere conversar con el grupo; 4 no")
    void bajoRespectoDelMesAnterior() { // spec 057 FR-011
        List<Accion> baja5 = QueSigue.acciones(new BigDecimal("75"), new BigDecimal("80"), OCTUBRE, 0, List.of());
        List<Accion> baja4 = QueSigue.acciones(new BigDecimal("76"), new BigDecimal("80"), OCTUBRE, 0, List.of());

        assertThat(tipos(baja5)).containsExactly(Tipo.REVISAR_GRUPO);
        assertThat(baja5.getFirst().titulo()).isEqualTo("Conversa con tu grupo sobre qué cambió");
        assertThat(baja5.getFirst().motivo()).isEqualTo("Bajaste 5 puntos desde octubre");
        assertThat(tipos(baja4)).doesNotContain(Tipo.REVISAR_GRUPO);
    }

    @Test
    @DisplayName("spec 057 FR-011: las evidencias fuera de hora piden recordar las horas de entrega")
    void tardias() { // spec 057 FR-011
        List<Accion> acciones = QueSigue.acciones(new BigDecimal("70"), null, OCTUBRE, 4, List.of());

        assertThat(tipos(acciones)).containsExactly(Tipo.VENTANAS);
        assertThat(acciones.getFirst().motivo()).isEqualTo("4 evidencias llegaron fuera de hora");
    }

    @Test
    @DisplayName("spec 057 FR-011: el orden es fijo y nunca pasa de 3 acciones")
    void maximoTresEnOrden() { // spec 057 FR-011
        List<AprendizQueBaja> lista = List.of(baja("Ana", "20", 1, 5), baja("Luis", "40", 2, 5));

        List<Accion> acciones = QueSigue.acciones(new BigDecimal("40"), new BigDecimal("60"), OCTUBRE, 3, lista);

        assertThat(tipos(acciones)).containsExactly(Tipo.ESCRIBIR, Tipo.ESCRIBIR, Tipo.REVISAR_GRUPO);
    }

    @Test
    @DisplayName("spec 057 FR-011: sin otra acción y con 80 % o más se reconoce el avance; con otra acción o con menos de 80, no")
    void reconocer() { // spec 057 FR-011
        assertThat(tipos(QueSigue.acciones(new BigDecimal("80"), null, OCTUBRE, 0, List.of()))).containsExactly(Tipo.RECONOCER);
        assertThat(tipos(QueSigue.acciones(new BigDecimal("90"), null, OCTUBRE, 2, List.of()))).containsExactly(Tipo.VENTANAS);
        assertThat(QueSigue.acciones(new BigDecimal("79"), null, OCTUBRE, 0, List.of())).isEmpty();
    }

    @Test
    @DisplayName("spec 057 FR-011: sin porcentaje (mes sin muestra o sin historial) no hay acciones")
    void sinPorcentaje() { // spec 057 FR-011
        assertThat(QueSigue.acciones(null, null, OCTUBRE, 5, List.of(baja("Ana", "20", 1, 5)))).isEmpty();
    }
}
