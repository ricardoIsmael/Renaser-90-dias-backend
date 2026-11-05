package com.renaser.os.habits.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lo medido en una obligación de hábito (hoy, los km de KILÓMETROS DIARIOS) y de dónde salió, para quien acompaña a la
 * persona (spec 037): su mentor, su líder y Administración lo ven en la ficha. Es un resumen de lectura: nunca entra a un
 * cálculo.
 *
 * @param origen       {@code MANUAL} (la persona escribió el número junto a su captura), {@code GPS} (spec 042) o {@code HEALTH_CONNECT} (lo leyó
 *                     el teléfono)
 * @param pasos        solo en {@code HEALTH_CONNECT}, y opcional
 * @param fuente       etiqueta legible de la fuente («Galaxy Watch», «Tu teléfono»); {@code null} en {@code MANUAL}
 * @param dispositivo  {@code TELEFONO}, {@code RELOJ} u {@code OTRO}; {@code null} en {@code MANUAL}
 * @param leidaEn      cuándo leyó el teléfono; {@code null} en {@code MANUAL}
 * @param porRevisar   el servidor aceptó una lectura dudosa y la dejó marcada
 * @param carreras     spec 042 (FR-044): las carreras con GPS de ese día (vacía si no hay). Con carreras, {@code origen} puede ser
 *                     {@code GPS}; si el hábito no quedó cumplido con un número (la carrera no contó, el día ya había cerrado),
 *                     {@code valor} y {@code origen} van en {@code null} y solo vienen las carreras
 */
public record MedicionDeLaObligacion(BigDecimal valor, String origen, Integer pasos, String fuente, String dispositivo,
                                      Instant leidaEn, boolean porRevisar, List<CarreraDeLaObligacion> carreras) {

    public MedicionDeLaObligacion {
        carreras = carreras == null ? List.of() : List.copyOf(carreras);
    }

    /** Sin carreras: la forma de la spec 037. */
    public MedicionDeLaObligacion(BigDecimal valor, String origen, Integer pasos, String fuente, String dispositivo,
                                  Instant leidaEn, boolean porRevisar) {
        this(valor, origen, pasos, fuente, dispositivo, leidaEn, porRevisar, List.of());
    }

    /** La misma medición con las carreras con GPS de ese día (spec 042, FR-044). */
    public MedicionDeLaObligacion conCarreras(List<CarreraDeLaObligacion> delDia) {
        return new MedicionDeLaObligacion(valor, origen, pasos, fuente, dispositivo, leidaEn, porRevisar, delDia);
    }

    /** Solo las carreras de un día cuyo hábito no quedó cumplido con un número (no cuentan, día cerrado): sin valor ni origen. */
    public static MedicionDeLaObligacion soloCarreras(List<CarreraDeLaObligacion> delDia) {
        return new MedicionDeLaObligacion(null, null, null, null, null, null, false, delDia);
    }

    /**
     * Una carrera con GPS de ese día (spec 042, FR-044), resumida: la ruta no viaja acá, se pide aparte al abrir el mapa.
     *
     * @param ritmoSegPorKm ritmo medio, en segundos por km
     */
    public record CarreraDeLaObligacion(UUID carreraId, BigDecimal kilometros, int duracionActivaSeg, int ritmoSegPorKm,
                                        boolean porRevisar, boolean cuentaParaElHabito, boolean tieneRuta) {
    }
}
