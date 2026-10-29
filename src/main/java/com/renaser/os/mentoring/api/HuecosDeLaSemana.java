package com.renaser.os.mentoring.api;

import java.time.LocalDate;
import java.util.List;

/**
 * Los huecos de horario de una persona en una semana de lunes a domingo (spec 015, primera versión): lo que su
 * acompañamiento lee en su ficha. Es también la respuesta JSON de las dos rutas
 * ({@code …/learners/{u}/huecos} del mentor y {@code …/people/{p}/huecos} del líder), con la forma de
 * {@code specs/015-huecos-de-horario/contracts/api.md}: las horas son texto {@code "HH:mm"} (un hueco puede terminar a
 * las {@code "24:00"}, que {@code LocalTime} no representa).
 *
 * <p><b>Vive en {@code api}</b> para que {@code leadership} la lea por {@link HuecosDeHorarioFinder} sin armar una
 * segunda semana: que el cálculo sea UNO solo es el requisito (FR-025).
 *
 * @param semana        el lunes de la semana
 * @param primeraSemana el lunes de la semana de su Día 1; {@code null} si todavía no eligió su Día 1
 * @param cerrada       {@code true} solo si sale de la foto guardada al cerrar la semana (el horario que regía
 *                      entonces); {@code false} si se calculó al pedirla, con el horario de hoy
 */
public record HuecosDeLaSemana(LocalDate semana, LocalDate primeraSemana, boolean cerrada, List<Dia> dias,
                               Resumen resumen) {

    public HuecosDeLaSemana {
        dias = List.copyOf(dias);
    }

    /**
     * @param ventana {@code null} si ese día no hay Despertar y Dormir coherentes: el día cuenta de 00:00 a 24:00
     * @param huecos  solo los de 30 minutos o más, dentro de la ventana y en orden
     */
    public record Dia(LocalDate fecha, Ventana ventana, int minutosOcupados, int minutosLibres, List<Hueco> huecos) {

        public Dia {
            huecos = List.copyOf(huecos);
        }
    }

    public record Ventana(String desde, String hasta) {
    }

    public record Hueco(String desde, String hasta, int minutos) {
    }

    /**
     * @param dias los días de la semana en que el tramo está libre, ISO-8601 (1 = lunes … 7 = domingo)
     */
    public record HuecoRepetido(String desde, String hasta, List<Integer> dias) {

        public HuecoRepetido {
            dias = List.copyOf(dias);
        }
    }

    /**
     * @param cumplidosATiempo hábitos y acciones cumplidos hasta su hora límite
     * @param cumplidosTarde   cumplidos después de ella
     * @param noCumplidos      de los días ya terminados, los que no se cumplieron; un día sin registros y lo opcional
     *                         no cuentan
     */
    public record Resumen(int minutosOcupados, int minutosLibres, List<HuecoRepetido> huecosQueSeRepiten,
                          int cumplidosATiempo, int cumplidosTarde, int noCumplidos) {

        public Resumen {
            huecosQueSeRepiten = List.copyOf(huecosQueSeRepiten);
        }
    }
}
