package com.renaser.os.onboarding.application.services.caja;

import com.renaser.os.onboarding.api.AvisoDeCaja;
import com.renaser.os.onboarding.api.AvisoDeCajaEvent;
import com.renaser.os.onboarding.application.ports.in.caja.MiCajaUseCase;
import com.renaser.os.onboarding.application.ports.out.caja.FichaDeEnvioPort;
import com.renaser.os.onboarding.application.ports.out.caja.PasosDeCajaPort;
import com.renaser.os.onboarding.application.services.caja.CajasDelPadron.CajaConFicha;
import com.renaser.os.onboarding.domain.model.caja.AccionDeCaja;
import com.renaser.os.onboarding.domain.model.caja.CajaRenaser;
import com.renaser.os.onboarding.domain.model.caja.DatosDelEnvio;
import com.renaser.os.onboarding.domain.model.caja.DestinoAlternativo;
import com.renaser.os.onboarding.domain.model.caja.EnvioSalido;
import com.renaser.os.onboarding.domain.model.caja.EstadoCaja;
import com.renaser.os.onboarding.domain.model.caja.PasoDeCaja;
import com.renaser.os.onboarding.domain.model.caja.TipoPasoCaja;
import com.renaser.os.shared.domain.UserId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * «Tu Caja Renaser» del aprendiz (D-219). Solo sobre SU caja: el id sale de la sesión, nunca del cliente, así
 * que no hay caja ajena a la que llegar. Una cuenta suspendida recibe 403 aunque su sesión siga viva.
 */
@Service
public class MiCajaService implements MiCajaUseCase {

    private final GuardiaDeCaja guardia;
    private final CajasDelPadron padron;
    private final PasosDeCajaPort pasos;
    private final FichaDeEnvioPort fichas;
    private final ApplicationEventPublisher eventos;
    private final LecturaDeCaja lectura;

    MiCajaService(GuardiaDeCaja guardia, CajasDelPadron padron, PasosDeCajaPort pasos, FichaDeEnvioPort fichas,
                  ApplicationEventPublisher eventos, LecturaDeCaja lectura) {
        this.guardia = guardia;
        this.padron = padron;
        this.pasos = pasos;
        this.fichas = fichas;
        this.eventos = eventos;
        this.lectura = lectura;
    }

    @Override
    public MiCaja ver(UserId actorId) {
        return miCaja(leer(actorId));
    }

    @Override
    @Transactional
    public MiCaja cambiarDestino(UserId actorId, PedidoDeDestino pedido) {
        CajaRenaser caja = leer(actorId).caja();
        AccionDeCaja.CAMBIAR_DESTINO.exigirDesde(caja.estado());
        DestinoAlternativo destino = DestinoAlternativo.de(pedido.otraDireccion(), pedido.otroCelular(),
                pedido.quienRecibe(), pedido.referencias(), pedido.provincia());
        fichas.guardarDestino(actorId, destino, caja.ahora());
        return miCaja(leer(actorId));
    }

    @Override
    @Transactional
    public MiCaja confirmarRecibida(UserId actorId) {
        CajaRenaser caja = leer(actorId).caja();
        PasoDeCaja paso = AccionDeCaja.CONFIRMAR_RECIBIDA.paso(caja, actorId, Map.of());
        pasos.registrar(paso);
        eventos.publishEvent(AvisoDeCajaEvent.de(actorId, paso.envio(), AvisoDeCaja.ENTREGADA));
        return miCaja(leer(actorId));
    }

    private CajaConFicha leer(UserId actorId) {
        guardia.exigirActiva(actorId);
        return padron.de(actorId).orElseThrow(() -> new NoSuchElementException("Usuario no encontrado"));
    }

    /**
     * Lo que ve el aprendiz de su caja. Sin comprobante, sin costo y sin la nota interna de un problema: solo el
     * motivo, que la respuesta HTTP dice en palabras en la app (D-220).
     */
    private MiCaja miCaja(CajaConFicha leida) {
        CajaRenaser caja = leida.caja();
        List<PasoVisible> pasosVisibles = List.of(
                new PasoVisible(EstadoCaja.EN_EVALUACION, null),
                new PasoVisible(EstadoCaja.POR_REVISAR, enRevisionDesde(caja)),
                new PasoVisible(EstadoCaja.ARMANDO, en(caja, TipoPasoCaja.ARMANDO)),
                new PasoVisible(EstadoCaja.ENVIADA, en(caja, TipoPasoCaja.ENVIADA)),
                new PasoVisible(EstadoCaja.ENTREGADA, en(caja, TipoPasoCaja.ENTREGADA)));
        DatosDelEnvio envio = caja.ultimo(TipoPasoCaja.ENVIADA).map(DatosDelEnvio::desde).orElse(null);
        return new MiCaja(caja.estado(), pasosVisibles, envio,
                AccionDeCaja.CONFIRMAR_RECIBIDA.sePuedeDesde(caja.estado()),
                AccionDeCaja.CAMBIAR_DESTINO.sePuedeDesde(caja.estado()), leida.ficha().destino(),
                EnvioSalido.de(caja.pasos()), lectura.fotoEnviada(caja));
    }

    /** Cuándo quedó en revisión: la aprobación del Admin, o el aviso del barrido si pasó sola. */
    private static Instant enRevisionDesde(CajaRenaser caja) {
        return caja.pasos().stream()
                .filter(p -> p.tipo() == TipoPasoCaja.APROBADA || p.tipo() == TipoPasoCaja.AVISO_EN_REVISION)
                .map(PasoDeCaja::en).findFirst().orElse(null);
    }

    private static Instant en(CajaRenaser caja, TipoPasoCaja tipo) {
        Optional<PasoDeCaja> paso = caja.ultimo(tipo);
        return paso.map(PasoDeCaja::en).orElse(null);
    }
}
