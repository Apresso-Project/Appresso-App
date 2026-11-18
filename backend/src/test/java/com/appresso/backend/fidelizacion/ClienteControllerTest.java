package com.appresso.backend.fidelizacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.appresso.backend.auditoria.RegistroAuditoria;
import com.appresso.backend.auditoria.RegistroAuditoriaRepository;
import com.appresso.backend.auth.JwtService;
import com.appresso.backend.usuarios.Rol;
import com.appresso.backend.usuarios.Usuario;
import com.appresso.backend.usuarios.UsuarioRepository;

/**
 * Pruebas de integración de la gestión de clientes y cumplimiento legal de la Ley 1581 de 2012:
 * 1. Exigencia de consentimiento explícito en el registro (falla si es falso o nulo).
 * 2. Trazabilidad del consentimiento en auditoría inmutable (CONSENTIMIENTO_DATOS_1581).
 * 3. Mecanismo de solicitud y ejecución de supresión de datos (SUPRESION_DATOS_LEY_1581).
 */
@SpringBootTest
@Transactional
class ClienteControllerTest {

	@Autowired WebApplicationContext context;
	@Autowired ClienteRepository clientes;
	@Autowired UsuarioRepository usuarios;
	@Autowired RegistroAuditoriaRepository auditoria;
	@Autowired JwtService jwt;

	MockMvc mvc;
	Usuario mesero;
	String tokenMesero;
	Usuario admin;
	String tokenAdmin;

	@BeforeEach
	void preparar() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

		mesero = new Usuario();
		mesero.setNombre("Carlos Mesero");
		mesero.setCorreo("mesero." + UUID.randomUUID() + "@appresso.test");
		mesero.setContrasenaHash("bcrypt-hash");
		mesero.setRol(Rol.MESERO);
		mesero.setActivo(true);
		usuarios.save(mesero);
		tokenMesero = jwt.generar(mesero);

		admin = new Usuario();
		admin.setNombre("Admin General");
		admin.setCorreo("admin." + UUID.randomUUID() + "@appresso.test");
		admin.setContrasenaHash("bcrypt-hash");
		admin.setRol(Rol.ADMINISTRADOR);
		admin.setActivo(true);
		usuarios.save(admin);
		tokenAdmin = jwt.generar(admin);
	}

	@Test
	void registrarCliente_exigeAceptacionExplicitaLey1581_fallaSiNoAcepta() throws Exception {
		String cuerpoRechazado = """
				{
				  "nombre": "Pepito",
				  "apellido": "Perez",
				  "documento": "111222333",
				  "celular": "3001234567",
				  "aceptoTratamientoDatos": false
				}
				""";

		mvc.perform(post("/api/clientes")
						.header("Authorization", "Bearer " + tokenMesero)
						.contentType(MediaType.APPLICATION_JSON)
						.content(cuerpoRechazado))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje", containsString("exige la aceptación explícita")));
	}

	@Test
	void registrarCliente_conConsentimiento_guardaClienteYRegistraAuditoria() throws Exception {
		String documento = "1098" + System.currentTimeMillis() % 100000;
		String cuerpoAceptado = """
				{
				  "nombre": "Ana",
				  "apellido": "Gomez",
				  "documento": "%s",
				  "celular": "3109876543",
				  "aceptoTratamientoDatos": true
				}
				""".formatted(documento);

		mvc.perform(post("/api/clientes")
						.header("Authorization", "Bearer " + tokenMesero)
						.contentType(MediaType.APPLICATION_JSON)
						.content(cuerpoAceptado))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.documento").value(documento))
				.andExpect(jsonPath("$.aceptoTratamientoDatos").value(true))
				.andExpect(jsonPath("$.fechaAceptacionDatos").isNotEmpty());

		Cliente guardado = clientes.findByDocumento(documento).orElseThrow();
		assertThat(guardado.isAceptoTratamientoDatos()).isTrue();
		assertThat(guardado.getFechaAceptacionDatos()).isNotNull();

		List<RegistroAuditoria> logs = auditoria.findAll().stream()
				.filter(a -> "CONSENTIMIENTO_DATOS_1581".equals(a.getTipoOperacion())
						&& documento.equals(guardado.getDocumento()))
				.toList();
		assertThat(logs).isNotEmpty();
		assertThat(logs.get(0).getDetalle()).contains("Ley 1581");
	}

	@Test
	void suprimirDatos_registraOperacionEnAuditoria() throws Exception {
		Cliente c = new Cliente();
		c.setNombre("Laura");
		c.setApellido("Suarez");
		c.setDocumento("999888777");
		c.setCelular("3150000000");
		c.setAceptoTratamientoDatos(true);
		c.setPuntos(10);
		Cliente guardado = clientes.save(c);

		mvc.perform(post("/api/clientes/" + guardado.getId() + "/solicitar-eliminacion")
						.header("Authorization", "Bearer " + tokenAdmin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"motivo\": \"Solicitud formal de supresión de datos Ley 1581\"}"))
				.andExpect(status().isNoContent());

		List<RegistroAuditoria> logs = auditoria.findAll().stream()
				.filter(a -> "SUPRESION_DATOS_LEY_1581".equals(a.getTipoOperacion()))
				.toList();
		assertThat(logs).isNotEmpty();
		assertThat(logs.get(logs.size() - 1).getDetalle()).contains("Solicitud formal de supresión");
	}

}
