package com.renaser.os.users.application.ports.out.mentorprofile;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.mentorprofile.MentorProfile;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LoadMentorProfilePort {

    Optional<MentorProfile> byUserId(UserId userId);

    /**
     * Los perfiles que EXISTEN entre esos ids. Un id sin perfil simplemente no vuelve: la lista
     * es mas corta que la de entrada y eso no es un error.
     *
     * <p>Va en lote porque el selector de mentor del panel administrativo pide todos los
     * candidatos de una vez; resolverlo con {@link #byUserId} en un bucle es el N+1 que este
     * proyecto ya se saco de encima en los otros pickers.
     */
    List<MentorProfile> byUserIds(Collection<UserId> userIds);
}
