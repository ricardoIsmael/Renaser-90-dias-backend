package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Los argumentos de {@code proponer_horario_por_dia_de_semana} ya leidos, y su forma canonica: la
 * que se guarda en la propuesta y la que se ejecuta al confirmar (fase 4, 2026-09-23). Lo leen
 * {@link PropuestaDeHorarioPorDiaDeSemana} y {@link HorarioPorDiaDeSemanaConfirmable}.
 *
 * @param horaInicio {@code null} salvo en {@link Accion#FIJAR}
 * @param horaLimite {@code null} = sin hora limite propia ese dia
 */
record HorarioSemanalPedido(UUID habitoId, DayOfWeek diaSemana, Accion accion, LocalTime horaInicio,
                            LocalTime horaLimite) {

    /** Las tres operaciones de {@code EditarHorarioSemanalUseCase}: fijar, apagar, quitar. */
    enum Accion {
        FIJAR, APAGAR, QUITAR
    }

    static HorarioSemanalPedido de(InvocacionHerramienta invocacion) {
        UUID habitoId = ArgumentosDeHorario.habitoId(invocacion.argumento(ArgumentosDeHorario.HABITO_ID));
        DayOfWeek dia = ArgumentosDeHorario.diaSemana(invocacion.argumento(ArgumentosDeHorario.DIA_SEMANA));
        Accion accion = accionDe(invocacion.argumento(ArgumentosDeHorario.ACCION));
        if (accion != Accion.FIJAR) {
            return new HorarioSemanalPedido(habitoId, dia, accion, null, null);
        }
        String inicio = invocacion.argumento(ArgumentosDeHorario.HORA_INICIO);
        if (!ArgumentosDeHorario.presente(inicio)) {
            throw new PropuestaImposibleException("Para 'fijar' hace falta hora_inicio (HH:mm).");
        }
        LocalTime horaInicio = ArgumentosDeHorario.hora(inicio, ArgumentosDeHorario.HORA_INICIO);
        String limite = invocacion.argumento(ArgumentosDeHorario.HORA_LIMITE);
        LocalTime horaLimite = ArgumentosDeHorario.presente(limite)
                ? ArgumentosDeHorario.hora(limite, ArgumentosDeHorario.HORA_LIMITE) : null;
        if (horaLimite != null && !horaLimite.isAfter(horaInicio)) {
            // habits no lo rechaza, lo acomoda a fin del dia (D-122): el boton mostraria otra hora.
            throw new PropuestaImposibleException("La hora_limite tiene que ser posterior a la hora_inicio. "
                    + "Preguntale a la persona hasta que hora quiere tener el habito.");
        }
        return new HorarioSemanalPedido(habitoId, dia, accion, horaInicio, horaLimite);
    }

    private static Accion accionDe(String texto) {
        try {
            return Accion.valueOf(String.valueOf(texto).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException accionDesconocida) {
            throw new PropuestaImposibleException("La accion tiene que ser 'fijar', 'apagar' o 'quitar'.");
        }
    }

    InvocacionHerramienta invocacion() {
        Map<String, String> argumentos = new HashMap<>();
        argumentos.put(ArgumentosDeHorario.HABITO_ID, habitoId.toString());
        argumentos.put(ArgumentosDeHorario.DIA_SEMANA, diaSemana.name());
        argumentos.put(ArgumentosDeHorario.ACCION, accion.name().toLowerCase(Locale.ROOT));
        if (horaInicio != null) {
            argumentos.put(ArgumentosDeHorario.HORA_INICIO, ArgumentosDeHorario.texto(horaInicio));
        }
        if (horaLimite != null) {
            argumentos.put(ArgumentosDeHorario.HORA_LIMITE, ArgumentosDeHorario.texto(horaLimite));
        }
        return new InvocacionHerramienta(PropuestaDeHorarioPorDiaDeSemana.NOMBRE, argumentos);
    }
}
