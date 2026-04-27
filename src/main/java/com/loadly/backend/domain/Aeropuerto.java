package com.loadly.backend.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "aeropuerto", indexes = {@Index(name = "ix_aeropuerto_codigo", columnList = "codigo")})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Aeropuerto {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idAeropuerto;

    @Column(nullable = false, length = 10)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String ciudad;

    @Column(nullable = false, length = 100)
    private String pais;

    @Column(nullable = false, length = 10)
    private String abreviatura;

    @Column(nullable = false)
    private Integer gmt;

    @Column(nullable = false)
    private Integer capacidad;

    @Column(nullable = false)
    private Double latitud;

    @Column(nullable = false)
    private Double longitud;

    @Column(nullable = false, length = 50)
    private String continente;
}
