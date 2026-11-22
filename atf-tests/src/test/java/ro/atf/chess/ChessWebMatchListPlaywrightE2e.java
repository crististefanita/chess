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
import com.microsoft.playwright.BrowserContext;
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
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** One real browser flow; storage limits and move rules stay in component tests. */
@AtfE2eTest
@Tag("web-evidence")
class ChessWebMatchListPlaywrightE2e {
    private static final String LOCAL = "1|f2f3 e7e5 g2g4";
    private static final String AI = "ai|3|WHITE|ADVANCED|0|d2d4 e7e5";
    private static final int WIDTH = 390;
    private static final int HEIGHT = 844;

    @Test void savedAndFinishedGamesRemainDistinctAfterMateAndReload() throws Exception {
        Path site = WebEvidencePublication.configuredPath("chess.web.bundle");
        Path report = WebEvidencePublication.configuredPath("chess.web.playwright.extent");
        Path browserFile = WebEvidencePublication.configuredPath("chess.web.playwright.browser");
        String commit = System.getProperty("chess.web.playwright.source", "");
        if (!commit.matches("[a-f0-9]{40}") || Files.exists(report))
            throw new IllegalArgumentException("Supply a full source commit and a fresh Extent path");
        String bundle = WebEvidencePublication.bundleSha256(site);
        Throwable failure = null;
        try (var session = new ExtentReportSession(report, AtfReportConfigurationLoader.load().reportingSettings());
             var scenario = session.startScenario(getClass().getSimpleName(),
                     "Saved and finished games remain separate through mate, history and reload", List.of("e2e", "web", "playwright"));
             var phase = scenario.startPhase("TEST BODY")) {
            try (var server = SiteServer.start(site); var playwright = Playwright.create()) {
                var options = new BrowserType.LaunchOptions().setExecutablePath(browserFile).setHeadless(true);
                try (Browser browser = playwright.chromium().launch(options);
                     BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                             .setViewportSize(new ViewportSize(WIDTH, HEIGHT)).setDeviceScaleFactor(1)
                             .setLocale("en-US").setIsMobile(true).setHasTouch(true))) {
                    context.setDefaultTimeout(10_000);
                    context.addInitScript("if(location.pathname==='/chess/' && !localStorage.getItem('chesslab.match-ledger.v1')) {"
                            + "localStorage.setItem('chesslab.active-match.v1'," + js(LOCAL) + ");"
                            + "localStorage.setItem('chesslab.ai-match.v1'," + js(AI) + ");}");
                    Page page = context.newPage();
                    var errors = new ArrayList<String>();
                    page.onPageError(errors::add);
                    phase.observation(new ReportObservation("Run and browser identity", List.of(
                            new ReportField("Run ID", UUID.randomUUID().toString()),
                            new ReportField("Source commit", commit),
                            new ReportField("Web bundle SHA-256", bundle),
                            new ReportField("Browser", browser.version()),
                            new ReportField("Viewport", WIDTH + " × " + HEIGHT + " CSS px · DPR 1 · en-US")), List.of(), List.of()));
                    page.navigate(server.url());
                    page.locator("#home-menu:not([hidden])").waitFor();
                    page.waitForFunction("document.querySelector('#review')?.getAttribute('aria-busy')==='false'");
                    verify(phase, "Home opens before any saved game", true, page.locator("#home-menu").isVisible());

                    click(phase, page, "#open-saved", "Open Saved games");
                    capture(phase, page, "Saved menu on entry");
                    phase.observation(new ReportObservation("Saved menu state", List.of(
                            new ReportField("Route", String.valueOf(page.evaluate("location.hash"))),
                            new ReportField("Home hidden", String.valueOf(page.evaluate("document.querySelector('#home-menu').hidden"))),
                            new ReportField("List hidden", String.valueOf(page.evaluate("document.querySelector('#match-list').hidden"))),
                            new ReportField("Visible rows", String.valueOf(page.locator(".match-row").count())),
                            new ReportField("Status", page.locator("#status").innerText()),
                            new ReportField("Page errors", errors.toString())), List.of(), List.of()));
                    page.waitForFunction("document.querySelectorAll('.match-row').length===2");
                    verify(phase, "Both migrated active games are visible", 2, page.locator(".match-row").count());
                    verify(phase, "No finished result appears among saved games", true,
                            page.locator(".match-row").evaluateAll("rows => rows.every(row => row.dataset.result==='IN_PROGRESS')"));
                    verify(phase, "Saved rows fit the compact viewport", true, rowsFit(page));
                    capture(phase, page, "Saved games before mate");

                    click(phase, page, "#return-menu", "Return to main menu");
                    click(phase, page, "#open-finished", "Open Finished games");
                    verify(phase, "No result is archived before mate", 0, page.locator(".match-row").count());
                    click(phase, page, "#return-menu", "Return to main menu");
                    click(phase, page, "#open-saved", "Reopen Saved games");
                    String manual = page.locator(".match-row[data-mode='LOCAL']").getAttribute("data-id");
                    click(phase, page, ".match-row[data-id='" + manual + "']", "Resume the manual game");
                    click(phase, page, "#match-board button[data-square='d8']", "Select the black queen");
                    click(phase, page, "#match-board button[data-square='h4']", "Play the mating move");
                    page.waitForFunction("document.querySelector('#match').dataset.result==='BLACK_WINS'");
                    verify(phase, "Mate preserves the original game ID", manual,
                            page.locator("#match").getAttribute("data-match-id"));

                    if (Boolean.parseBoolean(page.locator("#match").getAttribute("data-read-only"))) {
                        click(phase, page, "#return-menu", "Leave archived board");
                        click(phase, page, "#return-menu", "Return to main menu");
                    } else {
                        click(phase, page, "#match-options-button", "Open match menu");
                        click(phase, page, "#open-review", "Return to main menu");
                    }
                    click(phase, page, "#open-saved", "Check Saved games after mate");
                    verify(phase, "Only the unrelated AI game remains active", 1, page.locator(".match-row").count());
                    verify(phase, "The remaining active game has the AI mode", "AI",
                            page.locator(".match-row").first().getAttribute("data-mode"));
                    click(phase, page, "#return-menu", "Return to main menu");
                    click(phase, page, "#open-finished", "Check Finished games after mate");
                    verify(phase, "The completed game appears exactly once", 1, page.locator(".match-row").count());
                    verify(phase, "The archived row retains its original ID", manual,
                            page.locator(".match-row").first().getAttribute("data-id"));
                    verify(phase, "The finished row fits the compact viewport", true, rowsFit(page));
                    capture(phase, page, "Finished games after mate");

                    page.reload();
                    page.locator(".match-row[data-id='" + manual + "']").waitFor();
                    verify(phase, "Reload keeps one archived result", 1, page.locator(".match-row").count());
                    click(phase, page, ".match-row[data-id='" + manual + "']", "Open archived game");
                    page.locator("#match-board button[data-square='h4']").waitFor();
                    verify(phase, "The archived board is read-only", "true", page.locator("#match").getAttribute("data-read-only"));
                    verify(phase, "The archived board remains square and inside the viewport", true, boardFits(page));
                    capture(phase, page, "Archived game after reload");
                    click(phase, page, "#return-menu", "Return from archived board to results");
                    click(phase, page, "#return-menu", "Return from results to main menu");
                    click(phase, page, "#home-analysis", "Open Analysis board");
                    page.waitForFunction("document.querySelector('.analysis-study-board button[data-square=\"e2\"]')?.dataset.piece==='P'");
                    page.waitForFunction("document.querySelectorAll('.analysis-study-history button').length===1");
                    Object studyFrame = studyFrame(page);
                    verify(phase, "Analysis board fits beside the controls", true, studyFits(page));
                    capture(phase, page, "Analysis board before a move");
                    click(phase, page, ".analysis-study-board button[data-square='e2']", "Select the white pawn");
                    click(phase, page, ".analysis-study-board button[data-square='e4']", "Play e2 to e4 in analysis");
                    phase.observation(new ReportObservation("Analysis board after move", List.of(
                            new ReportField("Route", String.valueOf(page.evaluate("location.hash"))),
                            new ReportField("History items", String.valueOf(page.locator(".analysis-study-history button").count())),
                            new ReportField("Status", page.locator(".analysis-study-status").innerText()),
                            new ReportField("Page errors", errors.toString())), List.of(), List.of()));
                    capture(phase, page, "Analysis board after move");
                    page.waitForFunction("document.querySelectorAll('.analysis-study-history button').length===2");
                    verify(phase, "Analysis move does not shift the board", studyFrame, studyFrame(page));
                    click(phase, page, ".analysis-study-navigation button[aria-label='Previous move']", "Return to the study start");
                    verify(phase, "Analysis history navigation keeps the board anchored", studyFrame, studyFrame(page));
                    verify(phase, "The browser reports no page error", List.of(), errors);
                }
            } catch (Exception | AssertionError error) {
                failure = error;
                phase.diagnostic(Kind.JUNIT_FAILURE, error.toString());
            }
            var status = failure == null ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
            phase.complete(status);
            scenario.complete(status);
        }
        System.out.println("J7 Playwright Extent: " + report);
        if (failure != null) throw new AssertionError("Playwright E2E failed; inspect " + report, failure);
    }

    private static String js(String value) { return "'" + value.replace("'", "\\'") + "'"; }

    private static void click(Phase phase, Page page, String selector, String title) {
        try (var action = phase.startRequest("Playwright", "UI", title)) {
            action.detail("Control", selector);
            page.locator(selector).click();
            action.detail("Route after action", page.url());
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

    private static void capture(Phase phase, Page page, String title) throws IOException {
        byte[] bytes = page.screenshot(new Page.ScreenshotOptions().setFullPage(false));
        var image = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        verify(phase, title + " has the full viewport", true,
                image != null && image.getWidth() == WIDTH && image.getHeight() == HEIGHT);
        try (var action = phase.startRequest("Playwright", "UI", title)) {
            action.detail("PNG SHA-256", WebEvidencePublication.sha256(bytes));
            action.screenshot(title, Base64.getEncoder().encodeToString(bytes));
            action.complete(AtfReportStatus.PASS);
        }
    }

    private static boolean rowsFit(Page page) {
        return Boolean.TRUE.equals(page.evaluate("""
                () => [...document.querySelectorAll('.match-row')].every(row => {
                    const r = row.getBoundingClientRect();
                    return r.left >= 0 && r.right <= innerWidth && r.top >= 0 && r.bottom <= innerHeight;
                }) && document.documentElement.scrollWidth <= innerWidth
                """));
    }

    private static boolean boardFits(Page page) {
        return Boolean.TRUE.equals(page.evaluate("""
                () => { const r = document.querySelector('#match-board').getBoundingClientRect();
                    return r.width === r.height && r.left >= 0 && r.right <= innerWidth && r.top >= 0 && r.bottom <= innerHeight; }
                """));
    }

    private static Object studyFrame(Page page) {
        return page.evaluate("""
                () => { const r = document.querySelector('.analysis-study-board').getBoundingClientRect();
                    return [r.x, r.y, r.width, r.height].map(value => Math.round(value * 10) / 10); }
                """);
    }

    private static boolean studyFits(Page page) {
        return Boolean.TRUE.equals(page.evaluate("""
                () => { const r = document.querySelector('.analysis-study-board').getBoundingClientRect();
                    return r.width === r.height && r.left >= 0 && r.right <= innerWidth && r.top >= 0 && r.bottom <= innerHeight; }
                """));
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

        String url() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/chess/?lang=en"; }

        @Override public void close() { server.stop(0); }
    }
}
