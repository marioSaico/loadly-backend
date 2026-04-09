package com.loadly.backend.model;

import lombok.Data;

@Data
public class Aeropuerto {

    private int id;
    private String codigo;
    private String ciudad;
    private String pais;
    private String abreviatura;
    private int gmt;
    private int capacidad;
    private String latitud;
    private String longitud;

}
