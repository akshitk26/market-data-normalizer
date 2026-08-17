package com.akshit.marketdata.feed;

import com.akshit.marketdata.proto.Action;
import com.akshit.marketdata.proto.MarketDataEnvelope;
import com.akshit.marketdata.proto.Side;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FixTagValueMarketDataParserTest {
    private final FixTagValueMarketDataParser parser = new FixTagValueMarketDataParser();

    @Test
    void parsesGeminiSnapshotWithPipeDelimiter() {
        String raw = "8=FIX.4.4|9=108|35=W|34=2|49=GEMINI|52=20180425-17:51:40.787|"
                + "56=TRADEBOTMD002|55=BTCUSD|262=2|268=2|269=0|270=8490.07|271=1|"
                + "269=1|270=8491.07|271=2|10=075|";

        List<MarketDataEnvelope> events = parser.parse(raw);

        assertEquals(1, events.size());
        MarketDataEnvelope event = events.get(0);
        assertEquals(FixTagValueMarketDataParser.SOURCE_FEED, event.getSourceFeed());
        assertEquals("BTCUSD", event.getInstrument());
        assertTrue(event.hasBookSnapshot());
        assertEquals(1, event.getBookSnapshot().getBidsCount());
        assertEquals(1, event.getBookSnapshot().getAsksCount());
        assertEquals(8_490_070_000_000L, event.getBookSnapshot().getBids(0).getPriceNanos());
        assertEquals(2_000_000_000L, event.getBookSnapshot().getAsks(0).getQuantityNanos());
    }

    @Test
    void parsesGeminiIncrementalAddModifyDeleteUsingSohDelimiter() {
        String raw = "8=FIX.4.4\u00019=125\u000135=X\u000134=5696449\u000149=GEMINI\u0001"
                + "52=20180123-04:07:42.101\u000156=TESTMKT001\u0001262=40\u00019008=123456789\u0001"
                + "268=3\u0001279=0\u0001269=0\u000155=ETHUSD\u0001270=988.88\u0001271=2.8749\u0001"
                + "279=1\u0001269=1\u000155=ETHUSD\u0001270=989.88\u0001271=1.2\u0001"
                + "279=2\u0001269=0\u000155=ETHUSD\u0001270=987.88\u000110=187\u0001";

        List<MarketDataEnvelope> events = parser.parse(raw);

        assertEquals(3, events.size());
        assertEquals(Action.ADD, events.get(0).getL2Update().getAction());
        assertEquals(Side.BID, events.get(0).getL2Update().getSide());
        assertEquals(Action.MODIFY, events.get(1).getL2Update().getAction());
        assertEquals(Side.ASK, events.get(1).getL2Update().getSide());
        assertEquals(Action.DELETE, events.get(2).getL2Update().getAction());
        assertEquals(0, events.get(2).getL2Update().getQuantityNanos());
        assertEquals(5_696_449L, events.get(0).getSequenceNumber());
    }

    @Test
    void mapsGeminiTradeMakerSideAndTradeAction() {
        String raw = "8=FIX.4.4|9=125|35=X|34=3|49=GEMINI|52=20180809-15:59:16.698|"
                + "56=TRADEBOTMD002|262=2|9008=123456789|268=1|279=0|269=2|55=BTCUSD|"
                + "270=7544.94|271=0.001|9002=1|10=107|";

        MarketDataEnvelope event = parser.parse(raw).get(0);

        assertEquals(Action.TRADE, event.getL2Update().getAction());
        assertEquals(Side.BID, event.getL2Update().getSide());
    }

    @Test
    void prefersPerEntryTimeWhenProvided() {
        String raw = "35=X|34=4|52=20180809-15:59:16.698|268=1|279=0|269=0|55=BTCUSD|"
                + "270=7544.94|271=0.001|273=20180809-15:59:17.123|";

        MarketDataEnvelope event = parser.parse(raw).get(0);

        assertEquals(1_533_830_357_123_000_000L, event.getEventTimeNs());
    }
}
