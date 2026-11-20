package com.appresso.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.appresso.backend.auditoria.RegistroAuditoria;
import com.appresso.backend.auditoria.RegistroAuditoriaRepository;
import com.appresso.backend.correo.EnvioCorreo;
import com.appresso.backend.fidelizacion.Cliente;
import com.appresso.backend.fidelizacion.ClienteRepository;
import com.appresso.backend.usuarios.Rol;
import com.appresso.backend.usuarios.Usuario;
import com.appresso.backend.usuarios.UsuarioRepository;

/**
 * Recuperación de la contraseña del cliente con un código enviado al correo (RF-65), de punta a
 * punta. El envío de correo se reemplaza por un simulacro para leer el código.
 * <p>
 * No es {@code @Transactional}: el correo sale después del commit y los intentos fallidos deben
 * quedar guardados aunque la solicitud termine en error. Por eso cada prueba borra lo que creó.
 */
@SpringBootTest
class RecuperacionContrasenaTest {

	private static final String CLAVE = "Clave-Original-1";
	private static final String CLAVE_NUEVA = "Clave-Nueva-123";

	@Autowired WebApplicationContext context;
	@Autowired UsuarioRepository usuarios;
	@Autowired ClienteRepository clientes;
	@Autowired CodigoRecuperacionRepository codigos;
	@Autowired RegistroAuditoriaRepository auditoria;
	@Autowired PasswordEncoder codificador;
	@Autowired JdbcTemplate jdbc;

	@MockitoBean EnvioCorreo correo;

	MockMvc mvc;
	Usuario cuenta;
	String documento;
	private final List<Cliente> clientesCreados = new ArrayList<>();

	@BeforeEach
	void preparar() {
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
		documento = String.valueOf(1_000_000_000L + (long) (Math.random() * 8_000_000_000L));
		cuenta = crearCliente(documento);
	}

	@AfterEach
	void limpiar() {
		for (Cliente cliente : clientesCreados) {
			Long usuarioId = cliente.getUsuario().getId();
			codigos.deleteAll(codigos.findAll().stream().filter(c -> c.getUsuario().getId().equals(usuarioId)).toList());
			List<RegistroAuditoria> registros = auditoria.findAll().stream()
					.filter(r -> r.getUsuario() != null && usuarioId.equals(r.getUsuario().getId())).toList();
			auditoria.deleteAll(registros);
			clientes.deleteById(cliente.getId());
			usuarios.deleteById(usuarioId);
		}
		clientesCreados.clear();
	}

	@Test
	void conUnDocumentoRegistradoEnviaUnCodigoDeSeisDigitosAlCorreoDeLaCuenta() throws Exception {
		solicitar(documento)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mensaje").value(RecuperacionContrasenaService.MENSAJE_SOLICITUD));

		assertThat(codigoEnviado()).matches("\\d{6}");
	}

	@Test
	void conUnDocumentoNoRegistradoRespondeLoMismoYNoEnviaNada() throws Exception {
		solicitar("9999999999")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mensaje").value(RecuperacionContrasenaService.MENSAJE_SOLICITUD));

		verify(correo, after(500).never()).enviarCodigoRecuperacion(anyString(), anyString(), anyString(), anyLong());
	}

