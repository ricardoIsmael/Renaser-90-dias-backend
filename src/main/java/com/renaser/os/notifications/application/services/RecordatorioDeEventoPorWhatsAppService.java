package com.renaser.os.notifications.application.services;

import com.renaser.os.notifications.application.ports.in.evento.AvisoDeMensajeDelEvento;
import com.renaser.os.notifications.application.ports.in.whatsapp.RecordarEventoPorWhatsAppUseCase;
import com.renaser.os.notifications.application.ports.out.preferencia.LoadPreferenciasPort;
import com.renaser.os.notifications.application.ports.out.whatsapp.EnvioDeWhatsAppPort;
import com.renaser.os.notifications.application.ports.out.whatsapp.EntregasDeWhatsAppPort;
import com.renaser.os.notifications.domain.model.evento.AvisoDeEvento;
import com.renaser.os.notifications.domain.model.evento.VeredictoDelMensaje;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.notifications.domain.model.preferencia.PreferenciaNotificacion;
import com.renaser.os.notifications.domain.model.whatsapp.CausaDeFalla;
import com.renaser.os.notifications.domain.model.whatsapp.EntregaDeWhatsApp;
import com.renaser.os.notifications.domain.model.whatsapp.NumeroDeWhatsApp;
import com.renaser.os.notifications.domain.model.whatsapp.PalabrasDeBaja;
import com.renaser.os.notifications.domain.model.whatsapp.ResultadoDelEnvio;
import com.renaser.os.shared.application.Interruptores;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FormacionesFinder;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.WhatsappDeLaFichaFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * El recordatorio de un evento por WhatsApp (spec 041, FR-010 a FR-025). {@code calendar} decide QUE y CUANDO (los cuatro avisos) y A QUIEN le toca el
 * evento; aca se decide COMO: a quien dijo «Voy» (valor de configuracion), con un rol que recibe, sin haberse dado de baja y con un WhatsApp en la ficha.
 *
 * <p><b>Sin duplicados (FR-020):</b> la entrega se APARTA antes de llamar al proveedor, con clave unica por recordatorio y persona, y la llamada va fuera de
 * toda transaccion de la base. Una reentrega del *outbox*, dos pasadas o dos instancias del servidor no llaman al proveedor dos veces. Lo que quedo «enviando»
 * y no se supo como termino NUNCA se reenvia solo: el WhatsApp es un canal extra (ya llegaron el aviso de la app y el chat) y un reenvio repetiria un mensaje de pago.
 *
 * <p><b>Nunca lanza por una falla del proveedor (FR-021):</b> el *outbox* reintentaria y mandaria dos veces. Solo lanza si no pudo ni apartar la entrega, y
 * entonces nada salio. Sin la configuracion del proveedor, o con {@code eventos.whatsapp} apagado, no hace nada (y no deja filas): el chat sigue funcionando.
 */
@Service
public class RecordatorioDeEventoPorWhatsAppService implements RecordarEventoPorWhatsAppUseCase {

    static final String CLAVE_DEL_INTERRUPTOR = "eventos.whatsapp";
    static final String MOTIVO_SIN_WHATSAPP = "Sin WhatsApp";
    static final String MOTIVO_TOPE = "Tope mensual";
    private static final Logger log = LoggerFactory.getLogger(RecordatorioDeEventoPorWhatsAppService.class);

    private final DestinatarioDelMensajeDeEvento destinatarios;
    private final ReglasDelMensajeDeEvento reglas;
    private final LoadPreferenciasPort preferencias;
    private final EntregasDeWhatsAppPort entregas;
    private final EnvioDeWhatsAppPort envio;
    private final WhatsappDeLaFichaFinder fichas;
    private final FormacionesFinder formaciones;
    private final AvisoDeFallaDeWhatsApp avisoDeFalla;
    private final Interruptores interruptores;
    private final IdGenerator ids;
    private final Clock clock;

