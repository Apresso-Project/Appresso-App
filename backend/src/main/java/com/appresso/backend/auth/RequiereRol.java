package com.appresso.backend.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.appresso.backend.usuarios.Rol;

/**
 * Marca un controlador (o un método) como accesible solo para los roles indicados (RF-04, RNF-07).
 *
 * <p>{@link ControlAccesoInterceptor} lo revisa antes de ejecutar la operación: sin sesión válida
 * responde 401 y con un rol no autorizado responde 403. Ejemplo:</p>
 * <pre>
 * &#64;RestController
 * &#64;RequiereRol(Rol.ADMINISTRADOR)
 * public class UsuarioController { ... }
 * </pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface RequiereRol {

	Rol[] value();

}
