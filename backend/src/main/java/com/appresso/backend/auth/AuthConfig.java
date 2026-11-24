package com.appresso.backend.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.appresso.backend.correo.CorreoProperties;

@Configuration
@EnableConfigurationProperties({ JwtProperties.class, CorreoProperties.class })
// Para enviar el código de recuperación en otro hilo (EnvioCodigoRecuperacion).
@EnableAsync
public class AuthConfig {

	/** BCrypt con el costo por defecto. Las contraseñas nunca se guardan ni se comparan en texto plano. */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
