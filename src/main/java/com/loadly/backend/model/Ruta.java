package com.loadly.backend.model;

import lombok.Data;
import java.util.List;

@Data
public class Ruta {

    private Envio envio;
    private List<PlanVuelo> vuelos;
    private long tiempoTotalMinutos;
    private EstadoRuta estado;

}