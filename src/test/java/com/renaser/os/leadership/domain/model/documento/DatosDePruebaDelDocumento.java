package com.renaser.os.leadership.domain.model.documento;

import com.renaser.os.leadership.domain.model.cumplimiento.ColorDeLaFila;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.CeldaDelDia;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.Cifra;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.Cifras;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.DetalleDelDia;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.Encabezado;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.EvaluacionDelMentor;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.EvidenciaDeLaSemana;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.FilaDePilar;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.FocoDeLaSemana;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.HabitoDelDia;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.PalabraDeLaPersona;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.TablaDePilares;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.Titular;
import com.renaser.os.leadership.domain.model.documento.DatosDelMentor.AprendizDelMentor;
import com.renaser.os.leadership.domain.model.documento.DatosDelMentor.AprendizEnAlerta;
import com.renaser.os.leadership.domain.model.documento.DatosDelMentor.ConteoPorColor;
import com.renaser.os.leadership.domain.model.documento.DatosDelMentor.Foco;
import com.renaser.os.leadership.domain.model.documento.DatosDelMentor.LoQueLeDijoElLider;
import com.renaser.os.leadership.domain.model.documento.DatosDelMentor.Pendientes;
import com.renaser.os.leadership.domain.model.documento.DatosDelMentor.TitularDelGrupo;
import com.renaser.os.leadership.domain.model.documento.DatosDelMes.MentorDelRanking;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Datos de ejemplo de los tres documentos, para las pruebas del dominio, de la plantilla y del servicio (spec 056). Una semana de la
 * Escuela: del sábado 14 al viernes 20 de noviembre de 2026, con Ana en el día 47 de un programa de 365 días (así ninguna prueba se
 * apoya en un plazo escrito a mano).
 */
public final class DatosDePruebaDelDocumento {

    public static final LocalDate SABADO = LocalDate.of(2026, 11, 14);
    public static final LocalDate VIERNES = LocalDate.of(2026, 11, 20);
    public static final String ZONA = "America/Lima";

    private DatosDePruebaDelDocumento() {
    }

    public static DatosDelAprendiz aprendiz() {
        return aprendiz("Ana Pérez");
    }

    public static DatosDelAprendiz aprendiz(String nombre) {
        return new DatosDelAprendiz(new Encabezado(nombre, "Escuela Renaser", 47, 365, "Luisa Rojas", "Fénix", SABADO, VIERNES, ZONA),
                new Titular(ColorDeLaFila.AMARILLO, new BigDecimal("66.7")),
                new Cifras(new Cifra(8, 6), new Cifra(4, 5), new Cifra(3, null), new Cifra(5, 5)),
                new TablaDePilares(dias(), List.of(
                        new FilaDePilar("Cuerpo", List.of(celda(1, 1), celda(0, 1), celda(1, 1), celda(1, 1), celda(0, 0), celda(0, 0), celda(0, 0))),
                        new FilaDePilar("Emociones", List.of(celda(0, 0), celda(1, 1), celda(0, 1), celda(1, 1), celda(0, 0), celda(0, 0), celda(0, 0))))),
                List.of(new EvidenciaDeLaSemana("FOTO", LocalDate.of(2026, 11, 14), "VALIDA", "Caminar"),
                        new EvidenciaDeLaSemana("AUDIO", LocalDate.of(2026, 11, 16), "PENDIENTE", "Meditar")),
                List.of(new PalabraDeLaPersona(LocalDate.of(2026, 11, 16), "Meditar", "Me sentí en calma después de caminar.")),
                new EvaluacionDelMentor("2026-11", new BigDecimal("82.5"), "CALCULADA"),
                new FocoDeLaSemana("Emociones", 2, 4),
                List.of(new DetalleDelDia(LocalDate.of(2026, 11, 14), 46, null,
                                List.of(new HabitoDelDia("Cuerpo", "Caminar 30 minutos", "COMPLETADO", "ENTREGADA"))),
                        new DetalleDelDia(LocalDate.of(2026, 11, 15), 47, "SIN_NADA_PLANIFICADO", List.of())));
    }

