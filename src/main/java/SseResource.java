package com.example.demo;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseEventSink;
import javax.ws.rs.sse.SseEvent;
import javax.ws.rs.core.Context;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Path("/subscribe")
public class SseResource {

    @Context
    private Sse sse;

    private static final ConcurrentHashMap<String, SseEventSink> eventSinks = new ConcurrentHashMap<>();
    private ScheduledExecutorService cleanupExecutor;
    private static final Logger logger = Logger.getLogger(SseResource.class.getName());

    @PostConstruct
    public void init() {
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
        cleanupExecutor.scheduleAtFixedRate(this::cleanupEventSinks, 1, 1, TimeUnit.MINUTES);
    }

    @PreDestroy
    public void destroy() {
        if (cleanupExecutor != null && !cleanupExecutor.isShutdown()) {
            cleanupExecutor.shutdown();
        }
    }

    @GET
    @Path("/webhooksse/{id}")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void webhookSse(@PathParam("id") String id, @Context SseEventSink eventSink) {
        eventSinks.put(id, eventSink);
    }

    @GET
    @Path("/response/{id}")
    public void sendResponse(@PathParam("id") String id, @QueryParam("message") String message) {
        SseEventSink eventSink = eventSinks.get(id);
        if (eventSink != null) {
            eventSink.send(sse.newEvent(message));
            eventSink.close();
            eventSinks.remove(id);
        }
    }

    @GET
    @Path("/advertise")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void advertise(@Context SseEventSink eventSink) {
        new Thread(() -> {
            try (SseEventSink sink = eventSink) {
                while (!sink.isClosed()) {
                    int size = eventSinks.size();
                    sink.send(sse.newEvent("Current map size: " + size));
                    TimeUnit.SECONDS.sleep(10);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private void cleanupEventSinks() {
        eventSinks.forEach((id, sink) -> {
            if (sink.isClosed()) {
                logger.info("Removing closed SseEventSink with id: " + id);
                eventSinks.remove(id);
            } else {
                logger.info("SseEventSink with id: " + id + " is still open.");
            }
        });
    }
}
