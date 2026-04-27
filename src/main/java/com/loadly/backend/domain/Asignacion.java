package com.loadly.backend.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "asignacion", indexes = {@Index(name = "ix_asignacion_envio", columnList = "idEnvio")})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Asignacion {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idAsignacion;

    @Column(nullable = false)
    private Integer ordenRuta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idPlanVuelo", nullable = false)
    private PlanVuelo planVuelo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idEnvio", nullable = false)
    private Envio envio;
}
