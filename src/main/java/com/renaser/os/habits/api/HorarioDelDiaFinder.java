package com.renaser.os.habits.api;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * El horario YA RESUELTO de un dia del aprendiz, mas su cupo semanal de cambios de horario.
 *
 * <p>Primer consumidor: la herramienta {@code consultar_horarios} del acompanante de {@code rag}
 * (2026-09-23), que lo necesita antes de PROPONER un cambio ("te quedan 2 cambios esta semana").
 * Se expone aca y no se deja que {@code rag} lea {@code preferencias_horario} y compania por su
 * cuenta por la regla de siempre (D-41, regla 01): la precedencia fecha &gt; dia de semana &gt;
 * cambio general &gt; preferencia &gt; catalogo y la cuota de {@code CuotaEdicionHorario} viven en
 * este modulo y en ningun otro.
 *
 * <p><b>A diferencia de {@link AgendaDelDiaFinder}, si recibe fecha.</b> Aquel no la pide para que
 * nadie le pase la del servidor (E-91/E-105). Aca la fecha es la que el propio aprendiz pregunta
 * ("mi horario del jueves"), y el {@code null} —el caso "hoy"— se resuelve puertas adentro, en la
 * zona del participante. El llamador nunca calcula "hoy".
 *
 * <p>Ningun tipo de {@code habits.domain} cruza esta frontera: todo se traduce a los records de
 * abajo.
 */
public interface HorarioDelDiaFinder {

    /**
     * Llamada de modulo a modulo ya autorizada por quien invoca (mismo criterio que
     * {@link AgendaDelDiaFinder#deHoyDe}); el caso de uso de adentro igual rechaza a un suspendido.
     *
     * @param fecha el dia a consultar, en el calendario del participante; {@code null} = hoy en su
     *              zona
     * @throws java.util.NoSuchElementException si no tiene participacion en el programa
     * @throws com.renaser.os.shared.domain.NotAuthorizedException si la cuenta esta suspendida
     */
    HorariosDelDia deFecha(UserId participanteId, LocalDate fecha);

    /**
     * @param fecha       el dia efectivamente consultado (ya resuelto si se pidio "hoy")
     * @param diaPrograma el dia del programa que le corresponde a {@code fecha}: el de hoy mas la
     *                    distancia en dias. Puede caer fuera de [0, 90] si la fecha esta fuera del
     *                    programa — decidir que hacer con eso es del llamador
     */
    record HorariosDelDia(LocalDate fecha, int diaPrograma, List<HorarioResuelto> habitos,
                          CuotaCambiosHorario cuota) {
    }

    /**
     * @param horaDisparo       {@code null} si ni el aprendiz ni el catalogo le fijan hora ese dia
     * @param personalizado     el horario lo eligio el aprendiz, no vino del catalogo
     * @param apagado           el aprendiz apago el habito para esa fecha o para ese dia de la
     *                          semana (V38/V40)
     * @param pausado           la pausa del plan ({@code habit-unlocks}) cubre esa fecha
     * @param obligatorio       {@code habitos.desactivable = false} (V18): no se puede pausar ni
     *                          apagar un dia
     * @param cambioProgramado  {@code null} salvo que haya un cambio general diferido esperando su
     *                          fecha
     */
    record HorarioResuelto(UUID habitoId, String titulo, LocalTime horaDisparo, LocalTime horaLimite,
                           boolean personalizado, boolean apagado, boolean pausado, boolean obligatorio,
                           CambioHorarioProgramado cambioProgramado) {
    }

    record CambioHorarioProgramado(LocalTime horaDisparo, LocalTime horaLimite, LocalDate desde) {
    }

    /**
     * La misma cuota que cobra el PATCH de horario, de la semana de programa de la fecha consultada.
     *
     * @param semanaDeAcomodoLibre la primera semana del programa los cambios inmediatos no cuestan
     *                             cupo ({@code CuotaEdicionHorario.DIAS_DE_ACOMODO_LIBRE})
     */
    record CuotaCambiosHorario(int usados, int restantes, int limite, boolean semanaDeAcomodoLibre) {
    }
}
