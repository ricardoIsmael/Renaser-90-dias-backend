package com.renaser.os.community.application.services;

import com.renaser.os.community.application.ports.out.celula.LoadCelulaPort;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.GruposDelPersonalFinder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Los grupos que lidera un integrante del personal (spec 022, FR-011). Lo consume {@code users} por su {@code api}: la dependencia va de aquí hacia
 * allá, como siempre.
 */
@Service
class GruposDelPersonalService implements GruposDelPersonalFinder {

    private final LoadCelulaPort celulas;

    GruposDelPersonalService(LoadCelulaPort celulas) {
        this.celulas = celulas;
    }

    @Override
    public List<GrupoDelPersonal> queLidera(UserId persona) {
        return celulas.porMentor(persona).stream()
                .map(c -> new GrupoDelPersonal(c.id().value(), c.nombre(), c.formacion()))
                .toList();
    }
}
