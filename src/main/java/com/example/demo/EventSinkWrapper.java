package com.example.demo;

import javax.ws.rs.sse.SseEventSink;

public class EventSinkWrapper {
    private final SseEventSink eventSink;
    private final long timestamp;

    public EventSinkWrapper(SseEventSink eventSink) {
        this.eventSink = eventSink;
        this.timestamp = System.currentTimeMillis();
    }

    public SseEventSink getEventSink() {
        return eventSink;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
