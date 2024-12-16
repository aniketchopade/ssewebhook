package com.example.demo;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.sse.OutboundSseEvent;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseEventSink;

import org.glassfish.jersey.media.sse.OutboundEvent;

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
import java.util.Iterator;
import java.util.Map;
import javax.json.Json;
import javax.json.JsonObject;
import javax.ws.rs.ext.MessageBodyWriter;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import io.lettuce.core.pubsub.RedisPubSubAdapter;

@Path("/subscribe")
public class SseResource {

    @Context
    private Sse sse;

    private static final ConcurrentHashMap<String, EventSinkWrapper> eventSinks = new ConcurrentHashMap<>();
    private ScheduledExecutorService cleanupExecutor;
    private static final Logger logger = Logger.getLogger(SseResource.class.getName());

    private RedisSubscriber redisSubscriber;

    @PostConstruct
    public void init() {
        //this will start subscription. This is important to start subscription before starting the server
        //Listener will make sure that the message is sent to the client
        redisSubscriber = new RedisSubscriber();
        redisSubscriber.setSse(sse);
        redisSubscriber.init();

        cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
        cleanupExecutor.scheduleAtFixedRate(this::cleanupEventSinks, 1, 1, TimeUnit.MINUTES);
    }

    @PreDestroy
    public void destroy() {
        if (cleanupExecutor != null && !cleanupExecutor.isShutdown()) {
            cleanupExecutor.shutdown();
        }
        redisSubscriber.destroy();
    }

    //webhooksse endpoint will make sure that the server knows about the browser connection
    //it will remember the eventSink object and will use it to send the response
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
            JsonObject jsonMessage = Json.createObjectBuilder()
                                         .add("message", message)
                                         .build();
            OutboundSseEvent event = sse.newEventBuilder()
                                .name("response")
                                .mediaType(MediaType.APPLICATION_JSON_TYPE)
                                .data(JsonObject.class, jsonMessage)
                                .build();
            eventSink.send(event);
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

    public static ConcurrentHashMap<String, EventSinkWrapper> getEventSinks() {
        return eventSinks;
    }

    private void cleanupEventSinks() {
        long currentTime = System.currentTimeMillis();
        eventSinks.forEach((id, wrapper) -> {
            eventSinks.computeIfPresent(id, (key, value) -> {
                SseEventSink sink = value.getEventSink();
                if (sink.isClosed() || (currentTime - value.getTimestamp()) > TimeUnit.MINUTES.toMillis(1)) {
                    logger.info("Removing closed or old SseEventSink with id: " + id);
                    sink.close();
                    return null; // Returning null removes the entry
                } else {
                    logger.info("SseEventSink with id: " + id + " is still open.");
                    return value;
                }
            });
        });
    }
}
