package com.benitoventas.Scheduler;

import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.ScheduledExecution;
import io.smallrye.common.annotation.RunOnVirtualThread;

import com.benitoventas.Jobs.mongodb.panache.entity.Job; 
import com.benitoventas.Jobs.mongodb.panache.entity.TipoJob;
import com.benitoventas.Jobs.mongodb.panache.entity.EstadoJob;

import io.quarkus.logging.Log;

import java.util.List;

@ApplicationScoped
public class PlaceOrderBean {

    @Scheduled(every="5s")
    @RunOnVirtualThread
    void revisarPlaceOrderPendiente() {
        // encontrar Jobs en un listado del tipo PLACE_ORDER que estén PENDING
        List<Job> jobsPendientes = Job.list("tipo = ?1 and estado = ?2", TipoJob.PLACE_ORDER, EstadoJob.PENDING);
        // recorrer cada job y crear un log
        if (jobsPendientes.isEmpty()) {
            System.out.println("No se encontraron jobs pendientes de tipo PLACE_ORDER.");
        } else {
            System.out.println("Se encontraron " + jobsPendientes.size() + " jobs pendientes de tipo PLACE_ORDER.");
            for (Job job : jobsPendientes) {
                Log.info("Job encontrado " + job.id + " con estado " + job.getEstado() + " y tipo " + job.getTipo());
            }
        } 
    }
}
