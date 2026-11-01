package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.in.diagnostico.EditarDiagnosticoUseCase;
import com.renaser.os.onboarding.application.ports.out.diagnostico.BorradorDelDiagnosticoPort;
import com.renaser.os.onboarding.domain.model.diagnostico.FichaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.PreguntaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.SeccionDelDiagnostico;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/** Edita el borrador del diagnóstico (spec 026). Cada operación valida el resultado completo: lo que deja el borrador roto se deshace (transacción). */
@Service
public class EditarDiagnosticoService implements EditarDiagnosticoUseCase {

    private final GuardiaDelDiagnostico guardia;
    private final BorradorDelDiagnosticoPort borrador;
    private final QuitarPreguntasDelDiagnostico quitarPreguntas;

    public EditarDiagnosticoService(GuardiaDelDiagnostico guardia, BorradorDelDiagnosticoPort borrador, QuitarPreguntasDelDiagnostico quitarPreguntas) {
        this.guardia = guardia;
        this.borrador = borrador;
        this.quitarPreguntas = quitarPreguntas;
    }

    // ------------------------------------------------------------------------------------------- secciones

    @Override
    @Transactional
    public SeccionDelDiagnostico crearSeccion(UserId actor, String titulo, String descripcion) {
        guardia.exigirQuePuedaAdministrar(actor);
        String clave = "s_" + UUID.randomUUID().toString().substring(0, 8);
        SeccionDelDiagnostico validada = new SeccionDelDiagnostico(0, clave, titulo, descripcion, 0, List.of());
        return borrador.crearSeccion(clave, validada.titulo(), validada.descripcion());
    }

    @Override
    @Transactional
    public SeccionDelDiagnostico actualizarSeccion(UserId actor, int id, String titulo, String descripcion) {
        guardia.exigirQuePuedaAdministrar(actor);
        SeccionDelDiagnostico existente = seccion(id);
        SeccionDelDiagnostico validada = new SeccionDelDiagnostico(id, existente.clave(), titulo, descripcion, existente.orden(), List.of());
        borrador.actualizarSeccion(id, validada.titulo(), validada.descripcion());
        return seccion(id);
    }

    @Override
    @Transactional
    public ResultadoDeQuitar eliminarSeccion(UserId actor, int id, boolean quitarDependientes) {
        guardia.exigirQuePuedaAdministrar(actor);
        SeccionDelDiagnostico seccion = seccion(id);
        if (seccion.esPortada()) {
            throw new IllegalStateException("La portada no se puede borrar: se edita su texto");
        }
        ResultadoDeQuitar resultado = quitarPreguntas.quitar(seccion.preguntas().stream().map(PreguntaDelDiagnostico::id).toList(),
                quitarDependientes, false);
        borrador.eliminarSeccion(id);
        return resultado;
    }

    @Override
    @Transactional
    public void ordenarSecciones(UserId actor, List<Integer> ids) {
        guardia.exigirQuePuedaAdministrar(actor);
        List<Integer> actuales = borrador.cargar().secciones().stream().map(SeccionDelDiagnostico::id).toList();
        if (ids.size() != actuales.size() || !new HashSet<>(ids).equals(new HashSet<>(actuales))) {
            throw new IllegalArgumentException("El orden tiene que nombrar todas las secciones, una vez cada una");
        }
        borrador.ordenarSecciones(ids);
        exigirCondicionesValidas();
    }

    // ------------------------------------------------------------------------------------------- preguntas

    @Override
    @Transactional
    public PreguntaAdministrada crearPregunta(UserId actor, PreguntaPedida pedida) {
        guardia.exigirQuePuedaAdministrar(actor);
        exigirSeccionDeCuestionario(pedida.seccionId());
        PreguntaDelDiagnostico nueva = PreguntaDelDiagnostico.nueva(pedida.seccionId(), -1, null, pedida.texto(), pedida.ayuda(), pedida.tipo(),
                pedida.requerida(), pedida.maximoOpciones(), pedida.opciones(), pedida.escala(), pedida.condicion(), pedida.alimenta(),
                pedida.formaciones(), pedida.precarga());
        exigirDestinoLibre(nueva);
        PreguntaDelDiagnostico guardada = borrador.guardarPregunta(nueva);
        exigirCondicionesValidas();
        return administrada(guardada);
    }

