package com.renaser.os.rag.application.services;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

class FakeUserSummaryFinder implements UserSummaryFinder {

    private final Map<UserId, UserSummary> actores = new HashMap<>();

    FakeUserSummaryFinder conActor(UserId id, UserRole role) {
        return conActor(id, role, UserStatus.ACTIVE);
    }

    FakeUserSummaryFinder conActor(UserId id, UserRole role, UserStatus status) {
        actores.put(id, new UserSummary(id, "Actor de prueba", null, role, status));
        return this;
    }

    @Override
    public Optional<UserSummary> findById(UserId id) {
        return Optional.ofNullable(actores.get(id));
    }

    @Override
    public Map<UserId, UserSummary> findByIds(Collection<UserId> ids) {
        Map<UserId, UserSummary> encontrados = new LinkedHashMap<>();
        for (UserId id : ids) {
            findById(id).ifPresent(resumen -> encontrados.put(resumen.id(), resumen));
        }
        return encontrados;
    }
}
