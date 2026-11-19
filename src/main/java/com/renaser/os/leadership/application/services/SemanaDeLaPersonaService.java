package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDeLaPersonaUseCase;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDelAprendizDelMentorUseCase.SemanaDelAprendizParaElMentor;
import com.renaser.os.leadership.application.ports.out.GuardarConsultaDeFichaPort;
import com.renaser.os.leadership.application.services.PersonasDelPrograma.Mirada;
import com.renaser.os.leadership.domain.model.consulta.ConsultaDeFicha;
import com.renaser.os.leadership.domain.model.consulta.ConsultaDeFicha.Contenido;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * La semana de una persona de sábado a viernes para la ficha del líder (spec 056). Autoriza primero ({@link AccesoDeLiderazgo}) y lee
 * después —el camino que termina en 403 o en 404 no lee nada ni deja registro—, y arma con {@link SemanaDeSabadoAViernes}, el mismo
 * armado de la ficha del mentor (spec 055).
 *
 * <p>Deja la constancia de consulta (spec 005, FR-016) en la misma transacción que la lectura: abrir la ficha de una persona es «mirarla»
 * venga por la ruta de siempre o por ésta, y esa constancia alimenta el resumen que el mentor recibe de su líder. La persona puede no
 * tener grupo (spec 021) o ser un mentor que lleva su programa (D-318): se registra con el grupo y el mentor en NULL.
 */
@Service
public class SemanaDeLaPersonaService implements ConsultarSemanaDeLaPersonaUseCase {

    private final AccesoDeLiderazgo acceso;
    private final PersonasDelPrograma programa;
    private final SemanaDeSabadoAViernes semana;
    private final GuardarConsultaDeFichaPort consultas;
    private final IdGenerator idGenerator;
    private final Clock clock;

    SemanaDeLaPersonaService(AccesoDeLiderazgo acceso, PersonasDelPrograma programa, SemanaDeSabadoAViernes semana,
                             GuardarConsultaDeFichaPort consultas, IdGenerator idGenerator, Clock clock) {
        this.acceso = acceso;
        this.programa = programa;
        this.semana = semana;
        this.consultas = consultas;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public SemanaDelAprendizParaElMentor semanaDe(ConsultaSemanaDeLaPersona consulta) {
        UserRole rol = acceso.requireLiderazgoActivo(consulta.actorId());
        Instant ahora = clock.now();
        Mirada mirada = programa.mirarYDecirSiNoSigueSuPrograma(consulta.actorId(), UserId.of(consulta.personaId()), ahora);
        SemanaDelAprendizParaElMentor lectura = semana.armar(mirada, consulta.semanaHasta(), ahora);
        registrar(consulta.actorId(), rol, mirada, lectura);
        return lectura;
    }

    private void registrar(UserId actorId, UserRole rol, Mirada mirada, SemanaDelAprendizParaElMentor lectura) {
        Contenido contenido = new Contenido(actorId, rol, mirada.personaId(), mirada.grupoId(), mirada.mentorId(),
                lectura.semana().desde());
        consultas.insertar(ConsultaDeFicha.registrar(idGenerator.newId(), contenido, clock));
    }
}
