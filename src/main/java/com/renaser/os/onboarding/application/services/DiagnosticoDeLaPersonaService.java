package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder;
import com.renaser.os.onboarding.application.ports.in.diagnostico.DiagnosticoDeLaPersonaUseCase;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.diagnostico.ConsultarFormacionDeLaPersonaPort;
import com.renaser.os.onboarding.application.ports.out.diagnostico.ConsultarFormacionDeLaPersonaPort.FormacionDeLaPersona;
import com.renaser.os.onboarding.application.ports.out.diagnostico.VersionesDeFichaPort;
import com.renaser.os.onboarding.application.ports.out.diagnostico.VersionesDeFichaPort.VersionPublicada;
import com.renaser.os.onboarding.application.ports.out.estado.LoadEstadoOnboardingPort;
import com.renaser.os.onboarding.application.ports.out.estado.SaveEstadoOnboardingPort;
import com.renaser.os.onboarding.application.ports.out.mapa.EtapaOnboardingPort;
import com.renaser.os.onboarding.application.ports.out.respuesta.SaveRespuestaPort;
import com.renaser.os.onboarding.domain.model.diagnostico.Diagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.DiagnosticoIncompletoException;
import com.renaser.os.onboarding.domain.model.diagnostico.FichaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.ObjetivosDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.PreguntaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.ReglasDeRespuesta;
import com.renaser.os.onboarding.domain.model.diagnostico.RespuestaEntrante;
import com.renaser.os.onboarding.domain.model.diagnostico.SeccionDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.TextoParaLaPersona;
import com.renaser.os.onboarding.domain.model.estado.EstadoOnboarding;
import com.renaser.os.onboarding.domain.model.estado.TipoDeFicha;
import com.renaser.os.onboarding.domain.model.respuesta.Respuesta;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * El diagnóstico de la persona (spec 026): lo abre con la versión que le toca, guarda sus respuestas por pasos, lo termina y entrega lo que nace de él.
 * Leerlo, en palabras, para quien la acompaña es de {@link LecturaDelDiagnosticoService}.
 */
@Service
public class DiagnosticoDeLaPersonaService implements DiagnosticoDeLaPersonaUseCase {

    private final LoadEstadoOnboardingPort estados;
    private final SaveEstadoOnboardingPort guardarEstado;
    private final ConsultarActorPort actores;
    private final VersionesDeFichaPort versiones;
    private final SaveRespuestaPort guardarRespuesta;
    private final EtapaOnboardingPort etapas;
    private final ConsultarFormacionDeLaPersonaPort formaciones;
    private final FichaVigenteDeLaPersona vigente;
    private final DiagnosticoDeLaPersonaFinder lecturas;
    private final Clock clock;

    public DiagnosticoDeLaPersonaService(LoadEstadoOnboardingPort estados, SaveEstadoOnboardingPort guardarEstado, ConsultarActorPort actores,
                                         VersionesDeFichaPort versiones, SaveRespuestaPort guardarRespuesta, EtapaOnboardingPort etapas,
                                         ConsultarFormacionDeLaPersonaPort formaciones, FichaVigenteDeLaPersona vigente,
                                         DiagnosticoDeLaPersonaFinder lecturas, Clock clock) {
        this.estados = estados;
        this.guardarEstado = guardarEstado;
        this.actores = actores;
        this.versiones = versiones;
        this.guardarRespuesta = guardarRespuesta;
        this.etapas = etapas;
        this.formaciones = formaciones;
        this.vigente = vigente;
        this.lecturas = lecturas;
        this.clock = clock;
    }

    // ------------------------------------------------------------------------------------------ la persona

    @Override
    @Transactional(readOnly = true)
    public EstadoDeMiDiagnostico estado(UserId persona) {
        requireActiva(persona);
        Optional<EstadoOnboarding> estado = estados.deUsuario(persona).filter(EstadoOnboarding::conDiagnostico);
        if (estado.isEmpty()) {
            return new EstadoDeMiDiagnostico(TipoDeFicha.ESTANDAR, "NO_APLICA", null, null);
        }
        return new EstadoDeMiDiagnostico(TipoDeFicha.CON_DIAGNOSTICO, vigente.estadoDe(persona), estado.get().fichaVersion(),
                etapas.completadaEn(persona, Diagnostico.FLUJO).orElse(null));
    }

