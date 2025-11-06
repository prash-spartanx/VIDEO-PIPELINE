package com.prashant.pib.video_synthesis_service.service;

import com.prashant.pib.video_synthesis_service.dto.PibPressReleaseDto;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PibFetcherServiceImpl implements PibFetcherService {

    // ---------- Config ----------
    @Value("${pib.rss.url:https://www.pib.gov.in/RssMain.aspx?ModId=6&Lang=1&Regid=3}")
    private String rssUrl;

    @Value("${pib.fetch.max-items:15}")
    private int maxItems;

    @Value("${pib.fetch.jsoup-timeout-ms:15000}")
    private int jsoupTimeoutMs;

    @Value("${pib.fetch.delay-min-ms:80}")
    private int delayMinMs;

    @Value("${pib.fetch.delay-max-ms:180}")
    private int delayMaxMs;

    private final AtomicBoolean fetching = new AtomicBoolean(false);

    // Optional Selenium env
    private final String chromeDriverDir = System.getenv("CHROMEDRIVER_DIR");
    private final String chromeBin = System.getenv("CHROME_BIN");
    private final boolean seleniumAvailable;

    public PibFetcherServiceImpl() {
        this.seleniumAvailable = chromeDriverDir != null && !chromeDriverDir.isEmpty()
                && chromeBin != null && !chromeBin.isEmpty();
        if (seleniumAvailable) {
            System.setProperty("webdriver.chrome.driver", chromeDriverDir + "/chromedriver");
            System.setProperty("webdriver.chrome.bin", chromeBin);
            log.info("Selenium enabled (CHROMEDRIVER_DIR={}, CHROME_BIN set)", chromeDriverDir);
        } else {
            log.info("Selenium disabled — using Jsoup only.");
        }
    }

    @Override
    public boolean tryStartFetch() {
        boolean ok = fetching.compareAndSet(false, true);
        if (!ok) log.warn("Fetch ignored — another fetch is already in progress.");
        return ok;
    }

    @Override
    public void finishFetch() {
        fetching.set(false);
    }

    @Override
    public List<PibPressReleaseDto> fetchLatestPressReleases() {
        log.info("Fetching PIB RSS feed: {}", rssUrl);
        List<SyndEntry> entries = readRss(rssUrl);
        if (entries.isEmpty()) {
            log.warn("Empty or invalid RSS feed");
            return List.of();
        }

        // Sort newest first and normalize links to the server-rendered page
        List<String> linksSorted = entries.stream()
                .sorted(Comparator.comparing(SyndEntry::getPublishedDate, Comparator.nullsLast(Date::compareTo)).reversed())
                .map(SyndEntry::getLink)
                .filter(Objects::nonNull)
                .map(this::fixPibUrl) // <-- critical normalization
                .collect(Collectors.toList());

        Map<String, String> pridToLink = new LinkedHashMap<>();
        for (String link : linksSorted) {
            String prid = extractPrid(link);
            if (prid != null && !pridToLink.containsKey(prid)) {
                // always store canonical PressReleasePage.aspx URL
                pridToLink.put(prid, buildCanonicalLink(prid));
            }
            if (pridToLink.size() >= maxItems * 2) break;
        }

        if (pridToLink.isEmpty()) {
            log.warn("No valid PRIDs found in RSS");
            return List.of();
        }

        List<PibPressReleaseDto> results = new ArrayList<>();
        int taken = 0;

        for (Map.Entry<String, String> e : pridToLink.entrySet()) {
            if (taken >= maxItems) break;

            String prid = e.getKey();
            String link = e.getValue();

            try {
                sleepJitter();

                // Scrape title + content with resilience and validation
                ScrapeResult sr = fetchWithResilienceFull(link);
                if (sr != null && ContentValidator.isLikelyValid(sr.content)) {
                    PibPressReleaseDto dto = new PibPressReleaseDto();
                    dto.setPrid(prid);
                    dto.setLink(link);
                    dto.setLanguage("en");
                    dto.setTitle(safeTitle(sr.title)); // never null/blank
                    dto.setDescription(sr.content.trim().replaceAll("\\s+", " "));
                    dto.setPublishedAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")));
                    results.add(dto);
                    taken++;
                    log.info("✓ Scraped PRID {} ({} chars) title='{}'", prid, dto.getDescription().length(), dto.getTitle());
                } else {
                    log.warn("Skipping PRID {} — blocked/too short content", prid);
                }
            } catch (Exception ex) {
                log.warn("Scrape failed for {}: {}", link, ex.getMessage());
            }
        }

        log.info("✅ Successfully fetched {} valid press releases", results.size());
        return results;
    }

    // ---------- Content validation / helpers ----------

    static final class ContentValidator {
        private static final int MIN_LEN = 600;
        private static final String[] BLOCKED = {
                "enable javascript", "turn on javascript", "your browser",
                "cookie", "cookies", "iframepage", "pressreleaseiframepage",
                "***no release found***"
        };
        static boolean isLikelyValid(String txt) {
            if (txt == null) return false;
            String t = txt.trim();
            if (t.length() < MIN_LEN) return false;
            String lower = t.toLowerCase(Locale.ROOT);
            for (String b : BLOCKED) {
                if (lower.contains(b)) return false;
            }
            int sentences = t.split("[.!?]\\s+").length;
            return sentences >= 6;
        }
    }

    private record ScrapeResult(String title, String content) {}

    private List<SyndEntry> readRss(String url) {
        try (XmlReader reader = new XmlReader(new URL(url))) {
            SyndFeed feed = new SyndFeedInput().build(reader);
            List<SyndEntry> entries = feed.getEntries();
            return entries != null ? entries : List.of();
        } catch (Exception e) {
            log.error("Failed to parse RSS: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private ScrapeResult fetchWithResilienceFull(String url) {
        // 1) Jsoup
        try {
            ScrapeResult jsoup = fetchWithJsoupFull(url);
            if (jsoup != null && ContentValidator.isLikelyValid(jsoup.content)) {
                log.debug("Jsoup succeeded for {}", url);
                return jsoup;
            }
        } catch (Exception e) {
            log.debug("Jsoup failed for {}: {}", url, e.getMessage());
        }

        // 2) Selenium fallback
        if (seleniumAvailable) {
            int maxRetries = 2;
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    ScrapeResult sr = scrapePressReleaseFull(url);
                    if (sr != null && ContentValidator.isLikelyValid(sr.content)) {
                        return sr;
                    }
                    log.warn("Selenium attempt {} returned blocked/short content", attempt);
                } catch (Exception e) {
                    log.debug("Selenium attempt {} error: {}", attempt, e.getMessage());
                }
                if (attempt < maxRetries) sleep(1500);
            }
        }
        return null;
    }

    private ScrapeResult fetchWithJsoupFull(String url) throws Exception {
        // Always hit the canonical server-rendered page
        String canonical = buildCanonicalLink(extractPrid(url));
        Document doc = Jsoup.connect(canonical)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome Safari")
                .timeout(jsoupTimeoutMs)
                .followRedirects(true)
                .get();

        // Title: the first H2 with decent length (PIB uses an H2 for headline)
        String title = null;
        for (Element h2 : doc.select("h2")) {
            String t = textOrNull(h2);
            if (t != null && t.length() > 20) { title = t; break; }
        }

        // Main content: try some common containers, else fallback to article text
        String[] selectors = {
                "div#content", "div.content-area", "div.press-release-content",
                "article", "section", "main"
        };
        String content = null;
        for (String sel : selectors) {
            Element el = doc.selectFirst(sel);
            if (el != null) {
                String t = el.text();
                if (t != null && t.length() > 400) { content = t; break; }
            }
        }
        if (content == null && doc.body() != null) {
            String t = doc.body().text();
            if (ContentValidator.isLikelyValid(t)) {
                log.warn("Using validated full body fallback for {}", canonical);
                content = t;
            }
        }

        if (content == null) return null;
        return new ScrapeResult(safeTitle(title), content);
    }

    private ScrapeResult scrapePressReleaseFull(String url) {
        WebDriver driver = null;
        try {
            ChromeOptions options = createChromeOptions();
            driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(3));

            String canonical = buildCanonicalLink(extractPrid(url));
            driver.get(canonical);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
            wait.until(webDriver ->
                    ((JavascriptExecutor) webDriver).executeScript("return document.readyState").equals("complete"));
            sleep(1000);

            String title = null;
            try {
                WebElement h2 = driver.findElement(By.tagName("h2"));
                String t = h2.getText();
                if (t != null && t.trim().length() > 20) title = t.trim();
            } catch (org.openqa.selenium.NoSuchElementException ignored) {}

            String content = null;
            String[] selectors = {
                    "div#content", "div.content-area", "div.press-release-content",
                    "article", "section", "main"
            };
            for (String sel : selectors) {
                try {
                    WebElement el = driver.findElement(By.cssSelector(sel));
                    String t = el.getText();
                    if (t != null && t.length() > 400) { content = t; break; }
                } catch (org.openqa.selenium.NoSuchElementException ignored) {}
            }
            if (content == null) {
                String t = (String) ((JavascriptExecutor) driver).executeScript("return document.body.innerText || '';");
                if (ContentValidator.isLikelyValid(t)) content = t;
            }
            if (content == null) return null;

            return new ScrapeResult(safeTitle(title), content);
        } finally {
            if (driver != null) {
                try { driver.quit(); } catch (Exception ignored) {}
            }
        }
    }

    private String textOrNull(Element e) {
        if (e == null) return null;
        String t = e.text();
        return (t == null || t.isBlank()) ? null : t.trim();
    }

    private String safeTitle(String t) {
        String s = (t == null ? "" : t.trim());
        if (s.isEmpty() || s.equalsIgnoreCase("Press Information Bureau")) {
            return "Press Release";
        }
        // PIB titles can be very long; clip to 1024 chars (DB column)
        return s.length() > 1024 ? s.substring(0, 1024) : s;
    }

    private String buildCanonicalLink(String prid) {
        if (prid == null || prid.isBlank()) return null;
        return "https://www.pib.gov.in/PressReleasePage.aspx?PRID=" + prid;
    }

    private String extractPrid(String link) {
        if (link == null) return null;
        int idx = link.lastIndexOf("PRID=");
        if (idx == -1) return null;
        String tail = link.substring(idx + 5);
        int amp = tail.indexOf('&');
        return amp == -1 ? tail : tail.substring(0, amp);
    }

    /**
     * Normalize ANY incoming PIB link to the server-rendered endpoint + host:
     * - force https://www.pib.gov.in
     * - replace PressReleseDetail.aspx / PressReleaseDetail.aspx / PressReleaseIframePage.aspx with PressReleasePage.aspx
     */
    private String fixPibUrl(String url) {
        if (url == null) return null;
        String out = url
                .replace("http://pib.gov.in", "https://www.pib.gov.in")
                .replace("https://pib.gov.in", "https://www.pib.gov.in")
                .replace("http://www.pib.gov.in", "https://www.pib.gov.in");
        // normalize endpoint
        out = out.replace("PressReleseDetail.aspx", "PressReleasePage.aspx"); // misspelling -> canonical
        out = out.replace("PressReleaseDetail.aspx", "PressReleasePage.aspx");
        out = out.replace("PressReleaseIframePage.aspx", "PressReleasePage.aspx");
        // ensure PRID is preserved; if missing, we won’t use it anyway
        return out;
    }

    private ChromeOptions createChromeOptions() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
                "--headless=new", "--no-sandbox", "--disable-dev-shm-usage",
                "--window-size=1920,1080", "--disable-gpu",
                "--disable-blink-features=AutomationControlled",
                "user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome Safari"
        );
        options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);
        options.setPageLoadStrategy(PageLoadStrategy.NORMAL);
        if (chromeBin != null && !chromeBin.isEmpty()) options.setBinary(chromeBin);
        return options;
    }

    private void sleep(int millis) {
        try { Thread.sleep(millis); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    private void sleepJitter() {
        int d = ThreadLocalRandom.current().nextInt(Math.max(1, delayMinMs), Math.max(delayMinMs + 1, delayMaxMs));
        sleep(d);
    }

    @PreDestroy
    public void cleanup() {
        log.info("PibFetcherService shutting down");
    }
}
