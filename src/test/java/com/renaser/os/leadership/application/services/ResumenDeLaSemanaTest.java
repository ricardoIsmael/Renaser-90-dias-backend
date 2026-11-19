package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase.DiaDeLaFicha;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase.SemanaDeLaPersona;
import com.renaser.os.leadership.domain.model.cumplimiento.ColorDeLaFila;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.Cifras;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.EvidenciaDeLaSemana;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.FocoDeLaSemana;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.TablaDePilares;
import com.renaser.os.mentoring.api.EvidenciaDeObligacion;
import com.renaser.os.mentoring.api.ObligacionDelDia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Las cuentas de la semana de una persona para su documento (spec 056, FR-035), sin Spring ni base: solo la semana de entrada y los datos de salida. */
class ResumenDeLaSemanaTest {

    private static final LocalDate SABADO = LocalDate.of(2026, 11, 14);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private static ObligacionDelDia habito(String titulo, String pilar, String estado) {
        return new ObligacionDelDia(UUID.randomUUID(), titulo, estado, false, "NO_REQUERIDA", null, null, null, null, null, List.of(), null, pilar);
    }

    private static ObligacionDelDia conEvidencias(String titulo, String revision, String texto, EvidenciaDeObligacion... evidencias) {
        return new ObligacionDelDia(UUID.randomUUID(), titulo, "COMPLETADO", true, "ENTREGADA", null, revision, null, null, texto,
                List.of(evidencias), null, "CUERPO");
    }

