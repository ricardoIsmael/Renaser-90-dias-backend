package com.renaser.os.onboarding.application.services.caja;

import com.renaser.os.onboarding.application.ports.out.caja.FichaDeEnvioPort;
import com.renaser.os.onboarding.application.ports.out.caja.PasosDeCajaPort;
import com.renaser.os.onboarding.domain.model.caja.CajaRenaser;
import com.renaser.os.onboarding.domain.model.caja.CumplimientoFaseUno;
import com.renaser.os.onboarding.domain.model.caja.DiaDeHabitos;
import com.renaser.os.onboarding.domain.model.caja.FichaDeEnvio;
import com.renaser.os.onboarding.domain.model.caja.PasoDeCaja;
import com.renaser.os.onboarding.domain.model.caja.SituacionDelAprendiz;
import com.renaser.os.points.api.ConteoDiarioHabitosFinder;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ProgramasActivadosFinder;
import com.renaser.os.users.api.ProgramasActivadosFinder.ProgramaActivado;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Arma las cajas ({@link CajaRenaser}) de uno o de muchos aprendices con lo que hace falta de otros módulos,
 * siempre EN LOTE: una consulta por fuente para toda la página, nunca una por persona (D-43).
 *
 * <ul>
 *   <li>el padrón y el calendario de cada uno: {@link ProgramasActivadosFinder} (la fecha del Día 1 ya
 *       corrida por los ajustes de día, y su zona: regla 02);</li>
 *   <li>rol y estado de la cuenta: {@link UserSummaryFinder};</li>
 *   <li>los hábitos de los días 1 a 7: {@link ConteoDiarioHabitosFinder} (el mismo conteo del semáforo),
 *       una consulta por fecha de inicio distinta — los que empezaron juntos van juntos;</li>
 *   <li>los pasos y la ficha de envío: los puertos propios.</li>
 * </ul>
 */
@Component
class CajasDelPadron {

    /** Barridos y listas paginan el padrón de a este tamaño (regla 02 §4). */
    static final int TAMANO_LOTE = 200;

    private final ProgramasActivadosFinder programas;
    private final UserSummaryFinder usuarios;
    private final PasosDeCajaPort pasos;
    private final FichaDeEnvioPort fichas;
    private final ConteoDiarioHabitosFinder conteos;
    private final Clock clock;

    CajasDelPadron(ProgramasActivadosFinder programas, UserSummaryFinder usuarios, PasosDeCajaPort pasos,
                   FichaDeEnvioPort fichas, ConteoDiarioHabitosFinder conteos, Clock clock) {
        this.programas = programas;
        this.usuarios = usuarios;
        this.pasos = pasos;
        this.fichas = fichas;
        this.conteos = conteos;
        this.clock = clock;
    }

    /** La caja de una persona con su ficha, o vacío si la cuenta no existe. */
    Optional<CajaConFicha> de(UserId personaId) {
        return usuarios.findById(personaId).map(persona -> armar(List.of(persona),
                programas.de(personaId).map(p -> Map.of(personaId, p)).orElse(Map.of()), clock.now()).get(0));
    }

    /** @throws NoSuchElementException si no es una cuenta de aprendiz (404) */
    CajaConFicha deAprendiz(UserId aprendizId) {
        return de(aprendizId).filter(c -> c.persona().role() == UserRole.TRAINEE)
                .orElseThrow(() -> new NoSuchElementException("No hay un aprendiz con ese id"));
    }

    /** Una página del padrón (programas activados, cualquier rol): solo los aprendices. */
    PaginaDelPadron pagina(int offset) {
        List<ProgramaActivado> pagina = programas.pagina(offset, TAMANO_LOTE);
        Map<UserId, ProgramaActivado> porPersona = pagina.stream()
                .collect(Collectors.toMap(ProgramaActivado::participanteId, p -> p, (a, b) -> a));
        List<UserSummary> aprendices = porPersona.isEmpty() ? List.of()
                : usuarios.findByIds(porPersona.keySet()).values().stream()
                        .filter(persona -> persona.role() == UserRole.TRAINEE).toList();
        List<CajaConFicha> cajas = aprendices.isEmpty() ? List.of() : armar(aprendices, porPersona, clock.now());
        return new PaginaDelPadron(cajas, pagina.size() < TAMANO_LOTE);
    }

    /** Todo el padrón, página por página. */
    List<CajaConFicha> todas() {
        List<CajaConFicha> todas = new ArrayList<>();
        for (int offset = 0; ; offset += TAMANO_LOTE) {
            PaginaDelPadron pagina = pagina(offset);
            todas.addAll(pagina.cajas());
            if (pagina.ultima()) {
                return todas;
            }
        }
    }

    private List<CajaConFicha> armar(List<UserSummary> personas, Map<UserId, ProgramaActivado> porPersona,
                                     Instant ahora) {
        List<UserId> ids = personas.stream().map(UserSummary::id).toList();
        Map<UserId, List<PasoDeCaja>> pasosDe = pasos.deAprendices(ids);
        Map<UserId, FichaDeEnvio> fichaDe = fichas.de(ids);
        Map<UserId, List<DiaDeHabitos>> habitosDe = habitosDeLaFaseUno(porPersona.values());
        return personas.stream().map(persona -> {
            FichaDeEnvio ficha = fichaDe.get(persona.id());
            SituacionDelAprendiz situacion = situacion(persona, porPersona.get(persona.id()), ficha,
                    habitosDe.getOrDefault(persona.id(), List.of()));
            CajaRenaser caja = CajaRenaser.de(persona.id(), situacion,
                    pasosDe.getOrDefault(persona.id(), List.of()), ahora);
            return new CajaConFicha(caja, persona, ficha);
        }).toList();
    }

    private static SituacionDelAprendiz situacion(UserSummary persona, ProgramaActivado programa, FichaDeEnvio ficha,
                                                  List<DiaDeHabitos> habitos) {
        boolean suspendido = !persona.status().allowsAccess();
        if (persona.role() != UserRole.TRAINEE || programa == null) {
            return SituacionDelAprendiz.noAplica(suspendido, ficha.esDelPeru());
        }
        return new SituacionDelAprendiz(true, suspendido, ficha.esDelPeru(), programa.zona(),
                programa.primeraFecha(), habitos);
    }

    private Map<UserId, List<DiaDeHabitos>> habitosDeLaFaseUno(Collection<ProgramaActivado> activados) {
        Map<LocalDate, List<UserId>> porInicio = activados.stream().collect(Collectors.groupingBy(
                ProgramaActivado::primeraFecha, Collectors.mapping(ProgramaActivado::participanteId, Collectors.toList())));
        Map<UserId, List<DiaDeHabitos>> habitos = new HashMap<>();
        porInicio.forEach((inicio, ids) -> conteos
                .porParticipanteEntre(ids, inicio, inicio.plusDays(CumplimientoFaseUno.DIAS_MEDIDOS - 1L))
                .forEach((id, dias) -> habitos.put(id, dias.stream()
                        .map(d -> new DiaDeHabitos(d.fecha(), d.programados(), d.cumplidos())).toList())));
        return habitos;
    }

    /** @param ultima si no hay más páginas después de esta */
    record PaginaDelPadron(List<CajaConFicha> cajas, boolean ultima) {
    }

    /** La caja de una persona con su cuenta y su ficha de envío. */
    record CajaConFicha(CajaRenaser caja, UserSummary persona, FichaDeEnvio ficha) {

        /** El nombre para mostrar: el de su cuenta. */
        String nombre() {
            return persona.fullName();
        }
    }
}
