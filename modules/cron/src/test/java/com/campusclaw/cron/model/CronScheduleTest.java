package com.campusclaw.cron.model;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CronScheduleTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void atScheduleRoundTrip() throws Exception {
        CronSchedule original = new CronSchedule.At(1711800000000L);
        String json = mapper.writeValueAsString(original);
        assertTrue(json.contains("\"type\":\"at\""));
        CronSchedule deserialized = mapper.readValue(json, CronSchedule.class);
        assertEquals(original, deserialized);
    }

    @Test
    void everyScheduleRoundTrip() throws Exception {
        CronSchedule original = new CronSchedule.Every(60000L);
        String json = mapper.writeValueAsString(original);
        assertTrue(json.contains("\"type\":\"every\""));
        CronSchedule deserialized = mapper.readValue(json, CronSchedule.class);
        assertEquals(original, deserialized);
    }

    @Test
    void cronExprScheduleRoundTrip() throws Exception {
        CronSchedule original = new CronSchedule.CronExpr("0 0 * * * *", "Asia/Shanghai");
        String json = mapper.writeValueAsString(original);
        assertTrue(json.contains("\"type\":\"cron\""));
        assertTrue(json.contains("Asia/Shanghai"));
        CronSchedule deserialized = mapper.readValue(json, CronSchedule.class);
        assertEquals(original, deserialized);
    }

    @Test
    void cronExprWithNullTimezone() throws Exception {
        CronSchedule original = new CronSchedule.CronExpr("0 30 9 * * MON-FRI", null);
        String json = mapper.writeValueAsString(original);
        CronSchedule deserialized = mapper.readValue(json, CronSchedule.class);
        assertInstanceOf(CronSchedule.CronExpr.class, deserialized);
        assertNull(((CronSchedule.CronExpr) deserialized).timezone());
    }

    @Test
    void cronJobRoundTrip() throws Exception {
        var job = CronJob.create("test-job", "A test job",
            new CronSchedule.Every(5000L),
            new CronPayload.AgentPrompt("do something", null, null, null));

        String json = mapper.writeValueAsString(job);
        CronJob deserialized = mapper.readValue(json, CronJob.class);

        assertEquals(job.id(), deserialized.id());
        assertEquals(job.name(), deserialized.name());
        assertEquals(job.description(), deserialized.description());
        assertEquals(job.enabled(), deserialized.enabled());
        assertEquals(job.schedule(), deserialized.schedule());
    }

    @Test
    void cronPayloadRoundTrip() throws Exception {
        var payload = new CronPayload.AgentPrompt(
            "run tests", "You are a tester", "claude-sonnet-4", java.util.List.of("bash", "read"));
        String json = mapper.writeValueAsString(payload);
        assertTrue(json.contains("\"type\":\"agent_prompt\""));
        CronPayload deserialized = mapper.readValue(json, CronPayload.class);
        assertEquals(payload, deserialized);
    }
}
