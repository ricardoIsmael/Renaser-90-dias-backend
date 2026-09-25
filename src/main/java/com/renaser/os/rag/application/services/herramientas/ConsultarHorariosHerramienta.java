package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort.HorarioDeHabito;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort.HorariosDelDia;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ParametroHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.rag.domain.model.herramienta.TipoParametroHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * {@code consultar_horarios} (R0, solo lectura, 2026-09-23): el horario ya resuelto de un dia, por
 * habito, y el cupo semanal de cambios de horario. Es lo que el acompanante mira ANTES de proponer
 * un cambio ("te quedan 2 cambios esta semana", "ese no se puede apagar, es obligatorio").
 *
 * <p>No decide nada: la precedencia de horarios, el dia "hoy" en la zona del participante y la
 * cuota los resuelve {@code habits} ({@code HorarioDelDiaFinder}). Aca solo se valida el argumento
 * y se arma el texto que el modelo parafrasea.
 */
@Component
public class ConsultarHorariosHerramienta implements HerramientaAgente {

    public static final String NOMBRE = "consultar_horarios";
    public static final String ARGUMENTO_FECHA = "fecha";

    /** Regla 02: {@code dia_programa} se acota a [0, 90]. Fuera de eso no hay programa que mostrar. */
    private static final int PRIMER_DIA_DEL_PROGRAMA = 0;
    private static final int ULTIMO_DIA_DEL_PROGRAMA = 90;

    private static final Logger log = LoggerFactory.getLogger(ConsultarHorariosHerramienta.class);

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "Devuelve el horario de cada habito del aprendiz para un dia (hora de inicio y hora limite), si ese "
                    + "dia esta apagado o pausado, si el habito es obligatorio (no se puede pausar ni apagar) y "
                    + "cuantos cambios de horario le quedan esta semana. Usala antes de responder sobre a que hora "
                    + "le toca algo y SIEMPRE antes de proponer un cambio de horario.",
            List.of(new ParametroHerramienta(ARGUMENTO_FECHA, TipoParametroHerramienta.TEXTO,
                    "Dia a consultar en formato yyyy-MM-dd. Omitelo para hoy; no calcules tu la fecha de hoy.",
                    false)));

    private final ConsultarHorariosPort horariosPort;

    public ConsultarHorariosHerramienta(ConsultarHorariosPort horariosPort) {
        this.horariosPort = horariosPort;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        String argumento = invocacion.argumento(ARGUMENTO_FECHA);
        LocalDate fecha;
        try {
            fecha = argumento == null || argumento.isBlank() ? null : LocalDate.parse(argumento.trim());
        } catch (DateTimeParseException formatoInvalido) {
            return ResultadoHerramienta.fallo("La fecha tiene que venir como yyyy-MM-dd (por ejemplo 2026-09-24).");
        }
        return consultar(actorId, fecha);
    }

    private ResultadoHerramienta consultar(UserId actorId, LocalDate fecha) {
        HorariosDelDia dia;
        try {
            dia = horariosPort.deFecha(actorId, fecha);
            if (fecha != null && fueraDelPrograma(dia)) {
                dia = conElAnioCorregido(actorId, fecha).orElse(dia);
            }
        } catch (NoSuchElementException sinPrograma) {
            return ResultadoHerramienta.fallo("No encontre un programa activo para esta cuenta.");
        } catch (NotAuthorizedException suspendida) {
            return ResultadoHerramienta.fallo("La cuenta esta suspendida: no puedo consultar sus horarios.");
        } catch (RuntimeException falla) {
            log.warn("[rag] la herramienta {} no pudo leer los horarios", NOMBRE, falla);
            return ResultadoHerramienta.fallo("No pude consultar sus horarios en este momento.");
        }
        if (fueraDelPrograma(dia)) {
            return ResultadoHerramienta.fallo("El " + dia.fecha() + " queda fuera de sus 90 dias de programa: "
                    + "no hay horario que consultar para esa fecha.");
        }
        return ResultadoHerramienta.exito(textoDe(dia));
    }

    private static boolean fueraDelPrograma(HorariosDelDia dia) {
        return dia.diaPrograma() < PRIMER_DIA_DEL_PROGRAMA || dia.diaPrograma() > ULTIMO_DIA_DEL_PROGRAMA;
    }

    /**
     * E-276: el modelo a veces arma la fecha con el año de su entrenamiento ("el 5 de noviembre" en
     * 2025). Solo si la pedida cae fuera del programa se mira el dia de hoy y se prueba con el año
     * corregido; el caso comun no paga una consulta de mas. Si no se puede, queda la pedida.
     */
    private java.util.Optional<HorariosDelDia> conElAnioCorregido(UserId actorId, LocalDate fecha) {
        HorariosDelDia hoy = horariosPort.deFecha(actorId, null);
        LocalDate corregida = hoy == null ? fecha : HorariosParaProponer.dentroDelPrograma(fecha, hoy);
        return corregida.equals(fecha) ? java.util.Optional.empty()
                : java.util.Optional.of(horariosPort.deFecha(actorId, corregida));
    }

    private static String textoDe(HorariosDelDia dia) {
        StringBuilder texto = new StringBuilder("Horarios del ").append(dia.fecha())
                .append(" (dia ").append(dia.diaPrograma()).append(" del programa):\n");
        if (dia.habitos().isEmpty()) {
            texto.append("No tiene habitos activos.\n");
        }
        dia.habitos().forEach(habito -> texto.append(lineaDe(habito)).append('\n'));
        return texto.append(HorariosParaProponer.lineaDeCupo(dia.cuota())).toString();
    }

    /** Solo se nombran las marcas que SON ciertas: el modelo parafrasea lo que ve, no lo que falta. */
    private static String lineaDe(HorarioDeHabito habito) {
        StringBuilder linea = new StringBuilder("habito_id=").append(habito.habitoId())
                .append(" | ").append(habito.titulo())
                .append(" | ").append(franjaDe(habito));
        if (habito.personalizado()) {
            linea.append(" | horario_propio=si");
        }
        if (habito.apagado()) {
            linea.append(" | apagado_ese_dia=si");
        }
        if (habito.pausado()) {
            linea.append(" | pausado=si");
        }
        if (habito.obligatorio()) {
            linea.append(" | obligatorio=si (no se puede pausar ni apagar)");
        }
        if (habito.cambioProgramado() != null) {
            linea.append(" | cambio_programado=").append(habito.cambioProgramado().horaDisparo())
                    .append(" desde ").append(habito.cambioProgramado().desde());
        }
        return linea.toString();
    }

    private static String franjaDe(HorarioDeHabito habito) {
        if (habito.horaDisparo() == null) {
            return "sin hora fija ese dia";
        }
        return "inicio=" + habito.horaDisparo() + (habito.horaLimite() == null ? "" : " limite=" + habito.horaLimite());
    }
}
