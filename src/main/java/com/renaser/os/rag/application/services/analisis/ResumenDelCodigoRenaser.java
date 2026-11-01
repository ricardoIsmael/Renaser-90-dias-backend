package com.renaser.os.rag.application.services.analisis;

import com.renaser.os.habits.api.RespuestasDelRadarFinder.RespuestaDelRadar;
import com.renaser.os.rag.api.Analisis8020Finder.EnergiaDeSemana;
import com.renaser.os.rag.api.Analisis8020Finder.EnergiaPorMomento;
import com.renaser.os.rag.api.Analisis8020Finder.ResumenDelRadar;
import com.renaser.os.rocks.api.PeriodosDelProgramaFinder.PeriodoDelPrograma;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.Predicate;

/**
 * Cuenta lo que una persona respondió en el Código Renaser en un periodo (spec 031, 031-3 A): cuántas veces, la energía de 1 a 10 y cómo
 * cambia en el día. Pura, sin base de datos ni IA. Solo cuentas: lo escrito va aparte, recortado, en {@link MuestraDelRadar}.
 */
final class ResumenDelCodigoRenaser {

    private static final int HORA_DE_LA_TARDE = 12;
    private static final int HORA_DE_LA_NOCHE = 18;
    private static final int MAXIMO_DE_MUESTRAS = 18;
    private static final int LARGO_DE_LA_MUESTRA = 240;

    private ResumenDelCodigoRenaser() {
    }

    /** Lo escrito en una respuesta, recortado: el texto que la IA lee para ver patrones (nunca se cita). */
    record MuestraDelRadar(String momento, int energia, String queHago, String quePienso, String queSiento, String queEvito) {
    }

    /**
     * @param semanas las semanas del mes, para la energía semana a semana; vacía en un análisis semanal
     * @return {@code null} si hubo menos respuestas que {@code minimo}: sin suficiente, no se lee el Código Renaser
     */
    static ResumenDelRadar resumir(List<RespuestaDelRadar> respuestas, ZoneId zona, int minimo, List<PeriodoDelPrograma> semanas) {
        if (respuestas.size() < minimo) {
            return null;
        }
        List<EnergiaDeSemana> porSemana = semanas.stream().map(semana -> deLaSemana(respuestas, zona, semana)).toList();
        return new ResumenDelRadar(respuestas.size(), (int) respuestas.stream().filter(RespuestaDelRadar::dictado).count(),
                promedio(respuestas, r -> true), respuestas.stream().mapToInt(RespuestaDelRadar::nivelEnergia).min().orElse(0),
                respuestas.stream().mapToInt(RespuestaDelRadar::nivelEnergia).max().orElse(0),
                new EnergiaPorMomento(promedio(respuestas, r -> hora(r, zona) < HORA_DE_LA_TARDE),
                        promedio(respuestas, r -> hora(r, zona) >= HORA_DE_LA_TARDE && hora(r, zona) < HORA_DE_LA_NOCHE),
                        promedio(respuestas, r -> hora(r, zona) >= HORA_DE_LA_NOCHE)),
                porSemana);
    }

    /** Hasta dieciocho respuestas repartidas parejo en el periodo, con cada texto recortado. */
    static List<MuestraDelRadar> muestras(List<RespuestaDelRadar> respuestas, ZoneId zona) {
        List<MuestraDelRadar> muestras = new ArrayList<>();
        int total = respuestas.size();
        int cuantas = Math.min(total, MAXIMO_DE_MUESTRAS);
        for (int i = 0; i < cuantas; i++) {
            RespuestaDelRadar respuesta = respuestas.get(cuantas == 1 ? 0 : (int) Math.round((double) i * (total - 1) / (cuantas - 1)));
            muestras.add(new MuestraDelRadar(momento(hora(respuesta, zona)), respuesta.nivelEnergia(), recortar(respuesta.queHago()),
                    recortar(respuesta.quePienso()), recortar(respuesta.queSiento()), recortar(respuesta.queEvito())));
        }
        return muestras;
    }

    private static EnergiaDeSemana deLaSemana(List<RespuestaDelRadar> respuestas, ZoneId zona, PeriodoDelPrograma semana) {
        Predicate<RespuestaDelRadar> enLaSemana = r -> {
            var fecha = r.creadoEn().atZone(zona).toLocalDate();
            return !fecha.isBefore(semana.inicio()) && !fecha.isAfter(semana.fin());
        };
        return new EnergiaDeSemana(semana.numero(), (int) respuestas.stream().filter(enLaSemana).count(), promedio(respuestas, enLaSemana));
    }

    /** Promedio con un decimal; {@code null} si no hay respuestas. */
    private static Double promedio(List<RespuestaDelRadar> respuestas, Predicate<RespuestaDelRadar> filtro) {
        OptionalDouble promedio = respuestas.stream().filter(filtro).mapToInt(RespuestaDelRadar::nivelEnergia).average();
        return promedio.isPresent() ? Math.round(promedio.getAsDouble() * 10.0) / 10.0 : null;
    }

    private static int hora(RespuestaDelRadar respuesta, ZoneId zona) {
        return respuesta.creadoEn().atZone(zona).getHour();
    }

    private static String momento(int hora) {
        return hora < HORA_DE_LA_TARDE ? "mañana" : hora < HORA_DE_LA_NOCHE ? "tarde" : "noche";
    }

    /** Recorta sin partir un emoji por la mitad. */
    static String recortar(String texto) {
        if (texto == null) {
            return "";
        }
        String limpio = texto.strip();
        if (limpio.codePointCount(0, limpio.length()) <= LARGO_DE_LA_MUESTRA) {
            return limpio;
        }
        return limpio.substring(0, limpio.offsetByCodePoints(0, LARGO_DE_LA_MUESTRA));
    }
}
