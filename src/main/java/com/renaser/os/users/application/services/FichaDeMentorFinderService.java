package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FichaDeMentorFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.out.mentorprofile.LoadMentorProfilePort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.mentorprofile.MentorProfile;
import com.renaser.os.users.domain.model.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementacion de {@link FichaDeMentorFinder}. Solo lectura y sin autorizacion propia: quien la
 * consume ya decidio si el actor puede mirar. Dos lecturas en total (usuarios y perfiles), nunca una
 * por mentor.
 */
@Service
public class FichaDeMentorFinderService implements FichaDeMentorFinder {

    /** Paginado contra la base como el padron de aprendices: el cuerpo de mentores no se carga de una. */
    private static final int TAMANO_LOTE = 200;

    private final LoadUserPort loadUserPort;
    private final LoadMentorProfilePort loadMentorProfilePort;

    public FichaDeMentorFinderService(LoadUserPort loadUserPort, LoadMentorProfilePort loadMentorProfilePort) {
        this.loadUserPort = loadUserPort;
        this.loadMentorProfilePort = loadMentorProfilePort;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FichaDeMentor> mentoresActivos() {
        List<User> mentores = mentoresConCuentaActiva();
        Map<UserId, MentorProfile> perfiles = loadMentorProfilePort.byUserIds(mentores.stream().map(User::id).toList())
                .stream().collect(Collectors.toMap(MentorProfile::userId, Function.identity()));
        return mentores.stream()
                .map(mentor -> aFicha(mentor, perfiles.get(mentor.id())))
                .sorted(Comparator.comparing((FichaDeMentor f) -> f.nombreCompleto().toLowerCase())
                        .thenComparing(f -> f.id().value()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FichaDeMentor> mentor(UserId usuarioId) {
        return loadUserPort.byId(usuarioId)
                .filter(usuario -> usuario.role() == UserRole.MENTOR)
                .map(usuario -> aFicha(usuario, loadMentorProfilePort.byUserId(usuarioId).orElse(null)));
    }

    private List<User> mentoresConCuentaActiva() {
        List<User> todos = new ArrayList<>();
        for (int pagina = 0; ; pagina++) {
            List<User> lote = loadUserPort.byRoles(List.of(UserRole.MENTOR), UserStatus.ACTIVE, pagina, TAMANO_LOTE);
            todos.addAll(lote);
            if (lote.size() < TAMANO_LOTE) {
                return todos;
            }
        }
    }

    private static FichaDeMentor aFicha(User usuario, MentorProfile perfil) {
        PerfilDeMentor delPerfil = perfil == null ? null
                : new PerfilDeMentor(perfil.level().name(), perfil.operationalStatus().name(), perfil.createdAt());
        return new FichaDeMentor(usuario.id(), usuario.fullName(), usuario.avatarUrl(), usuario.status(), delPerfil);
    }
}
