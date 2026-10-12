package com.ipn.mx.fst.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Equivale a app/models.py :: User (tabla "users"). */
@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(nullable = false, unique = true, length = 255)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	@Column(nullable = false, length = 120)
	private String name;

	// SQLAlchemy creó un tipo ENUM nativo de PostgreSQL, no un varchar.
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(nullable = false, columnDefinition = "role")
	private Role role;

	@Column(name = "is_active", nullable = false)
	private Boolean isActive;

	@Column(name = "must_change_password", nullable = false)
	private Boolean mustChangePassword;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "last_login")
	private LocalDateTime lastLogin;

	public Integer getId() { return id; }
	public String getEmail() { return email; }
	public String getPasswordHash() { return passwordHash; }
	public String getName() { return name; }
	public Role getRole() { return role; }
	public Boolean getIsActive() { return isActive; }
	public Boolean getMustChangePassword() { return mustChangePassword; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public LocalDateTime getLastLogin() { return lastLogin; }
	public void setLastLogin(LocalDateTime lastLogin) { this.lastLogin = lastLogin; }
}
