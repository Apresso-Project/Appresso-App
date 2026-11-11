package com.appresso.backend.auth;

import com.appresso.backend.usuarios.Rol;

public record LoginResponse(String token, Rol rol, String nombre) {

}
