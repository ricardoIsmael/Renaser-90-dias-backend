package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.diagnostico.BorradorDelDiagnosticoPort;
import com.renaser.os.onboarding.application.ports.out.diagnostico.ConsultarFormacionDeLaPersonaPort;
import com.renaser.os.onboarding.application.ports.out.diagnostico.ConsultarFormacionDeLaPersonaPort.FormacionDeLaPersona;
import com.renaser.os.onboarding.application.ports.out.estado.LoadEstadoOnboardingPort;
import com.renaser.os.onboarding.application.ports.out.mapa.EtapaOnboardingPort;
import com.renaser.os.onboarding.domain.model.diagnostico.Diagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.FichaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.ObjetivosDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.PreguntaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.RespuestaLegible;
import com.renaser.os.onboarding.domain.model.estado.EstadoOnboarding;
import com.renaser.os.onboarding.domain.model.respuesta.Respuesta;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lo que otros módulos y quien acompaña a la persona leen de su diagnóstico (spec 026), en palabras: la lectura completa, los objetivos que nacen de él y su
 * trato. No escribe nada.
 */
@Service
public class LecturaDelDiagnosticoService implements DiagnosticoDeLaPersonaFinder {

    private final LoadEstadoOnboardingPort estados;
    private final ConsultarActorPort actores;
    private final BorradorDelDiagnosticoPort borrador;
    private final EtapaOnboardingPort etapas;
    private final ConsultarFormacionDeLaPersonaPort formaciones;
    private final FichaVigenteDeLaPersona vigente;
    private final ConstanciaDeLecturasDelDiagnostico constancia;

    public LecturaDelDiagnosticoService(LoadEstadoOnboardingPort estados, ConsultarActorPort actores, BorradorDelDiagnosticoPort borrador,
                                        EtapaOnboardingPort etapas, ConsultarFormacionDeLaPersonaPort formaciones, FichaVigenteDeLaPersona vigente,
                                        ConstanciaDeLecturasDelDiagnostico constancia) {
        this.estados = estados;
        this.actores = actores;
        this.borrador = borrador;
        this.etapas = etapas;
        this.formaciones = formaciones;
        this.vigente = vigente;
        this.constancia = constancia;
    }

    @Override
    @Transactional(readOnly = true)
    public LecturaDelDiagnostico lecturaDe(UserId persona) {
        PersonaLeida quien = new PersonaLeida(persona.value().toString(), actores.deActor(persona).map(ConsultarActorPort.ActorOnboarding::nombre).orElse(null));
        Optional<FormacionDeLaPersona> formacion = formaciones.de(persona);
        FormacionLeida formacionLeida = formacion.map(f -> new FormacionLeida(f.formacion().codigo(), f.nombre())).orElse(null);
        Optional<EstadoOnboarding> estado = estados.deUsuario(persona).filter(EstadoOnboarding::conDiagnostico);
        if (estado.isEmpty()) {
            return LecturaDelDiagnostico.sinDiagnostico(quien, "ESTANDAR", "NO_APLICA", null, formacionLeida);
        }
        Integer version = estado.get().fichaVersion();
        if (version == null) {
            return LecturaDelDiagnostico.sinDiagnostico(quien, "CON_DIAGNOSTICO", "PENDIENTE", null, formacionLeida);
        }
        FichaDelDiagnostico ficha = vigente.fichaDe(persona, version);
        Map<Integer, Respuesta> guardadas = vigente.respuestasPorPregunta(persona);
        Set<Integer> visibles = ficha.visibles(guardadas);
        Set<Integer> retiradas = borrador.retiradas().stream().map(PreguntaDelDiagnostico::id).collect(Collectors.toSet());
        List<SeccionLeida> secciones = ficha.secciones().stream().filter(s -> !s.esPortada())
                .map(s -> new SeccionLeida(s.titulo(), s.preguntas().stream().filter(p -> visibles.contains(p.id()))
                        .map(p -> aLeida(p, guardadas.get(p.id()), retiradas.contains(p.id()))).toList()))
                .filter(s -> !s.preguntas().isEmpty()).toList();
        return new LecturaDelDiagnostico(quien, "CON_DIAGNOSTICO", vigente.estadoDe(persona), version,
                etapas.completadaEn(persona, Diagnostico.FLUJO).orElse(null), formacionLeida, secciones);
    }

    @Override
    @Transactional
    public LecturaDelDiagnostico lecturaDe(UserId persona, LectorDelDiagnostico lector) {
        LecturaDelDiagnostico lectura = lecturaDe(persona);
        if (!lectura.secciones().isEmpty()) {
            constancia.dejar(persona, lector);
        }
        return lectura;
    }

    @Override
    @Transactional(readOnly = true)
    public ObjetivosPreparados objetivosDe(UserId persona) {
        Optional<EstadoOnboarding> estado = estados.deUsuario(persona).filter(EstadoOnboarding::conDiagnostico);
        if (estado.isEmpty() || estado.get().fichaVersion() == null || !vigente.terminado(persona)) {
            return ObjetivosPreparados.ninguno();
        }
        ObjetivosDelDiagnostico nacidos = ObjetivosDelDiagnostico.de(vigente.fichaDe(persona, estado.get().fichaVersion()),
                vigente.respuestasPorPregunta(persona));
        return new ObjetivosPreparados(nacidos.objetivos().stream().map(o -> new ObjetivoPreparado(o.eje().name(), o.texto(), metaDe(o))).toList(),
                nacidos.prioridad() == null ? null : nacidos.prioridad().name(), nacidos.primeraAccion());
    }

    private static MetaPreparada metaDe(ObjetivosDelDiagnostico.ObjetivoPorEje objetivo) {
        var meta = objetivo.meta();
        return meta == null ? null : new MetaPreparada(meta.meta(), meta.partida(), meta.unidad());
    }

    @Override
    @Transactional(readOnly = true)
    public String tratoDe(UserId persona) {
        Optional<EstadoOnboarding> estado = estados.deUsuario(persona).filter(EstadoOnboarding::conDiagnostico);
        if (estado.isEmpty() || estado.get().fichaVersion() == null) {
            return null;
        }
        return ObjetivosDelDiagnostico.de(vigente.fichaDe(persona, estado.get().fichaVersion()), vigente.respuestasPorPregunta(persona)).trato();
    }

    private PreguntaLeida aLeida(PreguntaDelDiagnostico p, Respuesta guardada, boolean retirada) {
        RespuestaLegible legible = RespuestaLegible.de(p, guardada);
        RespuestaLeida respuesta = legible == null ? null : new RespuestaLeida(legible.texto(), legible.valores(),
                legible.filas().stream().map(f -> new FilaLeida(f.etiqueta(), f.valor())).toList());
        return new PreguntaLeida(p.id(), p.texto(), p.tipo().name(), retirada, respuesta);
    }
}
