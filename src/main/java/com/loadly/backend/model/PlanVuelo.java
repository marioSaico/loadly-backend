package com.loadly.backend.model;

import lombok.Data;

@Data
public class PlanVuelo {

    private String origen;
    private String destino;
    private String horaSalida;
    private String horaLlegada;
    private int capacidad;
    private boolean cancelado;

}
