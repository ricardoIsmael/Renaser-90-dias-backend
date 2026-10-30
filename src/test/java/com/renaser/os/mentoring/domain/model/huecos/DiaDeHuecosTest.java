package com.renaser.os.mentoring.domain.model.huecos;

import com.renaser.os.shared.domain.TramoDelDia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El cálculo de un día (spec 015, historia 1): la ventana, lo ocupado, los huecos útiles y los casos de borde.
 */
class DiaDeHuecosTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 10, 26);

    private static HabitoConHora habito(String clave, int hora, int minuto) {
        String titulo = HabitoConHora.DESPERTAR.equals(clave) ? "Despertar"
                : HabitoConHora.DORMIR.equals(clave) ? "Dormir" : "Habito";
        return new HabitoConHora(clave, LocalTime.of(hora, minuto), titulo);
    }

    private static ItemPlanificado evento(LocalDateTime inicia, LocalDateTime termina) {
        return ItemPlanificado.deEvento(inicia, termina, "Evento");
    }

    private static TramoDelDia tramo(int horaDesde, int minutoDesde, int horaHasta, int minutoHasta) {
        return new TramoDelDia(horaDesde * 60 + minutoDesde, horaHasta * 60 + minutoHasta);
    }

    private static DiaDeHuecos calcular(List<HabitoConHora> habitos, List<ItemPlanificado> otros) {
        return DiaDeHuecos.calcular(LUNES, habitos, otros, List.of());
    }

    @Test
    @DisplayName("la prueba independiente de la spec: Despertar 06:00, un habito 07:00, un evento 10:00-11:00 y Dormir 22:00")
    void ejemploDeLaSpec() {
        DiaDeHuecos dia = calcular(
                List.of(habito(HabitoConHora.DESPERTAR, 6, 0), habito(null, 7, 0), habito(HabitoConHora.DORMIR, 22, 0)),
                List.of(evento(LUNES.atTime(10, 0), LUNES.atTime(11, 0))));

        assertThat(dia.ventana()).isEqualTo(new VentanaDelDia(360, 1320));
        assertThat(dia.huecos()).containsExactly(tramo(6, 30, 7, 0), tramo(7, 30, 10, 0), tramo(11, 0, 22, 0));
        assertThat(dia.conPlan()).isTrue();
        // Despertar y el habito: 30 min cada uno; el evento: 60; Dormir queda justo en el borde de la ventana.
        assertThat(dia.minutosOcupados()).isEqualTo(120);
        assertThat(dia.minutosLibres()).isEqualTo(960 - 120);
    }

    @Test
    @DisplayName("dos items que se cruzan son un solo bloque y ningun hueco se mete adentro")
    void seCruzan() {
        DiaDeHuecos dia = calcular(List.of(habito(null, 11, 0)),
                List.of(evento(LUNES.atTime(10, 0), LUNES.atTime(11, 30))));

        assertThat(dia.ocupados()).containsExactly(tramo(10, 0, 11, 30));
        assertThat(dia.huecos()).containsExactly(tramo(0, 0, 10, 0), tramo(11, 30, 24, 0));
    }

    @Test
    @DisplayName("varios habitos a la misma hora ocupan el tramo una sola vez")
    void mismaHora() {
        DiaDeHuecos dia = calcular(List.of(habito(null, 6, 0), habito(null, 6, 0), habito(null, 6, 0)), List.of());

        assertThat(dia.minutosOcupados()).isEqualTo(30);
    }

    @Test
    @DisplayName("un hueco de 20 minutos no se lista, pero sus minutos suman al tiempo libre")
    void huecoMenor() {
        DiaDeHuecos dia = calcular(List.of(habito(null, 7, 0)),
                List.of(evento(LUNES.atTime(7, 50), LUNES.atTime(8, 30))));

        assertThat(dia.huecos()).noneMatch(hueco -> hueco.desde() == 450);
        assertThat(dia.huecos()).containsExactly(tramo(0, 0, 7, 0), tramo(8, 30, 24, 0));
        assertThat(dia.minutosLibres()).isEqualTo(1440 - 30 - 40);
    }

    @Test
    @DisplayName("un hueco de exactamente 30 minutos si se lista")
    void huecoDeTreinta() {
        DiaDeHuecos dia = calcular(List.of(habito(null, 7, 0)),
                List.of(evento(LUNES.atTime(8, 0), LUNES.atTime(9, 0))));

        assertThat(dia.huecos()).contains(tramo(7, 30, 8, 0));
    }

    @Test
    @DisplayName("un dia sin plan es un solo hueco, la ventana entera, y se marca sin plan")
    void sinPlan() {
        DiaDeHuecos dia = calcular(List.of(), List.of());

        assertThat(dia.conPlan()).isFalse();
        assertThat(dia.ocupados()).isEmpty();
        assertThat(dia.huecos()).containsExactly(tramo(0, 0, 24, 0));
        assertThat(dia.minutosOcupados()).isZero();
        assertThat(dia.minutosLibres()).isEqualTo(1440);
    }

    @Test
    @DisplayName("un dia lleno no tiene ningun hueco")
    void diaLleno() {
        DiaDeHuecos dia = calcular(List.of(), List.of(
                evento(LUNES.atTime(0, 0), LUNES.plusDays(1).atTime(0, 0))));

        assertThat(dia.huecos()).isEmpty();
        assertThat(dia.minutosLibres()).isZero();
        assertThat(dia.minutosOcupados()).isEqualTo(1440);
    }

    @Test
    @DisplayName("sin Despertar o sin Dormir la ventana es el dia entero")
    void sinVentana() {
        DiaDeHuecos soloDespertar = calcular(List.of(habito(HabitoConHora.DESPERTAR, 6, 0)), List.of());
        DiaDeHuecos soloDormir = calcular(List.of(habito(HabitoConHora.DORMIR, 22, 30)), List.of());

        assertThat(soloDespertar.ventana().vieneDeLaPersona()).isFalse();
        assertThat(soloDormir.ventana().vieneDeLaPersona()).isFalse();
        // Dormir (22:30-23:00) tambien ocupa, y no hay ventana que lo deje afuera.
        assertThat(soloDormir.huecos()).containsExactly(tramo(0, 0, 22, 30), tramo(23, 0, 24, 0));
    }

    @Test
    @DisplayName("lo que cae fuera de la ventana no ocupa: Dormir a las 22:00 deja el resto de la noche fuera")
    void fueraDeLaVentana() {
        DiaDeHuecos dia = calcular(List.of(habito(HabitoConHora.DESPERTAR, 6, 0), habito(HabitoConHora.DORMIR, 22, 0)),
                List.of(evento(LUNES.atTime(21, 30), LUNES.atTime(23, 30))));

        assertThat(dia.ocupados()).containsExactly(tramo(6, 0, 6, 30), tramo(21, 30, 22, 0));
        assertThat(dia.huecos()).containsExactly(tramo(6, 30, 21, 30));
    }

    @Test
    @DisplayName("un evento que cruza la medianoche cuenta su parte en cada dia")
    void eventoQueCruza() {
        ItemPlanificado fiesta = evento(LUNES.atTime(23, 0), LUNES.plusDays(1).atTime(1, 0));

        DiaDeHuecos lunes = DiaDeHuecos.calcular(LUNES, List.of(), List.of(fiesta), List.of());
        DiaDeHuecos martes = DiaDeHuecos.calcular(LUNES.plusDays(1), List.of(), List.of(fiesta), List.of());

        assertThat(lunes.ocupados()).containsExactly(tramo(23, 0, 24, 0));
        assertThat(martes.ocupados()).containsExactly(tramo(0, 0, 1, 0));
    }

    @Test
    @DisplayName("las horas ocupadas que la persona guardo con SER cuentan como ocupadas y el dia tiene plan")
    void agendaGuardada() {
        DiaDeHuecos dia = DiaDeHuecos.calcular(LUNES, List.of(), List.of(), List.of(tramo(9, 0, 18, 0)));

        assertThat(dia.conPlan()).isTrue();
        assertThat(dia.ocupados()).containsExactly(tramo(9, 0, 18, 0));
        assertThat(dia.huecos()).containsExactly(tramo(0, 0, 9, 0), tramo(18, 0, 24, 0));
    }

    @Test
    @DisplayName("ocupado + libre es siempre la ventana, ningun hueco se cruza con un item y no importa el orden")
    void invariantes() {
        Random azar = new Random(29);
        for (int vuelta = 0; vuelta < 300; vuelta++) {
            List<ItemPlanificado> otros = new ArrayList<>();
            for (int i = 0; i < 1 + azar.nextInt(25); i++) {
                LocalDateTime inicia = LUNES.atStartOfDay().plusMinutes(azar.nextInt(1440));
                otros.add(evento(inicia, inicia.plusMinutes(1 + azar.nextInt(300))));
            }
            List<HabitoConHora> habitos = new ArrayList<>(List.of(
                    habito(HabitoConHora.DESPERTAR, 5 + azar.nextInt(3), 0),
                    habito(HabitoConHora.DORMIR, 21 + azar.nextInt(3), 0), habito(null, azar.nextInt(24), 0)));
            DiaDeHuecos dia = calcular(habitos, otros);

            assertThat(dia.minutosOcupados() + dia.minutosLibres()).isEqualTo(dia.ventana().minutos());
            dia.huecos().forEach(hueco -> assertThat(dia.ocupados())
                    .noneMatch(ocupado -> ocupado.desde() < hueco.hasta() && hueco.desde() < ocupado.hasta()));
            dia.huecos().forEach(hueco -> assertThat(hueco.minutos())
                    .isGreaterThanOrEqualTo(ReglasDeHuecos.MINUTOS_DEL_HUECO_UTIL));

            Collections.shuffle(otros, azar);
            Collections.shuffle(habitos, azar);
            assertThat(calcular(habitos, otros)).isEqualTo(dia);
        }
    }

    @Test
    @DisplayName("la agenda del dia: cada bloque con su tipo y nombre, recortado a la ventana y sin unir los que se cruzan")
    void bloquesDelDia() {
        DiaDeHuecos dia = DiaDeHuecos.calcular(LUNES,
                List.of(habito(HabitoConHora.DESPERTAR, 6, 0), habito(HabitoConHora.DORMIR, 22, 0)),
                List.of(evento(LUNES.atTime(10, 0), LUNES.atTime(11, 30)),
                        ItemPlanificado.deAccion(LUNES, LocalTime.of(11, 0), null, "Llamar al banco"),
                        // Cruza el borde de la ventana (22:00): solo queda lo que cae antes.
                        evento(LUNES.atTime(21, 30), LUNES.atTime(23, 0)),
                        // Toda la noche, fuera de la ventana: no sale.
                        evento(LUNES.atTime(23, 0), LUNES.plusDays(1).atTime(1, 0))),
                List.of(new TramoDelDia(780, 840)));

        assertThat(dia.detalle().bloques()).containsExactly(
                new BloqueOcupado(tramo(6, 0, 6, 30), TipoDeOcupado.HABITO, "Despertar"),
                new BloqueOcupado(tramo(10, 0, 11, 30), TipoDeOcupado.EVENTO, "Evento"),
                new BloqueOcupado(tramo(11, 0, 11, 30), TipoDeOcupado.ACCION, "Llamar al banco"),
                new BloqueOcupado(tramo(13, 0, 14, 0), TipoDeOcupado.AGENDA, null),
                new BloqueOcupado(tramo(21, 30, 22, 0), TipoDeOcupado.EVENTO, "Evento"));
        // La union de siempre no cambia: el evento y la accion siguen siendo un solo bloque ocupado.
        assertThat(dia.ocupados()).contains(tramo(10, 0, 11, 30));
    }

    @Test
    @DisplayName("la agenda personal nunca lleva titulo, aunque se lo pongan; un titulo en blanco es sin titulo")
    void agendaSinTitulo() {
        assertThat(new BloqueOcupado(tramo(9, 0, 10, 0), TipoDeOcupado.AGENDA, "Dentista").titulo()).isNull();
        assertThat(new BloqueOcupado(tramo(9, 0, 10, 0), TipoDeOcupado.HABITO, "   ").titulo()).isNull();
        assertThat(new BloqueOcupado(tramo(9, 0, 10, 0), TipoDeOcupado.EVENTO, "  Clase ").titulo()).isEqualTo("Clase");
    }

    @Test
    @DisplayName("sin ventana (el dia entero) los bloques no se recortan")
    void bloquesSinVentana() {
        DiaDeHuecos dia = calcular(List.of(habito(null, 7, 0)),
                List.of(evento(LUNES.atTime(0, 0), LUNES.atTime(1, 0)), evento(LUNES.atTime(23, 0), LUNES.plusDays(1).atTime(2, 0))));

        assertThat(dia.detalle().bloques()).extracting(BloqueOcupado::tramo)
                .containsExactly(tramo(0, 0, 1, 0), tramo(7, 0, 7, 30), tramo(23, 0, 24, 0));
        assertThat(dia.detalle().sinVentana()).isEqualTo(MotivoSinVentana.SIN_DESPERTAR_NI_DORMIR);
    }

    @Test
    @DisplayName("por que no hay ventana: sin Despertar, sin Dormir, sin ninguno o Dormir antes de Despertar; con ventana, nada")
    void motivoSinVentana() {
        assertThat(calcular(List.of(habito(HabitoConHora.DORMIR, 22, 0)), List.of()).detalle().sinVentana())
                .isEqualTo(MotivoSinVentana.SIN_DESPERTAR);
        assertThat(calcular(List.of(habito(HabitoConHora.DESPERTAR, 6, 0)), List.of()).detalle().sinVentana())
                .isEqualTo(MotivoSinVentana.SIN_DORMIR);
        assertThat(calcular(List.of(), List.of()).detalle().sinVentana())
                .isEqualTo(MotivoSinVentana.SIN_DESPERTAR_NI_DORMIR);
        assertThat(calcular(List.of(habito(HabitoConHora.DESPERTAR, 22, 0), habito(HabitoConHora.DORMIR, 6, 0)),
                List.of()).detalle().sinVentana()).isEqualTo(MotivoSinVentana.DORMIR_ANTES_DE_DESPERTAR);
        assertThat(calcular(List.of(habito(HabitoConHora.DESPERTAR, 6, 0), habito(HabitoConHora.DORMIR, 22, 0)),
                List.of()).detalle().sinVentana()).isNull();
    }

    @Test
    @DisplayName("un dia de la foto no lleva detalle y no se le puede poner lo real")
    void diaDeLaFoto() {
        DiaDeHuecos deLaFoto = new DiaDeHuecos(LUNES, VentanaDelDia.diaEntero(), List.of(), List.of(), false);

        assertThat(deLaFoto.detalle()).isNull();
        assertThatThrownBy(() -> deLaFoto.conLoReal(new LoReal(1, 0, 0))).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("conLoReal solo cambia lo real: los huecos y los bloques quedan")
    void conLoReal() {
        DiaDeHuecos dia = calcular(List.of(habito(null, 7, 0)), List.of());
        DiaDeHuecos conReal = dia.conLoReal(new LoReal(3, 2, 1));

        assertThat(conReal.detalle().real()).isEqualTo(new LoReal(3, 2, 1));
        assertThat(conReal.detalle().bloques()).isEqualTo(dia.detalle().bloques());
        assertThat(conReal.huecos()).isEqualTo(dia.huecos());
    }
}
