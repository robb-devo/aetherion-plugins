package de.aetherion.items.menu;

import de.aetherion.core.api.BookPage;

import net.kyori.adventure.text.Component;

import java.util.List;

/** The Manager's start page: book page glyph {@code } with a caption under every tile ({@link BookPage}). */
final class ManagerCaptions {

    private ManagerCaptions() {
    }

    static Component title(List<BookPage.Caption> captions) {
        return BookPage.title('', captions);
    }
}
