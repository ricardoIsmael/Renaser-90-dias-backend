package com.renaser.os.habits.domain.model.registro;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Objects;
import java.util.TreeMap;

/**
 * La racha de UN habito (D-254): cuantos de sus dias programados seguidos cumplio la persona, hasta
 * hoy. Es el «🔥 N dias» de cada tarjeta de Training, que hasta ahora la app fijaba en 0.
 *
 * <h2>Las reglas, tal como las decidio el dueño (2026-10-05)</h2>
 * <ul>
 *   <li><b>1A</b> — «solo cuentan los dias en que al habito le toca (dias programados). Un dia en que
 *   no le toca no corta la racha.»</li>
 *   <li><b>2A</b> — «si el habito esta en pausa, la racha se congela: los dias en pausa no cortan ni
 *   suman, y sigue al reanudar.»</li>
 *   <li>Igual que la racha general ({@code points.Racha}): <b>hoy pendiente no corta</b> — la racha
 *   cuenta hasta el ultimo dia programado ya vencido o cumplido —; un dia programado vencido sin
 *   cumplir la corta.</li>
 * </ul>
 *
 * <p>1A y 2A salen solas de la entrada: un dia no programado y un dia en pausa son el MISMO dato, una
 * fila que no existe (ver {@link DiaProgramado}). Aca solo hay que no inventarlos.
 *
 * <h2>Supuestos de D-254 (casos que las reglas no resuelven; se eligio lo mas conservador)</h2>
 * <ul>
 *   <li><b>S-1, dias opcionales:</b> un dia opcional (ciclo de intoxicacion, habito opcional del
 *   catalogo) que no se cumple corta igual: tiene registro, asi que le tocaba. Pregunta abierta al
 *   dueño; si decide que congela, el cambio es una linea en {@link #veredictoDe}.</li>
 *   <li><b>S-2, el dia de hoy:</b> hoy solo corta un {@code FALLIDO} (Santuario roto: es un
 *   veredicto y no se puede completar). Un {@code PENDIENTE}, {@code EN_CURSO} o {@code EXPIRADO}
 *   de hoy todavia se puede completar ({@link EstadoRegistro#puedeCompletarse()}), asi que no corta.</li>
 *   <li><b>S-3, dias ya terminados:</b> todo dia anterior a hoy que no esta {@code COMPLETADO} corta,
 *   aunque el barrido nocturno todavia no lo haya pasado a {@code EXPIRADO}. Si despues se completa
 *   tarde, la racha se recompone sola: se deriva, no se guarda.</li>
 *   <li><b>S-4, antes del programa:</b> no cuenta ningun dia anterior a {@code inicioDelPrograma},
 *   igual que la racha general; un programa que todavia no empezo no tiene racha.</li>
 *   <li><b>S-5, dia sin registro por una falla:</b> si el barrido no genero la fila de un dia (backend
 *   caido), ese dia no corta: un dato ausente no es incumplimiento (mismo criterio que
 *   {@code ObligacionHabito}).</li>
 * </ul>
 *
 * <p>Se DERIVA, nunca se acumula (regla 02 §2): idempotente, y una noche sin servidor no deja secuela.
 *
 * @param dias       la racha, nunca negativa
 * @param definitiva {@code true} si se encontro el dia que la corta (o el programa no empezo): mirar
 *                   dias anteriores ya no la puede cambiar. {@code false} = se recorrio todo lo dado
 *                   sin corte, y dias mas viejos podrian alargarla — lo usa la lectura por paginas.
 */
public record RachaDelHabito(int dias, boolean definitiva) {

    public RachaDelHabito {
        if (dias < 0) {
            throw new IllegalArgumentException("Una racha no puede ser negativa: " + dias);
        }
    }

    /**
     * @param diasProgramados   los dias con registro de ESTE habito, en cualquier orden. Una fila por
     *                          fecha (UNIQUE participante+habito+fecha); si llegaran dos, gana la mejor.
     * @param hoy               la fecha de hoy en la zona del PARTICIPANTE, nunca la del servidor (E-91)
     * @param inicioDelPrograma {@code fecha_inicio} del programa, o {@code null} si no se conoce
     */
    public static RachaDelHabito derivar(Collection<DiaProgramado> diasProgramados, LocalDate hoy,
                                         LocalDate inicioDelPrograma) {
        Objects.requireNonNull(hoy, "hoy es obligatorio");
        if (inicioDelPrograma != null && inicioDelPrograma.isAfter(hoy)) {
            return new RachaDelHabito(0, true);
        }
        int seguidos = 0;
        for (Veredicto veredicto : veredictosDesdeHoyHaciaAtras(diasProgramados, hoy, inicioDelPrograma)) {
            if (veredicto == Veredicto.INCUMPLIDO) {
                return new RachaDelHabito(seguidos, true);
            }
            if (veredicto == Veredicto.CUMPLIDO) {
                seguidos++;
            }
        }
        return new RachaDelHabito(seguidos, false);
    }

    /** Un veredicto por fecha, de la mas nueva a la mas vieja, sin fechas futuras ni previas al inicio. */
    private static Collection<Veredicto> veredictosDesdeHoyHaciaAtras(Collection<DiaProgramado> dias, LocalDate hoy,
                                                                     LocalDate inicioDelPrograma) {
        TreeMap<LocalDate, Veredicto> porFecha = new TreeMap<>();
        if (dias == null) {
            return porFecha.values();
        }
        for (DiaProgramado dia : dias) {
            boolean dentro = !dia.fecha().isAfter(hoy)
                    && (inicioDelPrograma == null || !dia.fecha().isBefore(inicioDelPrograma));
            if (dentro) {
                porFecha.merge(dia.fecha(), veredictoDe(dia, hoy), Veredicto::elMejor);
            }
        }
        return porFecha.descendingMap().values();
    }

    /** La regla del dia. S-1: {@code opcional} no se mira a proposito. */
    private static Veredicto veredictoDe(DiaProgramado dia, LocalDate hoy) {
        if (dia.estado() == EstadoRegistro.COMPLETADO) {
            return Veredicto.CUMPLIDO;
        }
        if (dia.fecha().equals(hoy) && dia.estado().puedeCompletarse()) {
            return Veredicto.ABIERTO;
        }
        return Veredicto.INCUMPLIDO;
    }

    /** Lo que un dia programado le hace a la racha. El orden es de mejor a peor. */
    private enum Veredicto {
        /** Suma uno. */
        CUMPLIDO,
        /** Hoy, todavia se puede cumplir: ni suma ni corta. */
        ABIERTO,
        /** Corta la racha. */
        INCUMPLIDO;

        static Veredicto elMejor(Veredicto a, Veredicto b) {
            return a.ordinal() <= b.ordinal() ? a : b;
        }
    }
}
