package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.application.ports.in.ConsultarMisFuncionalidadesUseCase;
import com.renaser.os.plataforma.domain.model.catalogo.DefinicionDeFuncionalidad;
import com.renaser.os.plataforma.domain.model.estado.ActorEvaluado;
import com.renaser.os.plataforma.domain.model.estado.ResultadoDeEvaluacion;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Lo apagado para una sesión (contrato §6) y, aparte, lo que no le corresponde (fase 2: se esconde). Recorre el catálogo entero sobre la copia en memoria: son unas 55 claves
 * y ninguna consulta a la base, salvo leer el rol una vez si alguna tiene excepciones por rol.
 */
@Service
class MisFuncionalidadesService implements ConsultarMisFuncionalidadesUseCase {

    private final EstadoVigente estado;
    private final RolDelActor rolDelActor;

    MisFuncionalidadesService(EstadoVigente estado, RolDelActor rolDelActor) {
        this.estado = estado;
        this.rolDelActor = rolDelActor;
    }

    @Override
    public MisFuncionalidades mias(UserId actor) {
        long version = estado.version();
        ActorEvaluado quien = rolDelActor.de(actor);
        List<FuncionalidadApagada> apagadas = new ArrayList<>();
        List<String> noDisponibles = new ArrayList<>();
        for (DefinicionDeFuncionalidad definicion : estado.catalogo().todas()) {
            ResultadoDeEvaluacion resultado = estado.evaluar(definicion.clave(), quien);
            if (resultado.noDisponible()) {
                noDisponibles.add(definicion.clave());
            } else if (!resultado.prendida()) {
                apagadas.add(new FuncionalidadApagada(definicion.clave(), resultado.aviso()));
            }
        }
        return new MisFuncionalidades(version, List.copyOf(apagadas), List.copyOf(noDisponibles));
    }
}
