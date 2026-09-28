package com.renaser.os.onboarding.application.services.caja;

import com.renaser.os.onboarding.api.AvisoDeCaja;
import com.renaser.os.onboarding.api.AvisoDeCajaEvent;
import com.renaser.os.onboarding.application.ports.in.caja.DetalleDeCaja;
import com.renaser.os.onboarding.application.ports.in.caja.OperarCajaUseCase;
import com.renaser.os.onboarding.application.ports.out.caja.ContenidoDeCajaPort;
import com.renaser.os.onboarding.application.ports.out.caja.PasosDeCajaPort;
import com.renaser.os.onboarding.application.services.caja.CajasDelPadron.CajaConFicha;
import com.renaser.os.onboarding.domain.model.caja.AccionDeCaja;
import com.renaser.os.onboarding.domain.model.caja.AvisosDeCaja;
import com.renaser.os.onboarding.domain.model.caja.CajaIncompletaException;
import com.renaser.os.onboarding.domain.model.caja.CajaRenaser;
import com.renaser.os.onboarding.domain.model.caja.ContenidoDeCaja;
import com.renaser.os.onboarding.domain.model.caja.DatosDelEnvio;
import com.renaser.os.onboarding.domain.model.caja.FaltaParaEnviar;
import com.renaser.os.onboarding.domain.model.caja.FotoDeCaja;
import com.renaser.os.onboarding.domain.model.caja.MotivoProblema;
import com.renaser.os.onboarding.domain.model.caja.PasoDeCaja;
import com.renaser.os.onboarding.domain.model.caja.TipoPasoCaja;
import com.renaser.os.shared.domain.UserId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lo que hace el Admin con una caja (D-219). Cada operación: exige ADMIN activo, arma la caja, pide la acción
 * al dominio ({@link AccionDeCaja}, que dice si corresponde al estado), guarda el paso y publica el aviso, todo
 * en UNA transacción: el aviso sale solo si el paso quedó (outbox de Modulith). Sin llamadas externas adentro.
 *
 * <p>Un doble toque choca contra la PK del paso ({@code DataIntegrityViolationException}, 409): no duplica.
 */
@Service
public class OperacionesDeCajaService implements OperarCajaUseCase {

    private static final int LARGO_MAXIMO_NOTA = 500;

    private final GuardiaDeCaja guardia;
    private final CajasDelPadron padron;
    private final LecturaDeCaja lectura;
    private final PasosDeCajaPort pasos;
    private final ContenidoDeCajaPort contenido;
    private final ApplicationEventPublisher eventos;

    OperacionesDeCajaService(GuardiaDeCaja guardia, CajasDelPadron padron, LecturaDeCaja lectura,
                             PasosDeCajaPort pasos, ContenidoDeCajaPort contenido, ApplicationEventPublisher eventos) {
        this.guardia = guardia;
        this.padron = padron;
        this.lectura = lectura;
        this.pasos = pasos;
        this.contenido = contenido;
        this.eventos = eventos;
    }

    @Override
    @Transactional
    public DetalleDeCaja aprobar(UserId actorId, UserId aprendizId) {
        CajaRenaser caja = cajaDe(actorId, aprendizId);
        pasos.registrar(AccionDeCaja.APROBAR.paso(caja, actorId, Map.of()));
        if (pasos.registrarSiFalta(AvisosDeCaja.marca(caja, TipoPasoCaja.AVISO_EN_REVISION))) {
            eventos.publishEvent(AvisoDeCajaEvent.de(aprendizId, caja.envioActual(), AvisoDeCaja.APROBADA));
        }
        return detalle(aprendizId);
    }

    @Override
    @Transactional
    public DetalleDeCaja armar(UserId actorId, UserId aprendizId) {
        CajaRenaser caja = cajaDe(actorId, aprendizId);
        PasoDeCaja paso = AccionDeCaja.ARMAR.paso(caja, actorId, Map.of());
        pasos.registrar(paso);
        eventos.publishEvent(AvisoDeCajaEvent.de(aprendizId, paso.envio(), AvisoDeCaja.ARMANDO));
        return detalle(aprendizId);
    }

    @Override
    @Transactional
    public DetalleDeCaja marcarContenido(UserId actorId, UserId aprendizId, List<String> marcados) {
        CajaRenaser caja = cajaDe(actorId, aprendizId);
        AccionDeCaja.MARCAR_CONTENIDO.exigirDesde(caja.estado());
        Set<String> validos = contenido.vigente().exigirMarcables(marcados);
        contenido.guardarMarcados(aprendizId, validos, caja.ahora());
        return detalle(aprendizId);
    }

