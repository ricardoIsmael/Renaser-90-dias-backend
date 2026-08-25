package com.renaser.os.users.infrastructure.adapter.out.auth;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.out.accountrequest.SupabaseAdminAuthPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


@Component
public class NoOpSupabaseAdminAuthAdapter implements SupabaseAdminAuthPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpSupabaseAdminAuthAdapter.class);

    @Override
    public void deleteUser(UserId userId) {
        log.warn("SupabaseAdminAuthPort.deleteUser({}) NO ejecutado de verdad: "
                + "adaptador placeholder, faltan credenciales de Supabase Admin API.", userId);
    }
}
