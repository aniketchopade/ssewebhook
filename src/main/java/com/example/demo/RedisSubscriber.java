package com.example.demo;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.json.Json;
import javax.json.JsonObject;
import javax.ws.rs.sse.OutboundSseEvent;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseEventSink;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import io.lettuce.core.RedisClient;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import io.lettuce.core.pubsub.RedisPubSubAdapter;

public class RedisSubscriber {

    private static final Logger logger = Logger.getLogger(RedisSubscriber.class.getName());

    private RedisClient redisClient;
    private StatefulRedisPubSubConnection<String, String> pubSubConnection;
    private RedisPubSubAsyncCommands<String, String> pubSubAsyncCommands;

    private Sse sse;

    public void setSse(Sse sse) {
        this.sse = sse;
    }

    @PostConstruct
    public void init() {
        redisClient = RedisClient.create("redis://localhost:6379");
        pubSubConnection = redisClient.connectPubSub();
        pubSubAsyncCommands = pubSubConnection.async();
        pubSubAsyncCommands.subscribe("sseresponse");
        logger.info("subscribing to redis channel");
        pubSubConnection.addListener(new RedisPubSubAdapter<String, String>() {
            @Override
            public void message(String channel, String message) {
                // Handle the message received from the Redis channel
                logger.info("Received message: " + message + " from channel: " + channel);

                // Access the eventSinks hashmap from SseResource using getter method and send the response
                ConcurrentHashMap<String, EventSinkWrapper> eventSinks = SseResource.getEventSinks();
                EventSinkWrapper wrapper = eventSinks.get(message);
                //if there is no matching wrapper for the message, then the message is not sent
                //this also means that browser never sent request to this instance of the server
                if (wrapper != null) {
                    System.out.println("wrapper found");
                    SseEventSink eventSink = wrapper.getEventSink();
                    JsonObject jsonObject = Json.createObjectBuilder()
                            .add("message", message)
                            .build();
                    OutboundSseEvent event = sse.newEventBuilder()
                            .mediaType(javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE)
                            .data(jsonObject.toString())
                            .build();
                    eventSink.send(event);
                    eventSink.close();
                    eventSinks.remove(channel);
                } else {
                    System.out.println("wrapper not found");
                }
            }
        });
    }

    @PreDestroy
    public void destroy() {
        if (pubSubAsyncCommands != null) {
            pubSubAsyncCommands.unsubscribe("sseresponse");
        }
        if (pubSubConnection != null) {
            pubSubConnection.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }
    }
}