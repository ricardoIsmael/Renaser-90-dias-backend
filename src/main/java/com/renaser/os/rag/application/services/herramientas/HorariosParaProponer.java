package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort.CuotaCambios;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort.HorarioDeHabito;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort.HorariosDelDia;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * Lo que las herramientas de horario leen ANTES de proponer (fase 4, 2026-09-23), con el mismo
 * puerto que {@code consultar_horarios}: horario resuelto, marcas (apagado, obligatorio) y cupo.
 *
 * <p><b>No decide ninguna regla.</b> "Hoy" lo resuelve {@code habits} en la zona del participante
 * (se pide con {@code null}), y el cupo que se mira es el que {@code habits} calcula para la semana
 * de programa de la fecha consultada. Esto solo evita ofrecer un boton que ya se sabe que va a
 * fallar; al confirmar, el caso de uso de {@code habits} vuelve a correr todas sus guardas.
 */
final class HorariosParaProponer {

    /** Regla 02: {@code dia_programa} se acota a [0, 90], igual que en {@code consultar_horarios}. */
    private static final int PRIMER_DIA_DEL_PROGRAMA = 0;
    private static final int ULTIMO_DIA_DEL_PROGRAMA = 90;

    private static final Logger log = LoggerFactory.getLogger(HorariosParaProponer.class);

    private HorariosParaProponer() {
    }

    /**
     * La fecha que pidio el modelo, con el año corregido si hace falta (E-276). Aun con la fecha de hoy
     * en el prompt, el modelo a veces arma "el 5 de noviembre" con el año de su entrenamiento, y la
     * fecha cae fuera del programa. Si con el año de hoy (o el siguiente, para un programa que cruza
     * el año nuevo) cae dentro, se usa esa. Si no, queda la pedida y se rechaza como antes.
     *
     * <p>No escribe nada por si sola: la tarjeta muestra la fecha ya corregida, con su dia de la
     * semana, y la persona la confirma o la cancela.
     */
    static LocalDate dentroDelPrograma(LocalDate pedida, HorariosDelDia hoy) {
        if (pedida == null || enElPrograma(pedida, hoy)) {
            return pedida;
        }
        for (int anio = hoy.fecha().getYear(); anio <= hoy.fecha().getYear() + 1; anio++) {
            LocalDate candidata = pedida.withYear(anio);
            if (enElPrograma(candidata, hoy)) {
                return candidata;
            }
        }
        return pedida;
    }

    /** La misma cuenta que usa {@code habits} para el dia de una fecha: el de hoy mas los dias que faltan. */
    private static boolean enElPrograma(LocalDate fecha, HorariosDelDia hoy) {
        long dia = hoy.diaPrograma() + ChronoUnit.DAYS.between(hoy.fecha(), fecha);
        return dia >= PRIMER_DIA_DEL_PROGRAMA && dia <= ULTIMO_DIA_DEL_PROGRAMA;
    }

    /** @param fecha {@code null} = hoy en la zona del participante */
    static HorariosDelDia de(ConsultarHorariosPort puerto, UserId actorId, LocalDate fecha) {
        HorariosDelDia dia = leer(puerto, actorId, fecha);
        if (dia.diaPrograma() < PRIMER_DIA_DEL_PROGRAMA || dia.diaPrograma() > ULTIMO_DIA_DEL_PROGRAMA) {
            throw new PropuestaImposibleException("El " + ArgumentosDeHorario.texto(dia.fecha())
                    + " queda fuera de sus 90 dias de programa: no hay horario que cambiar ese dia."
                    + conLaFechaDeHoy(puerto, actorId, fecha));
        }
        return dia;
    }

    /**
     * Bateria del 2026-09-25 (ronda 2): a "saltate la clase diaria este sabado" el acompanante
     * contesto "el programa no llega hasta ese sabado", en el dia 18 de 90. Una fecha mal armada (el
     * año, casi siempre) caia aca, y el modelo repetia el motivo sin darse cuenta. Con la fecha de
     * hoy al lado puede corregirla y volver a intentar.
     */
    private static String conLaFechaDeHoy(ConsultarHorariosPort puerto, UserId actorId, LocalDate fecha) {
        if (fecha == null) {
            return "";
        }
        try {
            HorariosDelDia hoy = leer(puerto, actorId, null);
            return " Hoy es " + ArgumentosDeHorario.texto(hoy.fecha()) + ", su dia " + hoy.diaPrograma()
                    + " de 90: revisa el año y la fecha que pediste y vuelve a intentar.";
        } catch (RuntimeException sinHoy) {
            return "";
        }
    }

