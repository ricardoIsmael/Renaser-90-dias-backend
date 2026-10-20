package com.renaser.os.habits.application.ports.in.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.SemanaDelHabito;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Lectura de la configuracion de horarios del propio aprendiz. Contraparte de
 * {@link EditarPreferenciaHorarioUseCase}: sin esto el aprendiz solo podia ESCRIBIR su horario
 * (PATCH) sin poder leer lo que rige ni lo que ya dejo programado (E-55) — no podia planificar.
 */
public interface ConsultarPreferenciasHorarioUseCase {

    /** Autoservicio estricto: solo el propio participante, y solo si no esta suspendido. */
    ResumenPreferenciasHorario consultar(UserId actorId);

    ResumenPreferenciasHorario consultar(UserId actorId, LocalDate fecha);

    /**
     * {@code cuota}: la misma que devuelve el PATCH, con los mismos literales de {@code periodo}
     * ("FREE"/"WEEK", D-36) — el cliente no tiene que reconciliar dos formas del mismo dato.
     */
    record ResumenPreferenciasHorario(List<HorarioDeHabito> habitos, CuotaEdicion cuota) {
    }

    /**
     * {@code horaDisparo}/{@code horaLimite} son lo vigente en la fecha consultada (hoy por defecto) (preferencia propia si la hay, si
     * no el default del catalogo). {@code personalizado} distingue "elegi este horario" de "es el
     * que vino de fabrica". {@code cambioProgramado} es {@code null} salvo que haya un cambio
     * diferido esperando su fecha.
     */
    /**
     * `recordatorioActivo`/`minutosRecordatorio` se agregaron el 2026-09-07. El PATCH ya los
     * ESCRIBIA desde siempre y este GET no los devolvia, asi que no habia forma de leer de vuelta
     * si la persona tenia recordatorio: el movil los mandaba en `false`/`null` en cada guardado
     * porque no tenia nada mejor, y con eso apagaba el recordatorio cada vez que se tocaba la hora.
     */
    /**
     * {@code antelacionesRecordatorio} (D-217, 2026-09-28): todas las antelaciones elegidas, de la más
     * temprana a la más tardía; {@code null} si no se conocen (fila anterior a V81): la app lo toma como
     * «desconocido» y no pisa lo que el teléfono tiene.
     */
    /**
     * {@code diasDeLaSemana} (spec 008, D-282): los siete dias ya resueltos, con la hora y el aviso de cada
     * uno, para que la app programe una alarma por dia sin un pedido por habito. Vacio en las vistas que no
     * lo calculan. Importante: {@code recordatorioActivo}, {@code minutosRecordatorio} y
     * {@code antelacionesRecordatorio} son el aviso GENERAL del habito (con la excepcion de la fecha
     * consultada, si la hay), nunca el del dia de la semana: el APK anterior los reescribe como general y el
     * editor los necesita para saber que heredan los dias sin aviso propio.
     */
    record HorarioDeHabito(HabitoId habitoId, String titulo, LocalTime horaDisparo, LocalTime horaLimite,
                            boolean personalizado, boolean recordatorioActivo, Integer minutosRecordatorio,
                            CambioProgramado cambioProgramado, List<Integer> antelacionesRecordatorio,
                            List<SemanaDelHabito.Dia> diasDeLaSemana) {

        /** Sin los dias: la forma de antes de la spec 008. */
        public HorarioDeHabito(HabitoId habitoId, String titulo, LocalTime horaDisparo, LocalTime horaLimite,
                               boolean personalizado, boolean recordatorioActivo, Integer minutosRecordatorio,
                               CambioProgramado cambioProgramado, List<Integer> antelacionesRecordatorio) {
            this(habitoId, titulo, horaDisparo, horaLimite, personalizado, recordatorioActivo, minutosRecordatorio,
                    cambioProgramado, antelacionesRecordatorio, List.of());
        }

        /** Sin el conjunto: la forma de antes de D-217. */
        public HorarioDeHabito(HabitoId habitoId, String titulo, LocalTime horaDisparo, LocalTime horaLimite,
                               boolean personalizado, boolean recordatorioActivo, Integer minutosRecordatorio,
                               CambioProgramado cambioProgramado) {
            this(habitoId, titulo, horaDisparo, horaLimite, personalizado, recordatorioActivo, minutosRecordatorio,
                    cambioProgramado, null, List.of());
        }
    }

    record CambioProgramado(LocalTime horaDisparo, LocalTime horaLimite, LocalDate fechaEfectiva) {
    }

    record CuotaEdicion(int cambiosUsados, int cambiosRestantes, int cambiosLimite, String periodo) {
    }
}
