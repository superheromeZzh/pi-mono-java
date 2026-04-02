package com.huawei.hicampus.mate.matecampusclaw.tui.component;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UndoStackTest {

    private static UndoStack<String> stringStack() {
        return new UndoStack<>(s -> s); // Strings are immutable, identity clone is fine
    }

    private static UndoStack<List<String>> listStack() {
        return new UndoStack<>(ArrayList::new); // Deep copy
    }

    @Nested
    class PushAndPop {

        @Test
        void pushAndPopReturnsState() {
            var stack = stringStack();
            stack.push("hello");
            assertEquals("hello", stack.pop());
        }

        @Test
        void popEmptyReturnsNull() {
            var stack = stringStack();
            assertNull(stack.pop());
        }

        @Test
        void lifoOrder() {
            var stack = stringStack();
            stack.push("a");
            stack.push("b");
            stack.push("c");
            assertEquals("c", stack.pop());
            assertEquals("b", stack.pop());
            assertEquals("a", stack.pop());
            assertNull(stack.pop());
        }

        @Test
        void deepClonesOnPush() {
            var stack = listStack();
            List<String> list = new ArrayList<>(List.of("a", "b"));
            stack.push(list);
            list.add("c"); // mutate original
            List<String> popped = stack.pop();
            assertEquals(2, popped.size()); // clone should be unaffected
        }
    }

    @Nested
    class SizeAndEmpty {

        @Test
        void emptyStackHasSizeZero() {
            var stack = stringStack();
            assertEquals(0, stack.size());
            assertTrue(stack.isEmpty());
        }

        @Test
        void sizeIncreasesWithPush() {
            var stack = stringStack();
            stack.push("a");
            assertEquals(1, stack.size());
            assertFalse(stack.isEmpty());
            stack.push("b");
            assertEquals(2, stack.size());
        }

        @Test
        void sizeDecreasesWithPop() {
            var stack = stringStack();
            stack.push("a");
            stack.push("b");
            stack.pop();
            assertEquals(1, stack.size());
        }
    }

    @Nested
    class Clear {

        @Test
        void clearRemovesAll() {
            var stack = stringStack();
            stack.push("a");
            stack.push("b");
            stack.clear();
            assertTrue(stack.isEmpty());
            assertNull(stack.pop());
        }
    }
}
