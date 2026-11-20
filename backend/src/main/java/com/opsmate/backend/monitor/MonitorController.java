package com.opsmate.backend.monitor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;

/**
 * REST endpoints para el monitor automático de salud.
 *
 * <ul>
 *   <li>{@code GET /api/v1/monitor/status} devuelve el estado del monitor.</li>
 *   <li>{@code POST /api/v1/monitor/start} activa el monitor.</li>
 *   <li>{@code POST /api/v1/monitor/stop} desactiva el monitor.</li>
 *   <li>{@code POST /api/v1/monitor/interval} cambia el intervalo de verificación.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/monitor")
public class MonitorController {

    private final MonitorService monitorService;

    public MonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @GetMapping("/status")
    public MonitorService.MonitorStatus status() {
        return monitorService.status();
    }

    @PostMapping("/start")
    public ResponseEntity<MonitorService.MonitorStatus> start() {
        monitorService.start();
        return ResponseEntity.created(URI.create("/api/v1/monitor/status")).body(monitorService.status());
    }

    @PostMapping("/stop")
    public ResponseEntity<MonitorService.MonitorStatus> stop() {
        monitorService.stop();
        return ResponseEntity.ok(monitorService.status());
    }

    @PostMapping("/interval")
    public ResponseEntity<MonitorService.MonitorStatus> interval(@RequestBody IntervalRequest request) {
        monitorService.setInterval(request.seconds());
        return ResponseEntity.ok(monitorService.status());
    }

    /**
     * Cuerpo de la petición para cambiar el intervalo.
     *
     * @param seconds nuevo intervalo en segundos
     */
    public record IntervalRequest(long seconds) {
    }
}