    /** La misma semana, sin un solo hábito programado (FR-038). */
    public static DatosDelAprendiz aprendizSinActividad() {
        DatosDelAprendiz base = aprendiz();
        return new DatosDelAprendiz(base.encabezado(), new Titular(ColorDeLaFila.SIN_DATOS, null),
                new Cifras(new Cifra(0, 0), new Cifra(0, 0), new Cifra(0, 0), new Cifra(0, 0)),
                new TablaDePilares(dias(), List.of()), List.of(), List.of(), null, null, List.of());
    }

    public static DatosDelAprendiz aprendizConPalabras(String texto) {
        DatosDelAprendiz base = aprendiz();
        return new DatosDelAprendiz(base.encabezado(), base.titular(), base.cifras(), base.tabla(), base.evidencias(),
                List.of(new PalabraDeLaPersona(LocalDate.of(2026, 11, 16), "Meditar", texto)), base.evaluacionDelMentor(),
                base.foco(), base.detalle());
    }

    public static DatosDelAprendiz aprendizConDia(Integer dia) {
        DatosDelAprendiz base = aprendiz();
        Encabezado e = base.encabezado();
        return new DatosDelAprendiz(new Encabezado(e.nombre(), e.formacion(), dia, e.duracionDias(), e.mentor(), e.grupo(), e.desde(),
                e.hasta(), e.zona()), base.titular(), base.cifras(), base.tabla(), base.evidencias(), base.palabras(),
                base.evaluacionDelMentor(), base.foco(), base.detalle());
    }

    public static DatosDelMentor mentor() {
        return mentor(null);
    }

    /** El mentor Luisa con su grupo Fénix; {@code suSemana} null = no sigue su programa. */
    public static DatosDelMentor mentor(DatosDelAprendiz suSemana) {
        return new DatosDelMentor(new DatosDelMentor.Encabezado("Luisa Rojas", "Escuela Renaser", 1, SABADO, VIERNES, ZONA),
                new TitularDelGrupo(ColorDeLaFila.AMARILLO, new BigDecimal("71.5"), 4, List.of("Fénix")),
                new ConteoPorColor(1, 1, 1, 1),
                new ArrayList<>(List.of(
                        new AprendizDelMentor("Ana Pérez", "Fénix", ColorDeLaFila.ROJO, new BigDecimal("40"), "BAJA"),
                        new AprendizDelMentor("Beto Álvarez", "Fénix", ColorDeLaFila.AMARILLO, new BigDecimal("70"), "SUBE"),
                        new AprendizDelMentor("Carla Ríos", "Fénix", ColorDeLaFila.SIN_DATOS, null, "SIN_COMPARACION"),
                        new AprendizDelMentor("Diego Soto", "Fénix", ColorDeLaFila.VERDE, new BigDecimal("95"), "IGUAL"))),
                new Pendientes(2, 5, "CALCULADA", new BigDecimal("82.5"), List.of("Ana Pérez")),
                List.of(new AprendizEnAlerta("Ana Pérez", 3)),
                List.of(new LoQueLeDijoElLider("SUGERENCIA", Instant.parse("2026-11-17T15:00:00Z"), "Líder Uno", "Escríbele a Ana esta semana.")),
                new Foco("ESCRIBIR_A_LOS_ROJOS", 1), suSemana);
    }

    public static DatosDelMes mes() {
        return new DatosDelMes("2026-11", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), ZONA, new BigDecimal("72.4"), 1,
                List.of(new MentorDelRanking("Luisa Rojas", new BigDecimal("84.2"), 6, 2),
                        new MentorDelRanking("Marco Díaz", new BigDecimal("55"), 1, 0)),
                List.of("Nora Sin Muestra"));
    }

    private static List<LocalDate> dias() {
        return List.of(SABADO, SABADO.plusDays(1), SABADO.plusDays(2), SABADO.plusDays(3), SABADO.plusDays(4), SABADO.plusDays(5), VIERNES);
    }

    private static CeldaDelDia celda(int cumplidos, int programados) {
        return new CeldaDelDia(cumplidos, programados);
    }
}
