package com.renaser.os.rag.application.services.analisis;

import com.renaser.os.habits.api.RespuestasDelRadarFinder.RespuestaDelRadar;
import com.renaser.os.rag.api.Analisis8020Finder.ResumenDelRadar;
import com.renaser.os.rag.application.services.analisis.ResumenDelCodigoRenaser.MuestraDelRadar;
import com.renaser.os.rocks.api.PeriodosDelProgramaFinder.PeriodoDelPrograma;
import com.renaser.os.rocks.api.PeriodosDelProgramaFinder.Tipo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.renaser.os.rag.application.services.analisis.CifrasDePrueba.LIMA;
import static com.renaser.os.rag.application.services.analisis.CifrasDePrueba.respuesta;
import static org.assertj.core.api.Assertions.assertThat;

/** Lo que se cuenta del Código Renaser (spec 031, 031-3 A): cuentas y energía por momento, con la hora local de la persona. */
class ResumenDelCodigoRenaserTest {

    /** 09:00 en Lima = 14:00 UTC; 15:00 en Lima = 20:00 UTC; 21:00 en Lima = 02:00 UTC del día siguiente. */
    private static final Instant MANANA = Instant.parse("2026-10-20T14:00:00Z");
    private static final Instant TARDE = Instant.parse("2026-10-20T20:00:00Z");
    private static final Instant NOCHE_CON_UTC_DEL_DIA_SIGUIENTE = Instant.parse("2026-10-21T02:00:00Z");

    @Test
    @DisplayName("promedio, mínima, máxima y energía por momento del día en la hora de la persona, no en UTC")
    void cuentas() {
        List<RespuestaDelRadar> respuestas = List.of(respuesta(MANANA, 8, "el correo"), respuesta(TARDE, 4, "el celular"),
                respuesta(TARDE.plusSeconds(3600), 5, "el celular"), respuesta(NOCHE_CON_UTC_DEL_DIA_SIGUIENTE, 6, ""));

        ResumenDelRadar resumen = ResumenDelCodigoRenaser.resumir(respuestas, LIMA, 3, List.of());

        assertThat(resumen.respuestas()).isEqualTo(4);
        assertThat(resumen.energiaPromedio()).isEqualTo(5.8);
        assertThat(resumen.energiaMinima()).isEqualTo(4);
        assertThat(resumen.energiaMaxima()).isEqualTo(8);
        assertThat(resumen.porMomento().manana()).isEqualTo(8.0);
        assertThat(resumen.porMomento().tarde()).isEqualTo(4.5);
        assertThat(resumen.porMomento().noche()).isEqualTo(6.0);
        assertThat(resumen.dictadas()).isZero();
    }

    @Test
    @DisplayName("con menos respuestas que el mínimo no hay lectura del Código Renaser")
    void pocasRespuestas() {
        assertThat(ResumenDelCodigoRenaser.resumir(List.of(respuesta(MANANA, 8, "x"), respuesta(TARDE, 4, "y")), LIMA, 3, List.of())).isNull();
        assertThat(ResumenDelCodigoRenaser.resumir(List.of(), LIMA, 3, List.of())).isNull();
    }

    @Test
    @DisplayName("un mes mira la energía semana a semana")
    void porSemana() {
        PeriodoDelPrograma semana9 = new PeriodoDelPrograma(Tipo.SEMANA, 9, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18));
        PeriodoDelPrograma semana10 = new PeriodoDelPrograma(Tipo.SEMANA, 10, LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25));
        List<RespuestaDelRadar> respuestas = List.of(respuesta(Instant.parse("2026-10-14T14:00:00Z"), 3, "a"), respuesta(MANANA, 8, "b"),
                respuesta(TARDE, 6, "c"));

        ResumenDelRadar resumen = ResumenDelCodigoRenaser.resumir(respuestas, LIMA, 3, List.of(semana9, semana10));

        assertThat(resumen.porSemana()).extracting("numero", "respuestas", "energiaPromedio")
                .containsExactly(org.assertj.core.groups.Tuple.tuple(9, 1, 3.0), org.assertj.core.groups.Tuple.tuple(10, 2, 7.0));
    }

    @Test
    @DisplayName("las muestras son pocas, repartidas parejo, y cada texto va recortado sin partir un emoji")
    void muestras() {
        List<RespuestaDelRadar> respuestas = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            respuestas.add(respuesta(MANANA.plusSeconds(i * 600L), 1 + i % 10, "a".repeat(10) + (i == 0 ? "😀".repeat(300) : "")));
        }

        List<MuestraDelRadar> muestras = ResumenDelCodigoRenaser.muestras(respuestas, LIMA);

        assertThat(muestras).hasSize(18);
        assertThat(muestras.get(0).queEvito().codePointCount(0, muestras.get(0).queEvito().length())).isEqualTo(240);
        assertThat(muestras.get(0).queEvito()).doesNotContain("�");
        assertThat(ResumenDelCodigoRenaser.muestras(respuestas.subList(0, 2), LIMA)).hasSize(2);
    }
}
