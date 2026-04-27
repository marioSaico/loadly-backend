package com.loadly.backend.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "envio", indexes = {@Index(name = "ix_envio_idCliente", columnList = "idCliente")})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Envio {

    @Id
    @Column(nullable = false, length = 50)
    private String idEnvio;

    @Column(nullable = false)
    private LocalDateTime fechaRegistro;

    @Column(nullable = false)
    private LocalDateTime fechaLimiteEntrega;

    @Column(nullable = false)
    private int idAeropuertoOrigen;

    @Column(nullable = false)
    private int idAeropuertoDestino;

    @Column(nullable = false)
    private Integer cantidadMaletas;

    @Column(nullable = false)
    private Boolean planificado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idCliente", nullable = false)
    private Cliente cliente;
}
