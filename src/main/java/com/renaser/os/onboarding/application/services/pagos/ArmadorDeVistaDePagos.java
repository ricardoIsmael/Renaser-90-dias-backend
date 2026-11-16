package com.renaser.os.onboarding.application.services.pagos;

import com.renaser.os.onboarding.application.ports.in.pagos.VistaDePagos.CuotaVista;
import com.renaser.os.onboarding.application.ports.in.pagos.VistaDePagos.PagoAnotado;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.pagos.CuotasDePagoPort;
import com.renaser.os.onboarding.domain.model.estado.EstadoOnboarding;
import com.renaser.os.onboarding.domain.model.pagos.CronogramaDePagos;
import com.renaser.os.onboarding.domain.model.pagos.Cuota;
import com.renaser.os.onboarding.domain.model.pagos.ModalidadDePago;
import com.renaser.os.onboarding.domain.model.pagos.PagoDeLaPersona;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Arma lo que se lee del pago de una persona (spec 032) con su estado de ingreso y sus cuotas: lo comparten «Mi ingreso» (ella) y la ficha de Administración. Aquí se
 * deriva si su confirmación sigue vigente (huella) y, solo para Administración, el nombre de quien marcó cada cuota.
 */
@Component
class ArmadorDeVistaDePagos {

    private final CuotasDePagoPort cuotas;
    private final ConsultarActorPort actores;

    ArmadorDeVistaDePagos(CuotasDePagoPort cuotas, ConsultarActorPort actores) {
        this.cuotas = cuotas;
        this.actores = actores;
    }

    /**
     * El pago de la persona, o {@code null} si no tiene nada anotado.
     *
     * @param estado     su estado de ingreso; {@code null} si todavía no tiene fila
     * @param conNombres si se resuelve el nombre de quien marcó cada cuota (solo Administración lo ve)
     */
    PagoAnotado armar(UserId persona, EstadoOnboarding estado, boolean conNombres) {
        PagoDeLaPersona pago = estado == null ? PagoDeLaPersona.sinAnotar() : estado.pago();
        if (!pago.anotado()) {
            return null;
        }
        if (!pago.esConCronograma()) {
            return new PagoAnotado(ModalidadDePago.SIN_CRONOGRAMA, null, List.of(), null, null, null);
        }
        CronogramaDePagos cronograma = cronogramaDe(persona, pago);
        Map<UserId, String> nombres = conNombres ? nombresDeQuienMarco(cronograma.cuotas()) : Map.of();
        return conCronograma(pago, cronograma, nombres);
    }

    /**
     * El pago «con cronograma» de una persona cuyas cuotas ya se leyeron (la lectura de todos, D-338: las cuotas y los nombres llegan en lote, sin una consulta por
     * persona). Mismo armado que {@link #armar}: la huella y la confirmación salen del cronograma entero.
     *
     * @param nombres quién marcó cada cuota; el que falte queda sin nombre
     */
    PagoAnotado conCronograma(PagoDeLaPersona pago, List<Cuota> cuotasGuardadas, Map<UserId, String> nombres) {
        return conCronograma(pago, CronogramaDePagos.guardado(pago.moneda(), cuotasGuardadas), nombres);
    }

    private static PagoAnotado conCronograma(PagoDeLaPersona pago, CronogramaDePagos cronograma, Map<UserId, String> nombres) {
        // Map.of() no admite pedir una llave nula (lanza NullPointerException): una cuota que nadie marcó no tiene a quién nombrar.
        List<CuotaVista> vistas = cronograma.cuotas().stream()
                .map(cuota -> new CuotaVista(cuota, cuota.estadoCambiadoPor() == null ? null : nombres.get(cuota.estadoCambiadoPor()))).toList();
        return new PagoAnotado(ModalidadDePago.CON_CRONOGRAMA, pago.moneda(), vistas, pago.confirmacion(cronograma.huella()), pago.confirmadoEn(),
                pago.confirmadoNombre());
    }

    /** El cronograma que tiene guardado la persona, con la moneda de su estado. */
    CronogramaDePagos cronogramaDe(UserId persona, PagoDeLaPersona pago) {
        return CronogramaDePagos.guardado(pago.moneda(), cuotas.deLaPersona(persona));
    }

    private Map<UserId, String> nombresDeQuienMarco(List<Cuota> delCronograma) {
        Map<UserId, String> nombres = new HashMap<>();
        delCronograma.stream().map(Cuota::estadoCambiadoPor).filter(java.util.Objects::nonNull).distinct()
                .forEach(quien -> actores.deActor(quien).ifPresent(actor -> nombres.put(quien, actor.nombre())));
        return nombres;
    }
}
