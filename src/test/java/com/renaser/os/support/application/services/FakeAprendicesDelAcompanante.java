package com.renaser.os.support.application.services;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.application.ports.out.ticketmentor.AprendicesDelAcompanantePort;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** A quiénes acompaña cada mentor o guía, como lo respondería `community` (que es quien lo decide; acá solo se arma el escenario). */
class FakeAprendicesDelAcompanante implements AprendicesDelAcompanantePort {

    private final Map<UserId, Set<UserId>> porAcompanante = new HashMap<>();

    FakeAprendicesDelAcompanante acompana(UserId acompanante, UserId aprendiz) {
        porAcompanante.computeIfAbsent(acompanante, k -> new HashSet<>()).add(aprendiz);
        return this;
    }

    FakeAprendicesDelAcompanante dejaDeAcompanar(UserId acompanante, UserId aprendiz) {
        porAcompanante.getOrDefault(acompanante, new HashSet<>()).remove(aprendiz);
        return this;
    }

    @Override
    public Set<UserId> aprendicesQueAcompana(UserId acompanante, Instant instante) {
        return Set.copyOf(porAcompanante.getOrDefault(acompanante, Set.of()));
    }
}