    RecordatorioDeEventoPorWhatsAppService(DestinatarioDelMensajeDeEvento destinatarios, ReglasDelMensajeDeEvento reglas,
                                           LoadPreferenciasPort preferencias, EntregasDeWhatsAppPort entregas, EnvioDeWhatsAppPort envio,
                                           WhatsappDeLaFichaFinder fichas, FormacionesFinder formaciones,
                                           AvisoDeFallaDeWhatsApp avisoDeFalla, Interruptores interruptores, IdGenerator ids, Clock clock) {
        this.destinatarios = destinatarios;
        this.reglas = reglas;
        this.preferencias = preferencias;
        this.entregas = entregas;
        this.envio = envio;
        this.fichas = fichas;
        this.formaciones = formaciones;
        this.avisoDeFalla = avisoDeFalla;
        this.interruptores = interruptores;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public void recordar(AvisoDeMensajeDelEvento aviso) {
        if (!interruptores.prendida(CLAVE_DEL_INTERRUPTOR) || !envio.configurado()) {
            return;
        }
        Optional<UserSummary> persona = destinatarios.siLeCorresponde(aviso);
        if (persona.isEmpty() || !leLlegaPorWhatsApp(aviso.destinatarioId())) {
            return;
        }
        Instant ahora = clock.now();
        UUID clave = new AvisoDeEvento(aviso.recordatorioId(), aviso.eventoId(), aviso.titulo(), aviso.inicio(),
                ZoneId.of(aviso.zonaHoraria()), false).claveDeduplicacion();
        Optional<String> motivoDeOmision = motivoParaOmitir(aviso, ahora);
        if (motivoDeOmision.isPresent()) {
            omitir(aviso, clave, motivoDeOmision.get(), ahora);
            return;
        }
        Optional<NumeroDeWhatsApp> numero = numeroDe(aviso.destinatarioId());
        if (numero.isEmpty()) {
            omitir(aviso, clave, MOTIVO_SIN_WHATSAPP, ahora);
            return;
        }
        UUID entregaId = ids.newId();
        EntregaDeWhatsApp apartada = EntregaDeWhatsApp.apartar(entregaId, clave, aviso.eventoId(), aviso.destinatarioId(),
                aviso.avisoMin(), ahora);
        if (!entregas.apartar(apartada)) {
            log.debug("[notifications.evento-whatsapp] recordatorio {} ya estaba apartado: no se llama al proveedor", aviso.recordatorioId());
            return;
        }
        ResultadoDelEnvio resultado = enviar(aviso, persona.get(), numero.get(), entregaId, ahora);
        entregas.guardarResultado(apartada.conResultado(resultado, clock.now()));
        avisoDeFalla.avisar(resultado.causa());
    }

    private boolean leLlegaPorWhatsApp(UserId persona) {
        return preferencias.habilitadaPara(persona, TipoNotificacion.RECORDATORIO_EVENTO_WHATSAPP)
                .orElse(PreferenciaNotificacion.DEFAULT_HABILITADA);
    }

    /** Llego tarde, el evento ya empezo, o se acabo el tope mensual (si hay uno). */
    private Optional<String> motivoParaOmitir(AvisoDeMensajeDelEvento aviso, Instant ahora) {
        VeredictoDelMensaje veredicto = VeredictoDelMensaje.de(aviso.inicio(), aviso.avisoMin(), ahora, VeredictoDelMensaje.ATRASO_MAXIMO);
        if (!veredicto.envia()) {
            return Optional.of(veredicto.motivo());
        }
        return reglas.topeMensual().isPresent() && topeAlcanzado(reglas.topeMensual().getAsLong(), ahora)
                ? Optional.of(MOTIVO_TOPE) : Optional.empty();
    }

    private boolean topeAlcanzado(long tope, Instant ahora) {
        LocalDate hoy = ahora.atZone(AvisoDeFallaDeWhatsApp.ZONA_DEL_PROGRAMA).toLocalDate();
        Instant desde = hoy.withDayOfMonth(1).atStartOfDay(AvisoDeFallaDeWhatsApp.ZONA_DEL_PROGRAMA).toInstant();
        Instant hasta = hoy.withDayOfMonth(1).plusMonths(1).atStartOfDay(AvisoDeFallaDeWhatsApp.ZONA_DEL_PROGRAMA).toInstant();
        boolean alcanzado = entregas.contarQueSalieron(desde, hasta) >= tope;
        if (alcanzado) {
            avisoDeFalla.avisar(CausaDeFalla.TOPE);
        }
        return alcanzado;
    }

    private void omitir(AvisoDeMensajeDelEvento aviso, UUID clave, String motivo, Instant ahora) {
        entregas.apartar(EntregaDeWhatsApp.omitida(ids.newId(), clave, aviso.eventoId(), aviso.destinatarioId(), aviso.avisoMin(),
                motivo, ahora));
        log.info("[notifications.evento-whatsapp] recordatorio {} omitido: {}", aviso.recordatorioId(), motivo);
    }

    private Optional<NumeroDeWhatsApp> numeroDe(UserId persona) {
        return Optional.ofNullable(fichas.de(Set.of(persona)).get(persona)).flatMap(NumeroDeWhatsApp::desdeLaFicha);
    }

    /** La llamada al proveedor, fuera de toda transaccion. Lo que salga mal queda anotado; nunca sube. */
    private ResultadoDelEnvio enviar(AvisoDeMensajeDelEvento aviso, UserSummary persona, NumeroDeWhatsApp numero, UUID entregaId,
                                     Instant ahora) {
        try {
            Formacion formacion = ReglasDelMensajeDeEvento.formacionDe(aviso.formacion());
            List<String> huecos = aviso.mensaje().huecosDeWhatsApp(NombreDePila.de(persona.fullName()),
                    formaciones.de(formacion).nombre(), ahora);
            ResultadoDelEnvio resultado = envio.enviarRecordatorio(numero.valor(), entregaId, huecos,
                    PalabrasDeBaja.cargaDelBoton(entregaId));
            log.info("[notifications.evento-whatsapp] recordatorio {} a {}: {}", aviso.recordatorioId(), numero.enmascarado(),
                    resultado.estado());
            return resultado;
        } catch (RuntimeException inesperada) {
            log.warn("[notifications.evento-whatsapp] recordatorio {} a {}: no se supo como termino ({})", aviso.recordatorioId(),
                    numero.enmascarado(), inesperada.getClass().getSimpleName());
            return ResultadoDelEnvio.noSeSupo("Error al enviar: " + inesperada.getClass().getSimpleName());
        }
    }
}
