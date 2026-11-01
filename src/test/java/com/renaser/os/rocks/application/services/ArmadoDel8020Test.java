package com.renaser.os.rocks.application.services;

import com.renaser.os.habits.api.EvaluacionDelDiaPort.MarcaDeCierre;
import com.renaser.os.rocks.api.Pareto8020Finder.AccionDel8020;
import com.renaser.os.rocks.api.Pareto8020Finder.CifrasDel8020;
import com.renaser.os.rocks.api.Pareto8020Finder.InsumosDel8020;
import com.renaser.os.rocks.api.Pareto8020Finder.Repeticion;
import com.renaser.os.rocks.domain.model.cierre.CierreDeAccion;
import com.renaser.os.rocks.domain.model.cierre.MotivoDeAccionPendiente;
import com.renaser.os.rocks.domain.model.cierre.PlanDeManana;
import com.renaser.os.rocks.domain.model.cierre.RespuestaDeCierre;
import com.renaser.os.rocks.domain.model.rocadiaria.ColorPareto;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiariaId;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La cuenta del 80/20 con una semana hecha a mano (spec 031, SC-002): cada cifra de acá se puede verificar con lápiz y papel. Sin Spring,
 * sin base y sin reloj: el «hoy» entra por parámetro.
 *
 * <p>La semana: 12 acciones planificadas del lunes 19 al jueves 22 de octubre de 2026; 9 cumplidas (3 de ellas verdes), 3 pendientes. El
 * puntaje de impacto lo escribió la persona.
 */
class ArmadoDel8020Test {