	@Test
	void conElCodigoCorrectoCambiaLaContrasenaYElCodigoNoSirveDosVeces() throws Exception {
		solicitar(documento).andExpect(status().isOk());
		String codigo = codigoEnviado();

		restablecer(documento, codigo, CLAVE_NUEVA)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mensaje").value(RecuperacionContrasenaService.MENSAJE_RESTABLECIDA));

		login(documento, CLAVE_NUEVA).andExpect(status().isOk());
		login(documento, CLAVE).andExpect(status().isUnauthorized());

		restablecer(documento, codigo, "Otra-Clave-456")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("El código no es válido o venció. Solicita uno nuevo."));
	}

	@Test
	void unCodigoIncorrectoNoCambiaLaContrasenaYAlQuintoFalloElCodigoDejaDeServir() throws Exception {
		solicitar(documento).andExpect(status().isOk());
		String codigo = codigoEnviado();
		String incorrecto = codigo.equals("000000") ? "111111" : "000000";

		for (int i = 0; i < RecuperacionContrasenaService.MAX_INTENTOS; i++) {
			restablecer(documento, incorrecto, CLAVE_NUEVA).andExpect(status().isBadRequest());
		}

		restablecer(documento, codigo, CLAVE_NUEVA).andExpect(status().isBadRequest());
		login(documento, CLAVE).andExpect(status().isOk());
	}

	@Test
	void unCodigoVencidoSeRechaza() throws Exception {
		solicitar(documento).andExpect(status().isOk());
		String codigo = codigoEnviado();
		CodigoRecuperacion guardado = codigos.findFirstByUsuarioIdOrderByFechaCreacionDescIdDesc(cuenta.getId()).orElseThrow();
		guardado.setExpira(Instant.now().minusSeconds(1));
		codigos.save(guardado);

		restablecer(documento, codigo, CLAVE_NUEVA).andExpect(status().isBadRequest());
		login(documento, CLAVE).andExpect(status().isOk());
	}

	@Test
	void dosSolicitudesSeguidasEnvianUnSoloCorreo() throws Exception {
		solicitar(documento).andExpect(status().isOk());
		solicitar(documento).andExpect(status().isOk());

		verify(correo, after(500).times(1)).enviarCodigoRecuperacion(anyString(), anyString(), anyString(), anyLong());
	}

	@Test
	void pedirUnCodigoNuevoInvalidaElAnterior() throws Exception {
		solicitar(documento).andExpect(status().isOk());
		String primero = codigoEnviado();
		// Simula que el primero se pidió hace dos minutos, para no chocar con la espera entre envíos.
		// La fecha de creación no se puede cambiar desde la entidad (updatable = false): se hace en SQL.
		jdbc.update("update codigos_recuperacion set fecha_creacion = ? where usuario_id = ?",
				java.sql.Timestamp.from(Instant.now().minusSeconds(120)), cuenta.getId());

		solicitar(documento).andExpect(status().isOk());
		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		verify(correo, timeout(3000).times(2)).enviarCodigoRecuperacion(eq(cuenta.getCorreo()), any(), captor.capture(), eq(15L));
		String segundo = captor.getAllValues().get(1);

		if (!primero.equals(segundo)) {
			restablecer(documento, primero, CLAVE_NUEVA).andExpect(status().isBadRequest());
		}
		restablecer(documento, segundo, CLAVE_NUEVA).andExpect(status().isOk());
	}

	@Test
	void cambiarLaContrasenaDesbloqueaLaCuenta() throws Exception {
		cuenta.setIntentosFallidos(5);
		cuenta.setBloqueadoHasta(Instant.now().plusSeconds(600));
		usuarios.save(cuenta);
		solicitar(documento).andExpect(status().isOk());

		restablecer(documento, codigoEnviado(), CLAVE_NUEVA).andExpect(status().isOk());

		login(documento, CLAVE_NUEVA).andExpect(status().isOk());
	}

	@Test
	void unaContrasenaNuevaFueraDe8A72CaracteresSeRechazaSinGastarElCodigo() throws Exception {
		solicitar(documento).andExpect(status().isOk());
		String codigo = codigoEnviado();

		restablecer(documento, codigo, "corta")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.contrasenaNueva").value("La contraseña debe tener entre 8 y 72 caracteres."));
		restablecer(documento, codigo, "ñ".repeat(40)).andExpect(status().isBadRequest());

		restablecer(documento, codigo, CLAVE_NUEVA).andExpect(status().isOk());
	}

	@Test
	void unCodigoQueNoTieneSeisDigitosDa400() throws Exception {
		restablecer(documento, "12ab", CLAVE_NUEVA)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.codigo").value("El código tiene 6 dígitos."));
	}

	// ---------------------------------------------------------------- apoyo

	private String codigoEnviado() {
		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		verify(correo, timeout(3000)).enviarCodigoRecuperacion(eq(cuenta.getCorreo()), any(), captor.capture(), eq(15L));
		return captor.getValue();
	}

	private ResultActions solicitar(String doc) throws Exception {
		return mvc.perform(post("/api/auth/recuperar").contentType(MediaType.APPLICATION_JSON)
				.content("{\"documento\":\"" + doc + "\"}"));
	}

	private ResultActions restablecer(String doc, String codigo, String nueva) throws Exception {
		return mvc.perform(post("/api/auth/restablecer").contentType(MediaType.APPLICATION_JSON)
				.content("{\"documento\":\"" + doc + "\",\"codigo\":\"" + codigo + "\",\"contrasenaNueva\":\"" + nueva + "\"}"));
	}

	private ResultActions login(String doc, String clave) throws Exception {
		return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"identificador\":\"" + doc + "\",\"contrasena\":\"" + clave + "\"}"));
	}

	private Usuario crearCliente(String doc) {
		Usuario u = new Usuario();
		u.setNombre("Cliente recuperación");
		u.setCorreo("recuperacion." + UUID.randomUUID() + "@appresso.test");
		u.setContrasenaHash(codificador.encode(CLAVE));
		u.setRol(Rol.CLIENTE);
		u.setActivo(true);
		Usuario guardado = usuarios.save(u);

		Cliente c = new Cliente();
		c.setNombre("Cliente");
		c.setApellido("Recuperación");
		c.setDocumento(doc);
		c.setCelular("3000000000");
		c.setAceptoTratamientoDatos(true);
		c.setFechaAceptacionDatos(Instant.now());
		c.setUsuario(guardado);
		clientesCreados.add(clientes.save(c));
		return guardado;
	}

}
