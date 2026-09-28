package com.renaser.os.onboarding.application.ports.in.caja;

import com.renaser.os.onboarding.domain.model.caja.DatosDelEnvio;
import com.renaser.os.onboarding.domain.model.caja.EstadoCaja;
import com.renaser.os.onboarding.domain.model.caja.FichaDeEnvio;
import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Una fila de la lista del Admin (y del CSV): la caja de un aprendiz con sus datos de envío.
 *
 * @param grupo             el grupo en que está hoy, {@code null} si ninguno
 * @param actualizadoEn     el último paso que se marcó, {@code null} si ninguno
 * @param cumplimientoFase1 {@code null} antes del Día 8 o sin hábitos programados
 * @param envioDatos        los datos del último envío, {@code null} si no salió
 */
public record ResumenDeCaja(UserId aprendizId, String nombre, String grupo, int diaPrograma, EstadoCaja estado,
                            int envio, Instant actualizadoEn, BigDecimal cumplimientoFase1, FichaDeEnvio ficha,
                            DatosDelEnvio envioDatos) {
}
