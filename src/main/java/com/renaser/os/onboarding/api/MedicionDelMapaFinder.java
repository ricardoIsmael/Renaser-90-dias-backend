package com.renaser.os.onboarding.api;

import com.renaser.os.shared.domain.UserId;

/**
 * Que declaro la persona en el Mapa de Renacimiento sobre <b>como se mide</b> cada uno de sus tres
 * objetivos. Es lo unico que otro modulo necesita del Mapa y no esta en ninguna otra parte.
 *
 * <h2>Por que existe</h2>
 *
 * {@code rocks} guarda el numero, la unidad y la linea base de cada Roca Maestra, pero <b>no que
 * se mide</b>. Y sin eso no se puede repartir el objetivo por mes: "82 kg de peso" se reparte y
 * tiene tope fisiologico, "8/10 de energia" se reparte solo en enteros, y un marcador clinico no se
 * reparte en absoluto. Deducirlo de la unidad seria adivinar, y si la adivinanza falla se muestra
 * justo el "baja 20 kg este mes" que el dueno prohibio.
 *
 * <p>Relaciones es el caso extremo: su objetivo viaja a {@code rocks} <b>sin</b> meta cuantitativa
 * a proposito (un puntaje de 1 a 10 no es una unidad de negocio, y mezclarlo con kilos o soles
 * romperia el porcentaje). Sus dos numeros existen unicamente aca.
 *
 * <p>Los valores viajan como <b>texto crudo</b>, tal como los sembro la V41
 * ({@code peso}, {@code facturacion}, {@code acumulado_dia_90}…). Este modulo no los interpreta:
 * traducirlos a una regla de negocio es trabajo de quien los consume, y exportar un enum propio
 * obligaria a tocar {@code onboarding} cada vez que otro modulo quiera leer una respuesta mas.
 */
public interface MedicionDelMapaFinder {

    /**
     * Lo que haya. Nunca {@code null} y nunca falla: no haber recorrido el Mapa —o haberlo
     * recorrido antes de que estas respuestas se guardaran en el servidor, cosa que recien pasa
     * desde el 2026-09-14— no es un error, es el estado normal de mucha gente. Los campos que
     * falten vienen en {@code null} y quien consume decide que hacer sin ellos.
     */
    MedicionDelMapa delParticipante(UserId usuarioId);

    /**
     * @param saludTipo       {@code map_health_result_type}: peso, medidas, energia, fuerza,
     *                        resistencia, sueno, condicion_clinica, otro.
     * @param saludUnidad     {@code map_health_unit}, texto libre. Solo sirve para detectar una
     *                        escala cuando el tipo es {@code otro}.
     * @param negocioTipo     {@code map_business_result_type}: facturacion, utilidad, ventas,
     *                        clientes, ahorro, deuda, ingreso_personal, otro.
     * @param negocioPeriodo  {@code map_business_period}: semanal, mensual, acumulado_dia_90.
     * @param relacionesBase  {@code map_relations_baseline_scale}, de 1 a 10.
     * @param relacionesMeta  {@code map_relations_target_scale}, de 1 a 10.
     */
    record MedicionDelMapa(String saludTipo, String saludUnidad, String negocioTipo, String negocioPeriodo,
                            Integer relacionesBase, Integer relacionesMeta) {

        /** Quien no recorrio el Mapa, o lo recorrio cuando esto todavia no se guardaba. */
        public static MedicionDelMapa vacia() {
            return new MedicionDelMapa(null, null, null, null, null, null);
        }
    }
}
