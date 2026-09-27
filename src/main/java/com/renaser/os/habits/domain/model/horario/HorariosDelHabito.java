package com.renaser.os.habits.domain.model.horario;

import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;

/**
 * Los horarios de UN habito y la pregunta que se hacen todos sus lectores: con que dia del programa
 * se resuelve ese habito (que horario rige, que guia, que contenido).
 *
 * <p>La regla de siempre: rige el horario cuyo rango {@code [dia_inicio, dia_fin]} contiene el dia
 * y cuyo tipo de dia coincide ({@link HorarioHabito#aplicaEnDia}).
 *
 * <p><b>D-200 (decision del dueño 2026-09-27): al retroceder el dia, lo que ya corrio sigue
 * activo.</b> {@code dia_inicio} es un dia ABSOLUTO del programa: un habito PERSONAL creado el dia
 * 30 nace con {@code dia_inicio = 30} ({@code MisHabitosService.crear}), y varios del catalogo
 * arrancan tarde (Pastilla Renacer el 8, Audioterapia el 11, los de domingo el 35). Si un admin
 * retrocedia a la persona por debajo de ese dia, el habito dejaba de generarse y "Mis habitos" le
 * ponia candado aunque ya lo venia haciendo. Es el mismo caso que D-196 resolvio para
 * {@code desbloqueos_habito}, con el mismo criterio: "ya corrio" se DERIVA del snapshot
 * {@code registros_habito.dia_programa} (algun registro del habito generado en un dia &gt;= al
 * {@code dia_inicio}), no se guarda ni se reescribe nada. Lo que nunca corrio sigue esperando su
 * dia.
 *
 * <p>Un habito que ya corrio y quedo por debajo del inicio de su horario se resuelve COMO EN EL
 * PRIMER DIA de ese horario ({@link #diaEfectivo}): rige ese horario, con su hora, su guia y su
 * contenido. El registro guarda igual el dia real, que es historia; solo cambia con que dia se lo
 * lee.
 *
 * <p><b>D-216 (2026-09-27, TZ-15 del e2e): un habito PERSONAL corre desde que se crea.</b> Su
 * {@code dia_inicio} no lo elige el programa: es el dia en que la persona estaba cuando lo creo. Asi
 * que crearlo ya prueba que llego a ese dia con el habito andando, aunque todavia no tenga ningun
 * registro (los del dia ya estaban generados cuando lo creo, y el primero sale al dia siguiente).
 * Antes, si el mismo dia la retrocedian, el habito que acababa de crear quedaba con candado
 * ("FALTAN 5 DIAS") y sin generarse hasta volver a ese dia. Para el catalogo no cambia nada: su
 * {@code dia_inicio} es del programa y el "ya corrio" sigue saliendo de los registros.
 */
public final class HorariosDelHabito {

    /** Antes del Dia 1 el programa no arranco: no hay retroceso que valga ni habito que haya corrido. */
    private static final int PRIMER_DIA_DEL_PROGRAMA = 1;

    /** Sin nada que pruebe a que dia llego la persona con el habito: el caso del catalogo. */
    private static final int NINGUN_DIA = 0;

    private final List<HorarioHabito> horarios;

    /**
     * El dia del programa al que la persona seguro llego con el habito ya existente, sin leer
     * registros: el primer dia de un habito PERSONAL, porque lo creo estando en ese dia (D-216).
     * {@link #NINGUN_DIA} en el catalogo.
     */
    private final int diaAlcanzadoAlCrearlo;

    private HorariosDelHabito(List<HorarioHabito> horarios, int diaAlcanzadoAlCrearlo) {
        this.horarios = List.copyOf(horarios);
        this.diaAlcanzadoAlCrearlo = diaAlcanzadoAlCrearlo;
    }

    /**
     * Los horarios de un habito del catalogo; la lista puede venir vacia (habito sin horario). Sirve
     * tambien, sea de quien sea el habito, para leer un registro YA GENERADO
     * ({@link #diaEfectivoDeUnRegistro}), que no depende de si ya corrio.
     */
    public static HorariosDelHabito de(List<HorarioHabito> horarios) {
        return new HorariosDelHabito(horarios, NINGUN_DIA);
    }

    /**
     * Los horarios de ESE habito: si es PERSONAL, su primer dia cuenta como alcanzado (D-216). Desde
     * afuera se entra por {@link #porHabito}, que es lo que usan los que deciden si el habito corre
     * hoy: la generacion, el candado de "Mis habitos" y el horario del dia.
     */
    static HorariosDelHabito de(Habito habito, List<HorarioHabito> horarios) {
        if (habito.esDeSistema()) {
            return de(horarios);
        }
        int primerDia = horarios.stream().mapToInt(HorarioHabito::diaInicio).min().orElse(NINGUN_DIA);
        return new HorariosDelHabito(horarios, primerDia);
    }

    /**
     * Los horarios de varios habitos, uno por habito y con {@link #de(Habito, List)}. Cada habito
     * tiene su entrada, aunque no tenga horarios.
     *
     * @param horarios los de todos esos habitos juntos (una sola consulta de lote, V-5)
     */
    public static Map<HabitoId, HorariosDelHabito> porHabito(List<Habito> habitos, List<HorarioHabito> horarios) {
        Map<HabitoId, List<HorarioHabito>> agrupados = horarios.stream()
                .collect(Collectors.groupingBy(HorarioHabito::habitoId));
        Map<HabitoId, HorariosDelHabito> porHabito = new HashMap<>();
        for (Habito habito : habitos) {
            porHabito.put(habito.id(), de(habito, agrupados.getOrDefault(habito.id(), List.of())));
        }
        return porHabito;
    }

