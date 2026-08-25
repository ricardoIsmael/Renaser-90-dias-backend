package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.cuestionario;

import com.renaser.os.onboarding.application.ports.out.cuestionario.LoadCuestionarioPort;
import com.renaser.os.onboarding.domain.model.cuestionario.OpcionPregunta;
import com.renaser.os.onboarding.domain.model.cuestionario.Pregunta;
import com.renaser.os.onboarding.domain.model.cuestionario.Seccion;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
class CuestionarioPersistenceAdapter implements LoadCuestionarioPort {

    private final SpringDataSeccionOnboardingRepository seccionRepository;
    private final SpringDataPreguntaOnboardingRepository preguntaRepository;
    private final SpringDataOpcionPreguntaRepository opcionRepository;
    private final CuestionarioPersistenceMapper mapper;

    CuestionarioPersistenceAdapter(SpringDataSeccionOnboardingRepository seccionRepository,
                                    SpringDataPreguntaOnboardingRepository preguntaRepository,
                                    SpringDataOpcionPreguntaRepository opcionRepository,
                                    CuestionarioPersistenceMapper mapper) {
        this.seccionRepository = seccionRepository;
        this.preguntaRepository = preguntaRepository;
        this.opcionRepository = opcionRepository;
        this.mapper = mapper;
    }

    @Override
    public List<Seccion> seccionesDeFlujo(String flujo) {
        return seccionRepository.findByFlujoOrderByOrdenAsc(flujo).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Pregunta> preguntasDeSeccion(short seccionId) {
        return preguntaRepository.findBySeccionIdOrderByOrdenAsc(seccionId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<OpcionPregunta> opcionesDePregunta(int preguntaId) {
        return opcionRepository.findByPreguntaIdOrderByOrdenAsc(preguntaId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<Pregunta> porId(int preguntaId) {
        return preguntaRepository.findById(preguntaId).map(mapper::toDomain);
    }
}
