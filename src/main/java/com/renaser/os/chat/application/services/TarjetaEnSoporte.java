package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase.AvisoDeLaPieza;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase.EntregaDelPrograma;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase.PiezaDelPrograma;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.mensaje.LoadMensajePort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.mensaje.ContenidoDelPrograma;
import com.renaser.os.chat.domain.model.semaforo.TarjetaDelSemaforo;
import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.MensajeDelChatFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * La tarjeta del día de UN aprendiz en su chat de soporte (D-223): la imagen del color y, después, la línea
 * con el porcentaje seguida (D-281) de un mensaje personalizado del catálogo del semáforo. Las dos firmadas por el
 * programa y a nombre del aprendiz, como la bienvenida (D-199).
 *
 * <p><b>Push solo al aprendiz.</b> El texto avisa únicamente a quien se refiere; la imagen no avisa (va con
 * el texto). Sin esto, cada Admin y Alquimista del soporte recibiría un push por cada aprendiz, todas las
 * noches. Lo ven igual al abrir el chat.
 *
 * <p><b>Si el texto ya salió, la de hoy está hecha</b> y no se mira nada más, ni el almacenamiento. Así el
 * reintento de las 23:55 no agrega una imagen DESPUÉS del texto cuando a las 23:50 S3 falló y salió solo la
 * línea.
 *
 * <p><b>El mensaje personalizado es un adorno</b> (D-281): se pide recién cuando se sabe que el texto va a salir (no
 * se elige ni se guarda nada para una tarjeta que ya está) y, si no hay mensaje o elegirlo falla, sale el texto de
 * siempre. El chat nunca falla ni se atrasa por el mensaje.
 */
@Component
class TarjetaEnSoporte {

    private static final Logger log = LoggerFactory.getLogger(TarjetaEnSoporte.class);

    private final LoadConversacionPort conversaciones;
    private final LoadMensajePort mensajes;
    private final TarjetasDelSemaforoPublicadas tarjetas;
    private final EnviarMensajeDelProgramaUseCase delPrograma;
    private final MensajeDelChatFinder mensajesPersonalizados;

    TarjetaEnSoporte(LoadConversacionPort conversaciones, LoadMensajePort mensajes,
                     TarjetasDelSemaforoPublicadas tarjetas, EnviarMensajeDelProgramaUseCase delPrograma,
                     MensajeDelChatFinder mensajesPersonalizados) {
        this.conversaciones = conversaciones;
        this.mensajes = mensajes;
        this.tarjetas = tarjetas;
        this.delPrograma = delPrograma;
        this.mensajesPersonalizados = mensajesPersonalizados;
    }

    /** @return si salió algo ahora ({@code false}: ya estaba, o el aprendiz no tiene soporte) */
    boolean entregar(TarjetaDelSemaforo tarjeta) {
        Optional<Conversacion> soporte = conversaciones.porClaveDirecta(Conversacion.claveSoporteDe(tarjeta.aprendiz()));
        if (soporte.isEmpty()) {
            log.debug("[chat.semaforo] {} no tiene chat de soporte: sin tarjeta", tarjeta.aprendiz());
            return false;
        }
        if (mensajes.porId(tarjeta.idDelTexto()).isPresent()) {
            return false;
        }
        return delPrograma.enviarUnaVez(new EntregaDelPrograma(soporte.get().id(), tarjeta.aprendiz(), piezas(tarjeta))) > 0;
    }

    private List<PiezaDelPrograma> piezas(TarjetaDelSemaforo tarjeta) {
        List<PiezaDelPrograma> piezas = new ArrayList<>();
        tarjetas.imagenDe(tarjeta.color()).ifPresent(imagen ->
                piezas.add(new PiezaDelPrograma(tarjeta.idDeLaImagen(), imagen, AvisoDeLaPieza.SIN_AVISO)));
        piezas.add(new PiezaDelPrograma(tarjeta.idDelTexto(), ContenidoDelPrograma.texto(textoDe(tarjeta)),
                AvisoDeLaPieza.SOLO_A_QUIEN_SE_REFIERE));
        return piezas;
    }

    /**
     * «Hoy llevas N % de tus hábitos.» y, debajo, el mensaje del catálogo para el color del día de la tarjeta. Sin
     * mensaje, o si pedirlo falla por lo que sea, el texto de siempre (se registra y sigue).
     */
    private String textoDe(TarjetaDelSemaforo tarjeta) {
        try {
            ColorSemaforo color = ColorSemaforo.valueOf(tarjeta.color().name());
            return mensajesPersonalizados.delDia(tarjeta.aprendiz(), tarjeta.fecha(), color)
                    .map(tarjeta::textoCon).orElseGet(tarjeta::texto);
        } catch (RuntimeException e) {
            log.warn("[chat.semaforo] el mensaje personalizado de {} del {} no se pudo armar; sale el texto de siempre. causa={}",
                    tarjeta.aprendiz(), tarjeta.fecha(), e.toString());
            return tarjeta.texto();
        }
    }
}
