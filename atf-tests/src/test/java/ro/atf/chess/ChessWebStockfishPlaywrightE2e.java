package ro.atf.chess;

import atf.reporting.configuration.AtfReportConfigurationLoader;
import atf.reporting.contract.AtfAssertion;
import atf.reporting.contract.AtfReportPorts.Phase;
import atf.reporting.contract.AtfReportStatus;
import atf.reporting.extent.session.ExtentReportSession;
import atf.reporting.model.ReportField;
import atf.reporting.model.ReportObservation;
import atf.reporting.model.diagnostic.ReportPhaseDiagnostic.Kind;
import atf.testkit.AtfE2eTest;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** One rendered flow. Protocol, legal PVs and journal negatives remain in components. */
@AtfE2eTest
@Tag("web-evidence")
class ChessWebStockfishPlaywrightE2e {
    @Test void realStockfishHintReviewAndReloadRemainUsable() throws Exception {
        Path site = WebEvidencePublication.configuredPath("chess.web.bundle");
        Path report = WebEvidencePublication.configuredPath("chess.stockfish.browser.extent");
        Path executable = WebEvidencePublication.configuredPath("chess.web.playwright.browser");
        if (Files.exists(report)) throw new IllegalArgumentException("Choose a fresh Stockfish browser report");
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chess/", exchange -> {
            try {
                String relative = exchange.getRequestURI().getPath().substring("/chess/".length());
                Path file = site.resolve(relative.isEmpty() ? "index.html" : relative).normalize();
                if (!file.startsWith(site) || !Files.isRegularFile(file)) { exchange.sendResponseHeaders(404, -1); return; }
                String name = file.getFileName().toString();
                String mime = name.endsWith(".wasm") ? "application/wasm" : name.endsWith(".mjs") || name.endsWith(".js")
                        ? "text/javascript" : name.endsWith(".css") ? "text/css" : name.endsWith(".svg") ? "image/svg+xml" : "text/html";
                exchange.getResponseHeaders().set("Content-Type", mime);
                exchange.sendResponseHeaders(200, Files.size(file));
                Files.copy(file, exchange.getResponseBody());
            } finally { exchange.close(); }
        });
        server.start(); Throwable failure = null;
        try (var session = new ExtentReportSession(report, AtfReportConfigurationLoader.load().reportingSettings());
             var scenario = session.startScenario(getClass().getSimpleName(), "Real Stockfish hint, playable line, full review and resume", List.of("e2e", "web", "stockfish"));
             var phase = scenario.startPhase("TEST BODY")) {
            try (var playwright = Playwright.create(); var browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setExecutablePath(executable).setHeadless(true));
                 var context = browser.newContext(new Browser.NewContextOptions().setViewportSize(390, 844).setLocale("en-US"))) {
                var page = context.newPage(); var errors = new ArrayList<String>(); page.onPageError(errors::add);
                var engineResponses = new ArrayList<String>();
                page.onResponse(response -> { if (response.url().contains("stockfish-19-lite-single")) engineResponses.add(response.status() + " " + response.url()); });
                page.setDefaultTimeout(45000);
                phase.observation(new ReportObservation("Native run and shipped bundle", List.of(
                        new ReportField("Run ID", UUID.randomUUID().toString()),
                        new ReportField("Source revision", System.getProperty("chess.web.playwright.source", "working candidate")),
                        new ReportField("Bundle SHA-256", WebEvidencePublication.bundleSha256(site)),
                        new ReportField("Browser", browser.version()), new ReportField("Viewport", "390 × 844 · en-US")), List.of(), List.of()));
                try {
                page.navigate("http://127.0.0.1:" + server.getAddress().getPort() + "/chess/");
                click(phase, page, "Analysis board");
                button(page, "white pawn e2").waitFor();
                verify(phase, "The legal board renders during asynchronous engine loading", true, button(page, "white pawn e2").isVisible());
                // Cold full-engine readiness follows the existing transport contract:
                // initialization 120 s + ready 10 s + first search 30 s. UI actions stay at 45 s.
                page.getByText("Stockfish 19", new Page.GetByTextOptions().setExact(false)).first()
                        .waitFor(new Locator.WaitForOptions().setTimeout(160000));
                click(phase, page, "white pawn e2"); click(phase, page, "e4, legal destination");
                page.getByText("Position 1 · 1", new Page.GetByTextOptions().setExact(true)).waitFor();
                Object beforeHint = boardGeometry(page);
                click(phase, page, "Show AI variation");
                var replay = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Open line replay.").setExact(false));
                replay.waitFor();
                verify(phase, "The hint identifies a playable Stockfish continuation", true, replay.getAttribute("aria-label").matches(".*[a-h][1-8] to [a-h][1-8].*"));
                verify(phase, "Revealing the hint does not move or resize the board", beforeHint, boardGeometry(page));
                for (int[] profile : List.of(new int[]{390, 844, 100}, new int[]{360, 640, 130}, new int[]{740, 420, 130})) {
                    try (var action = phase.startRequest("Playwright", "UI", "Display profile " + profile[0] + " × " + profile[1])) {
                        page.setViewportSize(profile[0], profile[1]);
                        page.evaluate("scale => document.documentElement.style.fontSize = scale + '%'", profile[2]);
                        action.detail("Text scale", profile[2] + "%"); action.complete(AtfReportStatus.PASS);
                    }
                    boolean fits = analysisLayoutFits(page);
                    if (!fits) {
                        phase.observation(new ReportObservation("Observed layout bounds", List.of(new ReportField("Rectangles", String.valueOf(page.evaluate("""
                                () => Object.fromEntries(['header','stage','board','status','feedback','toolbar'].map(part => {
                                    const b = document.querySelector('.analysis-study-' + part).getBoundingClientRect();
                                    return [part, {x:b.x,y:b.y,width:b.width,height:b.height,bottom:b.bottom}];
                                }))
                                """)))), List.of(), List.of()));
                        capture(phase, page, "Layout requiring correction");
                    }
                    verify(phase, "The square and compact control group fit at " + profile[0] + " × " + profile[1] + " / " + profile[2] + "%", true, fits);
                    if (profile[2] > 100) capture(phase, page, "Compact analysis " + profile[0] + " × " + profile[1] + " / " + profile[2] + "%");
                }
                page.setViewportSize(390, 844); page.evaluate("document.documentElement.style.fontSize = '100%'");
                capture(phase, page, "Stockfish hint after 1.e4");
                try (var action = phase.startRequest("Playwright", "UI", "Open the observed hint replay")) {
                    replay.click(); action.complete(AtfReportStatus.PASS);
                }
                click(phase, page, "Next move");
                verify(phase, "The replay uses the engine source and depth", true, page.locator("#study-analysis").innerText().contains("Stockfish 19 · Depth"));
                capture(phase, page, "Playable engine line with source and depth");
                click(phase, page, "Return to the position"); click(phase, page, "Analysis board options"); click(phase, page, "Review all moves");
                page.locator("#review-provider").getByText("Stockfish 19", new Locator.GetByTextOptions().setExact(true)).waitFor();
                verify(phase, "The new review identifies Stockfish", "Stockfish 19", page.locator("#review-provider").innerText());
                verify(phase, "No invalid score leaks into the review", false, page.locator("#review").innerText().contains("NaN"));
                capture(phase, page, "Full review from the shipped engine");
                click(phase, page, "Close"); page.reload();
                page.getByText("Position 1 · 1", new Page.GetByTextOptions().setExact(true)).waitFor();
                verify(phase, "Reload retains the exact selected move", true, button(page, "white pawn e4").isVisible());
                verify(phase, "Native browser reports no page error", List.of(), errors);
                capture(phase, page, "Analysis restored after reload");
                } catch (Exception | AssertionError error) {
                    phase.observation(new ReportObservation("Browser failure evidence", List.of(
                            new ReportField("Visible state", page.locator("body").innerText()),
                            new ReportField("Page errors", errors.toString()),
                            new ReportField("Engine responses", engineResponses.toString())), List.of(), List.of()));
                    capture(phase, page, "Observed browser failure");
                    throw error;
                }
            } catch (Exception | AssertionError error) { failure = error; phase.diagnostic(Kind.JUNIT_FAILURE, error.toString()); }
            var status = failure == null ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
            phase.complete(status); scenario.complete(status);
        } finally { server.stop(0); }
        if (failure != null) throw new AssertionError("Stockfish browser sanity failed; inspect " + report, failure);
    }
    private static Locator button(Page page, String name) { return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name).setExact(true)); }
    private static Object boardGeometry(Page page) {
        return page.evaluate("""
                () => { const b = document.querySelector('.analysis-study-board').getBoundingClientRect();
                    return [b.x,b.y,b.width,b.height].map(value => Math.round(value * 10) / 10); }
                """);
    }
    private static boolean analysisLayoutFits(Page page) {
        return Boolean.TRUE.equals(page.evaluate("""
                () => { const root = document.querySelector('#study-analysis');
                    const rect = selector => root.querySelector(selector).getBoundingClientRect();
                    const header = rect('.analysis-study-header'), stage = rect('.analysis-study-stage');
                    const board = rect('.analysis-study-board'), status = rect('.analysis-study-status');
                    const feedback = rect('.analysis-study-feedback'), controls = rect('.analysis-study-toolbar');
                    const near = gap => gap >= -0.6 && gap <= 8;
                    return board.width > 0 && Math.abs(board.width-board.height) < 0.6
                        && board.left >= 0 && board.right <= innerWidth + 0.6 && board.top >= 0
                        && controls.bottom <= innerHeight + 0.6 && document.documentElement.scrollWidth <= innerWidth
                        && near(stage.top-header.bottom) && near(status.top-stage.bottom)
                        && near(controls.top-feedback.bottom);
                }
                """));
    }
    private static void click(Phase phase, Page page, String name) {
        try (var action = phase.startRequest("Playwright", "UI", name)) { button(page, name).click(); action.complete(AtfReportStatus.PASS); }
    }
    private static void verify(Phase phase, String name, Object expected, Object actual) {
        boolean pass = expected.equals(actual);
        try (var step = phase.startAssertionStep(name)) { step.assertions(List.of(new AtfAssertion(name, String.valueOf(expected), String.valueOf(actual), pass))); step.complete(pass ? AtfReportStatus.PASS : AtfReportStatus.FAIL); }
        if (!pass) throw new AssertionError(name + ": expected " + expected + ", observed " + actual);
    }
    private static void capture(Phase phase, Page page, String name) {
        byte[] png = page.screenshot(new Page.ScreenshotOptions().setFullPage(false));
        try (var action = phase.startRequest("Playwright", "UI", name)) {
            action.detail("PNG SHA-256", WebEvidencePublication.sha256(png));
            action.screenshot(name, Base64.getEncoder().encodeToString(png)); action.complete(AtfReportStatus.PASS);
        }
    }
}
