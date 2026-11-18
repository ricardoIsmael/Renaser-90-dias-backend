package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.services.PersonasDelPrograma.Mirada;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FichaDeMentorFinder.FichaDeMentor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Los datos de los tres documentos, con sus puertas (spec 056, FR-041): el de un aprendiz lo piden el liderazgo de su formación y el mentor que lo
 * acompaña hoy ({@link AccesoAlDocumento}); el de un mentor y el del mes, solo el liderazgo ({@link AccesoDeLiderazgo}), y el mentor tiene que ser
 * de una formación que quien pide ve: de otra es un 404. Primero se autoriza y después se lee: un 403 o un 404 no lee ni arma nada.
 */
@Component
class FuenteDeDocumentosDelLiderazgo implements FuenteDeDocumentos {

    private final AccesoAlDocumento acceso;
    private final AccesoDeLiderazgo liderazgo;
    private final ArmadoDelDocumentoDelAprendiz aprendiz;
    private final ArmadoDelDocumentoDelMentor mentor;
    private final ArmadoDelDocumentoDelMes mes;
    private final Clock clock;

    FuenteDeDocumentosDelLiderazgo(AccesoAlDocumento acceso, AccesoDeLiderazgo liderazgo, ArmadoDelDocumentoDelAprendiz aprendiz,
                                   ArmadoDelDocumentoDelMentor mentor, ArmadoDelDocumentoDelMes mes, Clock clock) {
        this.acceso = acceso;
        this.liderazgo = liderazgo;
        this.aprendiz = aprendiz;
        this.mentor = mentor;
        this.mes = mes;
        this.clock = clock;
    }

    @Override
    public DocumentoArmado delAprendiz(UserId actorId, UUID personaId, LocalDate semanaHasta) {
        Instant ahora = clock.now();
        Mirada mirada = acceso.paraElAprendiz(actorId, personaId, ahora);
        return new DocumentoArmado(mirada.personaId(), aprendiz.armar(mirada, semanaHasta, ahora));
    }

    @Override
    public DocumentoArmado delMentor(UserId actorId, UUID mentorId, LocalDate semanaHasta) {
        liderazgo.requireLiderazgoActivo(actorId);
        FichaDeMentor ficha = liderazgo.mentoresDelAlcance(actorId).stream().filter(m -> m.id().value().equals(mentorId)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("Mentor no encontrado: " + mentorId));
        return new DocumentoArmado(ficha.id(), mentor.armar(actorId, ficha, semanaHasta, clock.now()));
    }

    @Override
    public DocumentoArmado delMes(UserId actorId, String mesPedido) {
        return new DocumentoArmado(actorId, mes.armar(actorId, mesPedido));
    }
}
