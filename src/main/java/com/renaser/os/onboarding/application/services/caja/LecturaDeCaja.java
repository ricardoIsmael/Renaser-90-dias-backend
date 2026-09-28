package com.renaser.os.onboarding.application.services.caja;

import com.renaser.os.onboarding.application.ports.in.caja.DetalleDeCaja;
import com.renaser.os.onboarding.application.ports.in.caja.DetalleDeCaja.ElementoMarcado;
import com.renaser.os.onboarding.application.ports.in.caja.DetalleDeCaja.PasoDelHistorial;
import com.renaser.os.onboarding.application.ports.out.caja.ContenidoDeCajaPort;
import com.renaser.os.onboarding.application.services.caja.CajasDelPadron.CajaConFicha;
import com.renaser.os.onboarding.domain.model.caja.CajaRenaser;
import com.renaser.os.onboarding.domain.model.caja.ContenidoDeCaja;
import com.renaser.os.onboarding.domain.model.caja.DatosDelEnvio;
import com.renaser.os.onboarding.domain.model.caja.FichaDeEnvio;
import com.renaser.os.onboarding.domain.model.caja.PasoDeCaja;
import com.renaser.os.onboarding.domain.model.caja.TipoPasoCaja;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Arma el detalle de una caja para el Admin: ficha, checklist, fotos, envío e historial con nombres. */
@Component
class LecturaDeCaja {

    /** Las fotos se ven con una URL firmada: vence, porque son datos de una persona. */
    private static final Duration VALIDEZ_URL_LECTURA = Duration.ofMinutes(15);

    private final ContenidoDeCajaPort contenido;
    private final AlmacenamientoPort almacenamiento;
    private final UserSummaryFinder usuarios;

    LecturaDeCaja(ContenidoDeCajaPort contenido, AlmacenamientoPort almacenamiento, UserSummaryFinder usuarios) {
        this.contenido = contenido;
        this.almacenamiento = almacenamiento;
        this.usuarios = usuarios;
    }

    DetalleDeCaja detalle(CajaConFicha leida) {
        CajaRenaser caja = leida.caja();
        ContenidoDeCaja lista = contenido.vigente();
        Set<String> marcados = contenido.marcadosDe(caja.aprendizId());
        List<ElementoMarcado> checklist = lista.elementos().stream()
                .map(e -> new ElementoMarcado(e.valor(), e.etiqueta(), marcados.contains(e.valor()))).toList();
        return new DetalleDeCaja(caja.aprendizId(), leida.nombre(), caja.estado(), caja.envioActual(),
                caja.cumplimientoFaseUno().orElse(null), conNombre(leida), checklist,
                urlDe(caja, TipoPasoCaja.FOTO), urlDe(caja, TipoPasoCaja.COMPROBANTE),
                caja.ultimo(TipoPasoCaja.ENVIADA).map(DatosDelEnvio::desde).orElse(null), historial(caja),
                caja.faltaParaEnviar(lista.completo(marcados)));
    }

    /** Si la ficha no tiene nombre, el de la cuenta: la caja siempre va a nombre de alguien. */
    private static FichaDeEnvio conNombre(CajaConFicha leida) {
        FichaDeEnvio ficha = leida.ficha();
        if (ficha.nombre() != null) {
            return ficha;
        }
        return new FichaDeEnvio(leida.nombre(), ficha.celular(), ficha.pais(), ficha.ciudad(), ficha.distrito(),
                ficha.direccion(), ficha.dni(), ficha.destino());
    }

    private String urlDe(CajaRenaser caja, TipoPasoCaja foto) {
        return caja.ultimo(foto).flatMap(paso -> paso.dato(PasoDeCaja.RUTA))
                .map(ruta -> almacenamiento.firmarLectura(ruta, VALIDEZ_URL_LECTURA).toString()).orElse(null);
    }

    /** Los pasos que mueven el estado, con el nombre de quien los marcó (una consulta para todos). */
    private List<PasoDelHistorial> historial(CajaRenaser caja) {
        List<PasoDeCaja> visibles = caja.pasos().stream()
                .filter(p -> p.tipo().estadoDelHistorial().isPresent()).toList();
        Map<UserId, UserSummary> autores = usuarios.findByIds(visibles.stream().map(PasoDeCaja::marcadaPor)
                .filter(Objects::nonNull).distinct().toList());
        return visibles.stream().map(p -> new PasoDelHistorial(p.envio(), p.tipo().estadoDelHistorial().orElseThrow(),
                p.en(), p.marcadaPor() == null || !autores.containsKey(p.marcadaPor()) ? null
                        : autores.get(p.marcadaPor()).fullName())).toList();
    }
}
