package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.PerfilMentorFinder;
import com.renaser.os.users.application.ports.out.mentorprofile.LoadMentorProfilePort;
import com.renaser.os.users.domain.model.mentorprofile.MentorProfile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementacion de {@link PerfilMentorFinder}. Es solo lectura y sin autorizacion propia: quien
 * llama ya resolvio si puede mirar. Devuelve el record publico, nunca el agregado
 * {@link MentorProfile}, para que nadie de otro modulo pueda mutarlo por accidente.
 */
@Service
public class PerfilMentorFinderService implements PerfilMentorFinder {

    private final LoadMentorProfilePort loadMentorProfilePort;

    public PerfilMentorFinderService(LoadMentorProfilePort loadMentorProfilePort) {
        this.loadMentorProfilePort = loadMentorProfilePort;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PerfilMentor> porUsuario(UserId usuarioId) {
        return loadMentorProfilePort.byUserId(usuarioId).map(PerfilMentorFinderService::aPerfil);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UserId, PerfilMentor> porUsuarios(Collection<UserId> usuarioIds) {
        if (usuarioIds == null || usuarioIds.isEmpty()) {
            return Map.of();
        }
        return loadMentorProfilePort.byUserIds(List.copyOf(usuarioIds)).stream()
                .map(PerfilMentorFinderService::aPerfil)
                .collect(Collectors.toMap(PerfilMentor::usuarioId, Function.identity()));
    }

    private static PerfilMentor aPerfil(MentorProfile perfil) {
        return new PerfilMentor(perfil.userId(), perfil.especialidad());
    }
}
