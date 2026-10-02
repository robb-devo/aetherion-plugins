import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { accountLevelOf, accountXpToNext, applyIncomingMitigation, attackSpreadTargets, compactChance, effectMultiplier, fromSkillXp, milestoneStatBonus, rollOutgoingHit, skillXpToNext, title, } from "../index.js";
describe("AetherionLevel parity", () => {
    it("uses flat 100 XP per level", () => {
        assert.equal(accountXpToNext(1), 100n);
        assert.equal(accountLevelOf(0n), 1);
        assert.equal(accountLevelOf(99n), 1);
        assert.equal(accountLevelOf(100n), 2);
        assert.equal(accountLevelOf(250n), 3);
    });
    it("compresses skill XP by 40", () => {
        assert.equal(fromSkillXp(39n), 0n);
        assert.equal(fromSkillXp(40n), 1n);
        assert.equal(fromSkillXp(400n), 10n);
    });
    it("grants milestone stats every 5 levels", () => {
        assert.equal(milestoneStatBonus(1), 0);
        assert.equal(milestoneStatBonus(5), 0);
        assert.equal(milestoneStatBonus(6), 1);
        assert.equal(milestoneStatBonus(25), 4);
    });
    it("maps rank titles", () => {
        assert.equal(title(1), "Adventurer");
        assert.equal(title(25), "Veteran");
        assert.equal(title(5000), "Aetherion");
    });
});
describe("SkillProgression parity", () => {
    it("matches early / late effect multipliers", () => {
        assert.ok(Math.abs(effectMultiplier(1) - 1.0) < 1e-9);
        assert.ok(Math.abs(effectMultiplier(25) - (1 + 24 * 0.0102 + 0.08)) < 1e-9);
    });
    it("lerps compact chance 0.6% → 2.8%", () => {
        assert.ok(Math.abs(compactChance(1) - 0.006) < 1e-12);
        assert.ok(Math.abs(compactChance(100) - 0.028) < 1e-12);
    });
    it("uses quadratic xp wall", () => {
        assert.equal(skillXpToNext(1), 32 + 11 + 0);
        assert.equal(skillXpToNext(50), 32 + 11 * 50 + Math.floor(2500 / 2) + 70);
    });
});
describe("Combat parity", () => {
    it("applies defense formula 100/(100+def*0.85)", () => {
        const mitigated = applyIncomingMitigation({ rawDamage: 100, defense: 280 });
        const expected = 100 * (100 / (100 + 280 * 0.85));
        assert.ok(Math.abs(mitigated - expected) < 1e-9);
    });
    it("rolls crit damage", () => {
        const hit = rollOutgoingHit({ damage: 50, critChance: 100, critDamage: 50 }, () => 0);
        assert.equal(hit.crit, true);
        assert.equal(hit.damage, 75);
    });
    it("counts attack spread targets", () => {
        assert.equal(attackSpreadTargets(100, () => 0.99), 1);
        assert.equal(attackSpreadTargets(130, () => 0), 2);
        assert.equal(attackSpreadTargets(130, () => 0.99), 1);
    });
});
