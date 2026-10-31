
package com.incidentrouter;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/worker")
public class IncidentWorkerController {

    private final IncidentQueueWorker worker;

    public IncidentWorkerController(IncidentQueueWorker worker) {
        this.worker = worker;
    }

    @PostMapping("/process")
    public String processMessages() {
        int processed = worker.processAvailableMessages();
        return "Messages received and processed: " + processed;
    }
}
