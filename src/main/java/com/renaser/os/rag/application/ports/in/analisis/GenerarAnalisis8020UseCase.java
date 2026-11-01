package com.renaser.os.rag.application.ports.in.analisis;

import com.renaser.os.rag.domain.model.analisis.TipoDePeriodo;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;

/**
 * Genera el análisis 80/20 de UN periodo de UNA persona (spec 031). Seguro de repetir: un periodo que ya tiene su resultado no se vuelve
 * a generar, y uno pendiente no se reintenta antes de la espera. No se expone por REST: la única forma de invocarlo es el barrido
 * ({@link GenerarAnalisisPendientesUseCase}).
 */
public interface GenerarAnalisis8020UseCase {

    ResultadoDeLaGeneracion generar(UserId persona, TipoDePeriodo tipo, LocalDate inicio);

    enum ResultadoDeLaGeneracion {
        /** El texto se generó, pasó la revisión y quedó guardado. */
        LISTO,
        /** El periodo no alcanzó el mínimo: se anotó y no se llamó a la IA. */
        SIN_DATOS,
        /** El intento falló (IA caída o texto que no se puede mostrar): se anotó y se reintentará. */
        FALLO,
        /** No se pudo intentar ahora (IA apagada, circuito abierto o tope del día): queda pendiente y no cuenta como intento. */
        DIFERIDO,
        /** Ya tenía resultado, o todavía no toca reintentar. */
        YA_DECIDIDO,
        /** La persona no puede tener análisis (cuenta suspendida o de prueba, sin programa) o el periodo no existe o no cerró. */
        NO_APLICA
    }
}
