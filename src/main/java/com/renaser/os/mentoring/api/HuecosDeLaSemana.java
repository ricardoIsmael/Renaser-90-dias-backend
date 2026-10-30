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
 * <p><b>Corregido 2026-10-29</b> (el dueño pidió ver el tiempo libre «por día», como una agenda): cada día suma, de
 * forma aditiva, sus bloques ocupados con su nombre ({@code ocupados}), los hábitos y acciones que cumplió a tiempo,
 * tarde o no, y por qué cuenta entero cuando no tiene ventana; la semana suma {@code hoy}. Los campos de antes no
 * cambian. Un día que sale de la foto de una semana cerrada <b>no trae</b> los tres primeros (la foto guarda tramos de
 * tiempo, no nombres): quien los lee tiene que tolerar su ausencia.
 *
 * @param semana        el lunes de la semana
 * @param primeraSemana el lunes de la semana de su Día 1; {@code null} si todavía no eligió su Día 1
 * @param hoy           el día de hoy en la zona de la persona; con él la app sabe cuál es hoy y cuáles días vienen
 * @param cerrada       {@code true} solo si sale de la foto guardada al cerrar la semana (el horario que regía
 *                      entonces); {@code false} si se calculó al pedirla, con el horario de hoy
 */
public record HuecosDeLaSemana(LocalDate semana, LocalDate primeraSemana, LocalDate hoy, boolean cerrada,
                               List<Dia> dias, Resumen resumen) {

    public HuecosDeLaSemana {
        dias = List.copyOf(dias);
    }

    /**
     * @param ventana          {@code null} si ese día no hay Despertar y Dormir coherentes: el día cuenta de 00:00 a
     *                         24:00
     * @param sinVentanaPor    por qué no hay ventana; {@code null} si la hay y también en un día de una semana cerrada
     * @param huecos           solo los de 30 minutos o más, dentro de la ventana y en orden
     * @param ocupados         lo planificado de ese día, bloque por bloque y en orden de hora; los que se cruzan vienen
     *                         separados. {@code null} (no vacío) en un día de una semana cerrada: ahí no se sabe
     * @param cumplidosATiempo hábitos y acciones de ese día cumplidos hasta su hora límite; {@code null} en un día de
     *                         una semana cerrada. Cero en un día que todavía no llega
     */
    public record Dia(LocalDate fecha, Ventana ventana, SinVentanaPor sinVentanaPor, int minutosOcupados,
                      int minutosLibres, List<Hueco> huecos, List<Ocupado> ocupados, Integer cumplidosATiempo,
                      Integer cumplidosTarde, Integer noCumplidos) {

        public Dia {
            huecos = List.copyOf(huecos);
            ocupados = ocupados == null ? null : List.copyOf(ocupados);
        }
    }

    /** Por qué un día cuenta entero: la app dice cuál de las dos horas hay que pedirle a la persona. */
    public enum SinVentanaPor {
        SIN_DESPERTAR,
        SIN_DORMIR,
        SIN_DESPERTAR_NI_DORMIR,
        DORMIR_ANTES_DE_DESPERTAR
    }

    /**
     * Un bloque ocupado de la agenda de un día, dentro de su ventana. Las horas son {@code "HH:mm"}.
     *
     * @param titulo el nombre del hábito, de la acción o del evento; {@code null} en {@link Tipo#AGENDA}, que son las
     *               horas que la persona dijo estar ocupada y no llevan etiqueta
     */
    public record Ocupado(String desde, String hasta, Tipo tipo, String titulo) {
    }

    public enum Tipo {
        HABITO,
        ACCION,
        EVENTO,
        AGENDA
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