    /**
     * Si el habito esta pausado, la tarjeta lo dice: el horario nuevo se guarda igual, pero no se va
     * a ver hasta que lo reactive (bateria del 2026-09-25, ronda 2: propuso "ducha fria a las 20:00"
     * sin decir que estaba pausada). Va en el resumen y no en el prompt: la persona lo lee antes de
     * confirmar, lo diga el modelo o no.
     */
    static String siEstaPausado(HorarioDeHabito habito) {
        return habito.pausado() ? " Esta pausado: el horario nuevo se vera cuando lo reactive." : "";
    }

    static HorarioDeHabito habito(HorariosDelDia dia, UUID habitoId) {
        return dia.habitos().stream()
                .filter(habito -> habitoId.equals(habito.habitoId()))
                .findFirst()
                .orElseThrow(() -> new PropuestaImposibleException("Ese habito no esta entre sus habitos activos del "
                        + ArgumentosDeHorario.texto(dia.fecha()) + ". Consulta consultar_horarios y usa el "
                        + "habito_id que devuelve."));
    }

    /** La cuota de {@code CuotaEdicionHorario}, tal como la informa {@code habits}: nunca se la saltea. */
    static void requireCupo(CuotaCambios cuota) {
        if (!cuota.semanaDeAcomodoLibre() && cuota.restantes() <= 0) {
            throw new PropuestaImposibleException("No se puede proponer: ya uso sus " + cuota.limite()
                    + " cambios de horario de esa semana del programa. No propongas el cambio; explicale que los "
                    + "habitos que ya reacomodo esa semana los puede seguir ajustando desde la app, y el resto la "
                    + "semana siguiente.");
        }
    }

    /**
     * Un cambio a la misma franja no cambia nada y gastaria un cupo: en la bateria del 2026-09-25 se
     * propuso "de 06:00 a 06:00". Se dice que ya esta asi y no se propone.
     */
    static void requireQueCambie(HorarioDeHabito actual, LocalTime inicio, LocalTime limite) {
        if (inicio.equals(actual.horaDisparo()) && Objects.equals(limite, actual.horaLimite())) {
            throw new PropuestaImposibleException("'" + actual.titulo() + "' ya esta a las " + franja(inicio, limite)
                    + " ese dia: no hay nada que cambiar y no se gasta un cambio de horario.");
        }
    }

    /** Lo que dice {@code consultar_horarios} del cupo, para que ninguna herramienta lo cuente distinto. */
    static String lineaDeCupo(CuotaCambios cuota) {
        if (cuota.semanaDeAcomodoLibre()) {
            return "Cambios de horario: sin tope, se puede cambiar cuantas veces quiera.";
        }
        return "Cambios de horario esta semana: " + cuota.usados() + " usados, " + cuota.restantes()
                + " restantes de " + cuota.limite() + ".";
    }

    /** La parte del resumen que dice cuanto cupo gasta, con los numeros que dio {@code habits}. */
    static String gastoDeCupo(CuotaCambios cuota) {
        if (cuota.semanaDeAcomodoLibre()) {
            return "No tiene tope de cambios.";
        }
        return "Usa 1 de sus " + cuota.limite() + " cambios de esa semana: le quedarian " + (cuota.restantes() - 1)
                + ".";
    }

    /** "06:00-07:00", "06:00" (sin hora limite) o "sin hora fija". */
    static String franja(LocalTime inicio, LocalTime limite) {
        if (inicio == null) {
            return "sin hora fija";
        }
        return ArgumentosDeHorario.texto(inicio) + (limite == null ? "" : "-" + ArgumentosDeHorario.texto(limite));
    }

    /** Lo pedido: sin hora limite no es "la de siempre", es "ninguna propia" (rige la del catalogo, si tiene). */
    static String franjaPedida(LocalTime inicio, LocalTime limite) {
        return franja(inicio, limite) + (limite == null ? " (sin hora limite propia)" : "");
    }

    private static HorariosDelDia leer(ConsultarHorariosPort puerto, UserId actorId, LocalDate fecha) {
        try {
            return puerto.deFecha(actorId, fecha);
        } catch (NoSuchElementException sinPrograma) {
            throw new PropuestaImposibleException("No encontre un programa activo para esta cuenta.");
        } catch (NotAuthorizedException suspendida) {
            throw new PropuestaImposibleException("La cuenta esta suspendida: no puedo cambiar sus horarios.");
        } catch (RuntimeException falla) {
            log.warn("[rag] no se pudieron leer los horarios para proponer un cambio", falla);
            throw new PropuestaImposibleException("No pude consultar sus horarios en este momento.");
        }
    }
}
