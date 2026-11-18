package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.AvisarAlMentorUseCase;
import com.renaser.os.leadership.application.services.EntregaDeAvisosAlMentor.Entrega;
import com.renaser.os.leadership.application.services.PersonasDelPrograma.Mirada;
import com.renaser.os.leadership.domain.model.observacion.LineaDeLaFicha;
import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.RechazoConCodigoException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * «Avisar a su mentor» (spec 056, FR-019): el líder le escribe al mentor vigente de una persona desde la ficha de esa persona.
 *
 * <p>El mensaje va al chat de dos del líder con el mentor y lleva el enlace a la ficha para que el mentor la abra; además sale el aviso del teléfono. Cómo se
 * entrega y por qué es idempotente está en {@link EntregaDeAvisosAlMentor}: es la observación de la spec 005 (R-7) sobre esa persona, con el envío hecho por
 * el servidor.
 */
@Service
public class AvisoAlMentorService implements AvisarAlMentorUseCase {

    static final String SIN_MENTOR = "SIN_MENTOR";

    private final AccesoDeLiderazgo acceso;
    private final PersonasDelPrograma personas;
    private final EntregaDeAvisosAlMentor entrega;
    private final Clock clock;

    AvisoAlMentorService(AccesoDeLiderazgo acceso, PersonasDelPrograma personas, EntregaDeAvisosAlMentor entrega, Clock clock) {
        this.acceso = acceso;
        this.personas = personas;
        this.entrega = entrega;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void avisar(AvisarAlMentorCommand comando) {
        acceso.requireLiderazgoActivo(comando.actorId());
        exigirTextoYClave(comando);
        entrega.exigirQueSeaDeSusFormaciones(comando.actorId(), comando.personaId());
        Mirada mirada = personas.mirar(comando.actorId(), comando.personaId(), clock.now());
        if (entrega.yaEntregada(comando.actorId(), comando.personaId(), comando.claveOperacion()).isPresent()) {
            return;
        }
        UserId mentor = mirada.mentorId();
        if (mentor == null) {
            throw RechazoConCodigoException.conflicto(SIN_MENTOR, "Esta persona no tiene un mentor a quien avisar.");
        }
        String texto = comando.texto().trim();
        entrega.entregar(new Entrega(comando.actorId(), mentor, comando.personaId(), texto, mensajeConEnlace(texto, mirada), comando.claveOperacion()));
    }

    /**
     * El texto del líder y, como última línea y separada por una línea en blanco, la ficha de la persona ({@link LineaDeLaFicha}): la ruta con que el mentor abre a
     * SU aprendiz (la misma de los avisos de acompañamiento), en el formato que la app reconoce para volverla tocable.
     */
    private static String mensajeConEnlace(String texto, Mirada mirada) {
        return LineaDeLaFicha.alFinalDe(texto, mirada.miembro().perfil().fullName(), mirada.grupoId(), mirada.personaId().value());
    }

    private static void exigirTextoYClave(AvisarAlMentorCommand comando) {
        if (comando.texto() == null || comando.texto().isBlank()) {
            throw new IllegalArgumentException("Escribe el aviso: no puede quedar vacío");
        }
        if (comando.texto().trim().length() > ObservacionDeMentor.LARGO_MAXIMO) {
            throw new IllegalArgumentException("El aviso no puede pasar de " + ObservacionDeMentor.LARGO_MAXIMO + " caracteres");
        }
        if (comando.claveOperacion() == null || comando.claveOperacion().isBlank() || comando.claveOperacion().trim().length() > 100) {
            throw new IllegalArgumentException("La clave de operación es obligatoria y de hasta 100 caracteres");
        }
    }
}
