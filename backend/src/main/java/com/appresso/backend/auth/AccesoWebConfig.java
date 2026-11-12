package com.appresso.backend.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Activa el control de acceso por rol ({@link RequiereRol}) en todas las rutas. */
@Configuration
public class AccesoWebConfig implements WebMvcConfigurer {

	private final ControlAccesoInterceptor interceptor;

	public AccesoWebConfig(ControlAccesoInterceptor interceptor) {
		this.interceptor = interceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registro) {
		registro.addInterceptor(interceptor);
	}

}
