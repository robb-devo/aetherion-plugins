/**
 * Cross-plugin service interfaces and {@link de.aetherion.core.api.AetherServices} registry.
 * First-party plugins register implementations on enable. FancyNpcs stays on
 * {@link de.aetherion.core.npc.FancyNpcFacade} (reflection, no compile dep).
 * WorldEdit, DiscordSRV, and TAB stay on reflection — they are external plugins.
 *
 * <p>QA test bots: {@link de.aetherion.core.api.TestBotsAccess} (AetherionStressBots).
 */
package de.aetherion.core.api;
