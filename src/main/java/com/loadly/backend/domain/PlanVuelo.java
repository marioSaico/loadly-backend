package com.loadly.backend.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalTime;

@Entity
@Table(name = "plan_vuelo", indexes = {@Index(name = "ix_plan_vuelo_origen_destino", columnList = "idAeropuertoOrigen, idAeropuertoDestino")})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlanVuelo {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idPlanVuelo;

    @Column(nullable = false)
    private int idAeropuertoOrigen;

    @Column(nullable = false)
    private int idAeropuertoDestino;

    @Column(nullable = false)
    private LocalTime horaSalida;

    @Column(nullable = false)
    private LocalTime horaLlegada;

    @Column(nullable = false)
    private Integer capacidad;

    @Column(nullable = false)
    private Boolean cancelado;
}