    private static final UserId ANA = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final Instant ANTES = Instant.parse("2026-10-20T15:00:00Z");
    private static final LocalDate LUNES = LocalDate.of(2026, 10, 19);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 10, 25);
    private static final LocalDate DESPUES_DE_LA_SEMANA = LocalDate.of(2026, 10, 26);

    private static RocaDiaria roca(int dia, EjeObjetivo eje, int posicion, int impacto, boolean cumplida, String titulo) {
        return RocaDiaria.rehydrate(new RocaDiariaId(UUID.randomUUID()), ANA, LUNES.plusDays(dia), posicion, titulo, null,
                ColorPareto.paraPosicion(posicion), impacto, false, eje, null, null, null, List.of(), cumplida,
                cumplida ? ANTES : null, 0, ANTES, ANTES, null);
    }

    private static RocaDiaria conCierre(RocaDiaria base, MotivoDeAccionPendiente motivo, PlanDeManana plan, String nota) {
        return RocaDiaria.rehydrate(base.id(), ANA, base.fecha(), base.posicion(), base.titulo(), null, base.color(),
                base.puntajeImpacto(), false, base.eje(), null, null, null, List.of(), false, null, 0, ANTES, ANTES,
                new CierreDeAccion(new RespuestaDeCierre(motivo, plan, nota), ANTES));
    }

    private static List<RocaDiaria> laSemana() {
        List<RocaDiaria> rocas = new ArrayList<>();
        rocas.add(roca(0, EjeObjetivo.CUERPO, 1, 9, true, "Entrenar"));
        rocas.add(roca(0, EjeObjetivo.CUERPO, 2, 5, true, "Caminar"));
        rocas.add(roca(0, EjeObjetivo.TRABAJO, 1, 8, true, "Cerrar la propuesta"));
        rocas.add(roca(1, EjeObjetivo.TRABAJO, 2, 4, true, "Ordenar correos"));
        rocas.add(roca(1, EjeObjetivo.RELACIONES, 1, 7, false, "Llamar a mamá"));
        rocas.add(roca(1, EjeObjetivo.RELACIONES, 2, 3, true, "Escribirle a un amigo"));
        rocas.add(roca(2, EjeObjetivo.CUERPO, 1, 9, true, "Entrenar piernas"));
        rocas.add(roca(2, EjeObjetivo.TRABAJO, 1, 10, false, "Llamar al cliente grande"));
        rocas.add(roca(2, EjeObjetivo.TRABAJO, 3, 2, true, "Archivar"));
        rocas.add(roca(3, EjeObjetivo.RELACIONES, 3, 1, true, "Saludar"));
        rocas.add(roca(3, EjeObjetivo.CUERPO, 3, 2, false, "Estirar"));
        rocas.add(roca(3, EjeObjetivo.TRABAJO, 2, 6, true, "Preparar la reunión"));
        return rocas;
    }

    private static CifrasDel8020 cifras(List<RocaDiaria> rocas, LocalDate hoy) {
        return ArmadoDel8020.armar(rocas, List.of(), LUNES, DOMINGO, hoy).cifras();
    }

    @Test
    @DisplayName("las cifras de la semana coinciden con la cuenta hecha a mano: 9 de 12, 75 %, impacto 47 de 66")
    void laCuentaBasica() {
        CifrasDel8020 cifras = cifras(laSemana(), DESPUES_DE_LA_SEMANA);

        assertThat(cifras.planificadas()).isEqualTo(12);
        assertThat(cifras.cumplidas()).isEqualTo(9);
        assertThat(cifras.porcentajeCumplidas()).isEqualTo(75);
        assertThat(cifras.diasConAcciones()).isEqualTo(4);
        assertThat(cifras.impactoPlanificado()).isEqualTo(66);
        assertThat(cifras.impactoCumplido()).isEqualTo(47);
        assertThat(cifras.porVenir()).isZero();
        assertThat(cifras.hayAcciones()).isTrue();
    }

    @Test
    @DisplayName("regla A (031-1): 5 verdes planificadas, 3 cumplidas, que son el 55 % del impacto cumplido")
    void reglaDeLasVerdes() {
        var verdes = cifras(laSemana(), DESPUES_DE_LA_SEMANA).verdes();

        assertThat(verdes.planificadas()).isEqualTo(5);
        assertThat(verdes.cumplidas()).isEqualTo(3);
        assertThat(verdes.impactoCumplido()).isEqualTo(26);
        assertThat(verdes.porcentajeDelImpacto()).isEqualTo(55);
    }

    @Test
    @DisplayName("regla B (031-1): el 20 % de 9 cumplidas son 2 (las de impacto 9), el 38 % del impacto cumplido")
    void reglaDelVeintePorCiento() {
        CifrasDel8020 cifras = cifras(laSemana(), DESPUES_DE_LA_SEMANA);

        assertThat(cifras.veintePorCiento().acciones()).isEqualTo(2);
        assertThat(cifras.veintePorCiento().impacto()).isEqualTo(18);
        assertThat(cifras.veintePorCiento().porcentajeDelImpacto()).isEqualTo(38);
        assertThat(cifras.lasQueMasMovieron()).extracting(AccionDel8020::titulo).containsExactly("Entrenar", "Entrenar piernas");
    }

    @Test
    @DisplayName("lo que se fue: las verdes y el 20 % de mayor impacto que quedaron pendientes; lo de poco impacto no se nombra")
    void loQueSeFue() {
        CifrasDel8020 cifras = cifras(laSemana(), DESPUES_DE_LA_SEMANA);

        assertThat(cifras.lasQueSeFueron()).extracting(AccionDel8020::titulo).containsExactly("Llamar al cliente grande", "Llamar a mamá");
        assertThat(cifras.lasQueSeFueron().get(0).color()).isEqualTo("VERDE");
        assertThat(cifras.cumplioLoQueMenosMovia()).isTrue();
    }

    @Test
    @DisplayName("por eje: acciones, cumplidas y la parte del impacto cumplido que puso cada uno")
    void porEje() {
        var ejes = cifras(laSemana(), DESPUES_DE_LA_SEMANA).porEje();

        assertThat(ejes).extracting("eje", "planificadas", "cumplidas", "impactoCumplido", "porcentajeDelImpacto")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("CUERPO", 4, 3, 23, 49),
                        org.assertj.core.groups.Tuple.tuple("TRABAJO", 5, 4, 20, 43),
                        org.assertj.core.groups.Tuple.tuple("RELACIONES", 3, 2, 4, 9));
    }

    @Test
    @DisplayName("con menos de cinco cumplidas no hay regla del 20 %: una sola acción explicando «el 100 %» no es un hallazgo")
    void pocasCumplidas() {
        List<RocaDiaria> rocas = List.of(roca(0, EjeObjetivo.CUERPO, 1, 9, true, "a"), roca(1, EjeObjetivo.CUERPO, 1, 7, true, "b"),
                roca(2, EjeObjetivo.CUERPO, 1, 5, true, "c"), roca(3, EjeObjetivo.CUERPO, 1, 3, true, "d"),
                roca(3, EjeObjetivo.CUERPO, 2, 2, false, "e"));

        CifrasDel8020 cifras = cifras(rocas, DESPUES_DE_LA_SEMANA);

        assertThat(cifras.cumplidas()).isEqualTo(4);
        assertThat(cifras.veintePorCiento()).isNull();
        assertThat(cifras.lasQueMasMovieron()).isEmpty();
    }

    @Test
    @DisplayName("los empates en el impacto se resuelven siempre igual: fecha más vieja, posición más baja")
    void empates() {
        List<RocaDiaria> rocas = new ArrayList<>(List.of(roca(2, EjeObjetivo.CUERPO, 1, 8, true, "Miércoles"),
                roca(0, EjeObjetivo.CUERPO, 2, 8, true, "Lunes amarilla"), roca(0, EjeObjetivo.TRABAJO, 1, 8, true, "Lunes verde"),
                roca(1, EjeObjetivo.TRABAJO, 1, 8, true, "Martes"), roca(3, EjeObjetivo.TRABAJO, 1, 8, true, "Jueves")));

        List<AccionDel8020> primera = cifras(rocas, DESPUES_DE_LA_SEMANA).lasQueMasMovieron();
        java.util.Collections.reverse(rocas);
        List<AccionDel8020> invertida = cifras(rocas, DESPUES_DE_LA_SEMANA).lasQueMasMovieron();

        assertThat(primera).extracting(AccionDel8020::titulo).containsExactly("Lunes verde");
        assertThat(invertida).isEqualTo(primera);
    }

    @Test
    @DisplayName("«hasta hoy»: lo de días que no llegaron no cuenta y se informa aparte; una acción de hoy sin cumplir todavía no «se fue»")
    void hastaHoy() {
        CifrasDel8020 cifras = cifras(laSemana(), LUNES.plusDays(2));

        assertThat(cifras.planificadas()).isEqualTo(9);
        assertThat(cifras.porVenir()).isEqualTo(3);
        assertThat(cifras.lasQueSeFueron()).extracting(AccionDel8020::titulo).containsExactly("Llamar a mamá");
    }

    @Test
    @DisplayName("sin acciones planificadas no hay nada que contar: ni porcentajes ni reglas inventados")
    void sinAcciones() {
        CifrasDel8020 cifras = cifras(List.of(), DESPUES_DE_LA_SEMANA);

        assertThat(cifras.hayAcciones()).isFalse();
        assertThat(cifras.porcentajeCumplidas()).isNull();
        assertThat(cifras.verdes().porcentajeDelImpacto()).isNull();
        assertThat(cifras.veintePorCiento()).isNull();
        assertThat(cifras.porEje()).isEmpty();
    }

    @Test
    @DisplayName("031-4 B: «Cierra tu día» cuenta días cerrados y sin cerrar, motivos y planes, y deja las notas aparte")
    void cierreDelDia() {
        List<RocaDiaria> rocas = laSemana();
        RocaDiaria mama = conCierre(rocas.get(4), MotivoDeAccionPendiente.SIN_ENERGIA, PlanDeManana.OTRA_HORA, "Llegué agotada");
        RocaDiaria cliente = conCierre(rocas.get(7), MotivoDeAccionPendiente.SIN_ENERGIA, PlanDeManana.MAS_CORTA, null);
        rocas.set(4, mama);
        rocas.set(7, cliente);
        List<MarcaDeCierre> marcas = List.of(new MarcaDeCierre(LUNES, null, ANTES), new MarcaDeCierre(LUNES.plusDays(1), "Fue un día largo", ANTES));

        InsumosDel8020 insumos = ArmadoDel8020.armar(rocas, marcas, LUNES, DOMINGO, DESPUES_DE_LA_SEMANA);

        assertThat(insumos.cifras().cierre().diasCerrados()).isEqualTo(2);
        assertThat(insumos.cifras().cierre().diasSinCerrar()).isEqualTo(2);
        assertThat(insumos.cifras().cierre().motivos()).containsExactly(new Repeticion("SIN_ENERGIA", 2));
        assertThat(insumos.cifras().cierre().planes()).containsExactly(new Repeticion("MAS_CORTA", 1), new Repeticion("OTRA_HORA", 1));
        assertThat(insumos.notasDeCierre()).containsExactly("Llegué agotada", "Fue un día largo");
    }

    @Test
    @DisplayName("las notas de cierre se acotan a las últimas veinte")
    void notasAcotadas() {
        List<MarcaDeCierre> marcas = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            marcas.add(new MarcaDeCierre(LUNES.plusDays(i), "nota " + i, ANTES));
        }

        InsumosDel8020 insumos = ArmadoDel8020.armar(List.of(), marcas, LUNES, LUNES.plusDays(40), LUNES.plusDays(41));

        assertThat(insumos.notasDeCierre()).hasSize(20).first().isEqualTo("nota 10");
    }

    @Test
    @DisplayName("el porcentaje redondea al entero más cercano y no divide entre cero")
    void porcentaje() {
        assertThat(ArmadoDel8020.porcentaje(1, 3)).isEqualTo(33);
        assertThat(ArmadoDel8020.porcentaje(2, 3)).isEqualTo(67);
        assertThat(ArmadoDel8020.porcentaje(1, 2)).isEqualTo(50);
        assertThat(ArmadoDel8020.porcentaje(0, 5)).isZero();
        assertThat(ArmadoDel8020.porcentaje(5, 0)).isNull();
    }
}
