package com.renaser.os.habits.application.politica;

import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.medicion.MedicionDiaria;
import com.renaser.os.habits.domain.model.medicion.UnidadMedicion;
import com.renaser.os.habits.domain.model.politica.ContextoCompletar;
import com.renaser.os.habits.domain.model.politica.DecisionPolitica;
import com.renaser.os.habits.domain.model.politica.PoliticaHabito;
import com.renaser.os.habits.domain.model.politica.SelectorHabito;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * KILÓMETROS DIARIOS (D-226, pedido del dueño del 2026-09-29): la persona sube la captura de su app
 * de actividad y ESCRIBE cuántos km recorrió. Se cumple con cualquier km mayor que cero.
 *
 * <p>El número se suma para su total y para el ranking de km acumulados, así que no puede faltar:
 * completar este hábito por el gesto genérico sin km es un 400 que dice qué falta. Eso incluye al
 * agente de {@code rag} ({@code marcar_habito_completado}), que no tiene el número.
 *
 * <p><b>Supuesto a confirmar con el dueño:</b> el tope de {@link #TOPE_KM_POR_DIA} km por día. Nadie
 * lo definió; está para que un error de tipeo (330 en vez de 3,30) no ponga a alguien primero en el
 * ranking para todo el programa. Un ultramaratón existe, pero no es el caso de este programa.
 *
 * <p>Selecciona por clave y no por tipo (su tipo, CHECKBOX, lo comparten otros quince): la clave
 * {@link #CLAVE_SISTEMA} se la puso {@code V84}. No decide puntos, ventana ni evento: eso sigue en
 * {@code RegistroService}, en un solo lugar.
 */
@Component
public class PoliticaKilometros implements PoliticaHabito {

    /** La clave que V84 le puso al hábito {@code ea87fdec…}; la usa también el ranking de km. */
    public static final String CLAVE_SISTEMA = "DAILY_KM";

    /** Supuesto (ver javadoc de la clase): más que esto en un día se toma como error de tipeo. */
    public static final BigDecimal TOPE_KM_POR_DIA = new BigDecimal("100");

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
}
