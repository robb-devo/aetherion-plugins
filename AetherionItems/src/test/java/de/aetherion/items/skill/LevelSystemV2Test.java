package de.aetherion.items.skill;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Level-system v2 (2026-11-27): talent unlock schedule and the one-time carry-over of old talent levels. */
class LevelSystemV2Test {

    @Test
    void firstThreeTalentsOfEverySkillAreOpenAtOnce() {
        for (AetherSkill.Category category : AetherSkill.Category.values()) {
            List<AetherSkill> talents = TalentUnlocks.of(category);
            for (int i = 0; i < Math.min(3, talents.size()); i++) {
                assertEquals(1, TalentUnlocks.level(talents.get(i)), talents.get(i).id());
            }
        }
    }

    @Test
    void laterTalentsOpenInOrderBetweenFiveAndHundred() {
        for (AetherSkill.Category category : AetherSkill.Category.values()) {
            List<AetherSkill> talents = TalentUnlocks.of(category);
            int last = 1;
            for (AetherSkill talent : talents) {
                int at = TalentUnlocks.level(talent);
                assertTrue(at >= last, category + " out of order at " + talent.id());
                assertTrue(at == 1 || (at >= 5 && at <= 100), talent.id() + " opens at " + at);
                last = at;
            }
        }
        // Dungeon has four talents: all open with the page.
        for (AetherSkill talent : TalentUnlocks.of(AetherSkill.Category.DUNGEON)) {
            assertEquals(1, TalentUnlocks.level(talent));
        }
    }

    @Test
    void nextTalentAndOpenedBetween() {
        AetherSkill.Category mining = AetherSkill.Category.MINING;
        AetherSkill next = TalentUnlocks.next(mining, 1);
        assertEquals(5, TalentUnlocks.level(next));
        assertTrue(TalentUnlocks.openedBetween(mining, 4, 5).contains(next));
        assertTrue(TalentUnlocks.openedBetween(mining, 5, 5).isEmpty());
        assertNull(TalentUnlocks.next(mining, 150));
    }

    @Test
    void carryOverKeepsBestLevelPerSkillAndEveryXp() {
        Map<AetherSkill, Integer> old = new EnumMap<>(AetherSkill.class);
        old.put(AetherSkill.HEAVY_HANDS, 80);
        old.put(AetherSkill.THICK_SKIN, 10);
        old.put(AetherSkill.DEPTH_GAUGE, 42);      // a late Mining talent the player already used
        old.put(AetherSkill.GOLDEN_HOUR, 95);      // Utility, higher than any real skill
        SkillService.PlayerSkills skills = SkillService.carriedOver(old, AetherSkill.LONG_ARM.id());

        assertEquals(80, skills.skillLevel(AetherSkill.Category.COMBAT));
        assertEquals(42, skills.skillLevel(AetherSkill.Category.MINING));
        assertEquals(1, skills.skillLevel(AetherSkill.Category.FISHING));
        // Utility follows the best skill but never drops below the old best Utility talent.
        assertEquals(95, skills.skillLevel(AetherSkill.Category.UTILITY));

        long raw = SkillProgression.spentXp(80) + SkillProgression.spentXp(10)
                + SkillProgression.spentXp(42) + SkillProgression.spentXp(95)
                + (AetherSkill.values().length - 4L) * SkillProgression.spentXp(1);
        assertEquals(raw, skills.carried(), "Aetherion pool keeps everything the old levels were worth");

        assertTrue(skills.keeps(AetherSkill.DEPTH_GAUGE.id()), "used talents stay open");
        assertTrue(skills.keeps(AetherSkill.LONG_ARM.id()), "equipped talents stay open");
        assertFalse(skills.keeps(AetherSkill.SEAM_READER.id()));
    }

    @Test
    void slotLevelsOpenSevenSlots() {
        assertEquals(1, SkillService.levelSlots(1));
        assertEquals(2, SkillService.levelSlots(5));
        assertEquals(4, SkillService.levelSlots(20));
        assertEquals(7, SkillService.levelSlots(75));
        assertEquals(7, SkillService.levelSlots(400));
    }
}