    @Override
    @Transactional
    public PreguntaAdministrada actualizarPregunta(UserId actor, int id, PreguntaPedida pedida) {
        guardia.exigirQuePuedaAdministrar(actor);
        PreguntaDelDiagnostico actual = pregunta(id);
        if (actual.estaRetirada()) {
            throw new IllegalStateException("La pregunta está retirada: restáurala para editarla");
        }
        exigirQueElCambioSeaPosible(actual, pedida);
        PreguntaDelDiagnostico nueva = new PreguntaDelDiagnostico(id, actual.clave(), actual.seccionId(), actual.orden(), pedida.texto(), pedida.ayuda(),
                pedida.tipo(), pedida.requerida(), pedida.maximoOpciones(), pedida.opciones(), pedida.escala(), pedida.condicion(), pedida.alimenta(),
                pedida.formaciones(), pedida.precarga(), null);
        exigirDestinoLibre(nueva);
        PreguntaDelDiagnostico guardada = borrador.guardarPregunta(nueva);
        if (pedida.seccionId() != actual.seccionId()) {
            exigirSeccionDeCuestionario(pedida.seccionId());
            borrador.moverPregunta(id, pedida.seccionId(), Integer.MAX_VALUE);
        }
        exigirCondicionesValidas();
        return administrada(borrador.pregunta(guardada.id()).orElseThrow());
    }

    @Override
    @Transactional
    public void moverPregunta(UserId actor, int id, int seccionId, int posicion) {
        guardia.exigirQuePuedaAdministrar(actor);
        PreguntaDelDiagnostico p = pregunta(id);
        if (p.estaRetirada()) {
            throw new IllegalStateException("La pregunta está retirada: restáurala para moverla");
        }
        exigirSeccionDeCuestionario(seccionId);
        borrador.moverPregunta(id, seccionId, posicion);
        exigirCondicionesValidas();
    }

    @Override
    @Transactional
    public PreguntaAdministrada duplicarPregunta(UserId actor, int id) {
        guardia.exigirQuePuedaAdministrar(actor);
        PreguntaDelDiagnostico original = pregunta(id);
        if (original.estaRetirada()) {
            throw new IllegalStateException("La pregunta está retirada: restáurala para duplicarla");
        }
        if (original.tipo() == com.renaser.os.onboarding.domain.model.cuestionario.TipoPreguntaOnboarding.CASILLA) {
            throw new IllegalStateException("El consentimiento es uno solo: no se duplica");
        }
        PreguntaDelDiagnostico copia = PreguntaDelDiagnostico.nueva(original.seccionId(), -1, null, original.texto(), original.ayuda(), original.tipo(),
                original.requerida(), original.maximoOpciones(), original.opciones(), original.escala(), original.condicion(), null,
                original.formaciones(), null);
        PreguntaDelDiagnostico guardada = borrador.guardarPregunta(copia);
        int posicion = posicionDe(original) + 1;
        borrador.moverPregunta(guardada.id(), original.seccionId(), posicion);
        exigirCondicionesValidas();
        return administrada(borrador.pregunta(guardada.id()).orElseThrow());
    }

