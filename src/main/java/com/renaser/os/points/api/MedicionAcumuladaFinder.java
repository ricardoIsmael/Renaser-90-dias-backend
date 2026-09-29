package com.renaser.os.points.api;

import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

/**
 * Contrato entre módulos (D-226): lo que cada participante MIDIÓ en un hábito medible, sumado desde su
 * Día 1 hasta {@code hasta} inclusive. Lo consume {@code points} para el ranking de km acumulados; lo
 * implementa {@code habits}, dueño de {@code registros_habito}.
 *
 * <p><b>Por qué vive en {@code points} y no en {@code habits} (DIP):</b> igual que
 * {@link PorcentajeHabitosFinder}, {@code habits} ya depende de {@code points} para otorgar puntos;
 * declararlo en {@code habits} cerraría un ciclo que Spring Modulith rechaza.
 *
 * <p><b>En lote</b> (D-43): una consulta para todos, nunca una por participante.
 *
 * <p>Un método por medición y no un parámetro con la clave del hábito: {@code points} no conoce las
 * claves del catálogo de {@code habits}, y así no puede pedir la suma de algo que no se mide. Los
 * pasos (fase 2) serían otro método.
 */
public interface MedicionAcumuladaFinder {

    /**
     * @param participantes a quiénes sumar (se deduplican; vacío = mapa vacío sin consultar)
     * @param hasta         último día (inclusive) de {@code fecha_ejecucion} que entra en la suma
     * @return km con dos decimales por participante, <b>solo para quienes registraron alguno</b>. Sin
     *         clave = nunca registró km; el ranking lo pone con cero, al fondo.
     */
    Map<UserId, BigDecimal> kilometrosAcumulados(Collection<UserId> participantes, LocalDate hasta);
}
