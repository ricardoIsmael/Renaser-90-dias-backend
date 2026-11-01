package com.renaser.os.rag.application.services.analisis;

import com.renaser.os.habits.api.RespuestasDelRadarFinder.RespuestaDelRadar;
import com.renaser.os.rocks.api.Pareto8020Finder.AccionDel8020;
import com.renaser.os.rocks.api.Pareto8020Finder.CifrasDel8020;
import com.renaser.os.rocks.api.Pareto8020Finder.CifrasDelEje;
import com.renaser.os.rocks.api.Pareto8020Finder.InsumosDel8020;
import com.renaser.os.rocks.api.Pareto8020Finder.ReglaDeLasVerdes;
import com.renaser.os.rocks.api.Pareto8020Finder.ReglaDelVeintePorCiento;
import com.renaser.os.rocks.api.Pareto8020Finder.Repeticion;
import com.renaser.os.rocks.api.Pareto8020Finder.ResumenDelCierre;
import com.renaser.os.rocks.api.PeriodosDelProgramaFinder.PeriodoDelPrograma;
import com.renaser.os.rocks.api.PeriodosDelProgramaFinder.Tipo;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Cifras y periodos armados a mano para las pruebas del análisis 80/20: sin Spring, sin base y sin IA. */
final class CifrasDePrueba {

    static final ZoneId LIMA = ZoneId.of("America/Lima");
    static final PeriodoDelPrograma SEMANA_10 = new PeriodoDelPrograma(Tipo.SEMANA, 10, LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25));

    private CifrasDePrueba() {
    }

    /** Una semana de 12 acciones planificadas, 9 cumplidas (75 %), 3 verdes cumplidas del 55 % del impacto cumplido. */
    static CifrasDel8020 semanaDeDoce() {
        return cifras(12, 9, 4);
    }

    static CifrasDel8020 cifras(int planificadas, int cumplidas, int dias) {
        return new CifrasDel8020(SEMANA_10.inicio(), SEMANA_10.fin(), planificadas, cumplidas, dias, 0, 66, 47,
                planificadas == 0 ? null : 75, new ReglaDeLasVerdes(5, 3, 26, 55), new ReglaDelVeintePorCiento(2, 18, 38),
                List.of(new CifrasDelEje("CUERPO", 4, 3, 23, 49)),
                List.of(new AccionDel8020("CUERPO", "VERDE", 9, LocalDate.of(2026, 10, 19), "Entrenar")),
                List.of(new AccionDel8020("TRABAJO", "VERDE", 10, LocalDate.of(2026, 10, 21), "Llamar al cliente grande 30 minutos")),
                true, new ResumenDelCierre(2, 2, List.of(new Repeticion("SIN_ENERGIA", 2)), List.of(new Repeticion("OTRA_HORA", 1))));
    }

    static InsumosDelAnalisis insumos(CifrasDel8020 cifras, List<RespuestaDelRadar> respuestas) {
        return new InsumosDelAnalisis(SEMANA_10, LIMA, new InsumosDel8020(cifras, List.of("Llegué agotada")), List.of(), respuestas,
                ResumenDelCodigoRenaser.resumir(respuestas, LIMA, 3, List.of()));
    }

    static RespuestaDelRadar respuesta(Instant cuando, int energia, String evito) {
        return new RespuestaDelRadar(cuando, null, energia, "Trabajando", "En la reunión", "Cansada", evito, false);
    }
}
