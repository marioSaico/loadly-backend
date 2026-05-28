package com.loadly.backend.loader;

import com.loadly.backend.model.Aeropuerto;
import com.loadly.backend.model.Envio;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class EnvioLoaderTest {

    @Autowired
    private EnvioLoader envioLoader;

    @Test
    void testCargaYFiltradoParalelo() {
        // 1. Preparar datos de prueba
        List<Aeropuerto> aeropuertos = new ArrayList<>();
        Aeropuerto lima = new Aeropuerto();
        lima.setCodigo("SPIM");
        lima.setGmt(-5);
        aeropuertos.add(lima);

        Map<String, List<String>> archivosRaw = new HashMap<>();
        List<String> lineas = Arrays.asList(
            "E001-20260525-10-00-KJFK-2-C001",
            "E002-20260525-11-00-KJFK-1-C002",
            "E003-20260525-12-00-KJFK-3-C003"
        );
        archivosRaw.put("_envios_SPIM_.txt", lineas);

        // 2. Cargar en memoria
        envioLoader.setArchivosEnMemoria(archivosRaw, aeropuertos);

        // 3. Probar carga parcial (ventana de tiempo)
        // El envío E001 es a las 10:00 Local Lima (GMT-5) -> 15:00 GMT.
        // Si ponemos límite 16:00 GMT (que equivale a las 11:00 am en Lima), debería entrar.
        
        List<Envio> pendientes = envioLoader.cargarPendientes(
            null, 
            "20260525-00-00", 
            "20260525-16-00", 
            aeropuertos
        );

        assertEquals(1, pendientes.size(), "Debería haber cargado solo 1 envío (el de las 15:00 GMT)");
        assertEquals("E001", pendientes.get(0).getIdEnvio());
        
        // 4. Probar siguiente ventana
        // E002 es 11:00 Local (16:00 GMT), E003 es 12:00 Local (17:00 GMT)
        // Ponemos límite 18:00 GMT
        pendientes = envioLoader.cargarPendientes(
            null, 
            "20260525-00-00", 
            "20260525-18-00", 
            aeropuertos
        );
        
        assertEquals(2, pendientes.size(), "Debería haber cargado los otros 2 envíos");
        assertTrue(pendientes.stream().anyMatch(e -> e.getIdEnvio().equals("E002")));
        assertTrue(pendientes.stream().anyMatch(e -> e.getIdEnvio().equals("E003")));
    }
}
