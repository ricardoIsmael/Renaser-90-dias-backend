package com.renaser.os.shared.web;

import com.renaser.os.shared.web.security.ActorAutenticadoArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ActorAutenticadoArgumentResolver actorAutenticadoArgumentResolver;

    public WebMvcConfig(ActorAutenticadoArgumentResolver actorAutenticadoArgumentResolver) {
        this.actorAutenticadoArgumentResolver = actorAutenticadoArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(actorAutenticadoArgumentResolver);
    }
}
