package com.renaser.os.onboarding.domain.model.diagnostico;

import com.renaser.os.onboarding.domain.model.cuestionario.TipoPreguntaOnboarding;
import com.renaser.os.onboarding.domain.model.respuesta.Respuesta;
import com.renaser.os.shared.domain.Formacion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * El diagnóstico completo (spec 026): el borrador que edita Administración o la foto de una versión publicada. Sabe lo que ninguna pregunta sabe sola: si una
 * condición apunta a una pregunta anterior, qué preguntas se muestran dadas unas respuestas, qué falta para terminar y si se puede publicar. Sin Spring.
 *
 * <p>Solo tiene preguntas activas: las retiradas no entran ni al borrador publicable ni a la foto.
 */
public record FichaDelDiagnostico(List<SeccionDelDiagnostico> secciones) {

    /** El texto con el que se siembra la declaración mientras el dueño no entrega la suya (026-6). Publicar con él está prohibido. */
    public static final String MARCA_DE_DECLARACION_PENDIENTE = "[PENDIENTE DEL DUEÑO]";

    public FichaDelDiagnostico {
        secciones = List.copyOf(Objects.requireNonNull(secciones, "secciones es obligatorio"));
    }

    public List<PreguntaDelDiagnostico> preguntas() {
        return secciones.stream().flatMap(s -> s.preguntas().stream()).toList();
    }

    public Optional<PreguntaDelDiagnostico> pregunta(int id) {
        return preguntas().stream().filter(p -> p.id() == id).findFirst();
    }

    public Optional<SeccionDelDiagnostico> portada() {
        return secciones.stream().filter(SeccionDelDiagnostico::esPortada).findFirst();
    }

    /** Lo que ve una persona de esa formación: solo sus preguntas, sin secciones vacías (la portada se queda). {@code null} = todas. */
    public FichaDelDiagnostico paraLaFormacion(Formacion formacion) {
        List<SeccionDelDiagnostico> filtradas = new ArrayList<>();
        for (SeccionDelDiagnostico s : secciones) {
            List<PreguntaDelDiagnostico> delaFormacion = s.preguntas().stream().filter(p -> p.seMuestraEn(formacion)).toList();
            if (s.esPortada() || !delaFormacion.isEmpty()) {
                filtradas.add(s.conPreguntas(delaFormacion));
            }
        }
        return new FichaDelDiagnostico(filtradas);
    }

    /** La ficha con {@code {duracion}} y {@code {formacion}} reemplazados en los textos que la persona lee. */
    public FichaDelDiagnostico conTextosPara(String duracion, String formacion) {
        return new FichaDelDiagnostico(secciones.stream().map(s -> new SeccionDelDiagnostico(s.id(), s.clave(),
                TextoParaLaPersona.reemplazar(s.titulo(), duracion, formacion), TextoParaLaPersona.reemplazar(s.descripcion(), duracion, formacion),
                s.orden(), s.preguntas().stream().map(p -> p.conTextos(
                        TextoParaLaPersona.reemplazar(p.texto(), duracion, formacion),
                        TextoParaLaPersona.reemplazar(p.ayuda(), duracion, formacion))).toList())).toList());
    }

    /**
     * Los ids de las preguntas que se muestran dadas estas respuestas: una con condición solo se muestra si su padre se muestra Y la respuesta de su padre
     * es el valor que la activa. Una cuyo padre no está en la ficha (de otra formación, retirado) no se muestra.
     */
    public Set<Integer> visibles(Map<Integer, Respuesta> respuestas) {
        Set<Integer> visibles = new HashSet<>();
        for (PreguntaDelDiagnostico p : preguntas()) {
            if (p.condicion() == null || condicionSeCumple(p.condicion(), visibles, respuestas)) {
                visibles.add(p.id());
            }
        }
        return visibles;
    }

    /** Los ids de las preguntas obligatorias que se muestran y siguen sin responder (completas), en el orden de la ficha. */
    public List<Integer> faltantes(Map<Integer, Respuesta> respuestas) {
        Set<Integer> visibles = visibles(respuestas);
        return preguntas().stream()
                .filter(p -> p.requerida() && visibles.contains(p.id()))
                .filter(p -> !ReglasDeRespuesta.estaContestada(p, respuestas.get(p.id())))
                .map(PreguntaDelDiagnostico::id)
                .toList();
    }

