package com.qazr.legacy.util;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ChatParserTest {
    @Test
    public void findsVictimInVanillaDeathMessage() {
        assertEquals("Alex", ChatParser.findKilledPlayer("Alex was slain by Steve", "Steve", Arrays.asList("Alex", "Steve")));
    }

    @Test
    public void doesNotMatchPartOfAnotherName() {
        assertEquals("", ChatParser.findKilledPlayer("Alexander was slain by Steve", "Steve", Arrays.asList("Alex", "Steve")));
    }

    @Test
    public void parsesCommonChatFormats() {
        assertEquals("Alex", ChatParser.parseChatLine("<Alex> hello").author);
        assertEquals("hello", ChatParser.parseChatLine("Alex: hello").body);
        assertNull(ChatParser.parseChatLine("Alex joined the game"));
    }
}
