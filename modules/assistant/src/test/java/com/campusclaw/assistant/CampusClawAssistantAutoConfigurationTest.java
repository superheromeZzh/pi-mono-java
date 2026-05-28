/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import javax.sql.DataSource;

import com.campusclaw.assistant.mapper.ChatMemoryMapper;
import com.campusclaw.assistant.memory.ChatMemoryRepository;
import com.campusclaw.assistant.memory.ChatMemoryStore;
import com.campusclaw.assistant.memory.MyBatisChatMemoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Unit tests for {@link CampusClawAssistantAutoConfiguration} that exercise the
 * bean factory methods directly (avoiding {@code @ComponentScan} side-effects)
 * and pin the conditional metadata introduced by d986437c.
 *
 * <p>The full context-driven matrix (enabled=true/false × DataSource present/absent
 * × MapperScan on/off classpath) is intentionally out of scope here — running the
 * real {@code @Configuration} pulls in the {@code com.campusclaw.assistant}
 * component scan (WebSocket gateway, MyBatis mapper scan, etc.) which would require
 * a full Spring Boot test context. The asserted metadata is what determines whether
 * the conditions kick in at runtime; the factory bodies are the only executable code.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/28]
 * @since [br_eCampusCore 25.1.0_Next]
 */
class CampusClawAssistantAutoConfigurationTest {

    @Nested
    class BeanFactoryMethods {

        private final CampusClawAssistantAutoConfiguration config = new CampusClawAssistantAutoConfiguration();

        @Test
        void assistantObjectMapperRegistersJavaTimeAndDisablesTimestamps() throws Exception {
            ObjectMapper mapper = config.assistantObjectMapper();

            assertFalse(
                    mapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS),
                    "WRITE_DATES_AS_TIMESTAMPS must be disabled for ISO-8601 output");

            OffsetDateTime ts = OffsetDateTime.of(2026, 5, 28, 15, 11, 35, 0, ZoneOffset.UTC);
            String json = mapper.writeValueAsString(ts);
            assertEquals("\"2026-05-28T15:11:35Z\"", json, "JavaTimeModule must be registered for OffsetDateTime");
        }

        @Test
        void assistantObjectMapperEachCallProducesFreshInstance() {
            ObjectMapper a = config.assistantObjectMapper();
            ObjectMapper b = config.assistantObjectMapper();
            assertNotSame(a, b, "@Bean factory should produce a new instance each call (Spring then scopes it)");
        }

        @Test
        void chatMemoryRepositoryReturnsMyBatisImpl() {
            ObjectMapper om = new ObjectMapper();
            ChatMemoryMapper mapper = mock(ChatMemoryMapper.class);

            ChatMemoryRepository repo = config.chatMemoryRepository(om, mapper);

            assertThat(repo).isInstanceOf(MyBatisChatMemoryRepository.class);
        }