    /**
     * El dia con el que se resuelve el habito ese dia del programa: el mismo, salvo que ningun
     * horario lo cubra solo porque todos arrancan despues, y el habito ya haya corrido desde el
     * primero de ellos (D-200). Entonces, el inicio de ese horario.
     *
     * @param diaMasAltoYaGenerado el {@code dia_programa} mas alto de los registros de este habito
     *                             para la persona, o {@code null} si no tiene ninguno
     */
    public int diaEfectivo(int diaPrograma, TipoDia tipoDia, Integer diaMasAltoYaGenerado) {
        if (diaPrograma < PRIMER_DIA_DEL_PROGRAMA || algunoAplica(diaPrograma, tipoDia)) {
            return diaPrograma;
        }
        OptionalInt inicio = primerInicioPorEncima(diaPrograma, tipoDia);
        if (inicio.isPresent() && yaCorrioDesde(inicio.getAsInt(), diaMasAltoYaGenerado)) {
            return inicio.getAsInt();
        }
        return diaPrograma;
    }

    /**
     * El dia con el que se lee un registro YA GENERADO. Que exista prueba que el habito corria ese
     * dia: si ningun horario lo cubre es porque se genero por debajo de su inicio (D-200), y se lo
     * lee con el mismo dia con el que se lo genero: {@link #diaEfectivo} elige ese mismo inicio (el
     * mas cercano por encima, con el tipo de dia del registro), y si el habito no hubiera corrido
     * desde ahi el registro no existiria. No hace falta volver a leer los registros.
     */
    public int diaEfectivoDeUnRegistro(int diaDelRegistro, TipoDia tipoDia) {
        if (diaDelRegistro < PRIMER_DIA_DEL_PROGRAMA || algunoAplica(diaDelRegistro, tipoDia)) {
            return diaDelRegistro;
        }
        return primerInicioPorEncima(diaDelRegistro, tipoDia).orElse(diaDelRegistro);
    }

    /**
     * Si para resolver ese dia hace falta saber si el habito ya corrio: ningun horario lo cubre,
     * pero alguno lo cubriria de no ser por su inicio, y eso no esta probado ya sin leer registros
     * (el primer dia de un habito PERSONAL lo esta, D-216). Existe para no leer los registros en el
     * caso comun, en que un horario rige o ninguno podria regir.
     */
    public boolean necesitaSaberSiYaCorrio(int diaPrograma, TipoDia tipoDia) {
        if (diaPrograma < PRIMER_DIA_DEL_PROGRAMA || algunoAplica(diaPrograma, tipoDia)) {
            return false;
        }
        OptionalInt inicio = primerInicioPorEncima(diaPrograma, tipoDia);
        return inicio.isPresent() && !yaCorrioDesde(inicio.getAsInt(), null);
    }

    /**
     * Cuantos dias le faltan a la persona para que el habito arranque: 0 si su primer dia ya llego
     * o si el habito ya corrio desde ahi (D-200). Es el candado de "Mis habitos", y coincide con la
     * generacion: un habito con dias por delante no genera, y uno sin dias por delante genera los
     * dias que su tipo de dia le toca. Sin horarios no hay primer dia: 0.
     */
    public int diasParaArrancar(int diaPrograma, Integer diaMasAltoYaGenerado) {
        OptionalInt primero = primerDia();
        if (primero.isEmpty() || diaPrograma >= primero.getAsInt()
                || yaCorrioDesde(primero.getAsInt(), diaMasAltoYaGenerado)) {
            return 0;
        }
        return primero.getAsInt() - diaPrograma;
    }

    /** El {@code dia_inicio} mas chico: desde cuando existe el habito para la persona. Vacio sin horarios. */
    public OptionalInt primerDia() {
        return horarios.stream().mapToInt(HorarioHabito::diaInicio).min();
    }

    /** Los horarios que rigen un dia YA EFECTIVO: la regla de siempre, rango y tipo de dia. */
    public List<HorarioHabito> vigentesEn(int diaEfectivo, TipoDia tipoDia) {
        return horarios.stream().filter(h -> h.aplicaEnDia(diaEfectivo, tipoDia)).toList();
    }

    private boolean algunoAplica(int dia, TipoDia tipoDia) {
        return horarios.stream().anyMatch(h -> h.aplicaEnDia(dia, tipoDia));
    }

    /** El inicio mas cercano entre los horarios que cubririan el dia de no ser por su inicio. */
    private OptionalInt primerInicioPorEncima(int dia, TipoDia tipoDia) {
        return horarios.stream().filter(h -> h.quedaPorDebajoDeSuInicio(dia, tipoDia))
                .mapToInt(HorarioHabito::diaInicio).min();
    }

    /**
     * El criterio de D-196 y D-200: la persona llego a ese inicio con el habito ya existente. Lo
     * prueba algun registro del habito generado en un dia &gt;= a ese inicio, o, en un habito
     * PERSONAL, haberlo creado en ese dia (D-216).
     */
    private boolean yaCorrioDesde(int inicio, Integer diaMasAltoYaGenerado) {
        int alcanzado = Math.max(diaAlcanzadoAlCrearlo,
                diaMasAltoYaGenerado == null ? NINGUN_DIA : diaMasAltoYaGenerado);
        return alcanzado >= Math.max(inicio, PRIMER_DIA_DEL_PROGRAMA);
    }
}
