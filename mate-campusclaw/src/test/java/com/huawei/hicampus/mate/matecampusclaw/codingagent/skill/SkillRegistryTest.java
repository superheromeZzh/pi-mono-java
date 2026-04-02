package com.huawei.hicampus.mate.matecampusclaw.codingagent.skill;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SkillRegistryTest {

    SkillRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SkillRegistry();
    }

    private Skill skill(String name, boolean disableModelInvocation) {
        return new Skill(name, "Description for " + name,
                Path.of("/skills/" + name + "/SKILL.md"),
                Path.of("/skills/" + name),
                "project", disableModelInvocation);
    }

    // -------------------------------------------------------------------
    // register / getByName
    // -------------------------------------------------------------------

    @Nested
    class RegisterAndLookup {

        @Test
        void registersAndRetrievesByName() {
            Skill s = skill("commit", false);
            registry.register(s);

            var result = registry.getByName("commit");
            assertTrue(result.isPresent());
            assertEquals(s, result.get());
        }

        @Test
        void returnsEmptyForUnknownName() {
            assertTrue(registry.getByName("nonexistent").isEmpty());
        }

        @Test
        void overwritesDuplicateName() {
            Skill s1 = skill("commit", false);
            Skill s2 = new Skill("commit", "Updated",
                    Path.of("/other/SKILL.md"), Path.of("/other"), "user", false);

            registry.register(s1);
            registry.register(s2);

            assertEquals("Updated", registry.getByName("commit").orElseThrow().description());
        }
    }

    // -------------------------------------------------------------------
    // registerAll
    // -------------------------------------------------------------------

    @Nested
    class RegisterAll {

        @Test
        void registersMultipleSkills() {
            registry.registerAll(List.of(
                    skill("commit", false),
                    skill("review", false),
                    skill("test-runner", false)
            ));

            assertEquals(3, registry.getAll().size());
        }
    }

    // -------------------------------------------------------------------
    // getAll
    // -------------------------------------------------------------------

    @Nested
    class GetAll {

        @Test
        void returnsAllRegisteredSkills() {
            registry.register(skill("a", false));
            registry.register(skill("b", true));

            List<Skill> all = registry.getAll();
            assertEquals(2, all.size());
        }

        @Test
        void returnsEmptyListWhenEmpty() {
            assertTrue(registry.getAll().isEmpty());
        }

        @Test
        void preservesRegistrationOrder() {
            registry.register(skill("z-skill", false));
            registry.register(skill("a-skill", false));
            registry.register(skill("m-skill", false));

            List<Skill> all = registry.getAll();
            assertEquals("z-skill", all.get(0).name());
            assertEquals("a-skill", all.get(1).name());
            assertEquals("m-skill", all.get(2).name());
        }
    }

    // -------------------------------------------------------------------
    // getVisibleSkills
    // -------------------------------------------------------------------

    @Nested
    class GetVisibleSkills {

        @Test
        void filtersOutDisabledSkills() {
            registry.register(skill("visible", false));
            registry.register(skill("hidden", true));
            registry.register(skill("also-visible", false));

            List<Skill> visible = registry.getVisibleSkills();

            assertEquals(2, visible.size());
            assertTrue(visible.stream().noneMatch(Skill::disableModelInvocation));
        }

        @Test
        void returnsEmptyWhenAllDisabled() {
            registry.register(skill("hidden-a", true));
            registry.register(skill("hidden-b", true));

            assertTrue(registry.getVisibleSkills().isEmpty());
        }

        @Test
        void returnsAllWhenNoneDisabled() {
            registry.register(skill("a", false));
            registry.register(skill("b", false));

            assertEquals(2, registry.getVisibleSkills().size());
        }
    }

    // -------------------------------------------------------------------
    // clear
    // -------------------------------------------------------------------

    @Nested
    class Clear {

        @Test
        void removesAllSkills() {
            registry.register(skill("a", false));
            registry.register(skill("b", false));

            registry.clear();

            assertTrue(registry.getAll().isEmpty());
            assertTrue(registry.getByName("a").isEmpty());
        }
    }
}
