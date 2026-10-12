package com.ipn.mx.fst.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Equivale a app/models.py :: Experiment (tabla "experiments"). */
@Entity
@Table(name = "experiments")
public class Experiment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "user_id", nullable = false)
	private Integer userId;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(name = "experiment_date")
	private LocalDateTime experimentDate;

	@Column(length = 255)
	private String treatment;

	@Column(length = 120)
	private String species;

	@Column(nullable = false, length = 20)
	private String layout;

	@Column(columnDefinition = "text")
	private String notes;

	@PrePersist
	void alCrear() {
		if (createdAt == null) createdAt = Reloj.ahora();
		if (layout == null) layout = "auto";
	}

	public Integer getId() { return id; }
	public Integer getUserId() { return userId; }
	public void setUserId(Integer userId) { this.userId = userId; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public LocalDateTime getExperimentDate() { return experimentDate; }
	public void setExperimentDate(LocalDateTime d) { this.experimentDate = d; }
	public String getTreatment() { return treatment; }
	public void setTreatment(String t) { this.treatment = t; }
	public String getSpecies() { return species; }
	public void setSpecies(String s) { this.species = s; }
	public String getLayout() { return layout; }
	public void setLayout(String l) { this.layout = l; }
	public String getNotes() { return notes; }
	public void setNotes(String n) { this.notes = n; }
}
