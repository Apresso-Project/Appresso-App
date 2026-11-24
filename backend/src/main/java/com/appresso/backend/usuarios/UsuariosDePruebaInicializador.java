package com.appresso.backend.usuarios;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.appresso.backend.fidelizacion.Cliente;
import com.appresso.backend.fidelizacion.ClienteRepository;

/**
 * Crea un usuario de prueba por cada rol que no es administrador, solo con el perfil {@code dev},
 * para verificar el control de acceso con los cinco roles (SCRUM-51). El administrador lo crea
 * {@link AdminDePruebaInicializador}.
 * <p>
 * Los cuatro comparten la contraseña de la variable {@code DEV_USUARIOS_CONTRASENA} (en
 * {@code backend/.env}), nunca del repositorio. Igual que el administrador: si la variable está
 * vacía no se crea nada, y si un correo ya existe no se toca.
 * <p>
 * El cliente de prueba también tiene su registro de cliente, porque los clientes inician sesión con
 * su documento ({@value #DOCUMENTO_CLIENTE}) y no con el correo.
 */
@Component
@Profile("dev")
public class UsuariosDePruebaInicializador implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(UsuariosDePruebaInicializador.class);
	private static final int MAX_BYTES_CONTRASENA = 72;

	/** Correo y nombre de cada usuario de prueba. */
	static final Map<Rol, String[]> USUARIOS = Map.of(
			Rol.CAJERO, new String[] { "cajero@appresso.test", "Cajero de prueba" },
			Rol.MESERO, new String[] { "mesero@appresso.test", "Mesero de prueba" },
			Rol.COCINERO, new String[] { "cocinero@appresso.test", "Cocinero de prueba" },
			Rol.CLIENTE, new String[] { "cliente@appresso.test", "Cliente de prueba" });

	/** Documento con el que inicia sesión el cliente de prueba. */
	static final String DOCUMENTO_CLIENTE = "1000000001";

	private final UsuarioRepository usuarios;
	private final ClienteRepository clientes;
	private final PasswordEncoder codificador;
	private final String contrasena;

	public UsuariosDePruebaInicializador(UsuarioRepository usuarios, ClienteRepository clientes,
			PasswordEncoder codificador, @Value("${appresso.dev.usuarios-contrasena:}") String contrasena) {
		this.usuarios = usuarios;
		this.clientes = clientes;
		this.codificador = codificador;
		this.contrasena = contrasena;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (contrasena.isBlank()) {
			log.info("DEV_USUARIOS_CONTRASENA está vacía: no se crean los usuarios de prueba de los demás roles.");
			return;
		}
		if (contrasena.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES_CONTRASENA) {
			log.warn("DEV_USUARIOS_CONTRASENA supera los {} bytes que admite BCrypt: no se crean los usuarios de prueba.",
					MAX_BYTES_CONTRASENA);
			return;
		}
		USUARIOS.forEach((rol, datos) -> crearSiNoExiste(rol, datos[0], datos[1]));
	}

	private void crearSiNoExiste(Rol rol, String correo, String nombre) {
		if (usuarios.findByCorreoIgnoreCase(correo).isPresent()) {
			log.info("El usuario de prueba {} ya existe; no se modifica.", correo);
			return;
		}
		Usuario usuario = new Usuario();
		usuario.setNombre(nombre);
		usuario.setCorreo(correo);
		usuario.setContrasenaHash(codificador.encode(contrasena));
		usuario.setRol(rol);
		usuarios.save(usuario);
		if (rol == Rol.CLIENTE && !clientes.existsByDocumento(DOCUMENTO_CLIENTE)) {
			Instant ahora = Instant.now();
			Cliente cliente = new Cliente();
			cliente.setNombre("Cliente");
			cliente.setApellido("de prueba");
			cliente.setDocumento(DOCUMENTO_CLIENTE);
			cliente.setCelular("3000000000");
			cliente.setAceptoTratamientoDatos(true);
			cliente.setFechaAceptacionDatos(ahora);
			cliente.setFechaRegistro(ahora);
			cliente.setUsuario(usuario);
			clientes.save(cliente);
			log.info("Usuario de prueba creado: {} ({}), inicia sesión con el documento {}", correo, rol, DOCUMENTO_CLIENTE);
			return;
		}
		log.info("Usuario de prueba creado: {} ({})", correo, rol);
	}

}