    /** Siete días desde el sábado; {@code porDia[i]} son los hábitos del día i (hasta siete; el resto, sin nada). */
    @SafeVarargs
    private static SemanaDeLaPersona semana(List<ObligacionDelDia>... porDia) {
        List<DiaDeLaFicha> dias = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            List<ObligacionDelDia> delDia = i < porDia.length ? porDia[i] : List.of();
            dias.add(new DiaDeLaFicha(SABADO.plusDays(i), 40 + i, delDia.isEmpty() ? "SIN_NADA_PLANIFICADO" : null, delDia, List.of()));
        }
        return new SemanaDeLaPersona(UUID.randomUUID(), UUID.randomUUID(), "Ana", null, null, SABADO, SABADO.plusDays(6), "America/Lima", true, null,
                null, ColorDeLaFila.AMARILLO, new BigDecimal("66.7"), null, dias, null);
    }

    private static List<ObligacionDelDia> de(ObligacionDelDia... obligaciones) {
        return List.of(obligaciones);
    }

    @Test
    @DisplayName("spec 056 FR-035: las cuatro cifras son hábitos cumplidos, días activos, evidencias y días programados, cada una contra la semana anterior")
    void lasCuatroCifras() {
        SemanaDeLaPersona actual = semana(
                de(habito("Caminar", "CUERPO", "COMPLETADO"), habito("Meditar", "CONSCIENCIA", "FALLIDO")),
                de(habito("Caminar", "CUERPO", "COMPLETADO")),
                de(habito("Caminar", "CUERPO", "FALLIDO")),
                de(conEvidencias("Foto", "VALIDA", null, new EvidenciaDeObligacion(UUID.randomUUID(), "FOTO", Instant.parse("2026-11-17T20:00:00Z")),
                        new EvidenciaDeObligacion(UUID.randomUUID(), "AUDIO", Instant.parse("2026-11-17T20:05:00Z")))));
        SemanaDeLaPersona anterior = semana(de(habito("Caminar", "CUERPO", "COMPLETADO")));

        Cifras cifras = ResumenDeLaSemana.cifras(actual, anterior);

        assertThat(cifras.habitosCumplidos().valor()).isEqualTo(3);
        assertThat(cifras.habitosCumplidos().anterior()).isEqualTo(1);
        assertThat(cifras.habitosCumplidos().diferencia()).isEqualTo(2);
        assertThat(cifras.diasActivos().valor()).as("el primer, el segundo y el cuarto día cumplieron algo").isEqualTo(3);
        assertThat(cifras.evidencias().valor()).isEqualTo(2);
        assertThat(cifras.diasProgramados().valor()).as("cuatro días con hábitos").isEqualTo(4);
        assertThat(cifras.diasProgramados().anterior()).isEqualTo(1);
    }

    @Test
    @DisplayName("spec 056 FR-035: sin semana anterior (antes de su Día 1) las cifras no traen comparación, y no se inventa un cero")
    void sinSemanaAnterior() {
        Cifras cifras = ResumenDeLaSemana.cifras(semana(de(habito("Caminar", "CUERPO", "COMPLETADO"))), null);

        assertThat(cifras.habitosCumplidos().anterior()).isNull();
        assertThat(cifras.habitosCumplidos().diferencia()).isNull();
        assertThat(cifras.diasProgramados().diferencia()).isNull();
    }

    @Test
    @DisplayName("spec 056 FR-035: la tabla va un pilar por fila en el orden de la ficha del mentor —Cuerpo, Mente, Espíritu, Emociones— y los de otra formación después")
    void laTablaDePilares() {
        SemanaDeLaPersona semana = semana(
                de(habito("Caminar", "CUERPO", "COMPLETADO"), habito("Meditar", "CONSCIENCIA", "FALLIDO"), habito("Vender", "TRABAJO", "COMPLETADO")),
                de(habito("Caminar", "CUERPO", "FALLIDO"), habito("Leer", "MENTE", "COMPLETADO"), habito("Suelto", null, "COMPLETADO")));

        TablaDePilares tabla = ResumenDeLaSemana.tabla(semana);

        assertThat(tabla.dias()).hasSize(7).startsWith(SABADO);
        assertThat(tabla.filas()).extracting(f -> f.pilar()).containsExactly("Cuerpo", "Mente", "Emociones", "Otros", "Trabajo");
        assertThat(tabla.filas().getFirst().celdas()).hasSize(7);
        assertThat(tabla.filas().getFirst().celdas().get(0)).extracting(c -> c.cumplidos(), c -> c.programados()).containsExactly(1, 1);
        assertThat(tabla.filas().getFirst().celdas().get(1)).extracting(c -> c.cumplidos(), c -> c.programados()).containsExactly(0, 1);
        assertThat(tabla.filas().getFirst().celdas().get(2)).extracting(c -> c.cumplidos(), c -> c.programados()).containsExactly(0, 0);
    }

    @Test
    @DisplayName("spec 056 FR-035: una evidencia subida de noche cuenta en el día LOCAL de la persona, no en el de UTC (regla 02), con la revisión de su hábito")
    void lasEvidenciasEnElDiaLocal() {
        // 03:30 UTC del domingo 15 = 22:30 del sábado 14 en Lima.
        SemanaDeLaPersona semana = semana(de(conEvidencias("Caminar", "VALIDA", null,
                new EvidenciaDeObligacion(UUID.randomUUID(), "FOTO", Instant.parse("2026-11-15T03:30:00Z")))));

        List<EvidenciaDeLaSemana> evidencias = ResumenDeLaSemana.evidencias(semana, LIMA);

        assertThat(evidencias).singleElement().satisfies(e -> {
            assertThat(e.fecha()).isEqualTo(SABADO);
            assertThat(e.tipo()).isEqualTo("FOTO");
            assertThat(e.estado()).isEqualTo("VALIDA");
            assertThat(e.habito()).isEqualTo("Caminar");
        });
    }

    @Test
    @DisplayName("spec 056 FR-035: «En sus palabras» trae solo lo que la persona escribió, recortado; los hábitos sin texto no entran")
    void lasPalabras() {
        SemanaDeLaPersona semana = semana(
                de(conEvidencias("Meditar", null, "  Me sentí en calma  "), conEvidencias("Caminar", null, "   "), conEvidencias("Leer", null, null)),
                de(habito("Sin nada", "MENTE", "COMPLETADO")));

        assertThat(ResumenDeLaSemana.palabras(semana)).singleElement().satisfies(p -> {
            assertThat(p.texto()).isEqualTo("Me sentí en calma");
            assertThat(p.habito()).isEqualTo("Meditar");
            assertThat(p.fecha()).isEqualTo(SABADO);
        });
        assertThat(ResumenDeLaSemana.palabras(semana(de(habito("Caminar", "CUERPO", "COMPLETADO"))))).isEmpty();
    }

    @Test
    @DisplayName("spec 056 FR-035: el foco es el pilar en que más faltó; ante un empate, el primero de la ficha; si todo se cumplió, sostener el ritmo; sin hábitos, nada")
    void elFoco() {
        SemanaDeLaPersona flojaEnEmociones = semana(
                de(habito("Caminar", "CUERPO", "COMPLETADO"), habito("Meditar", "CONSCIENCIA", "FALLIDO")),
                de(habito("Caminar", "CUERPO", "COMPLETADO"), habito("Meditar", "CONSCIENCIA", "COMPLETADO")),
                de(habito("Caminar", "CUERPO", "FALLIDO"), habito("Meditar", "CONSCIENCIA", "FALLIDO")));
        SemanaDeLaPersona empate = semana(de(habito("Caminar", "CUERPO", "FALLIDO"), habito("Leer", "MENTE", "FALLIDO")));
        SemanaDeLaPersona completa = semana(de(habito("Caminar", "CUERPO", "COMPLETADO")));

        assertThat(ResumenDeLaSemana.foco(ResumenDeLaSemana.tabla(flojaEnEmociones))).isEqualTo(new FocoDeLaSemana("Emociones", 1, 3));
        assertThat(ResumenDeLaSemana.foco(ResumenDeLaSemana.tabla(empate))).isEqualTo(new FocoDeLaSemana("Cuerpo", 0, 1));
        assertThat(ResumenDeLaSemana.foco(ResumenDeLaSemana.tabla(completa))).isEqualTo(new FocoDeLaSemana(null, 0, 0));
        assertThat(ResumenDeLaSemana.foco(ResumenDeLaSemana.tabla(semana()))).isNull();
    }

    @Test
    @DisplayName("spec 056 FR-035: el detalle trae los siete días con su día del programa, el motivo si no se midió y cada hábito con su pilar y su estado")
    void elDetalle() {
        SemanaDeLaPersona semana = semana(de(habito("Caminar", "CUERPO", "COMPLETADO")));

        var detalle = ResumenDeLaSemana.detalle(semana);

        assertThat(detalle).hasSize(7);
        assertThat(detalle.getFirst().dia()).isEqualTo(40);
        assertThat(detalle.getFirst().motivo()).isNull();
        assertThat(detalle.getFirst().habitos()).singleElement().satisfies(h -> {
            assertThat(h.pilar()).isEqualTo("Cuerpo");
            assertThat(h.titulo()).isEqualTo("Caminar");
            assertThat(h.estado()).isEqualTo("COMPLETADO");
        });
        assertThat(detalle.get(1).motivo()).isEqualTo("SIN_NADA_PLANIFICADO");
        assertThat(detalle.get(1).habitos()).isEmpty();
    }

    @Test
    @DisplayName("spec 056 FR-038: una semana sin un solo hábito programado tiene cero días programados: es la que el documento dice «sin actividad»")
    void laSemanaVacia() {
        assertThat(ResumenDeLaSemana.diasProgramados(semana())).isZero();
        assertThat(ResumenDeLaSemana.diasProgramados(semana(de(habito("Caminar", "CUERPO", "FALLIDO"))))).isEqualTo(1);
    }
}
