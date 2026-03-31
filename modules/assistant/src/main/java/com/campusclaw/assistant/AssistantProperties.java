package com.campusclaw.assistant;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pi.assistant")
public class AssistantProperties {

    private Memory memory = new Memory();
    private Task task = new Task();
    private Channel channel = new Channel();

    public Memory getMemory() {
        return memory;
    }

    public void setMemory(Memory memory) {
        this.memory = memory;
    }

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public static class Memory {
        // Placeholder for future memory configuration extensions
    }

    public static class Task {
        private String baseDir = "./data/tasks";

        public String getBaseDir() {
            return baseDir;
        }

        public void setBaseDir(String baseDir) {
            this.baseDir = baseDir;
        }
    }

    public static class Channel {
        // Placeholder for future channel configuration extensions
    }
}
