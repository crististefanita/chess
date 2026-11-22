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
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.ViewportSize;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Rendered compact landscape control; opening rules and layout edges stay in component tests. */
@AtfE2eTest
@Tag("web-evidence")
class ChessWebOpeningLandscapePlaywrightE2e {
    private static final int WIDTH = 740;
    private static final int HEIGHT = 420;

    @Test void lessonUsesAdjacentPanelsWithoutDocumentScrollInBothLanguages() throws Exception {
        Path site = WebEvidencePublication.configuredPath("chess.web.bundle");
        Path report = WebEvidencePublication.configuredPath("chess.web.opening.extent");
        Path browserFile = WebEvidencePublication.configuredPath("chess.web.playwright.browser");
        String commit = System.getProperty("chess.web.playwright.source", "");
        if (!commit.matches("[a-f0-9]{40}") || Files.exists(report))
            throw new IllegalArgumentException("Supply a full source commit and a fresh Extent path");
        Throwable failure = null;
        try (var session = new ExtentReportSession(report, AtfReportConfigurationLoader.load().reportingSettings());
             var scenario = session.startScenario(getClass().getSimpleName(),
                     "Opening lesson stays beside its explanation in compact landscape", List.of("e2e", "web", "playwright"));
             var phase = scenario.startPhase("TEST BODY")) {
            try (var server = SiteServer.start(site); var playwright = Playwright.create();
                 Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                         .setExecutablePath(browserFile).setHeadless(true))) {
                phase.observation(new ReportObservation("Qualified browser and source", List.of(
                        new ReportField("Run ID", UUID.randomUUID().toString()),
                        new ReportField("Source commit", commit),
                        new ReportField("Bundle SHA-256", WebEvidencePublication.bundleSha256(site)),
                        new ReportField("Browser", browser.version()),
                        new ReportField("Profile", WIDTH + " × " + HEIGHT + " CSS px · text 130% · EN/RO")),
                        List.of(), List.of()));
                for (String language : List.of("en", "ro")) inspect(phase, browser, server, language);
            } catch (Exception | AssertionError error) {
                failure = error;
                phase.diagnostic(Kind.JUNIT_FAILURE, error.toString());
            }
            var status = failure == null ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
            phase.complete(status);
            scenario.complete(status);
        }
        if (failure != null) throw new AssertionError("Opening landscape E2E failed; inspect " + report, failure);
    }

    private static void inspect(Phase phase, Browser browser, SiteServer server, String language) throws IOException {
        try (var context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(new ViewportSize(WIDTH, HEIGHT)).setLocale(language).setDeviceScaleFactor(1))) {
            Page page = context.newPage();
            var errors = new ArrayList<String>();
            page.onPageError(errors::add);
            page.navigate(server.url(language));
            page.addStyleTag(new Page.AddStyleTagOptions().setContent(":root { font-size: 130% !important; }"));
            click(phase, page, "#home-openings", language + " · open lessons");
            click(phase, page, ".opening-card:first-child", language + " · choose Italian lesson");
            click(phase, page, ".opening-dialog-actions button:first-child", language + " · start as White");
            page.locator(".opening-workspace:not([hidden])").waitFor();
            verify(phase, language + " · no document scrolling or horizontal overflow", true, page.evaluate("""
                    () => document.documentElement.scrollHeight <= innerHeight + 1
                      && document.documentElement.scrollWidth <= innerWidth + 1
                    """));
            verify(phase, language + " · board beside its own scrollable details", true, page.evaluate("""
                    () => { const board=document.querySelector('.opening-board-row .board').getBoundingClientRect();
                      const details=document.querySelector('.opening-controls').getBoundingClientRect();
                      const seats=[...document.querySelectorAll('.opening-seat')].map(e=>e.getBoundingClientRect());
                      return board.right < details.left && board.width >= 250 && Math.abs(board.width-board.height)<1
                        && board.top >= 0 && board.bottom <= innerHeight && details.right <= innerWidth
                        && seats.every(r=>r.top>=0 && r.bottom<=innerHeight); }
                    """));
            double side = ((Number) page.evaluate("document.querySelector('.opening-board-row .board').getBoundingClientRect().width")).doubleValue();
            int nativeSide = BoardPanelLayout.fit(370, OpeningLessonLayout.panelHeight(370, 13, 56,
                    OpeningLessonLayout.boardLimit(370, true), 314), 28, 28, 13).side();
            verify(phase, language + " · Web square follows native compact side within 2px", true,
                    Math.abs(side - nativeSide) <= 2);
            capture(phase, page, language + " · lesson entry");
            String frame = frame(page);
            click(phase, page, ".opening-navigation button:last-child", language + " · next theory move");
            page.waitForFunction("document.querySelector('.opening-navigation [data-chess-position]').dataset.boundary==='FIRST'");
            verify(phase, language + " · next does not move the board", frame, frame(page));
            click(phase, page, ".opening-navigation button:first-child", language + " · previous theory move");
            page.waitForFunction("document.querySelector('.opening-navigation [data-chess-position]').dataset.boundary==='START'");
            verify(phase, language + " · previous restores the initial position without shifting the board", frame, frame(page));
            verify(phase, language + " · no browser errors", List.of(), errors);
            capture(phase, page, language + " · after history navigation");
        }
    }

    private static String frame(Page page) {
        return String.valueOf(page.evaluate("""
                () => { const r=document.querySelector('.opening-board-row .board').getBoundingClientRect();
                  return [r.x,r.y,r.width,r.height].map(v=>Math.round(v*10)/10); }
                """));
    }

    private static void click(Phase phase, Page page, String selector, String title) {
        try (var action = phase.startRequest("Playwright", "UI", title)) {
            action.detail("Control", selector);
            page.locator(selector).click();
            action.detail("Route", page.url());
            action.complete(AtfReportStatus.PASS);
        }
    }

    private static void verify(Phase phase, String title, Object expected, Object actual) {
        boolean passed = expected.equals(actual);
        try (var step = phase.startAssertionStep(title)) {
            step.assertions(List.of(new AtfAssertion(title, String.valueOf(expected), String.valueOf(actual), passed)));
            step.complete(passed ? AtfReportStatus.PASS : AtfReportStatus.FAIL);
        }
        if (!passed) throw new AssertionError(title + ": expected " + expected + ", observed " + actual);
    }

    private static void capture(Phase phase, Page page, String title) {
        byte[] bytes = page.screenshot(new Page.ScreenshotOptions().setFullPage(false));
        try (var action = phase.startRequest("Playwright", "UI", title)) {
            action.detail("PNG SHA-256", WebEvidencePublication.sha256(bytes));
            action.screenshot(title, Base64.getEncoder().encodeToString(bytes));
            action.complete(AtfReportStatus.PASS);
        }
    }

    private record SiteServer(HttpServer server) implements AutoCloseable {
        static SiteServer start(Path site) throws IOException {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/chess/", exchange -> {
                String resource = exchange.getRequestURI().getPath().substring("/chess/".length());
                if (resource.isEmpty()) resource = "index.html";
                Path path = site.resolve(resource).normalize();
                if (!path.startsWith(site) || !Files.isRegularFile(path)) {
                    exchange.sendResponseHeaders(404, -1);
                    exchange.close();
                    return;
                }
                String type = resource.endsWith(".mjs") || resource.endsWith(".js") ? "text/javascript"
                        : resource.endsWith(".css") ? "text/css" : resource.endsWith(".html") ? "text/html" : "application/octet-stream";
                exchange.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
                byte[] bytes = Files.readAllBytes(path);
                exchange.sendResponseHeaders(200, bytes.length);
                try (var body = exchange.getResponseBody()) { body.write(bytes); }
            });
            server.start();
            return new SiteServer(server);
        }

        String url(String language) { return "http://127.0.0.1:" + server.getAddress().getPort() + "/chess/?lang=" + language; }
        @Override public void close() { server.stop(0); }
    }
}