    @Override
    @Transactional
    public DiagnosticoAbierto abrir(UserId persona) {
        requireActiva(persona);
        EstadoOnboarding estado = conDiagnostico(persona);
        if (vigente.terminado(persona)) {
            throw new IllegalStateException("Ya terminaste tu diagnóstico");
        }
        if (estado.fichaVersion() == null) {
            VersionPublicada ultima = versiones.ultima()
                    .orElseThrow(() -> new NoSuchElementException("Todavía no hay un diagnóstico disponible"));
            estado.fijarVersionDelDiagnostico(ultima.version(), clock);
            guardarEstado.guardar(estado);
        }
        return abierto(persona, estado.fichaVersion());
    }

    @Override
    @Transactional
    public int guardar(UserId persona, List<RespuestaEntrante> entrantes) {
        requireActiva(persona);
        EstadoOnboarding estado = conDiagnostico(persona);
        if (vigente.terminado(persona)) {
            throw new IllegalStateException("Ya terminaste tu diagnóstico");
        }
        if (estado.fichaVersion() == null) {
            throw new NoSuchElementException("Abre primero tu diagnóstico");
        }
        FichaDelDiagnostico ficha = vigente.fichaDe(persona, estado.fichaVersion());
        Map<Integer, Respuesta> guardadas = vigente.respuestasPorPregunta(persona);
        int guardadasAhora = 0;
        for (RespuestaEntrante entrante : entrantes) {
            PreguntaDelDiagnostico pregunta = ficha.pregunta(entrante.preguntaId())
                    .orElseThrow(() -> new NoSuchElementException("Esa pregunta no es de tu diagnóstico"));
            RespuestaEntrante valida = ReglasDeRespuesta.validar(pregunta, entrante);
            if (valida.estaVacia()) {
                if (guardadas.containsKey(pregunta.id())) {
                    guardarRespuesta.eliminar(persona, pregunta.id());
                }
                continue;
            }
            guardarRespuesta.guardar(deLaPregunta(pregunta, persona, valida, guardadas.get(pregunta.id())));
            guardadasAhora++;
        }
        return guardadasAhora;
    }

