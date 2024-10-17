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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

@Path("/subscribe")
public class SseResource {

    @Context
    private Sse sse;

    private static final ConcurrentHashMap<String, EventSinkWrapper> eventSinks = new ConcurrentHashMap<>();
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
        eventSinks.put(id, new EventSinkWrapper(eventSink));
    }

    @GET
    @Path("/response/{id}")
    public void sendResponse(@PathParam("id") String id, @QueryParam("message") String message) {
        EventSinkWrapper wrapper = eventSinks.get(id);
        if (wrapper != null) {
            SseEventSink eventSink = wrapper.getEventSink();
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
                DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                                                               .withLocale(Locale.getDefault())
                                                               .withZone(ZoneId.systemDefault());
                while (!sink.isClosed()) {
                    StringBuilder message = new StringBuilder("Current map size: " + eventSinks.size() + "\n");
                    eventSinks.forEach((id, wrapper) -> {
                        String formattedTimestamp = formatter.format(Instant.ofEpochMilli(wrapper.getTimestamp()));
                        message.append("ID: ").append(id)
                               .append(", Timestamp: ").append(formattedTimestamp)
                               .append("\n");
                    });
                    sink.send(sse.newEvent(message.toString()));
                    TimeUnit.SECONDS.sleep(10);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private void cleanupEventSinks() {
        long currentTime = System.currentTimeMillis();
        eventSinks.forEach((id, wrapper) -> {
            SseEventSink sink = wrapper.getEventSink();
            if (sink.isClosed() || (currentTime - wrapper.getTimestamp()) > TimeUnit.MINUTES.toMillis(1)) {
                logger.info("Removing closed or old SseEventSink with id: " + id);
                sink.close();
                eventSinks.remove(id);
            } else {
                logger.info("SseEventSink with id: " + id + " is still open.");
            }
        });
    }
}
