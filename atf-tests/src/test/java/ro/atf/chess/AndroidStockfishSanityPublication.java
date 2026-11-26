package ro.atf.chess;

import atf.reporting.configuration.AtfReportConfigurationLoader;
import atf.reporting.contract.AtfAssertion;
import atf.reporting.contract.AtfReportPorts;
import atf.reporting.contract.AtfReportStatus;
import atf.reporting.extent.session.ExtentReportSession;
import atf.reporting.model.ReportField;
import atf.reporting.model.ReportObservation;
import atf.reporting.model.diagnostic.ReportPhaseDiagnostic.Kind;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

/** Explicit, offline publisher. No annotation admits this native-evidence importer to JVM component runs. */
public final class AndroidStockfishSanityPublication {
    private AndroidStockfishSanityPublication() { }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) throw new IllegalArgumentException("Usage: AndroidStockfishSanityPublication <evidence-folder> <fresh-report.html>");
        publish(Path.of(arguments[0]), Path.of(arguments[1]));
    }

    public static void publish(Path folder, Path target) throws Exception {
        Path report = target.toAbsolutePath();
        if (Files.exists(report)) throw new IllegalArgumentException("Use a fresh Extent report path");
        Files.createDirectories(report.getParent());
        Exception rejection = null;
        try (var session = new ExtentReportSession(report, AtfReportConfigurationLoader.load().reportingSettings());
             var scenario = session.startScenario("BoardSanity", "Use the packaged Stockfish engine for APK coaching", List.of("e2e", "android", "stockfish-only"));
             var phase = scenario.startPhase("TEST BODY")) {
            try {
                var evidence = AndroidStockfishSanityEvidence.verify(folder);
                phase.observation(new ReportObservation("Original native run", List.of(
                        new ReportField("Run ID", evidence.execution().path("runId").asText()),
                        new ReportField("Source commit", evidence.execution().path("sourceCommit").asText()),
                        new ReportField("APK SHA-256", evidence.execution().path("apkSha256").asText()),
                        new ReportField("Scope", "Actual packaged-engine and manual UI smoke; no playing-strength or Elo measurement"),
                        new ReportField("Imported assertions", "37 original native assertions; replayed as evidence, not re-executed"),
                        new ReportField("UCI journal snapshots", Integer.toString(evidence.journalCount())),
                        new ReportField("Native process scope", "The closed exit receipt belongs to the ADB instrumentation orchestrator; per-Stockfish-process exit is not captured")), List.of(), List.of()));
                try (var step = phase.startAssertionStep("Offline evidence integrity checks")) {
                    step.assertions(evidence.integrity()); step.complete(AtfReportStatus.PASS);
                }
                original(phase, "GIVEN the production APK and one native run", evidence.rows().subList(0, 2), null);
                original(phase, "WHEN match, manual board and opening ask the packaged engine", evidence.rows().subList(2, 20), null);
                original(phase, "THEN every review turn has free and played evidence at the same depth", evidence.rows().subList(20, 25), null);
                original(phase, "AND original raw UCI observations bind every route", evidence.rows().subList(25, 33), null);
                original(phase, "AND the manual board renders and reveals a legal engine recommendation", evidence.rows().subList(33, 37), evidence.screenshot());
                try (var action = phase.startRequest("Retain exact native inputs", "OFFLINE IMPORT", "No new engine queries")) {
                    action.detail("Provenance limitation", "Journal snapshot file SHA values are original device observations. Transcript SHA, full context, UCI position/go/bestmove and each retained exact score/WDL/PV are independently reconciled offline. No signature or independent Stockfish process exit is invented.");
                    for (var entry : evidence.inputs().entrySet()) {
                        action.detail(entry.getKey() + " path", entry.getValue().toString());
                        action.detail(entry.getKey() + " SHA-256", AndroidStockfishSanityEvidence.sha(entry.getValue()));
                        if (!entry.getKey().equals("apk") && !entry.getKey().equals("screenshot")) {
                            String content = Files.readString(entry.getValue(), StandardCharsets.UTF_8);
                            for (int index = 0; index < content.length(); index += 2048)
                                action.detail(entry.getKey() + " original UTF-8 [" + index + "," + Math.min(index + 2048, content.length()) + ")",
                                        content.substring(index, Math.min(index + 2048, content.length())));
                        }
                    }
                    action.complete(AtfReportStatus.PASS);
                }
                phase.complete(AtfReportStatus.PASS); scenario.complete(AtfReportStatus.PASS);
            } catch (Exception failure) {
                rejection = failure;
                phase.diagnostic(Kind.JUNIT_FAILURE, failure.toString());
                try (var step = phase.startAssertionStep("Missing or substituted native evidence")) {
                    step.assertions(List.of(new AtfAssertion("Native evidence qualification", "Closed original run, all 37 assertions, bound APK/JSON/PNG/UCI", failure.toString(), false)));
                    step.complete(AtfReportStatus.FAIL);
                }
                phase.complete(AtfReportStatus.FAIL); scenario.complete(AtfReportStatus.FAIL);
            }
        }
        System.out.println("J7 Android Stockfish Extent: " + report);
        if (rejection != null) throw rejection;
    }

    private static void original(AtfReportPorts.Phase phase, String title, List<AtfAssertion> rows, byte[] screenshot) {
        try (var action = phase.startRequest(title, "ANDROID", "Original native assertions");
             var validation = action.startValidation("Imported original expected/actual/verdict; not re-executed")) {
            if (screenshot != null) validation.screenshot("Actual APK manual-board engine hint", Base64.getEncoder().encodeToString(screenshot));
            validation.assertions(rows); validation.complete(AtfReportStatus.PASS); action.complete(AtfReportStatus.PASS);
        }
    }

}
