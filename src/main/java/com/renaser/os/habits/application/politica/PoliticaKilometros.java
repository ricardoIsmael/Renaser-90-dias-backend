package com.renaser.os.habits.application.politica;

import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.medicion.LecturaAutomatica;
import com.renaser.os.habits.domain.model.medicion.MedicionDiaria;
import com.renaser.os.habits.domain.model.medicion.UnidadMedicion;
import com.renaser.os.habits.domain.model.politica.ContextoCompletar;
import com.renaser.os.habits.domain.model.politica.DecisionPolitica;
import com.renaser.os.habits.domain.model.politica.PoliticaHabito;
import com.renaser.os.habits.domain.model.politica.SelectorHabito;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * KILÓMETROS DIARIOS (D-226, pedido del dueño del 2026-09-29): la persona sube la captura de su app
 * de actividad y ESCRIBE cuántos km recorrió. Se cumple con cualquier km mayor que cero.
 *
 * <p>El número se suma para su total y para el ranking de km acumulados, así que no puede faltar:
 * completar este hábito por el gesto genérico sin km es un 400 que dice qué falta. Eso incluye al
 * agente de {@code rag} ({@code marcar_habito_completado}), que no tiene el número.
 *
 * <p><b>Tope de {@link #TOPE_KM_POR_DIA} km por día, confirmado por el dueño (2026-09-29).</b> Está
 * para que un error de tipeo (330 en vez de 3,30) no ponga a alguien primero en el ranking para todo
 * el programa.
 *
 * <p>Que pague el puntaje completo a cualquier hora del día local (también confirmado el 29/09) NO
 * vive acá: es el horario del hábito, 07:00 – 23:59, que le pone {@code V86}.
 *
 * <p><b>Lectura automática (spec 037, D-310).</b> El teléfono también puede cerrar el hábito con los pasos y la distancia que
 * leyó de Health Connect. Eso NO pasa por {@link #puedeCompletarseDirecto} (que pide la captura y el número escrito) sino por
 * {@link #validarMedicionAutomatica}: exige llegar a la META (la que fija Administración; {@link #META_POR_DEFECTO_KM} si no hay),
 * topa lo imposible ({@link #TOPE_KM_AUTOMATICO}, {@link #TOPE_PASOS_AUTOMATICO}) y deja «por revisar»
 * ({@link #conRevision}) lo dudoso sin rechazarlo: el servidor no confía en el teléfono, pero tampoco le quita a nadie un día
 * que sí caminó. La captura con número a mano sigue igual (mayor que cero, hasta 100 km).
 *
 * <p>Selecciona por clave y no por tipo (su tipo, CHECKBOX, lo comparten otros quince): la clave
 * {@link #CLAVE_SISTEMA} se la puso {@code V84}. No decide puntos, ventana ni evento: eso sigue en
 * {@code RegistroService}, en un solo lugar.
 */
@Component
public class PoliticaKilometros implements PoliticaHabito {

    /** La clave que V84 le puso al hábito {@code ea87fdec…}; la usa también el ranking de km. */
    public static final String CLAVE_SISTEMA = "DAILY_KM";

    /** Confirmado por el dueño (ver javadoc de la clase): más que esto en un día es un error de tipeo. */
    public static final BigDecimal TOPE_KM_POR_DIA = new BigDecimal("100");

    /** La meta cuando Administración no fijó otra (supuesto del equipo, spec 037: el dueño lo confirma). */
    public static final BigDecimal META_POR_DEFECTO_KM = new BigDecimal("5");
    /** Lo que admite la columna {@code habitos.meta_medicion} (CHECK de V161). */
    public static final BigDecimal META_MINIMA_KM = new BigDecimal("0.5");
    public static final BigDecimal META_MAXIMA_KM = new BigDecimal("50");

    /** Más que esto en un día no se registra solo: se sube la captura. */
    public static final BigDecimal TOPE_KM_AUTOMATICO = new BigDecimal("60");
    public static final int TOPE_PASOS_AUTOMATICO = 150_000;

    /** Por encima de esto una lectura se acepta pero queda «por revisar». */
    public static final BigDecimal KM_PARA_REVISAR = new BigDecimal("30");
    /** Una zancada fuera de este rango (en metros por paso) hace dudar de la lectura: bici, auto o datos escritos por otra app. */
    public static final BigDecimal METROS_POR_PASO_MINIMO = new BigDecimal("0.3");
    public static final BigDecimal METROS_POR_PASO_MAXIMO = new BigDecimal("1.6");

    @Override
    public SelectorHabito selector() {
        return SelectorHabito.porClave(CLAVE_SISTEMA);
    }

    @Override
    public Optional<UnidadMedicion> unidadDeMedicion() {
        return Optional.of(UnidadMedicion.KILOMETROS);
    }

    /** El motivo lo lee una persona en el teléfono: dice qué falta y cómo se arregla. */
    @Override
    public DecisionPolitica puedeCompletarseDirecto(Habito habito, ContextoCompletar contexto) {
        Optional<MedicionDiaria> medicion = contexto.medicion();
        if (medicion.isEmpty()) {
            // Un APK sin la pantalla de km llega acá: el mensaje le dice también qué hacer.
            return DecisionPolitica.noProcede(
                    "Escribe cuántos km recorriste hoy para registrar este hábito. Si no ves dónde, actualiza la app.");
        }
        BigDecimal km = medicion.get().valor();
        if (km.signum() <= 0) {
            return DecisionPolitica.noProcede("Los km recorridos tienen que ser más que cero.");
        }
        if (km.compareTo(TOPE_KM_POR_DIA) > 0) {
            return DecisionPolitica.noProcede("Revisa el número: más de 100 km en un día no se puede registrar.");
        }
        return DecisionPolitica.procede();
    }

    @Override
    public Optional<BigDecimal> metaPorDefectoDeLaMedicion() {
        return Optional.of(META_POR_DEFECTO_KM);
    }

    /** La meta que rige: la que fijó Administración o, sin ella, {@link #META_POR_DEFECTO_KM}. */
    public static BigDecimal metaEfectiva(BigDecimal configurada) {
        return configurada == null ? META_POR_DEFECTO_KM : configurada;
    }

    /** {@code true} si una meta cabe en la columna: de 0,5 a 50 km. */
    public static boolean metaValida(BigDecimal meta) {
        return meta != null && meta.compareTo(META_MINIMA_KM) >= 0 && meta.compareTo(META_MAXIMA_KM) <= 0;
    }

    /**
     * Si una lectura de Health Connect puede cerrar el hábito: es automática, positiva, dentro de los topes y llega a la
     * {@code meta}. El motivo lo lee una persona en el teléfono: dice qué falta o qué hacer (subir la captura).
     *
     * @param meta la meta que rige ({@link #metaEfectiva})
     */
    public DecisionPolitica validarMedicionAutomatica(MedicionDiaria medicion, BigDecimal meta) {
        if (!medicion.esAutomatica()) {
            return DecisionPolitica.noProcede("Esta lectura no viene de tus pasos.");
        }
        if (medicion.valor().signum() <= 0) {
            return DecisionPolitica.noProcede("Los km recorridos tienen que ser más que cero.");
        }
        if (medicion.valor().compareTo(TOPE_KM_AUTOMATICO) > 0 || pasosDeMas(medicion.lectura())) {
            return DecisionPolitica.noProcede("Revisa los datos: más de " + TOPE_KM_AUTOMATICO.toPlainString()
                    + " km en un día no se puede registrar solo. Sube tu captura.");
        }
        if (medicion.valor().compareTo(meta) < 0) {
            return DecisionPolitica.noProcede("Todavía no llegas a tu meta de " + comoSeLee(meta) + " km.");
        }
        return DecisionPolitica.procede();
    }

    /** La misma lectura, marcada «por revisar» si es dudosa (nunca desmarca una que ya viene marcada). */
    public MedicionDiaria conRevision(MedicionDiaria medicion) {
        LecturaAutomatica lectura = medicion.lectura();
        boolean dudosa = lectura.porRevisar() || esDudosa(medicion.valor(), lectura.pasos());
        return MedicionDiaria.automatica(medicion.valor(), lectura.conRevision(dudosa));
    }

    private static boolean esDudosa(BigDecimal km, Integer pasos) {
        return km.compareTo(KM_PARA_REVISAR) > 0 || (pasos != null && pasos > 0 && !zancadaPosible(km, pasos));
    }

    private static boolean zancadaPosible(BigDecimal km, int pasos) {
        BigDecimal metrosPorPaso = km.multiply(new BigDecimal(1000)).divide(new BigDecimal(pasos), 4, RoundingMode.HALF_UP);
        return metrosPorPaso.compareTo(METROS_POR_PASO_MINIMO) >= 0 && metrosPorPaso.compareTo(METROS_POR_PASO_MAXIMO) <= 0;
    }

    private static boolean pasosDeMas(LecturaAutomatica lectura) {
        return lectura.pasos() != null && lectura.pasos() > TOPE_PASOS_AUTOMATICO;
    }

    /** «5» y «6,5»: como se escribe un número en el teléfono de una persona de Perú. */
    private static String comoSeLee(BigDecimal km) {
        return km.stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
