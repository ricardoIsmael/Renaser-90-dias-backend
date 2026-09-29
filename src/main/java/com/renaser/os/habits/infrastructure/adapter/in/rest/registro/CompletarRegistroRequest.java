package com.renaser.os.habits.infrastructure.adapter.in.rest.registro;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Sin campo `puntos`: el otorgamiento SIEMPRE lo calcula el servidor (CLAUDE.MD §5.3.3).
 *
 * <p>{@code valorMedido} (D-226, aditivo: un cliente que no lo manda sigue igual): el numero del dia
 * de un habito medible — hoy los km de KILÓMETROS DIARIOS. Numero JSON con punto decimal; la coma que
 * escribe la persona la convierte la app. Aca solo se exige que no sea negativo; el redondeo a dos
 * decimales y el maximo que entra en la columna los pone {@code MedicionDiaria} (un 4,126 se guarda
 * 4,13, no se rechaza), y si el valor tiene sentido para ese habito lo decide su politica
 * ({@code PoliticaKilometros}). En un habito que no mide nada es un 400.
 */
public record CompletarRegistroRequest(@Size(max = 4000) String respuestaTexto, Integer calificacionProductividad,
                                        @DecimalMin("0") BigDecimal valorMedido) {
}
