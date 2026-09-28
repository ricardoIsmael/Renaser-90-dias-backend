package com.renaser.os.onboarding.domain.model.caja;

import com.renaser.os.shared.domain.UserId;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.renaser.os.onboarding.domain.model.caja.EstadoCaja.ARMANDO;
import static com.renaser.os.onboarding.domain.model.caja.EstadoCaja.CON_PROBLEMA;
import static com.renaser.os.onboarding.domain.model.caja.EstadoCaja.EN_EVALUACION;
import static com.renaser.os.onboarding.domain.model.caja.EstadoCaja.EN_PAUSA;
import static com.renaser.os.onboarding.domain.model.caja.EstadoCaja.ENTREGADA;
import static com.renaser.os.onboarding.domain.model.caja.EstadoCaja.ENVIADA;
import static com.renaser.os.onboarding.domain.model.caja.EstadoCaja.FUERA_DE_LA_APP;
import static com.renaser.os.onboarding.domain.model.caja.EstadoCaja.NO_APLICA;
import static com.renaser.os.onboarding.domain.model.caja.EstadoCaja.POR_REVISAR;

/**
 * Lo que se le puede hacer a una caja y DESDE qué estados (D-219). Las transiciones viven acá y en ningún
 * otro lado: el caso de uso pide la acción, y si el estado no corresponde sale un 409 con el motivo.
 *
 * <p>Las acciones que no dejan un paso ({@link #MARCAR_CONTENIDO}, {@link #CAMBIAR_DESTINO}) solo validan
 * el estado: lo que escriben es una respuesta del formulario, no una etapa.
 */
public enum AccionDeCaja {

    /** «Aprobar para la caja»: el Admin la aprueba sin que haya cumplido el requisito. */
    APROBAR(TipoPasoCaja.APROBADA, "aprobarla", EnumSet.of(EN_EVALUACION)),
    ARMAR(TipoPasoCaja.ARMANDO, "empezar a armarla", EnumSet.of(POR_REVISAR)),
    MARCAR_CONTENIDO(null, "marcar su contenido", EnumSet.of(ARMANDO)),
    FIJAR_FOTO(TipoPasoCaja.FOTO, "cambiar la foto", EnumSet.of(ARMANDO)),
    FIJAR_COMPROBANTE(TipoPasoCaja.COMPROBANTE, "cambiar el comprobante", EnumSet.of(ARMANDO)),
    ENVIAR(TipoPasoCaja.ENVIADA, "marcarla enviada", EnumSet.of(ARMANDO)),
    /** El Admin la marca entregada (también si reapareció después de reportarla perdida). */
    ENTREGAR(TipoPasoCaja.ENTREGADA, "marcarla entregada", EnumSet.of(ENVIADA, CON_PROBLEMA)),
    /** «Ya se envió antes» (spec §8): el padrón que la recibió antes de la app. Sin datos ni avisos. */
    ENTREGAR_PREVIA(TipoPasoCaja.ENTREGADA, "marcarla como ya enviada antes",
            EnumSet.of(NO_APLICA, EN_EVALUACION, POR_REVISAR, ARMANDO, EN_PAUSA, FUERA_DE_LA_APP)),
    /** «Ya la recibí», del propio aprendiz. */
    CONFIRMAR_RECIBIDA(TipoPasoCaja.ENTREGADA, "confirmar que llegó", EnumSet.of(ENVIADA)),
    REPORTAR_PROBLEMA(TipoPasoCaja.CON_PROBLEMA, "reportar un problema", EnumSet.of(ENVIADA, ENTREGADA)),
    /** Vuelve a armando, con el número de envío siguiente. */
    REENVIAR(TipoPasoCaja.ARMANDO, "reenviarla", EnumSet.of(CON_PROBLEMA)),
    /** Otra dirección, otro número o quién la recibe: antes de que salga. */
    CAMBIAR_DESTINO(null, "cambiar a dónde va", EnumSet.of(EN_EVALUACION, POR_REVISAR, ARMANDO, CON_PROBLEMA));

    private final TipoPasoCaja paso;
    private final String verbo;
    private final Set<EstadoCaja> desde;

    AccionDeCaja(TipoPasoCaja paso, String verbo, Set<EstadoCaja> desde) {
        this.paso = paso;
        this.verbo = verbo;
        this.desde = desde;
    }

    public boolean sePuedeDesde(EstadoCaja estado) {
        return desde.contains(estado);
    }

    /** @throws IllegalStateException si la caja no está en uno de los estados de los que parte (409) */
    public void exigirDesde(EstadoCaja estado) {
        if (!sePuedeDesde(estado)) {
            throw new IllegalStateException("La caja " + estado.enPalabras() + ": no se puede " + verbo + ".");
        }
    }

    /**
     * El paso que deja esta acción sobre esa caja, marcado por {@code actor} en el instante de la caja.
     *
     * @throws IllegalStateException si la caja no está en un estado del que parte esta acción
     */
    public PasoDeCaja paso(CajaRenaser caja, UserId actor, Map<String, String> detalle) {
        if (paso == null) {
            throw new IllegalStateException(name() + " no deja un paso");
        }
        exigirDesde(caja.estado());
        int envio = this == REENVIAR ? caja.envioActual() + 1 : caja.envioActual();
        return new PasoDeCaja(caja.aprendizId(), envio, paso, caja.ahora(), actor, detalle);
    }
}
