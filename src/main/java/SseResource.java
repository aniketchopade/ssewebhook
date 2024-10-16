package com.example.demo;

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
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

@Path("/subscribe")
public class SseResource {

    @Context
    private Sse sse;

    private static final Map<String, WeakReference<SseEventSink>> eventSinks = Collections.synchronizedMap(new WeakHashMap<>());

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void subscribe(@Context SseEventSink eventSink) {
        new Thread(() -> {
            try (SseEventSink sink = eventSink) {
                for (int i = 0; i < 10; i++) {
                    sink.send(sse.newEvent("Event " + i));
                    TimeUnit.SECONDS.sleep(1);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    @GET
    @Path("/weather")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void getWeather(@QueryParam("zip") String zip, @Context SseEventSink eventSink) {
        new Thread(() -> {
            Client client = ClientBuilder.newClient();
            WebTarget target = client.target("http://api.openweathermap.org/data/2.5/weather")
                                     .queryParam("zip", zip)
                                     .queryParam("appid", "0bceea881cef1f9016e7f602491af2e0"); // Replace with your actual API key

            try (SseEventSink sink = eventSink) {
                Response response = target.request(MediaType.APPLICATION_JSON).get();
                if (response.getStatus() == 200) {
                    String jsonResponse = response.readEntity(String.class);
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode jsonNode = mapper.readTree(jsonResponse);
                    sink.send(sse.newEvent(jsonNode.toString()));
                } else {
                    sink.send(sse.newEvent("Error: Unable to fetch weather data"));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    @GET
    @Path("/webhooksse/{id}")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void webhookSse(@PathParam("id") String id, @Context SseEventSink eventSink) {
        eventSinks.put(id, new WeakReference<>(eventSink));
    }

    @GET
    @Path("/response/{id}")
    public void sendResponse(@PathParam("id") String id, @QueryParam("message") String message) {
        WeakReference<SseEventSink> weakRef = eventSinks.get(id);
        if (weakRef != null) {
            SseEventSink eventSink = weakRef.get();
            if (eventSink != null) {
                eventSink.send(sse.newEvent(message));
                eventSink.close();
                eventSinks.remove(id);
            }
        }
    }
}
