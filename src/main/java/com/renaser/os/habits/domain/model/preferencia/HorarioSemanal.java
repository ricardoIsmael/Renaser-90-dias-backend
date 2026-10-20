package com.renaser.os.habits.domain.model.preferencia;

import com.renaser.os.habits.domain.model.horario.VentanaDelDia;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

/**
 * La hora de un habito para UN dia de la semana, que se repite todas las semanas.
 *
 * <p>Hermana de {@link HorarioPorFecha} y distinta a proposito: aquella es una excepcion de una
 * FECHA y no se repite ("el lunes 8 a las 5"); esta es un patron ("los lunes a las 5"). Las dos
 * conviven y la de fecha exacta gana, porque es la mas especifica.
 *
 * <p><b>Ampliado 2026-09-40 (V40).</b> Tambien puede APAGAR el dia. La version anterior no lo
 * traia con el argumento de que apagar ya se podia por fecha (V38), y ese razonamiento confundia
 * dos preguntas distintas: "este martes no" no es "los martes nunca", y ninguna de las dos tablas
 * puede expresar la otra.
 *
 * <p>Con {@code activo = false} la hora deja de ser obligatoria: una fila que solo apaga el dia no
 * necesita hora, y exigirla congelaria el horario de ese dia si despues cambia el general.
 *
 * <p><b>Ampliado 2026-10-20 (V111, spec 008, D-282).</b> Tambien puede traer el AVISO propio del dia
 * ({@link RecordatorioDelDia}): «el sabado, 30 min antes». Es un campo aparte del horario y se resuelve
 * por campo, igual que la hora: un dia puede tener hora propia y heredar el aviso, heredar la hora y
 * tener aviso propio, o las dos cosas. {@code recordatorio = null} es «hereda el aviso general»; para
 * «sin aviso ese dia» se usa {@link RecordatorioDelDia#sinAviso()}. Un dia encendido necesita ahora
 * hora propia O aviso propio: sin ninguna de las dos no dice nada que lo general no diga ya.
 */
public record HorarioSemanal(DayOfWeek diaSemana, PreferenciaHorario preferencia, boolean activo,
                             RecordatorioDelDia recordatorio) {

    public HorarioSemanal {
        Objects.requireNonNull(diaSemana, "diaSemana es obligatorio");
        Objects.requireNonNull(preferencia, "preferencia es obligatoria");
        if (!dejaAlgoPropio(activo, preferencia.horaDisparo(), recordatorio)) {
            // Sin hora de disparo ni aviso propio una fila ENCENDIDA no dice nada que la preferencia
            // general no diga ya. Para volver al horario general se BORRA, no se vacia.
            throw new NullPointerException("horaDisparo o recordatorio propio son obligatorios con el dia encendido");
        }
        // D-122: la regla vive en VentanaDelDia, no copiada aca. Lo que se escribe ya viene
        // normalizado por PreferenciaHorario; esto solo cubre una fila rehidratada de la base.
        VentanaDelDia.requireVentanaNoVacia(preferencia.horaDisparo(), preferencia.horaLimite());
    }

    /** Firma historica (V40): sin aviso propio, el dia hereda el general. */
    public HorarioSemanal(DayOfWeek diaSemana, PreferenciaHorario preferencia, boolean activo) {
        this(diaSemana, preferencia, activo, null);
    }

    /** Firma historica (V39): una hora propia siempre dejaba el dia encendido. */
    public HorarioSemanal(DayOfWeek diaSemana, PreferenciaHorario preferencia) {
        this(diaSemana, preferencia, true, null);
    }

    /**
     * La regla de arriba, para quien tiene que decidir sin construir el agregado: el adaptador la usa para
     * saber si la fila que queda tras quitar el aviso de un dia todavia dice algo (si no, la borra, igual
     * que el CHECK de V111). Una fila APAGADA siempre dice algo: que ese dia no va.
     */
    public static boolean dejaAlgoPropio(boolean activo, LocalTime horaDisparo, RecordatorioDelDia recordatorio) {
        return !activo || horaDisparo != null || recordatorio != null;
    }

    /** {@code true} si el dia tiene hora propia; si no, usa la hora general. */
    public boolean tieneHoraPropia() {
        return preferencia.horaDisparo() != null;
    }

    /** El mismo dia con ese aviso propio; {@code null} lo devuelve a heredar el general. */
    public HorarioSemanal conRecordatorio(RecordatorioDelDia nuevo) {
        return new HorarioSemanal(diaSemana, preferencia, activo, nuevo);
    }
}
