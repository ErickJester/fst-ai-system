package com.ipn.mx.fst.service;

import com.ipn.mx.fst.dto.LoginResponse;

public interface AuthService {

	/** Devuelve null si las credenciales no son válidas o la cuenta está inactiva. */
	LoginResponse login(String email, String password);
}