    @Override
    @Transactional
    public ResultadoDeQuitar quitar(UserId actor, List<Integer> ids, boolean quitarDependientes) {
        guardia.exigirQuePuedaAdministrar(actor);
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("Marca al menos una pregunta");
        }
        return quitarPreguntas.quitar(ids, quitarDependientes, false);
    }

    @Override
    @Transactional
    public PreguntaAdministrada restaurar(UserId actor, int id) {
        guardia.exigirQuePuedaAdministrar(actor);
        PreguntaDelDiagnostico p = pregunta(id);
        if (!p.estaRetirada()) {
            throw new IllegalStateException("La pregunta no está retirada");
        }
        FichaDelDiagnostico ficha = borrador.cargar();
        int destino = ficha.secciones().stream().filter(s -> s.id() == p.seccionId() && !s.esPortada()).map(SeccionDelDiagnostico::id).findFirst()
                .or(() -> ficha.secciones().stream().filter(s -> !s.esPortada()).map(SeccionDelDiagnostico::id).findFirst())
                .orElseThrow(() -> new IllegalStateException("Crea primero una sección para restaurarla"));
        borrador.restaurarPregunta(id, destino);
        PreguntaDelDiagnostico restaurada = borrador.pregunta(id).orElseThrow();
        if (borrador.cargar().errorDeLaCondicion(restaurada).isPresent()) {
            restaurada = borrador.guardarPregunta(restaurada.sinCondicion());
        }
        return administrada(restaurada);
    }

    // ------------------------------------------------------------------------------------------------ reglas

    private void exigirQueElCambioSeaPosible(PreguntaDelDiagnostico actual, PreguntaPedida pedida) {
        int respuestas = borrador.respuestasPorPregunta().getOrDefault(actual.id(), 0);
        if (pedida.tipo() != actual.tipo()) {
            if (respuestas > 0) {
                throw new IllegalStateException("Esta pregunta ya tiene " + respuestas + (respuestas == 1 ? " respuesta" : " respuestas")
                        + ": no se le cambia el tipo. Quítala y crea otra");
            }
            if (actual.esProtegida()) {
                throw new IllegalStateException("Esta pregunta la usa otra función de la app y no se le cambia el tipo");
            }
        }
        boolean dejaDeSerProtegida = actual.alimenta() == com.renaser.os.onboarding.domain.model.diagnostico.DestinoDeRespuesta.TRATO_DE_LA_PERSONA
                && pedida.alimenta() != actual.alimenta();
        if (dejaDeSerProtegida) {
            throw new IllegalStateException("Esta pregunta la usa SER para tratar bien a cada persona: no se le quita ese destino");
        }
    }

    /** Cada destino admite una sola pregunta activa. El índice único lo vuelve a decir, pero así el mensaje nombra a la que ya lo tiene. */
    private void exigirDestinoLibre(PreguntaDelDiagnostico p) {
        if (p.alimenta() == null) {
            return;
        }
        borrador.cargar().preguntas().stream().filter(o -> o.alimenta() == p.alimenta() && o.id() != p.id()).findFirst().ifPresent(o -> {
            throw new IllegalStateException("«" + o.texto() + "» ya alimenta «" + p.alimenta().etiqueta() + "»: cada destino admite una sola pregunta");
        });
    }

    /** Después de cualquier cambio, ninguna condición puede quedar apuntando a algo que no existe, que viene después o cuya opción ya no está. */
    private void exigirCondicionesValidas() {
        FichaDelDiagnostico ficha = borrador.cargar();
        for (PreguntaDelDiagnostico p : ficha.preguntas()) {
            Optional<String> error = ficha.errorDeLaCondicion(p);
            if (error.isPresent()) {
                throw new IllegalArgumentException("«" + p.texto() + "»: " + error.get());
            }
        }
    }

    private void exigirSeccionDeCuestionario(int seccionId) {
        SeccionDelDiagnostico s = seccion(seccionId);
        if (s.esPortada()) {
            throw new IllegalArgumentException("La portada no lleva preguntas");
        }
    }

    private int posicionDe(PreguntaDelDiagnostico p) {
        List<Integer> ids = new ArrayList<>(seccion(p.seccionId()).preguntas().stream().map(PreguntaDelDiagnostico::id).toList());
        return Math.max(0, ids.indexOf(p.id()));
    }

    private PreguntaAdministrada administrada(PreguntaDelDiagnostico p) {
        return new PreguntaAdministrada(p, borrador.respuestasPorPregunta().getOrDefault(p.id(), 0), p.esProtegida());
    }

    private PreguntaDelDiagnostico pregunta(int id) {
        return borrador.pregunta(id).orElseThrow(() -> new NoSuchElementException("La pregunta no existe"));
    }

    private SeccionDelDiagnostico seccion(int id) {
        return borrador.seccion(id).orElseThrow(() -> new NoSuchElementException("La sección no existe"));
    }
}
