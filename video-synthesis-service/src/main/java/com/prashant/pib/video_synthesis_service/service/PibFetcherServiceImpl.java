package com.prashant.pib.video_synthesis_service.service;

import com.prashant.pib.video_synthesis_service.dto.PibPressReleaseDto;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class PibFetcherServiceImpl implements PibFetcherService {

    private static final String PIB_RSS_URL = "https://www.pib.gov.in/RssMain.aspx?ModId=6&Lang=1&Regid=3";
    private static final int MAX_FETCHES = 10;

    public PibFetcherServiceImpl() {
        String chromeDriverPath = System.getenv("CHROMEDRIVER_DIR");
        if (chromeDriverPath == null || chromeDriverPath.isEmpty()) {
            log.warn("CHROMEDRIVER_DIR env var is not set! Scraper will fail in Docker.");
        } else {
            System.setProperty("webdriver.chrome.driver", chromeDriverPath + "/chromedriver");
            log.info("ChromeDriver path set: {}/chromedriver", chromeDriverPath);
        }

        String chromeBinPath = System.getenv("CHROME_BIN");
        if (chromeBinPath == null || chromeBinPath.isEmpty()) {
            log.warn("CHROME_BIN env var is not set! Scraper will fail in Docker.");
        } else {
            System.setProperty("webdriver.chrome.bin", chromeBinPath);
            log.info("Chrome binary path set: {}", chromeBinPath);
        }
        
        log.info("PibFetcherService initialized - using multi-strategy scraper");
    }

    @Override
    public List<PibPressReleaseDto> fetchLatestPressReleases() {
        List<PibPressReleaseDto> pressReleases = new ArrayList<>();
        try {
            log.info("Fetching PIB RSS feed: {}", PIB_RSS_URL);
            SyndFeed feed;
            try (XmlReader reader = new XmlReader(new URL(PIB_RSS_URL))) {
                feed = new SyndFeedInput().build(reader);
            }

            if (feed == null || feed.getEntries() == null) {
                log.warn("Empty or invalid RSS feed");
                return pressReleases;
            }

            int processed = 0;
            for (SyndEntry entry : feed.getEntries()) {
                if (processed >= MAX_FETCHES) break;
                try {
                    PibPressReleaseDto dto = processEntry(entry);
                    if (dto != null) {
                        pressReleases.add(dto);
                        processed++;
                    }
                } catch (Exception e) {
                    log.error("❌ Error processing entry '{}': {}", entry.getTitle(), e.getMessage());
                }
            }

            log.info("✅ Successfully fetched {} valid press releases", pressReleases.size());
        } catch (Exception e) {
            log.error("Failed to fetch PIB feed: {}", e.getMessage(), e);
        }
        return pressReleases;
    }

    private PibPressReleaseDto processEntry(SyndEntry entry) {
        String title = entry.getTitle();
        String link = fixPibUrl(entry.getLink());

        if (link == null || !link.contains("PRID=")) {
            log.warn("Skipping malformed entry: {}", title);
            return null;
        }

        String prid = extractPrid(link);
        if (prid == null) {
            log.warn("Could not extract PRID from {}", link);
            return null;
        }

        PibPressReleaseDto dto = new PibPressReleaseDto();
        dto.setTitle(title);
        dto.setLink(link);
        dto.setPrid(prid);
        dto.setLanguage("en");
        dto.setPublishedAt(entry.getPublishedDate() != null
                ? LocalDateTime.ofInstant(entry.getPublishedDate().toInstant(), ZoneId.systemDefault())
                : LocalDateTime.now());

        String content = fetchWithResilience(link);
        if (content == null || content.isBlank()) {
            log.warn("No valid content for PRID {}", prid);
            return null;
        }

        dto.setDescription(content.replaceAll("\\s+", " ").trim());
        log.info("✓ Successfully scraped PRID {} ({} chars)", prid, content.length());
        return dto;
    }

    public String fetchWithResilience(String url) {
        // Try Jsoup FIRST (faster and more reliable for PIB)
        try {
            log.info("Attempting Jsoup for: {}", url);
            String content = fetchWithJsoup(url);
            if (content != null && !content.isBlank()) {
                log.info("✓ Jsoup succeeded for {}", url);
                return content;
            }
        } catch (Exception e) {
            log.warn("Jsoup failed for {}: {}", url, e.getMessage());
        }

        // Fallback to Selenium with retries
        int maxRetries = 2;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("Selenium attempt {} for: {}", attempt, url);
                String content = scrapePressReleaseContent(url); 
                if (content != null && !content.isBlank()) {
                    return content;
                }
                log.warn("Selenium attempt {} returned empty content", attempt);
            } catch (Exception e) {
                log.error("Selenium attempt {} error: {}", attempt, e.getMessage());
            }

            if (attempt < maxRetries) {
                try {
                    Thread.sleep(3000L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        log.error("All methods failed for {}", url);
        return null;
    }

    /**
     * Jsoup-based fetcher - tries multiple selectors
     */
    private String fetchWithJsoup(String url) throws Exception {
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                             "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .timeout(15000)
                .followRedirects(true)
                .get();

        // Try multiple possible selectors
        String[] selectors = {
            "#PrintPressRelease",           // Main content div
            "div.innner-page-main-about",   // Alternative container
            "div.press-release-content",    // Another possible class
            "div.content-area",             // Generic content area
            "div#content",                  // Generic ID
            ".press-content"                // Class-based selector
        };

        for (String selector : selectors) {
            Element content = doc.selectFirst(selector);
            if (content != null) {
                String text = content.text();
                if (text != null && text.length() > 100) { // Minimum viable content
                    log.debug("Jsoup found content with selector: {}", selector);
                    return text;
                }
            }
        }

        // Last resort: try to find any large text block
        Element body = doc.body();
        if (body != null) {
            String bodyText = body.text();
            if (bodyText.length() > 500) {
                log.warn("Using full body text as fallback");
                return bodyText;
            }
        }

        return null;
    }

    /**
     * Selenium-based fetcher with improved element detection
     */
    private String scrapePressReleaseContent(String url) throws Exception {
        WebDriver driver = null;
        try {
            ChromeOptions options = createChromeOptions();
            driver = new ChromeDriver(options);
            
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(3));
            
            log.debug("Selenium navigating to: {}", url);
            driver.get(url);
            
            // Wait for page load
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
            wait.until(webDriver -> 
                ((JavascriptExecutor) webDriver)
                    .executeScript("return document.readyState").equals("complete")
            );
            
            // Give JavaScript time to render
            Thread.sleep(2000);
            
            // Try multiple strategies to find content
            String content = null;
            
            // Strategy 1: Wait for PrintPressRelease
            try {
                WebElement contentDiv = wait.until(
                    ExpectedConditions.presenceOfElementLocated(By.id("PrintPressRelease"))
                );
                content = contentDiv.getText();
                if (content != null && !content.isBlank()) {
                    log.debug("Found content via PrintPressRelease ID");
                    return content;
                }
            } catch (Exception e) {
                log.debug("PrintPressRelease ID not found, trying alternatives");
            }
            
            // Strategy 2: Try other selectors
            String[] selectors = {
                "div.innner-page-main-about",
                "div.press-release-content",
                "div.content-area",
                "div#content"
            };
            
            for (String selector : selectors) {
                try {
                    WebElement element = driver.findElement(By.cssSelector(selector));
                    content = element.getText();
                    if (content != null && content.length() > 100) {
                        log.debug("Found content via selector: {}", selector);
                        return content;
                    }
                } catch (Exception ignored) {}
            }
            
            // Strategy 3: Use JavaScript to extract text
            try {
                Object result = ((JavascriptExecutor) driver).executeScript(
                    "return document.getElementById('PrintPressRelease')?.innerText || " +
                    "document.querySelector('.press-release-content')?.innerText || " +
                    "document.body.innerText"
                );
                if (result != null) {
                    content = result.toString();
                    if (content.length() > 100) {
                        log.debug("Found content via JavaScript extraction");
                        return content;
                    }
                }
            } catch (Exception e) {
                log.debug("JavaScript extraction failed");
            }
            
            log.warn("No content found with any strategy");
            return null;
            
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    log.error("Error closing WebDriver: {}", e.getMessage());
                }
            }
        }
    }

    private ChromeOptions createChromeOptions() {
        ChromeOptions options = new ChromeOptions();
        
        // Headless mode
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--disable-gpu");
        
        // Anti-detection (critical for PIB)
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                 "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                 "Chrome/120.0.0.0 Safari/537.36");
        options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);
        
        // Performance
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-popup-blocking");
        options.addArguments("--disable-notifications");
        options.addArguments("--disable-infobars");
        
        // Page load strategy - try EAGER for faster loads
        options.setPageLoadStrategy(org.openqa.selenium.PageLoadStrategy.NORMAL);
        
        String chromeBinPath = System.getenv("CHROME_BIN");
        if (chromeBinPath != null && !chromeBinPath.isEmpty()) {
            options.setBinary(chromeBinPath);
        }
        
        return options;
    }

    private String extractPrid(String link) {
        try {
            int idx = link.lastIndexOf("PRID=");
            if (idx == -1) return null;
            String prid = link.substring(idx + 5);
            int amp = prid.indexOf('&');
            return amp != -1 ? prid.substring(0, amp) : prid;
        } catch (Exception e) {
            log.error("PRID extraction failed: {}", e.getMessage());
            return null;
        }
    }

    private String fixPibUrl(String url) {
        if (url == null) return null;
        url = url.replace("http://pib.gov.in", "https://www.pib.gov.in")
                 .replace("https://pib.gov.in", "https://www.pib.gov.in");
        if (url.contains("PressReleaseIframePage.aspx")) {
            url = url.replace("PressReleaseIframePage.aspx", "PressReleseDetail.aspx");
        }
        return url;
    }

    @PreDestroy
    public void cleanup() {
        log.info("PibFetcherService shutting down");
    }
}