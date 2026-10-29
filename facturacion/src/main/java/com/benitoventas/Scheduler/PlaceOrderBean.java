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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@ApplicationScoped
public class PlaceOrderBean {

    @Channel("place-order")
    Emitter<String> placeOrderEmitter;

    @Scheduled(every="5s")
    @RunOnVirtualThread
    void revisarPlaceOrderPendiente() {
        // encontrar Jobs en un listado del tipo PLACE_ORDER que estén PENDING
        List<Job> jobsPendientes;
        try {
          jobsPendientes = Job.list("tipo = ?1 and estado = ?2", TipoJob.PLACE_ORDER, EstadoJob.PENDING);
        } catch (RuntimeException e) {
          Log.error("Error al consultar jobs pendientes de tipo PLACE_ORDER", e);
          return;
        }
        // recorrer cada job y crear un log
        if (jobsPendientes.isEmpty()) {
            System.out.println("No se encontraron jobs pendientes de tipo PLACE_ORDER.");
        } else {
            System.out.println("Se encontraron " + jobsPendientes.size() + " jobs pendientes de tipo PLACE_ORDER.");
            for (Job job : jobsPendientes) {
                Log.info("Job encontrado " + job.id + " con estado " + job.getEstado() + " y tipo " + job.getTipo());
                try {
                    placeOrderEmitter.send(job.getContenido())
                            .toCompletableFuture()
                            .get(10, TimeUnit.SECONDS);

                    Job.update(
                            "estado = ?1, processed_at = ?2",
                            EstadoJob.PROCESSED, java.time.Instant.now())
                            .where("_id", job.id);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.error("Ejecución interrumpida para Job " + job.id, e);
                    return;
                } catch (ExecutionException | TimeoutException e) {
                    Log.error("No se pudo confirmar publicación del Job " + job.id, e);
                } catch (RuntimeException e) {
                    Log.error("Error al iniciar envío o actualizar Job " + job.id, e);
                }
            }
        } 
    }
}
