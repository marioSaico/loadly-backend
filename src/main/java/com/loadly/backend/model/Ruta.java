package com.loadly.backend.model;

import lombok.Data;
import java.util.List;

@Data
public class Ruta {

    private Envio envio;
    private List<PlanVuelo> vuelos;
    private long tiempoTotalMinutos;
    private EstadoRuta estado;
    //Es simplemente la posición dentro de la lista vuelos de la Ruta que indica en qué vuelo está viajando actualmente el grupo de maletas.
    //Sirve principalmente para el monitoreo en el mapa. Cuando el visualizador quiera saber dónde está una maleta en este momento
    private int indiceVueloActual; // 0, 1, 2... indica en qué vuelo va la maleta

}