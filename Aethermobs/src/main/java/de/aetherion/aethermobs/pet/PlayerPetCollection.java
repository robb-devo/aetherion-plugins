package de.aetherion.aethermobs.pet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class PlayerPetCollection {

    private static final int MAX_EQUIPPED_PETS = 1;

    private final UUID owner;

    private final List<PetInstance> pets;
    private final List<PetInstance> equippedPets;
    private final Set<String> sighted;
    private final Set<String> catchXpClaimed;

    public PlayerPetCollection(
            UUID owner
    ) {
        this.owner = owner;

        this.pets =
                new ArrayList<>();

        this.equippedPets =
                new ArrayList<>();

        this.sighted =
                new LinkedHashSet<>();

        this.catchXpClaimed =
                new LinkedHashSet<>();
    }

    public UUID getOwner() {
        return owner;
    }

    public void addPet(
            PetInstance pet
    ) {

        if (pet == null) {
            return;
        }

        if (pets.contains(pet)) {
            return;
        }

        pets.add(pet);

        if (pet.getDefinition() != null) {

            markSighted(
                    pet.getDefinition()
                            .getId()
            );
        }
    }

    public boolean removePet(
            PetInstance pet
    ) {

        if (pet == null) {
            return false;
        }

        if (equippedPets.contains(pet)) {
            unequipPet(pet);
        }

        return pets.remove(pet);
    }

    public List<PetInstance> getPets() {

        return Collections.unmodifiableList(
                pets
        );
    }

    public int getSize() {
        return pets.size();
    }

    public boolean contains(
            PetInstance pet
    ) {

        return pets.contains(pet);
    }

    public boolean equipPet(
            PetInstance pet
    ) {

        if (pet == null) {
            return false;
        }

        if (!pets.contains(pet)) {
            return false;
        }

        if (equippedPets.contains(pet)) {
            return true;
        }

        /*
         * Only one pet can be equipped.
         *
         * Equipping another pet automatically
         * replaces the currently equipped pet.
         */
        if (equippedPets.size()
                >= MAX_EQUIPPED_PETS) {

            equippedPets.clear();
        }

        equippedPets.add(
                pet
        );

        return true;
    }

    public boolean unequipPet(
            PetInstance pet
    ) {

        if (pet == null) {
            return false;
        }

        return equippedPets.remove(
                pet
        );
    }

    public boolean isEquipped(
            PetInstance pet
    ) {

        return equippedPets.contains(
                pet
        );
    }

    public List<PetInstance> getEquippedPets() {

        return Collections.unmodifiableList(
                equippedPets
        );
    }

    public int getEquippedCount() {
        return equippedPets.size();
    }

    public int getMaxEquippedPets() {
        return MAX_EQUIPPED_PETS;
    }

    public PetInstance getEquippedPet() {

        if (equippedPets.isEmpty()) {
            return null;
        }

        return equippedPets.get(0);
    }

    public boolean hasCaught(
            String petId
    ) {

        if (petId == null) {
            return false;
        }

        String id =
                petId.toLowerCase(
                        Locale.ROOT
                );

        for (PetInstance pet : pets) {

            if (pet.getDefinition() != null
                    && id.equals(
                    pet.getDefinition()
                            .getId()
                            .toLowerCase(
                                    Locale.ROOT
                            )
            )) {

                return true;
            }
        }

        return false;
    }

    public boolean markSighted(
            String petId
    ) {

        if (petId == null
                || petId.isBlank()) {

            return false;
        }

        return sighted.add(
                petId.toLowerCase(
                        Locale.ROOT
                )
        );
    }

    public boolean hasSighted(
            String petId
    ) {

        if (petId == null) {
            return false;
        }

        return sighted.contains(
                petId.toLowerCase(
                        Locale.ROOT
                )
        );
    }

    public boolean hasRevealed(
            String petId
    ) {

        return hasCaught(petId)
                || hasSighted(petId);
    }

    public Set<String> getSighted() {

        return Collections.unmodifiableSet(
                sighted
        );
    }

    /**
     * Marks first-catch Aetherion XP as claimed for this species.
     * @return true the first time only
     */
    public boolean claimFirstCatchXp(
            String petId
    ) {

        if (petId == null
                || petId.isBlank()) {

            return false;
        }

        return catchXpClaimed.add(
                petId.toLowerCase(
                        Locale.ROOT
                )
        );
    }

    public boolean hasClaimedFirstCatchXp(
            String petId
    ) {

        if (petId == null) {
            return false;
        }

        return catchXpClaimed.contains(
                petId.toLowerCase(
                        Locale.ROOT
                )
        );
    }

    public Set<String> getCatchXpClaimed() {

        return Collections.unmodifiableSet(
                catchXpClaimed
        );
    }

    public void clear() {

        pets.clear();
        equippedPets.clear();
    }
}