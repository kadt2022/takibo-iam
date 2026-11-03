package com.takibo.messaging.application;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TemplateEngineTest {

    private final TemplateEngine engine = new TemplateEngine();

    @Test
    void returnsNullForNullTemplate() {
        assertNull(engine.render(null, Map.of("name", "Alice")));
    }

    @Test
    void returnsTemplateUnchangedWhenModelIsNullOrEmpty() {
        assertEquals("Hello ${name}", engine.render("Hello ${name}", null));
        assertEquals("Hello ${name}", engine.render("Hello ${name}", Map.of()));
    }

    @Test
    void replacesEveryOccurrenceOfKnownPlaceholders() {
        String rendered = engine.render("Hi ${name}, welcome to ${space.name}! Bye ${name}.",
                Map.of("name", "Alice", "space.name", "Ops"));

        assertEquals("Hi Alice, welcome to Ops! Bye Alice.", rendered);
    }

    @Test
    void supportsDigitsUnderscoresDotsAndDashesInKeys() {
        String rendered = engine.render("${a_1}|${b.c}|${d-e}", Map.of("a_1", "x", "b.c", "y", "d-e", "z"));

        assertEquals("x|y|z", rendered);
    }

    @Test
    void keepsUnknownPlaceholdersVerbatim() {
        assertEquals("Hello ${missing}", engine.render("Hello ${missing}", Map.of("name", "Alice")));
    }

    @Test
    void keepsPlaceholderWhenValueIsNull() {
        Map<String, Object> model = new HashMap<>();
        model.put("name", null);
        model.put("other", "x");

        assertEquals("Hello ${name}", engine.render("Hello ${name}", model));
    }

    @Test
    void ignoresPlaceholdersWithUnsupportedCharacters() {
        assertEquals("${a b} ${} $name", engine.render("${a b} ${} $name", Map.of("a b", "x", "name", "y")));
    }

    @Test
    void rendersNonStringValuesWithToString() {
        assertEquals("You have 3 invites (true)", engine.render("You have ${count} invites (${flag})",
                Map.of("count", 3, "flag", true)));
    }

    @Test
    void insertsRegexSpecialCharactersLiterally() {
        String rendered = engine.render("Amount: ${amount}", Map.of("amount", "$1\\2 ${x}"));

        assertEquals("Amount: $1\\2 ${x}", rendered);
    }

    @Test
    void doesNotRecursivelyExpandPlaceholdersInjectedThroughValues() {
        String rendered = engine.render("Hello ${name}", Map.of("name", "${secret}", "secret", "TOP-SECRET"));

        assertEquals("Hello ${secret}", rendered,
                "une valeur fournie par l'utilisateur ne doit pas pouvoir faire evaluer d'autres attributs");
    }
}
