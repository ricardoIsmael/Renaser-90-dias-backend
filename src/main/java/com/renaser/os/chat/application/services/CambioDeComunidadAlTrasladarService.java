package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.conversacion.CambiarDeComunidadAlTrasladarUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.IncorporarUsuarioAlSoporteUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.UnirseAConversacionGlobalUseCase;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.participante.QuitarParticipantePort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.TipoConversacion;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lo que la Tribu hace cuando un aprendiz cambia de formación (spec 024, FR-026, historia 9): sale de la comunidad general y del soporte de la
 * anterior —se le quita la fila de participante; los mensajes no se tocan, quedan en la formación donde se escribieron— y entra a la comunidad
 * general de la nueva y a su soporte (que nace con la clave de la nueva formación).
 *
 * <p>Idempotente: el evento llega al menos una vez. Si la persona ya no figura en lo de la formación anterior no hay nada que quitar, y unirse
 * y crear el soporte ya lo son.
 */
@Service
class CambioDeComunidadAlTrasladarService implements CambiarDeComunidadAlTrasladarUseCase {

    private static final Logger log = LoggerFactory.getLogger(CambioDeComunidadAlTrasladarService.class);
    private static final Set<TipoConversacion> QUE_SE_DEJAN = EnumSet.of(TipoConversacion.GLOBAL, TipoConversacion.SOPORTE);

    private final LoadConversacionPort conversaciones;
    private final QuitarParticipantePort quitarParticipante;
    private final UnirseAConversacionGlobalUseCase unirseALaComunidad;
    private final IncorporarUsuarioAlSoporteUseCase incorporarAlSoporte;

    CambioDeComunidadAlTrasladarService(LoadConversacionPort conversaciones, QuitarParticipantePort quitarParticipante,
                                        UnirseAConversacionGlobalUseCase unirseALaComunidad,
                                        IncorporarUsuarioAlSoporteUseCase incorporarAlSoporte) {
        this.conversaciones = conversaciones;
        this.quitarParticipante = quitarParticipante;
        this.unirseALaComunidad = unirseALaComunidad;
        this.incorporarAlSoporte = incorporarAlSoporte;
    }

    @Override
    public void alTrasladar(UserId aprendiz, Formacion desde, Formacion hacia) {
        int dejadas = 0;
        for (Conversacion conversacion : conversaciones.misConversaciones(aprendiz)) {
            if (conversacion.formacion() == desde && QUE_SE_DEJAN.contains(conversacion.tipo())) {
                quitarParticipante.quitar(conversacion.id(), aprendiz);
                dejadas++;
            }
        }
        unirseALaComunidad.unirse(aprendiz);
        incorporarAlSoporte.incorporar(aprendiz);
        log.info("[chat.traslado] {} pasó de {} a {}: dejó {} conversación(es) y entró a las de la nueva formación", aprendiz, desde, hacia,
                dejadas);
    }
}
