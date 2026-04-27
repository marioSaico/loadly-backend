package com.loadly.backend.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cliente", indexes = {@Index(name = "ix_cliente_idCliente", columnList = "idCliente")})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Cliente {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idCliente;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false, length = 50)
    private String rol;

    @Column(nullable = false, length = 100)
    private String contacto;
}
