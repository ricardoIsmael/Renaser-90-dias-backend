package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.out.diagnostico.BorradorDelDiagnosticoPort;
import com.renaser.os.onboarding.domain.model.diagnostico.FichaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.PreguntaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.SeccionDelDiagnostico;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** El borrador del diagnóstico en memoria, para probar los servicios sin base: guarda, ordena y retira como lo hace el adaptador JDBC. */
final class BorradorEnMemoria implements BorradorDelDiagnosticoPort {

    private final Map<Integer, SeccionDelDiagnostico> secciones = new HashMap<>();
    private final Map<Integer, PreguntaDelDiagnostico> preguntas = new HashMap<>();
    private int siguienteId = 100;
    final Map<Integer, Integer> respuestas = new HashMap<>();
    final Set<Integer> publicadas = new HashSet<>();
    final List<Integer> clavesLiberadas = new ArrayList<>();

    BorradorEnMemoria con(SeccionDelDiagnostico seccion) {
        secciones.put(seccion.id(), new SeccionDelDiagnostico(seccion.id(), seccion.clave(), seccion.titulo(), seccion.descripcion(), seccion.orden(), List.of()));
        seccion.preguntas().forEach(this::ponerEnOrden);
        return this;
    }

    private void ponerEnOrden(PreguntaDelDiagnostico p) {
        preguntas.put(p.id(), p);
    }

    @Override
    public FichaDelDiagnostico cargar() {
        List<SeccionDelDiagnostico> ordenadas = secciones.values().stream().sorted(Comparator.comparingInt(SeccionDelDiagnostico::orden)).toList();
        return new FichaDelDiagnostico(ordenadas.stream().map(s -> s.conPreguntas(activasDe(s.id()))).toList());
    }

    private List<PreguntaDelDiagnostico> activasDe(int seccionId) {
        return preguntas.values().stream().filter(p -> p.seccionId() == seccionId && !p.estaRetirada())
                .sorted(Comparator.comparingInt(PreguntaDelDiagnostico::orden)).toList();
    }

    @Override
    public List<PreguntaDelDiagnostico> retiradas() {
        return preguntas.values().stream().filter(PreguntaDelDiagnostico::estaRetirada)
                .sorted(Comparator.comparing(PreguntaDelDiagnostico::retiradaEn).reversed()).toList();
    }

    @Override
    public Optional<PreguntaDelDiagnostico> pregunta(int id) {
        return Optional.ofNullable(preguntas.get(id));
    }

    @Override
    public Optional<SeccionDelDiagnostico> seccion(int id) {
        return Optional.ofNullable(secciones.get(id)).map(s -> s.conPreguntas(activasDe(id)));
    }

    @Override
    public SeccionDelDiagnostico crearSeccion(String clave, String titulo, String descripcion) {
        int orden = secciones.values().stream().mapToInt(SeccionDelDiagnostico::orden).max().orElse(-1) + 1;
        SeccionDelDiagnostico s = new SeccionDelDiagnostico(siguienteId++, clave, titulo, descripcion, orden, List.of());
        secciones.put(s.id(), s);
        return s;
    }

    @Override
    public void actualizarSeccion(int id, String titulo, String descripcion) {
        SeccionDelDiagnostico s = secciones.get(id);
        secciones.put(id, new SeccionDelDiagnostico(id, s.clave(), titulo, descripcion, s.orden(), List.of()));
    }

    @Override
    public void eliminarSeccion(int id) {
        secciones.remove(id);
        preguntas.values().removeIf(p -> p.seccionId() == id && !p.estaRetirada());
    }

    @Override
    public void ordenarSecciones(List<Integer> ids) {
        for (int i = 0; i < ids.size(); i++) {
            SeccionDelDiagnostico s = secciones.get(ids.get(i));
            secciones.put(s.id(), new SeccionDelDiagnostico(s.id(), s.clave(), s.titulo(), s.descripcion(), i, List.of()));
        }
    }

    @Override
    public PreguntaDelDiagnostico guardarPregunta(PreguntaDelDiagnostico p) {
        int id = p.id() == 0 ? siguienteId++ : p.id();
        String clave = p.clave() != null ? p.clave() : "diag_p" + id;
        int orden = p.id() == 0 ? activasDe(p.seccionId()).size() : p.orden();
        PreguntaDelDiagnostico guardada = new PreguntaDelDiagnostico(id, clave, p.seccionId(), orden, p.texto(), p.ayuda(), p.tipo(), p.requerida(),
                p.maximoOpciones(), p.opciones(), p.escala(), p.condicion(), p.alimenta(), p.formaciones(), p.precarga(), p.retiradaEn());
        preguntas.put(id, guardada);
        return guardada;
    }

    @Override
    public void moverPregunta(int id, int seccionId, int posicion) {
        PreguntaDelDiagnostico mover = preguntas.get(id);
        List<PreguntaDelDiagnostico> destino = new ArrayList<>(activasDe(seccionId));
        destino.removeIf(p -> p.id() == id);
        destino.add(Math.min(Math.max(posicion, 0), destino.size()), mover);
        for (int i = 0; i < destino.size(); i++) {
            preguntas.put(destino.get(i).id(), destino.get(i).enSeccion(seccionId, i));
        }
    }

    @Override
    public void eliminarPreguntas(Collection<Integer> ids) {
        ids.forEach(preguntas::remove);
    }

    @Override
    public void retirarPreguntas(Collection<Integer> ids, Instant ahora) {
        ids.forEach(id -> preguntas.put(id, preguntas.get(id).retirada(ahora)));
    }

    @Override
    public void restaurarPregunta(int id, int seccionId) {
        preguntas.put(id, preguntas.get(id).restaurada().enSeccion(seccionId, activasDe(seccionId).size()));
    }

    @Override
    public void liberarClaves(Collection<Integer> ids) {
        clavesLiberadas.addAll(ids);
    }

    @Override
    public Map<Integer, Integer> respuestasPorPregunta() {
        return respuestas.entrySet().stream().filter(e -> e.getValue() > 0).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public Set<Integer> publicadasAlgunaVez(Collection<Integer> ids) {
        return ids.stream().filter(publicadas::contains).collect(Collectors.toSet());
    }

    boolean existe(int preguntaId) {
        return preguntas.containsKey(preguntaId);
    }
}
