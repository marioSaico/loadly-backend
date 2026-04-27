package com.loadly.backend.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "registro_monitoreo", indexes = {@Index(name = "ix_registro_monitoreo_envio", columnList = "idEnvio")})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RegistroMonitoreo {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idRegistro;

    @Column(nullable = false, length = 50)
    private String estado;

    @Column(nullable = false)
    private LocalDateTime fechaHora;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idEnvio", nullable = false)
    private Envio envio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idAeropuerto", nullable = false)
    private Aeropuerto aeropuerto;
}
