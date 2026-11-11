package com.appresso.backend.usuarios;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crea un administrador de prueba al arrancar, solo con el perfil {@code dev}, para poder iniciar
 * sesión en un entorno local recién creado.
 * <p>
 * No se hace con una migración de Flyway porque esas corren también en producción y dejarían
 * una cuenta de administrador con una contraseña conocida. Aquí la contraseña sale de la variable
 * {@code DEV_ADMIN_CONTRASENA} (en {@code backend/.env}), nunca del repositorio, y sin el perfil
 * {@code dev} esta clase ni siquiera se carga.
 * <p>
 * Si el correo ya existe no se toca: cambiar la variable no cambia la contraseña de una cuenta ya
 * creada. Para empezar de cero basta con borrar el usuario o la base local.
 */
@Component
@Profile("dev")
public class AdminDePruebaInicializador implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminDePruebaInicializador.class);
	private static final int MAX_BYTES_CONTRASENA = 72;

	private final UsuarioRepository usuarios;
	private final PasswordEncoder codificador;
	private final String correo;
	private final String contrasena;

	public AdminDePruebaInicializador(UsuarioRepository usuarios, PasswordEncoder codificador,
			@Value("${appresso.dev.admin-correo:admin@appresso.test}") String correo,
			@Value("${appresso.dev.admin-contrasena:}") String contrasena) {
		this.usuarios = usuarios;
		this.codificador = codificador;
		this.correo = correo;
		this.contrasena = contrasena;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (contrasena.isBlank()) {
			log.warn("Perfil dev activo pero DEV_ADMIN_CONTRASENA está vacía: no se crea el administrador de prueba.");
			return;
		}
		if (contrasena.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES_CONTRASENA) {
			log.warn("DEV_ADMIN_CONTRASENA supera los {} bytes que admite BCrypt: no se crea el administrador de prueba.",
					MAX_BYTES_CONTRASENA);
			return;
		}
		if (usuarios.findByCorreoIgnoreCase(correo).isPresent()) {
			log.info("El administrador de prueba {} ya existe; no se modifica.", correo);
			return;
		}

		Usuario admin = new Usuario();
		admin.setNombre("Administrador de prueba");
		admin.setCorreo(correo);
		admin.setContrasenaHash(codificador.encode(contrasena));
		admin.setRol(Rol.ADMINISTRADOR);
		usuarios.save(admin);
		log.info("Administrador de prueba creado: {}", correo);
	}

}
