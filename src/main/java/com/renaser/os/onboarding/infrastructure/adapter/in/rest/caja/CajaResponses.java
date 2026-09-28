package com.renaser.os.onboarding.infrastructure.adapter.in.rest.caja;

import com.renaser.os.onboarding.application.ports.in.caja.ConsultarCajasUseCase.PaginaDeCajas;
import com.renaser.os.onboarding.application.ports.in.caja.DetalleDeCaja;
import com.renaser.os.onboarding.application.ports.in.caja.MiCajaUseCase.MiCaja;
import com.renaser.os.onboarding.application.ports.in.caja.ResumenDeCaja;
import com.renaser.os.onboarding.domain.model.caja.ContenidoDeCaja;
import com.renaser.os.onboarding.domain.model.caja.DatosDelEnvio;
import com.renaser.os.onboarding.domain.model.caja.DestinoAlternativo;
import com.renaser.os.onboarding.domain.model.caja.EstadoCaja;
import com.renaser.os.onboarding.domain.model.caja.FaltaParaEnviar;
import com.renaser.os.onboarding.domain.model.caja.FichaDeEnvio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Las respuestas de la Caja Renaser (D-219, contrato en docs/specs/CAJA_RENASER.md §9). Records de la API a
 * mano, campo por campo: un campo nuevo del dominio no se filtra solo al cliente (regla 04).
 */
final class CajaResponses {

    private CajaResponses() {
    }

    record PaginaResponse(List<ItemResponse> items, int total, Map<String, Integer> conteos) {

        static PaginaResponse from(PaginaDeCajas pagina) {
            Map<String, Integer> conteos = new LinkedHashMap<>();
            pagina.conteos().forEach((estado, n) -> conteos.put(estado.name(), n));
            return new PaginaResponse(pagina.items().stream().map(ItemResponse::from).toList(), pagina.total(),
                    conteos);
        }
    }

    record ItemResponse(UUID aprendizId, String nombre, String grupo, int diaPrograma, EstadoCaja estado, int envio,
                        Instant actualizadoEn, BigDecimal cumplimientoFase1) {

        static ItemResponse from(ResumenDeCaja r) {
            return new ItemResponse(r.aprendizId().value(), r.nombre(), r.grupo(), r.diaPrograma(), r.estado(),
                    r.envio(), r.actualizadoEn(), r.cumplimientoFase1());
        }
    }

    record DetalleResponse(UUID aprendizId, String nombre, EstadoCaja estado, int envio, BigDecimal cumplimientoFase1,
                           DestinoResponse destino, List<ElementoResponse> contenido, String fotoArmadaUrl,
                           String comprobanteUrl, EnvioResponse envioDatos, List<HistorialResponse> historial,
                           List<FaltaParaEnviar> faltaParaEnviar) {

        static DetalleResponse from(DetalleDeCaja d) {
            return new DetalleResponse(d.aprendizId().value(), d.nombre(), d.estado(), d.envio(), d.cumplimientoFase1(),
                    DestinoResponse.from(d.destino()),
                    d.contenido().stream().map(e -> new ElementoResponse(e.valor(), e.etiqueta(), e.marcado())).toList(),
                    d.fotoArmadaUrl(), d.comprobanteUrl(), EnvioResponse.from(d.envioDatos()),
                    d.historial().stream().map(h -> new HistorialResponse(h.envio(), h.estado(), h.en(), h.porNombre()))
                            .toList(),
                    d.faltaParaEnviar());
        }
    }

    record DestinoResponse(String nombre, String celular, String pais, String ciudad, String distrito, String provincia,
                           String direccion, String referencias, String dni, String quienRecibe, String otraDireccion,
                           String otroCelular) {

        static DestinoResponse from(FichaDeEnvio f) {
            DestinoAlternativo d = f.destino();
            return new DestinoResponse(f.nombre(), f.celular(), f.pais(), f.ciudad(), f.distrito(), d.provincia(),
                    f.direccion(), d.referencias(), f.dni(), d.quienRecibe(), d.otraDireccion(), d.otroCelular());
        }
    }

    record ElementoResponse(String valor, String etiqueta, boolean marcado) {
    }

    record HistorialResponse(int envio, EstadoCaja estado, Instant en, String porNombre) {
    }

    record EnvioResponse(String medio, String courier, String codigo, BigDecimal costo, String rastreoUrl) {

        static EnvioResponse from(DatosDelEnvio datos) {
            return datos == null ? null : new EnvioResponse(datos.medio(), datos.courier(), datos.codigo(),
                    datos.costo(), datos.rastreoUrl().orElse(null));
        }
    }

    record ContenidoResponse(List<ElementoDeLista> elementos) {

        static ContenidoResponse from(ContenidoDeCaja contenido) {
            return new ContenidoResponse(contenido.elementos().stream()
                    .map(e -> new ElementoDeLista(e.valor(), e.etiqueta())).toList());
        }
    }

    record ElementoDeLista(String valor, String etiqueta) {
    }

    /** Lo que ve el aprendiz: sin costo ni datos de la ficha que no puede cambiar. */
    record MiCajaResponse(EstadoCaja estado, List<PasoResponse> pasos, MiEnvioResponse envioDatos,
                          boolean puedeConfirmar, boolean puedeCambiarDestino, MiDestino destino) {

        static MiCajaResponse from(MiCaja caja) {
            DatosDelEnvio envio = caja.envioDatos();
            return new MiCajaResponse(caja.estado(),
                    caja.pasos().stream().map(p -> new PasoResponse(p.estado(), p.en())).toList(),
                    envio == null ? null : new MiEnvioResponse(envio.medio(), envio.courier(), envio.codigo(),
                            envio.rastreoUrl().orElse(null)),
                    caja.puedeConfirmar(), caja.puedeCambiarDestino(), MiDestino.from(caja.destino()));
        }
    }

    record PasoResponse(EstadoCaja estado, Instant en) {
    }

    record MiEnvioResponse(String medio, String courier, String codigo, String rastreoUrl) {
    }

    /** El destino alternativo: la respuesta de GET /me/caja y el cuerpo de PUT /me/caja/destino. */
    record MiDestino(String otraDireccion, String otroCelular, String quienRecibe, String referencias,
                     String provincia) {

        static MiDestino from(DestinoAlternativo d) {
            return new MiDestino(d.otraDireccion(), d.otroCelular(), d.quienRecibe(), d.referencias(), d.provincia());
        }
    }

    record EstadoResponse(EstadoCaja estado) {
    }

    record UrlDeSubidaResponse(String url, String ruta) {
    }

    /** El 409 de «enviar» cuando falta algo: el mensaje de siempre, más QUÉ falta. */
    record CajaIncompletaResponse(String message, List<FaltaParaEnviar> faltan, Instant timestamp) {
    }
}
