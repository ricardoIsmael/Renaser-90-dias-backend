package com.renaser.os.habits.infrastructure.adapter.in.rest.carrera;

import com.renaser.os.habits.application.ports.in.carrera.RegistrarCarreraUseCase.CarreraRegistrada;
import com.renaser.os.habits.domain.model.carrera.Carrera;
import com.renaser.os.habits.domain.model.carrera.EfectoEnElHabito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * La respuesta de {@code POST /api/v1/carreras} (contrato §1). En una repetida ({@code repetida: true}) {@code habito} es el mismo
 * que se devolvió la primera vez: viene guardado con la carrera, no se recalcula.
 */
public record CarreraResponse(UUID carreraId, boolean repetida, LocalDate fecha, BigDecimal kilometros, boolean cuentaParaElHabito,
                              String motivoNoCuenta, boolean porRevisar, String motivoRevision, HabitoResponse habito) {

    /**
     * @param resultado        {@code CERRADO}, {@code ACTUALIZADO}, {@code SIN_CAMBIO}, {@code YA_CON_CAPTURA},
     *                         {@code DIA_CERRADO}, {@code SIN_HABITO} o {@code NO_CUENTA}
     * @param kilometrosDelDia los km del hábito ese día después de aplicar; {@code null} si no hay un registro cumplido
     * @param origenDelDia     {@code GPS}, {@code HEALTH_CONNECT} o {@code MANUAL}; {@code null} si no hay un registro cumplido
     */
    public record HabitoResponse(String resultado, UUID registroId, BigDecimal kilometrosDelDia, String origenDelDia,
                                 int puntosOtorgados) {

        static HabitoResponse from(EfectoEnElHabito efecto) {
            return new HabitoResponse(efecto.resultado().name(), efecto.registroId(), efecto.kmDelDia(),
                    efecto.origenDelDia() == null ? null : efecto.origenDelDia().name(), efecto.puntos());
        }
    }

    static CarreraResponse from(CarreraRegistrada registrada) {
        Carrera c = registrada.carrera();
        return new CarreraResponse(c.datos().carreraId(), registrada.repetida(), c.fecha(), c.datos().kilometros(),
                c.evaluacion().cuenta(), c.evaluacion().motivoNoCuenta(), c.evaluacion().porRevisar(),
                c.evaluacion().motivoRevision(), HabitoResponse.from(c.efecto()));
    }
}
