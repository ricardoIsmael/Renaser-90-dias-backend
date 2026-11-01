package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.in.diagnostico.EditarDiagnosticoUseCase.ResultadoDeQuitar;
import com.renaser.os.onboarding.application.ports.out.diagnostico.BorradorDelDiagnosticoPort;
import com.renaser.os.onboarding.domain.model.diagnostico.FichaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.PreguntaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.TieneDependientesException;
import com.renaser.os.shared.domain.Clock;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * La regla de «quitar preguntas» (spec 026, US7): una pregunta que nunca estuvo en una versión publicada y no tiene respuestas se ELIMINA; si alguna versión la
 * incluyó, o ya la respondió alguien, se RETIRA (deja de mostrarse y se conservan las respuestas). Una de la que dependen otras no se quita sola.
 */
@Component
class QuitarPreguntasDelDiagnostico {

    private final BorradorDelDiagnosticoPort borrador;
    private final Clock clock;

    QuitarPreguntasDelDiagnostico(BorradorDelDiagnosticoPort borrador, Clock clock) {
        this.borrador = borrador;
        this.clock = clock;
    }

    ResultadoDeQuitar quitar(List<Integer> ids, boolean quitarDependientes, boolean permitirProtegidas) {
        FichaDelDiagnostico ficha = borrador.cargar();
        Set<Integer> pedidas = new LinkedHashSet<>();
        for (Integer id : ids) {
            PreguntaDelDiagnostico p = ficha.pregunta(id).orElseThrow(() -> new NoSuchElementException("La pregunta ya no está en el diagnóstico"));
            if (p.esProtegida() && !permitirProtegidas) {
                throw new IllegalStateException("«" + p.texto() + "» la usa otra función de la app y no se puede quitar");
            }
            pedidas.add(id);
        }
        Set<Integer> dependientes = ficha.dependientesDe(pedidas);
        if (!dependientes.isEmpty() && !quitarDependientes) {
            throw new TieneDependientesException(new ArrayList<>(dependientes));
        }
        Set<Integer> todas = new LinkedHashSet<>(pedidas);
        todas.addAll(dependientes);
        return aplicar(todas);
    }

    private ResultadoDeQuitar aplicar(Set<Integer> todas) {
        Map<Integer, Integer> conteos = borrador.respuestasPorPregunta();
        Set<Integer> publicadas = borrador.publicadasAlgunaVez(todas);
        List<Integer> aEliminar = new ArrayList<>();
        List<Integer> aRetirar = new ArrayList<>();
        int conRespuestas = 0;
        for (Integer id : todas) {
            boolean respondida = conteos.getOrDefault(id, 0) > 0;
            conRespuestas += respondida ? 1 : 0;
            (respondida || publicadas.contains(id) ? aRetirar : aEliminar).add(id);
        }
        borrador.eliminarPreguntas(aEliminar);
        borrador.retirarPreguntas(aRetirar, clock.now());
        return new ResultadoDeQuitar(aEliminar.size(), aRetirar.size(), conRespuestas);
    }
}
