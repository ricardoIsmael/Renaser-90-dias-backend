package com.renaser.os.community.infrastructure.adapter.out.persistence.testimonio;

import com.renaser.os.community.application.ports.out.testimonio.LoadTestimonioPort;
import com.renaser.os.community.application.ports.out.testimonio.SaveTestimonioPort;
import com.renaser.os.community.domain.model.testimonio.Testimonio;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class TestimonioPersistenceAdapter implements LoadTestimonioPort, SaveTestimonioPort {

    private final SpringDataTestimonioRepository repository;
    private final TestimonioPersistenceMapper mapper;

    TestimonioPersistenceAdapter(SpringDataTestimonioRepository repository, TestimonioPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<Testimonio> listarDestacados(int limite) {
        return repository.findByDestacadoTrueOrderByCreadoEnDesc(PageRequest.of(0, limite)).stream()
                .map(mapper::toDomain).toList();
    }

    @Override
    public Testimonio save(Testimonio testimonio) {
        var guardado = repository.saveAndFlush(mapper.toEntity(testimonio));
        return mapper.toDomain(guardado);
    }
}
