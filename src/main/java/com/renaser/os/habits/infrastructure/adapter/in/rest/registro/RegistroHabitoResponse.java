package com.renaser.os.habits.infrastructure.adapter.in.rest.registro;

import com.renaser.os.habits.domain.model.medicion.LecturaAutomatica;
import com.renaser.os.habits.domain.model.medicion.MedicionDiaria;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Proyeccion explicita de salida (CLAUDE.MD §5.4.1) — nunca la entidad de dominio serializada.
 *
 * <p>{@code valorMedido} (D-226, aditivo): el numero del dia de un habito medible (km), {@code null}
 * en los demas.
 *
 * <p>{@code medicion} (spec 037, aditivo): de donde salio ese numero —{@code MANUAL} o {@code HEALTH_CONNECT}— y, si lo leyo
 * el telefono, sus pasos, su fuente, la hora de la lectura y si quedo «por revisar». {@code null} sin medicion.
 */
public record RegistroHabitoResponse(String id, UUID habitoId, LocalDate fechaEjecucion, int diaPrograma,
                                      String tipoDia, boolean esOpcional, String estado, int puntosOtorgados,
                                      String respuestaTexto, Integer calificacionProductividad,
                                      Instant completadoEn, BigDecimal valorMedido, MedicionResponse medicion) {

    public static RegistroHabitoResponse from(RegistroHabito r) {
        return new RegistroHabitoResponse(r.id().toString(), r.habitoId().value(), r.fechaEjecucion(),
                r.diaPrograma(), r.tipoDia().name(), r.esOpcional(), r.estado().name(), r.puntosOtorgados(),
                r.respuestaTexto(), r.calificacionProductividad(), r.completadoEn(),
                r.medicion() != null ? r.medicion().valor() : null, MedicionResponse.from(r.medicion()));
    }

    /** {@code pasos}, {@code fuente}, {@code dispositivo}, {@code leidoEn} y {@code porRevisar} solo existen en la lectura automática. */
    public record MedicionResponse(BigDecimal valor, String origen, Integer pasos, String fuente, String dispositivo,
                                   Instant leidoEn, boolean porRevisar) {

        static MedicionResponse from(MedicionDiaria medicion) {
            if (medicion == null) {
                return null;
            }
            LecturaAutomatica lectura = medicion.lectura();
            if (lectura == null) {
                return new MedicionResponse(medicion.valor(), medicion.origen().name(), null, null, null, null, false);
            }
            return new MedicionResponse(medicion.valor(), medicion.origen().name(), lectura.pasos(), lectura.fuente(),
                    lectura.dispositivo().name(), lectura.leidaEn(), lectura.porRevisar());
        }
    }
}
