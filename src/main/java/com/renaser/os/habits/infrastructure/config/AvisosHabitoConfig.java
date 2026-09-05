package com.renaser.os.habits.infrastructure.config;

import com.renaser.os.habits.domain.model.aviso.CalculadoraAvisosHabito;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Convierte la configuracion de entorno en la regla de dominio de los avisos automaticos.
 *
 * <p><b>Por que son parametros y no constantes.</b> Cuantos minutos antes avisar es una decision
 * de negocio que el dueno todavia no confirmo, y este repo tiene una regla explicita contra
 * rellenar esos huecos con supuestos (regla 00). Dejarlos en configuracion cumple las dos cosas:
 * el sistema arranca y funciona, y el numero definitivo se fija sin tocar codigo — que es ademas
 * lo que el dueno pidio en general para los parametros de negocio (no hardcodear duraciones ni
 * umbrales).
 *
 * <p><b>Los defaults de abajo son PROVISORIOS y estan a la espera de confirmacion.</b> 15 minutos
 * antes de que empiece y 30 antes de que venza son valores razonables para un aviso dentro de la
 * app, no una regla del programa. Se cambian con {@code HABITS_AVISO_ANTELACION_INICIO} y
 * {@code HABITS_AVISO_ANTELACION_VENCIMIENTO} (formato ISO-8601, {@code PT15M}, o el corto de
 * Spring, {@code 15m}).
 *
 * <p><b>Como se apaga un aviso:</b> poniendo su antelacion en {@code PT0S}. La franja de
 * {@link CalculadoraAvisosHabito} queda vacia y ese aviso deja de emitirse, sin ninguna bandera
 * aparte. El aprendiz tambien puede apagar los dos desde sus preferencias de notificaciones
 * ({@code RECORDATORIO_HABITO}), que es donde corresponde decidirlo por persona.
 */
@Configuration
class AvisosHabitoConfig {

    @Bean
    CalculadoraAvisosHabito calculadoraAvisosHabito(
            @Value("${renaser.habits.avisos.antelacion-inicio:PT15M}") Duration antelacionInicio,
            @Value("${renaser.habits.avisos.antelacion-vencimiento:PT30M}") Duration antelacionVencimiento) {
        return new CalculadoraAvisosHabito(antelacionInicio, antelacionVencimiento);
    }
}
