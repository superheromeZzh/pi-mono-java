/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PromptTemplateTest {

    @Nested
    class Expand {

        @Test
        void nullTemplateReturnsNull() {
            assertThat(PromptTemplate.expand(null, List.of("a"))).isNull();
        }

        @Test
        void emptyTemplateReturnsEmpty() {
            assertThat(PromptTemplate.expand("", List.of("a"))).isEmpty();
        }

        @Test
        void noArgsRemovesPlaceholders() {
            assertThat(PromptTemplate.expand("Hello $1 from $2", null)).isEqualTo("Hello  from ");
            assertThat(PromptTemplate.expand("Hello $1", List.of())).isEqualTo("Hello ");
        }

        @Test
        void positionalArgs() {
            assertThat(PromptTemplate.expand("Hello $1 from $2", List.of("World", "Earth")))
                    .isEqualTo("Hello World from Earth");
        }

        @Test
        void outOfRangeBecomesEmpty() {
            assertThat(PromptTemplate.expand("Hello $1 $5", List.of("World"))).isEqualTo("Hello World ");
        }

        @Test
        void allArgsJoined() {
            assertThat(PromptTemplate.expand("Args: $@", List.of("a", "b", "c")))
                    .isEqualTo("Args: a b c");
        }

        @Test
        void argCount() {
            assertThat(PromptTemplate.expand("Total: $#", List.of("a", "b", "c")))
                    .isEqualTo("Total: 3");
        }

        @Test
        void noPlaceholdersUnchanged() {
            assertThat(PromptTemplate.expand("Hello world", List.of("a"))).isEqualTo("Hello world");
        }
    }

    @Nested
    class HasParameters {

        @Test
        void detectsPositional() {
            assertThat(PromptTemplate.hasParameters("Hi $1")).isTrue();
        }

        @Test
        void detectsAtAndHash() {
            assertThat(PromptTemplate.hasParameters("Hi $@")).isTrue();
            assertThat(PromptTemplate.hasParameters("Hi $#")).isTrue();
        }

        @Test
        void plainTextFalse() {
            assertThat(PromptTemplate.hasParameters("Hello world")).isFalse();
        }

        @Test
        void nullFalse() {
            assertThat(PromptTemplate.hasParameters(null)).isFalse();
        }
    }
}