    @Override
    @Transactional
    public DetalleDeCaja enviar(UserId actorId, UserId aprendizId, PedidoDeEnvio pedido) {
        CajaRenaser caja = cajaDe(actorId, aprendizId);
        AccionDeCaja.ENVIAR.exigirDesde(caja.estado());
        ContenidoDeCaja lista = contenido.vigente();
        List<FaltaParaEnviar> faltan = caja.faltaParaEnviar(lista.completo(contenido.marcadosDe(aprendizId)));
        if (!faltan.isEmpty()) {
            throw new CajaIncompletaException(faltan);
        }
        DatosDelEnvio datos = DatosDelEnvio.de(pedido.medio(), pedido.courier(), pedido.codigo(), pedido.costo());
        PasoDeCaja paso = AccionDeCaja.ENVIAR.paso(caja, actorId, detalleDelEnvio(caja, datos));
        pasos.registrar(paso);
        String fotoRuta = paso.dato(PasoDeCaja.FOTO_RUTA).orElse(null);
        eventos.publishEvent(new AvisoDeCajaEvent(AvisoDeCajaEvent.idDe(aprendizId, paso.envio(), AvisoDeCaja.EN_CAMINO),
                aprendizId, paso.envio(), AvisoDeCaja.EN_CAMINO, datos.medio(), datos.courier(), datos.codigo(),
                datos.rastreoUrl().orElse(null), fotoRuta));
        return detalle(aprendizId);
    }

    @Override
    @Transactional
    public DetalleDeCaja marcarEntregada(UserId actorId, UserId aprendizId, boolean previa) {
        CajaRenaser caja = cajaDe(actorId, aprendizId);
        if (previa) {
            pasos.registrar(AccionDeCaja.ENTREGAR_PREVIA.paso(caja, actorId, Map.of(PasoDeCaja.PREVIA, "true")));
            return detalle(aprendizId);
        }
        PasoDeCaja paso = AccionDeCaja.ENTREGAR.paso(caja, actorId, Map.of());
        pasos.registrar(paso);
        eventos.publishEvent(AvisoDeCajaEvent.de(aprendizId, paso.envio(), AvisoDeCaja.ENTREGADA));
        return detalle(aprendizId);
    }

    @Override
    @Transactional
    public DetalleDeCaja reportarProblema(UserId actorId, UserId aprendizId, String motivo, String nota) {
        CajaRenaser caja = cajaDe(actorId, aprendizId);
        AccionDeCaja.REPORTAR_PROBLEMA.exigirDesde(caja.estado());
        MotivoProblema elegido = MotivoProblema.de(motivo);
        String limpia = nota == null ? null : nota.strip();
        if (limpia != null && limpia.length() > LARGO_MAXIMO_NOTA) {
            throw new IllegalArgumentException("La nota puede tener hasta " + LARGO_MAXIMO_NOTA + " caracteres.");
        }
        Map<String, String> detalle = new HashMap<>();
        detalle.put(PasoDeCaja.MOTIVO, elegido.name());
        detalle.put(PasoDeCaja.NOTA, limpia);
        pasos.registrar(AccionDeCaja.REPORTAR_PROBLEMA.paso(caja, actorId, detalle));
        return detalle(aprendizId);
    }

    /** Vuelve a armando con el envío siguiente: el checklist empieza de cero (es otra caja). */
    @Override
    @Transactional
    public DetalleDeCaja reenviar(UserId actorId, UserId aprendizId) {
        CajaRenaser caja = cajaDe(actorId, aprendizId);
        PasoDeCaja paso = AccionDeCaja.REENVIAR.paso(caja, actorId, Map.of());
        pasos.registrar(paso);
        contenido.guardarMarcados(aprendizId, Set.of(), caja.ahora());
        eventos.publishEvent(AvisoDeCajaEvent.de(aprendizId, paso.envio(), AvisoDeCaja.ARMANDO));
        return detalle(aprendizId);
    }

    private CajaRenaser cajaDe(UserId actorId, UserId aprendizId) {
        guardia.exigirAdmin(actorId);
        return padron.deAprendiz(aprendizId).caja();
    }

    private DetalleDeCaja detalle(UserId aprendizId) {
        CajaConFicha leida = padron.deAprendiz(aprendizId);
        return lectura.detalle(leida);
    }

    /** Los datos del envío más las dos fotos de este envío, para que el historial no dependa de filas que cambian. */
    private static Map<String, String> detalleDelEnvio(CajaRenaser caja, DatosDelEnvio datos) {
        Map<String, String> detalle = new HashMap<>(datos.comoDetalle());
        for (FotoDeCaja foto : FotoDeCaja.values()) {
            caja.ultimo(foto.paso()).ifPresent(paso -> {
                detalle.put(foto.claveMediaEnvio(), paso.dato(PasoDeCaja.MEDIA_ID).orElse(null));
                detalle.put(foto.claveRutaEnvio(), paso.dato(PasoDeCaja.RUTA).orElse(null));
            });
        }
        return detalle;
    }
}
