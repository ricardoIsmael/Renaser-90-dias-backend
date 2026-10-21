package com.renaser.os.habits.domain.model.preferencia;

import java.util.List;

/**
 * El aviso propio de UN dia de la semana de un habito (spec 008, D-282, 2026-10-20): «el sabado, 30
 * min antes». Pedido del dueno: «los recordatorios tambien deben de ser por dia, no en general».
 *
 * <p><b>Existe como objeto para no confundir dos cosas distintas.</b> «Este dia hereda el aviso general»
 * es la AUSENCIA de este objeto (un {@code null} en {@link HorarioSemanal#recordatorio()}); «este dia va
 * sin aviso» es {@link #sinAviso()}. Con un booleano y una lista suelta, las dos cosas se escribirian
 * igual ({@code false, vacio}) y apagar el aviso de un dia no se distinguiria de no haberlo tocado.
 *
 * <p>Las antelaciones son las del recordatorio general: minutos, de 0 («a la hora») al tope de
 * {@link AntelacionDelRecordatorio}, sin repetidos y de la mas temprana a la mas tardia
 * ({@link AntelacionesDelRecordatorio}). Un aviso activo tiene al menos una; uno apagado no tiene
 * ninguna, venga lo que venga en la lista.
 */
public record RecordatorioDelDia(boolean activo, List<Integer> antelaciones) {

    private static final RecordatorioDelDia SIN_AVISO = new RecordatorioDelDia(false, List.of());

    public RecordatorioDelDia {
        if (!activo) {
            antelaciones = List.of();
        } else {
            antelaciones = AntelacionesDelRecordatorio.normalizar(antelaciones == null ? List.of() : antelaciones);
            if (antelaciones.isEmpty()) {
                throw new IllegalArgumentException("Un aviso activo necesita al menos una antelacion");
            }
        }
    }

    /** Ese dia va sin aviso, aunque el general tenga uno. */
    public static RecordatorioDelDia sinAviso() {
        return SIN_AVISO;
    }

    /** Ese dia avisa con esas antelaciones, en minutos. */
    public static RecordatorioDelDia con(List<Integer> antelaciones) {
        return new RecordatorioDelDia(true, antelaciones);
    }

    /**
     * El aviso GENERAL de un habito, para compararlo con el de un dia y para heredarlo. Sin preferencia,
     * apagado, o encendido sin ninguna antelacion conocida (nunca eligio minutos), no hay aviso que
     * heredar: es {@link #sinAviso()}. Si el conjunto de antelaciones no se conoce (fila anterior a V81),
     * vale el numero suelto, que es su mas temprana.
     */
    public static RecordatorioDelDia delGeneral(PreferenciaHorario general) {
        if (general == null || !general.recordatorioActivo()) {
            return SIN_AVISO;
        }
        List<Integer> conjunto = general.antelacionesRecordatorio();
        if (conjunto != null && !conjunto.isEmpty()) {
            return con(conjunto);
        }
        return general.minutosRecordatorio() == null ? SIN_AVISO : con(List.of(general.minutosRecordatorio()));
    }

    /** La antelacion mas temprana (la mayor), que es la que ve el aviso de inicio del servidor; {@code null} sin aviso. */
    public Integer minutosMasTemprana() {
        return antelaciones.isEmpty() ? null : antelaciones.getFirst();
    }
}
