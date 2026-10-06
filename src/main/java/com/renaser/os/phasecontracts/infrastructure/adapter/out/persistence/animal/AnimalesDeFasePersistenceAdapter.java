package com.renaser.os.phasecontracts.infrastructure.adapter.out.persistence.animal;

import com.renaser.os.phasecontracts.application.ports.out.animal.AnimalesDeFasePort;
import com.renaser.os.phasecontracts.domain.model.animal.AnimalDeFase;
import com.renaser.os.phasecontracts.domain.model.contrato.FasePrograma;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
class AnimalesDeFasePersistenceAdapter implements AnimalesDeFasePort {

    private final SpringDataAnimalDeFaseRepository repository;

    AnimalesDeFasePersistenceAdapter(SpringDataAnimalDeFaseRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<AnimalDeFase> todos() {
        return Arrays.stream(FasePrograma.values()).map(this::porFase).toList();
    }

    @Override
    public AnimalDeFase porFase(FasePrograma fase) {
        return repository.findById((short) fase.numero()).map(e -> aDominio(fase, e))
                .orElseGet(() -> AnimalDeFase.sinPersonalizar(fase));
    }

    @Override
    public void guardar(AnimalDeFase animal) {
        UserId autor = animal.actualizadoPor();
        repository.saveAndFlush(new AnimalDeFaseJpaEntity((short) animal.fase().numero(), animal.nombre(),
                animal.rutaImagen(), autor == null ? null : autor.value(), animal.actualizadoEn()));
    }

    private static AnimalDeFase aDominio(FasePrograma fase, AnimalDeFaseJpaEntity e) {
        UserId autor = e.getActualizadoPor() == null ? null : UserId.of(e.getActualizadoPor());
        return AnimalDeFase.rehidratar(fase, e.getNombreAnimal(), e.getImagenRuta(), autor, e.getActualizadoEn());
    }
}