    @Override
    @Transactional
    public ResultadoDeTerminar terminar(UserId persona) {
        requireActiva(persona);
        EstadoOnboarding estado = conDiagnostico(persona);
        if (estado.fichaVersion() == null) {
            throw new NoSuchElementException("Abre primero tu diagnóstico");
        }
        FichaDelDiagnostico ficha = vigente.fichaDe(persona, estado.fichaVersion());
        Map<Integer, Respuesta> guardadas = vigente.respuestasPorPregunta(persona);
        if (!vigente.terminado(persona)) {
            List<Integer> faltan = ficha.faltantes(guardadas);
            if (!faltan.isEmpty()) {
                throw new DiagnosticoIncompletoException(faltan);
            }
            etapas.marcarCompletada(persona, Diagnostico.FLUJO);
        }
        ObjetivosDelDiagnostico nacidos = ObjetivosDelDiagnostico.de(ficha, guardadas);
        return new ResultadoDeTerminar("COMPLETADO", aObjetivos(nacidos), nombreDelEje(nacidos), nacidos.primeraAccion());
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenDeMiDiagnostico resumen(UserId persona) {
        requireActiva(persona);
        DiagnosticoDeLaPersonaFinder.ObjetivosPreparados preparados = lecturas.objetivosDe(persona);
        return new ResumenDeMiDiagnostico(terminadoConDiagnostico(persona), preparados.prioridad(),
                preparados.primeraAccion(), preparados.objetivos().stream().map(o -> new ObjetivoNacido(o.eje(), o.texto())).toList());
    }

    // ------------------------------------------------------------------------------------------------ ayudas

    private DiagnosticoAbierto abierto(UserId persona, int version) {
        Optional<FormacionDeLaPersona> formacion = formaciones.de(persona);
        int dias = formacion.map(FormacionDeLaPersona::duracionDias).orElse(FichaVigenteDeLaPersona.DURACION_POR_DEFECTO);
        FichaDelDiagnostico ficha = vigente.fichaDe(persona, version);
        Map<Integer, Respuesta> guardadas = vigente.respuestasPorPregunta(persona);
        SeccionDelDiagnostico portada = ficha.portada().orElse(null);
        String titulo = portada == null ? "Diagnóstico inicial" : portada.titulo();
        return new DiagnosticoAbierto(version, vigente.estadoDe(persona), titulo, titulo, portada == null ? null : portada.descripcion(),
                formacion.map(f -> f.formacion().codigo()).orElse(null), formacion.map(FormacionDeLaPersona::nombre).orElse(FichaVigenteDeLaPersona.SIN_FORMACION),
                dias, TextoParaLaPersona.duracionEnPalabras(dias), pasoDe(persona), ficha,
                guardadas, precargas(persona, ficha, guardadas));
    }

    /** El paso en que se quedó, solo si el último flujo que movió es el del diagnóstico (el cursor es compartido con la ficha estándar). */
    private Integer pasoDe(UserId persona) {
        return estados.deUsuario(persona).filter(e -> Diagnostico.FLUJO.equals(e.flujoActual())).map(EstadoOnboarding::pasoActual).orElse(null);
    }

    /** El nombre que ya consta de la cuenta, para confirmarlo, en las preguntas que lo piden y que la persona aún no respondió. */
    private Map<Integer, String> precargas(UserId persona, FichaDelDiagnostico ficha, Map<Integer, Respuesta> guardadas) {
        Map<Integer, String> precargas = new HashMap<>();
        String nombre = actores.deActor(persona).map(ConsultarActorPort.ActorOnboarding::nombre).orElse(null);
        for (PreguntaDelDiagnostico p : ficha.preguntas()) {
            if (PreguntaDelDiagnostico.PRECARGA_NOMBRE.equals(p.precarga()) && nombre != null && !guardadas.containsKey(p.id())) {
                precargas.put(p.id(), nombre);
            }
        }
        return precargas;
    }

    private Respuesta deLaPregunta(PreguntaDelDiagnostico p, UserId persona, RespuestaEntrante r, Respuesta existente) {
        java.math.BigDecimal numero = r.valorNumero();
        if (existente != null) {
            return existente.actualizarValor(p.tipo(), r.valorTexto(), numero, r.valorBooleano(), r.valorEscala(), r.valorJson(), null, clock);
        }
        return Respuesta.crear(p.tipo(), persona, p.id(), r.valorTexto(), numero, r.valorBooleano(), r.valorEscala(), r.valorJson(), null, clock);
    }

    private static List<ObjetivoNacido> aObjetivos(ObjetivosDelDiagnostico nacidos) {
        return nacidos.objetivos().stream().map(o -> new ObjetivoNacido(o.eje().name(), o.texto())).toList();
    }

    private static String nombreDelEje(ObjetivosDelDiagnostico nacidos) {
        return nacidos.prioridad() == null ? null : nacidos.prioridad().name();
    }

    private boolean terminadoConDiagnostico(UserId persona) {
        return estados.deUsuario(persona).filter(EstadoOnboarding::conDiagnostico).isPresent() && vigente.terminado(persona);
    }

    private EstadoOnboarding conDiagnostico(UserId persona) {
        return estados.deUsuario(persona).filter(EstadoOnboarding::conDiagnostico)
                .orElseThrow(() -> new NoSuchElementException("No tienes un diagnóstico asignado"));
    }

    private void requireActiva(UserId persona) {
        ConsultarActorPort.ActorOnboarding actor = actores.deActor(persona)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + persona));
        if (actor.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
    }
}