    /** Las preguntas (y sus descendientes) que dependen de alguna de estas, sin contar las propias. */
    public Set<Integer> dependientesDe(Collection<Integer> ids) {
        Set<Integer> alcanzados = new LinkedHashSet<>(ids);
        boolean crecio = true;
        while (crecio) {
            crecio = false;
            for (PreguntaDelDiagnostico p : preguntas()) {
                if (p.condicion() != null && alcanzados.contains(p.condicion().preguntaId()) && alcanzados.add(p.id())) {
                    crecio = true;
                }
            }
        }
        alcanzados.removeAll(ids);
        return alcanzados;
    }

    /**
     * El motivo por el que la condición de esta pregunta no vale, o vacío si vale: el padre existe, está ANTES (en el orden de la ficha), es de una
     * pregunta de opciones y la respuesta que la activa es una de sus opciones.
     */
    public Optional<String> errorDeLaCondicion(PreguntaDelDiagnostico p) {
        CondicionDeVisibilidad c = p.condicion();
        if (c == null) {
            return Optional.empty();
        }
        List<PreguntaDelDiagnostico> orden = preguntas();
        Optional<PreguntaDelDiagnostico> padre = orden.stream().filter(q -> q.id() == c.preguntaId()).findFirst();
        if (padre.isEmpty()) {
            return Optional.of("«Se muestra solo si» apunta a una pregunta que no existe");
        }
        if (padre.get().id() == p.id() || posicion(orden, padre.get().id()) >= posicion(orden, p.id()) && p.id() != 0) {
            return Optional.of("«Se muestra solo si» tiene que apuntar a una pregunta anterior");
        }
        TipoPreguntaOnboarding t = padre.get().tipo();
        if (t != TipoPreguntaOnboarding.SELECCION_UNICA && t != TipoPreguntaOnboarding.SELECCION_MULTIPLE) {
            return Optional.of("«Se muestra solo si» solo puede depender de una pregunta de opciones");
        }
        if (!padre.get().tieneOpcion(c.valor())) {
            return Optional.of("La respuesta «" + c.valor() + "» no es una opción de la pregunta de la que depende");
        }
        return Optional.empty();
    }

    /**
     * Lo que impide publicar esta ficha, para decírselo a Administración: vacía, sin consentimiento o con la declaración todavía de relleno, condiciones
     * rotas o preguntas de opciones sin ellas. Lista vacía = se puede publicar.
     */
    public List<String> motivosParaNoPublicar() {
        List<String> motivos = new ArrayList<>();
        List<PreguntaDelDiagnostico> preguntas = preguntas();
        if (preguntas.isEmpty()) {
            motivos.add("El diagnóstico no tiene preguntas");
        }
        motivoDeLaDeclaracion(preguntas).ifPresent(motivos::add);
        preguntas.forEach(p -> errorDeLaCondicion(p).ifPresent(e -> motivos.add("«" + corto(p.texto()) + "»: " + e)));
        return motivos;
    }

    private Optional<String> motivoDeLaDeclaracion(List<PreguntaDelDiagnostico> preguntas) {
        Optional<PreguntaDelDiagnostico> consentimiento = preguntas.stream()
                .filter(p -> p.tipo() == TipoPreguntaOnboarding.CASILLA).findFirst();
        if (consentimiento.isEmpty()) {
            return Optional.of("Falta la pregunta del consentimiento");
        }
        String declaracion = consentimiento.get().ayuda();
        if (declaracion == null || declaracion.contains(MARCA_DE_DECLARACION_PENDIENTE)) {
            return Optional.of("Falta el texto de la declaración de responsabilidad y consentimiento");
        }
        return Optional.empty();
    }

    private boolean condicionSeCumple(CondicionDeVisibilidad c, Set<Integer> visibles, Map<Integer, Respuesta> respuestas) {
        if (!visibles.contains(c.preguntaId())) {
            return false;
        }
        return pregunta(c.preguntaId())
                .map(padre -> ReglasDeRespuesta.valoresElegidos(padre, respuestas.get(padre.id())).contains(c.valor()))
                .orElse(false);
    }

    private static int posicion(List<PreguntaDelDiagnostico> orden, int id) {
        for (int i = 0; i < orden.size(); i++) {
            if (orden.get(i).id() == id) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    private static String corto(String t) {
        return t.length() <= 60 ? t : t.substring(0, 57) + "...";
    }
}
