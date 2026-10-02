package com.renaser.os.rag.infrastructure.adapter.out.rocks;

import com.renaser.os.rag.application.ports.out.rocas.ConsultarCompuertaDeRocasPort;
import com.renaser.os.rocks.api.CompuertaDeRocasFinder;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/** Implementa {@link ConsultarCompuertaDeRocasPort} delegando en {@code rocks.api.CompuertaDeRocasFinder} (D-41, D-247). */
@Component
class ConsultarCompuertaDeRocasAdapter implements ConsultarCompuertaDeRocasPort {

    private final CompuertaDeRocasFinder compuerta;

    ConsultarCompuertaDeRocasAdapter(CompuertaDeRocasFinder compuerta) {
        this.compuerta = compuerta;
    }

    @Override
    public boolean rocasMaestrasCompletas(UserId aprendizId) {
        return compuerta.rocasMaestrasCompletas(aprendizId);
    }

    @Override
    public List<String> ejesSinObjetivoSemanal(UserId aprendizId, LocalDate fecha) {
        return compuerta.ejesSinObjetivoSemanal(aprendizId, fecha);
    }
}
