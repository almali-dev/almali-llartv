package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;

public class WeatherTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    // Shape returned by Open-Meteo for the forecast URL Weather.fetch builds (trimmed units blocks kept).
    static final String JSON = "{\"latitude\":41.42,\"longitude\":2.2,\"generationtime_ms\":0.05,\"utc_offset_seconds\":7200,"
            + "\"timezone\":\"Europe/Madrid\",\"timezone_abbreviation\":\"GMT+2\",\"elevation\":12.0,"
            + "\"current_units\":{\"time\":\"iso8601\",\"interval\":\"seconds\",\"temperature_2m\":\"°C\",\"weather_code\":\"wmo code\"},"
            + "\"current\":{\"time\":\"2026-10-25T09:15\",\"interval\":900,\"temperature_2m\":16.4,\"weather_code\":0},"
            + "\"daily_units\":{\"time\":\"iso8601\",\"temperature_2m_max\":\"°C\",\"temperature_2m_min\":\"°C\"},"
            + "\"daily\":{\"time\":[\"2026-10-25\"],\"temperature_2m_max\":[21.6],\"temperature_2m_min\":[12.3]}}";

    @Test public void parsesCurrentAndDailyRoundingToInt() throws Exception {
        Weather w = Weather.parse(JSON, 1234L);
        assertEquals(16, w.temp);
        assertEquals(22, w.max);
        assertEquals(12, w.min);
        assertEquals(0, w.code);
        assertEquals(1234L, w.fetchedAt);
    }

    static final Strings CA = LanguageTest.strings(Language.CA), ES = LanguageTest.strings(Language.ES),
            EN = LanguageTest.strings(Language.EN);

    @Test public void lineAndRangeAreCatalan() throws Exception {
        Weather w = Weather.parse(JSON, 0);
        assertEquals("16° · Serè", w.line(CA));
        assertEquals("Màx. 22° · Mín. 12°", w.range(CA));
    }

    @Test public void lineAndRangeFollowTheInterfaceLanguage() throws Exception {
        Weather w = Weather.parse(JSON, 0);
        assertEquals("16° · Despejado", w.line(ES));
        assertEquals("Máx. 22° · Mín. 12°", w.range(ES));
        assertEquals("16° · Clear", w.line(EN));
        assertEquals("High 22° · Low 12°", w.range(EN));
    }

    @Test public void describesRepresentativeCodesInSpanishAndEnglish() {
        assertEquals("Niebla", Weather.describe(ES, 45));
        assertEquals("Tormenta con granizo", Weather.describe(ES, 99));
        assertEquals("Fog", Weather.describe(EN, 48));
        assertEquals("Snow showers", Weather.describe(EN, 86));
        assertEquals("Thunderstorm with hail", Weather.describe(EN, 96));
    }

    @Test public void theCacheHoldsTheCodeNotATextSoALanguageChangeNeedsNoRefetch() throws Exception {
        File dir = tmp.newFolder();
        Weather.parse(JSON, 1L).save(dir);
        Weather w = Weather.load(dir);
        assertEquals("16° · Serè", w.line(CA));
        assertEquals("16° · Clear", w.line(EN));
    }

    @Test public void negativeTemperaturesRoundAndShowSign() throws Exception {
        Weather w = Weather.parse(JSON.replace("16.4", "-2.6").replace("12.3", "-4.5"), 0);
        assertEquals(-3, w.temp);
        assertEquals(-4, w.min);   // Math.round(-4.5) == -4
        assertEquals("-3° · Serè", w.line(CA));
    }

    @Test public void describesRepresentativeCodesInCatalan() {
        assertEquals("Serè", Weather.describe(CA, 0));
        assertEquals("Poc ennuvolat", Weather.describe(CA, 1));
        assertEquals("Mig ennuvolat", Weather.describe(CA, 2));
        assertEquals("Ennuvolat", Weather.describe(CA, 3));
        assertEquals("Boira", Weather.describe(CA, 45));
        assertEquals("Boira", Weather.describe(CA, 48));
        assertEquals("Plugim", Weather.describe(CA, 53));
        assertEquals("Pluja", Weather.describe(CA, 61));
        assertEquals("Pluja forta", Weather.describe(CA, 65));
        assertEquals("Neu", Weather.describe(CA, 73));
        assertEquals("Ruixats", Weather.describe(CA, 80));
        assertEquals("Ruixats de neu", Weather.describe(CA, 85));
        assertEquals("Tempesta", Weather.describe(CA, 95));
        assertEquals("Tempesta amb calamarsa", Weather.describe(CA, 99));
    }

    @Test public void everyOpenMeteoCodeHasText() {
        int[] codes = {0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99};
        for (String lang : Language.OPTIONS) for (int c : codes) {
            if (Weather.describe(LanguageTest.strings(lang), c).isEmpty()) throw new AssertionError(lang + ": no text for code " + c);
        }
    }

    @Test public void unknownCodeLeavesOnlyTheTemperature() throws Exception {
        assertEquals("", Weather.describe(CA, 42));
        Weather w = Weather.parse(JSON.replace("\"weather_code\":0", "\"weather_code\":42"), 0);
        assertEquals("16°", w.line(CA));
        assertEquals("", Weather.describe(EN, 42));
    }

    @Test public void cacheRoundTrips() throws Exception {
        File dir = tmp.newFolder();
        Weather.parse(JSON, 987654321L).save(dir);
        Weather w = Weather.load(dir);
        assertEquals(16, w.temp);
        assertEquals(22, w.max);
        assertEquals(12, w.min);
        assertEquals(0, w.code);
        assertEquals(987654321L, w.fetchedAt);
        assertEquals(false, new File(dir, "weather.cache.part").exists());
    }

    @Test public void theCacheKeepsThePlaceItWasFetchedFor() throws Exception {
        File dir = tmp.newFolder();
        Weather.parse(JSON, 5L).at(41.98311, 2.82493).save(dir);
        Weather w = Weather.load(dir);
        assertEquals(16, w.temp);
        assertEquals(5L, w.fetchedAt);
        assertTrue(w.isFor(41.98311, 2.82493));
        assertTrue(w.isFor(41.99, 2.83));                       // a TV location a few hundred metres away
        assertFalse(w.isFor(Config.DEFAULT_LAT, Config.DEFAULT_LON));   // Barcelona is another place
    }

    @Test public void theCacheStoresThePlaceAtAboutAKilometreOnly() throws Exception {
        // The cache is in external storage, readable by other apps on Android 9 and 10, and in automatic the place
        // is the TV's own location (#117): two decimals (about 1.1 km) are kept, never the exact fix.
        File dir = tmp.newFolder();
        Weather.parse(JSON, 5L).at(41.983117, 2.824931).save(dir);
        String[] p = new String(java.nio.file.Files.readAllBytes(new File(dir, Weather.CACHE).toPath()),
                java.nio.charset.StandardCharsets.UTF_8).split("\t");
        assertEquals(7, p.length);
        assertEquals("41.98", p[5]);
        assertEquals("2.82", p[6]);
        assertTrue(Weather.load(dir).isFor(41.983117, 2.824931));   // still the same place for the refetch rule
    }

    @Test public void aCacheWrittenWithFiveDecimalsStillLoads() throws Exception {
        File dir = tmp.newFolder();
        try (FileWriter w = new FileWriter(new File(dir, "weather.cache"))) { w.write("16\t22\t12\t0\t5\t41.98311\t2.82493"); }
        assertTrue(Weather.load(dir).isFor(41.98311, 2.82493));
    }

    @Test public void aCacheFromBeforeThePlaceWasKeptIsForNoPlace() throws Exception {
        File dir = tmp.newFolder();
        try (FileWriter w = new FileWriter(new File(dir, "weather.cache"))) { w.write("16\t22\t12\t0\t987654321"); }
        Weather w = Weather.load(dir);
        assertEquals(16, w.temp);
        assertEquals(987654321L, w.fetchedAt);
        assertFalse(w.isFor(Config.DEFAULT_LAT, Config.DEFAULT_LON));
    }

    @Test public void missingOrCorruptCacheIsNull() throws Exception {
        File dir = tmp.newFolder();
        assertNull(Weather.load(dir));
        try (FileWriter w = new FileWriter(new File(dir, "weather.cache"))) { w.write("garbage"); }
        assertNull(Weather.load(dir));
    }

    @Test public void anOkResponseIsParsedAsBefore() throws Exception {
        assertEquals(16, Weather.parse(Weather.read(new FakeConnection(200, JSON)), 0).temp);
    }

    @Test public void aWeatherBodyOver256KibFailsInsteadOfFillingTheMemory() throws Exception {
        FakeConnection c = FakeConnection.ofSize(256 * 1024 + 1);
        try {
            Weather.read(c);
            fail("an oversized weather response must fail");
        } catch (Exception expected) { /* a failed fetch: wxFailed and the cache stays */ }
    }
}
