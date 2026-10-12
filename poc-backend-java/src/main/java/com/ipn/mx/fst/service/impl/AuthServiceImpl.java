package com.ipn.mx.fst.service.impl;

import com.ipn.mx.fst.domain.User;
import com.ipn.mx.fst.dto.LoginResponse;
import com.ipn.mx.fst.repository.UserRepository;
import com.ipn.mx.fst.service.AuthService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

	private final UserRepository userRepository;

	public AuthServiceImpl(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	@Transactional
	public LoginResponse login(String email, String password) {
		Optional<User> encontrado = userRepository.findByEmail(email);
		if (encontrado.isEmpty() || !Boolean.TRUE.equals(encontrado.get().getIsActive())) {
			return null;
		}
		User user = encontrado.get();

		// Misma deuda que el backend de Flask: la contraseña todavía no se verifica.
		// TODO: BCryptPasswordEncoder.matches(password, user.getPasswordHash())

		user.setLastLogin(com.ipn.mx.fst.domain.Reloj.ahora());

		return new LoginResponse(
				"placeholder-jwt-" + user.getId(),
				new LoginResponse.Usuario(
						user.getId(),
						user.getEmail(),
						user.getName(),
						user.getRole().name(),
						user.getMustChangePassword()));
	}
}
