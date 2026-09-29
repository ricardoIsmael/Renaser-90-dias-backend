package com.renaser.os.habits.application.ports.out.medicion;

import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

/**
 * La suma de lo medido por participante en un hábito del catálogo, desde su primer registro hasta
 * {@code hasta} inclusive (D-226: los km acumulados del programa).
 *
 * <p><b>En lote</b> (D-43): una sola consulta para todos los participantes pedidos, nunca una por
 * cabeza — el ranking la pide para todo el padrón. Solo cuentan los registros COMPLETADOS con número.
 * Quien no tiene ninguno no aparece en el mapa: el llamador decide qué vale "sin dato".
 */
public interface SumarMedicionesPort {

    Map<UserId, BigDecimal> sumaPorParticipante(Collection<UserId> participantes, String claveSistema,
                                                LocalDate hasta);
}
