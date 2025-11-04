package com.prashant.pib.video_synthesis_service.controller;

import com.prashant.pib.video_synthesis_service.dto.PibPressReleaseDto;
import com.prashant.pib.video_synthesis_service.service.PibFetcherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
@Slf4j
public class DebugController {

    private final PibFetcherService pibFetcherService;

    @GetMapping("/test-pib-connection")
    public ResponseEntity<Map<String, Object>> testPibConnection() {
        Map<String, Object> result = new HashMap<>();
        String rssUrl = "https://www.pib.gov.in/RssMain.aspx?ModId=6&Lang=1&Regid=3";
        
        try {
            log.info("Testing PIB RSS connection...");
            HttpURLConnection conn = (HttpURLConnection) new URL(rssUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            
            int responseCode = conn.getResponseCode();
            result.put("rss_url", rssUrl);
            result.put("response_code", responseCode);
            result.put("success", responseCode == 200);
            
            conn.disconnect();
            log.info("Connection test: HTTP {}", responseCode);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Connection test failed", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    @GetMapping("/test-pib-fetch")
    public ResponseEntity<Map<String, Object>> testPibFetch() {
        Map<String, Object> result = new HashMap<>();
        try {
            log.info("Testing PIB fetch service...");
            long startTime = System.currentTimeMillis();
            
            List<PibPressReleaseDto> dtos = pibFetcherService.fetchLatestPressReleases();
            
            long duration = System.currentTimeMillis() - startTime;
            
            result.put("success", true);
            result.put("fetched_count", dtos.size());
            result.put("duration_ms", duration);
            result.put("entries", dtos);
            
            log.info("✓ Test fetch completed: {} entries in {} ms", dtos.size(), duration);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("❌ Test fetch failed", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("error_type", e.getClass().getSimpleName());
            result.put("stack_trace", getStackTraceString(e));
            return ResponseEntity.status(500).body(result);
        }
    }

    @GetMapping("/test-scrape")
    public ResponseEntity<Map<String, Object>> testScrape(@RequestParam String url) {
        Map<String, Object> result = new HashMap<>();
        try {
            log.info("Testing scrape for URL: {}", url);
            
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .timeout(20000)
                    .referrer("https://www.pib.gov.in/")
                    .followRedirects(true)
                    .maxBodySize(0)
                    .get();

            result.put("success", true);
            result.put("url", url);
            result.put("title", doc.title());
            result.put("has_print_div", !doc.select("#PrintPressRelease").isEmpty());
            
            if (!doc.select("#PrintPressRelease").isEmpty()) {
                String content = doc.select("#PrintPressRelease").first().text();
                result.put("content_length", content.length());
                result.put("content_preview", content.substring(0, Math.min(200, content.length())));
            } else {
                result.put("available_selectors", doc.select("[id]").stream()
                        .limit(10)
                        .map(el -> el.id())
                        .toList());
            }
            
            log.info("✓ Scrape test successful");
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("❌ Scrape test failed", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("error_type", e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(result);
        }
    }

    @GetMapping("/test-single-prid")
    public ResponseEntity<Map<String, Object>> testSinglePrid(@RequestParam String prid) {
        Map<String, Object> result = new HashMap<>();
        // Use the correct content URL (note: "PressReleseDetail" - PIB has a typo!)
        String url = "https://www.pib.gov.in/PressReleseDetail.aspx?PRID=" + prid;
        
        try {
            log.info("Testing single PRID: {}", prid);
            
            // Test redirect
            String resolvedUrl = testResolveRedirect(url);
            result.put("original_url", url);
            result.put("resolved_url", resolvedUrl);
            
            // Test scrape
            Document doc = Jsoup.connect(resolvedUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .timeout(20000)
                    .referrer("https://www.pib.gov.in/")
                    .followRedirects(true)
                    .maxBodySize(0)
                    .get();

            result.put("page_title", doc.title());
            result.put("has_content_div", !doc.select("#PrintPressRelease").isEmpty());
            
            if (!doc.select("#PrintPressRelease").isEmpty()) {
                String content = doc.select("#PrintPressRelease").first().text()
                        .replaceAll("\\s+", " ").trim();
                result.put("content_length", content.length());
                result.put("content_preview", content.substring(0, Math.min(300, content.length())));
                result.put("success", true);
            } else {
                result.put("success", false);
                result.put("error", "Content div #PrintPressRelease not found");
            }
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("❌ Single PRID test failed", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    private String testResolveRedirect(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.connect();
            
            int status = conn.getResponseCode();
            if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                String newUrl = conn.getHeaderField("Location");
                conn.disconnect();
                return newUrl != null ? newUrl : url;
            }
            conn.disconnect();
        } catch (Exception e) {
            log.warn("Redirect test failed: {}", e.getMessage());
        }
        return url;
    }

    private String getStackTraceString(Exception e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.toString()).append("\n");
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
            if (sb.length() > 2000) break; // Limit size
        }
        return sb.toString();
    }
}