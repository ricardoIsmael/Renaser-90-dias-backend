package com.renaser.os.chat.application.services;

import com.renaser.os.chat.api.RecordatorioDeEventoEnElChat;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase.AvisoDeLaPieza;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase.EntregaDelPrograma;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase.PiezaDelPrograma;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.evento.RecordatorioDeEventoEnSoporte;
import com.renaser.os.chat.domain.model.mensaje.ContenidoDelPrograma;
import com.renaser.os.shared.application.Interruptores;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * El recordatorio de un evento en el chat de soporte de la persona (spec 041, FR-001 a FR-006). Reusa el mensaje del programa de siempre
 * ({@link EnviarMensajeDelProgramaUseCase#enviarUnaVez}): lo firma el nombre de la formacion de ESA conversacion, y el soporte es el de la
 * formacion vigente de la persona ({@link SoporteVigenteDelAprendiz}), nunca el de la anterior.
 *
 * <p><b>Sin segundo push</b> ({@link AvisoDeLaPieza#SIN_AVISO}): el aviso de la app ya fue el push. <b>Sin chat, no se crea uno</b>: quien no
 * tiene soporte (el personal) recibe el aviso de la app y, si aplica, el WhatsApp, pero no este mensaje.
 */
@Service
public class RecordatorioDeEventoEnSoporteService implements RecordatorioDeEventoEnElChat {

    static final String CLAVE_DEL_CHAT_DE_SOPORTE = "chat.soporte";
    private static final Logger log = LoggerFactory.getLogger(RecordatorioDeEventoEnSoporteService.class);

    private final SoporteVigenteDelAprendiz soportes;
    private final EnviarMensajeDelProgramaUseCase delPrograma;
    private final Interruptores interruptores;

    RecordatorioDeEventoEnSoporteService(SoporteVigenteDelAprendiz soportes, EnviarMensajeDelProgramaUseCase delPrograma,
                                         Interruptores interruptores) {
        this.soportes = soportes;
        this.delPrograma = delPrograma;
        this.interruptores = interruptores;
    }

    @Override
    public Resultado escribir(UserId persona, long recordatorioId, String texto) {
        if (!interruptores.prendida(CLAVE_DEL_CHAT_DE_SOPORTE)) {
            log.info("[chat.evento] el chat de soporte esta apagado: el recordatorio {} no se escribe", recordatorioId);
            return Resultado.CHAT_APAGADO;
        }
        Optional<Conversacion> soporte = soportes.de(persona);
        if (soporte.isEmpty()) {
            log.debug("[chat.evento] {} no tiene chat de soporte: el recordatorio {} no se escribe", persona, recordatorioId);
            return Resultado.SIN_CHAT_DE_SOPORTE;
        }
        RecordatorioDeEventoEnSoporte aviso = new RecordatorioDeEventoEnSoporte(recordatorioId, texto);
        PiezaDelPrograma pieza = new PiezaDelPrograma(aviso.idDelMensaje(), ContenidoDelPrograma.texto(aviso.texto()),
                AvisoDeLaPieza.SIN_AVISO);
        int salieron = delPrograma.enviarUnaVez(new EntregaDelPrograma(soporte.get().id(), persona, List.of(pieza)));
        return salieron > 0 ? Resultado.ESCRITO : Resultado.YA_ESTABA;
    }
}