        @Test
        void chatMemoryStoreDelegatesClearToRepository() {
            ChatMemoryRepository repo = mock(ChatMemoryRepository.class);
            ChatMemoryStore store = config.chatMemoryStore(repo);

            // Behavioral assertion: store must forward calls to the underlying repository.
            // A wiring break that swaps the field would fail this, where a smoke
            // test that only constructed the bean would silently pass.
            store.clear("conv-1");
            verify(repo).clear("conv-1");
        }
    }

    /**
     * Pin the four conditional annotations introduced by d986437c so a future
     * refactor that quietly removes one (and silently re-enables persistence
     * beans in mate integrators that should stay opt-in) fails this test.
     */
    @Nested
    class ConditionalMetadata {

        @Test
        void classLevelConditionalOnClassRequiresMapperScanAndDataSource() {
            assertTrue(
                    CampusClawAssistantAutoConfiguration.class.isAnnotationPresent(ConditionalOnClass.class),
                    "@ConditionalOnClass must be present on the configuration");
            ConditionalOnClass ann = CampusClawAssistantAutoConfiguration.class.getAnnotation(ConditionalOnClass.class);
            assertArrayEquals(
                    new Class<?>[] {MapperScan.class, DataSource.class},
                    ann.value(),
                    "guard must require both MapperScan and DataSource on the classpath");
        }

        @Test
        void classLevelConditionalOnPropertyIsOptIn() {
            assertTrue(
                    CampusClawAssistantAutoConfiguration.class.isAnnotationPresent(ConditionalOnProperty.class),
                    "@ConditionalOnProperty must be present");
            ConditionalOnProperty ann =
                    CampusClawAssistantAutoConfiguration.class.getAnnotation(ConditionalOnProperty.class);
            assertEquals("pi.assistant", ann.prefix());
            assertArrayEquals(new String[] {"enabled"}, ann.name());
            assertEquals("true", ann.havingValue());
            assertFalse(ann.matchIfMissing(), "must be opt-in (matchIfMissing=false)");
        }

        @Test
        void classIsAnnotatedConfigurationWithComponentScanAndMapperScan() {
            Class<?> c = CampusClawAssistantAutoConfiguration.class;
            assertTrue(c.isAnnotationPresent(Configuration.class));
            assertTrue(c.isAnnotationPresent(ComponentScan.class));
            assertTrue(c.isAnnotationPresent(MapperScan.class));
            assertTrue(c.isAnnotationPresent(EnableConfigurationProperties.class));

            MapperScan ms = c.getAnnotation(MapperScan.class);
            assertArrayEquals(new String[] {"com.campusclaw.assistant.mapper"}, ms.value());
        }

        @Test
        void chatMemoryRepositoryGatedOnDataSourceAndMissingBean() throws Exception {
            Method m = CampusClawAssistantAutoConfiguration.class.getMethod(
                    "chatMemoryRepository", ObjectMapper.class, ChatMemoryMapper.class);
            assertTrue(
                    m.isAnnotationPresent(ConditionalOnBean.class),
                    "@ConditionalOnBean(DataSource) must gate the repository bean");
            assertTrue(
                    m.isAnnotationPresent(ConditionalOnMissingBean.class),
                    "@ConditionalOnMissingBean(ChatMemoryRepository) must be present");

            ConditionalOnBean onBean = m.getAnnotation(ConditionalOnBean.class);
            assertArrayEquals(new Class<?>[] {DataSource.class}, onBean.value());

            ConditionalOnMissingBean onMissing = m.getAnnotation(ConditionalOnMissingBean.class);
            assertArrayEquals(new Class<?>[] {ChatMemoryRepository.class}, onMissing.value());
        }

        @Test
        void chatMemoryStoreGatedOnDataSourceAndMissingBean() throws Exception {
            Method m =
                    CampusClawAssistantAutoConfiguration.class.getMethod("chatMemoryStore", ChatMemoryRepository.class);
            assertTrue(
                    m.isAnnotationPresent(ConditionalOnBean.class),
                    "@ConditionalOnBean(DataSource) must gate the store bean");
            assertTrue(
                    m.isAnnotationPresent(ConditionalOnMissingBean.class),
                    "@ConditionalOnMissingBean(ChatMemoryStore) must be present");

            ConditionalOnBean onBean = m.getAnnotation(ConditionalOnBean.class);
            assertArrayEquals(new Class<?>[] {DataSource.class}, onBean.value());

            ConditionalOnMissingBean onMissing = m.getAnnotation(ConditionalOnMissingBean.class);
            assertArrayEquals(new Class<?>[] {ChatMemoryStore.class}, onMissing.value());
        }

        @Test
        void assistantObjectMapperGatedOnMissingBean() throws Exception {
            Method m = CampusClawAssistantAutoConfiguration.class.getMethod("assistantObjectMapper");
            assertTrue(
                    m.isAnnotationPresent(ConditionalOnMissingBean.class),
                    "@ConditionalOnMissingBean(ObjectMapper) must be present");
            ConditionalOnMissingBean onMissing = m.getAnnotation(ConditionalOnMissingBean.class);
            assertArrayEquals(new Class<?>[] {ObjectMapper.class}, onMissing.value());
        }
    }
}
