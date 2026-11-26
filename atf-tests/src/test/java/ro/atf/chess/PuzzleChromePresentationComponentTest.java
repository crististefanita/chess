package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.file.Files;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class PuzzleChromePresentationComponentTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";

    @Test void keepsTheBulbQuietAndItsTouchTargetAccessibleOnBothAdapters(Checks check) throws Exception {
        var root = ProjectRoot.locate();
        var factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        var guidance = factory.newDocumentBuilder()
                .parse(root.resolve("android/app/src/main/res/layout/quest_guidance.xml").toFile()).getDocumentElement();
        String android = Files.readString(root.resolve("android/app/src/main/java/ro/atf/chess/android/PuzzleChrome.java"));
        String web = Files.readString(root.resolve("web/src/main/web/study-quests.css"));

        check.same("Android bulb remains a 48 dp by 48 dp accessible target",
                List.of("48dp", "48dp"),
                List.of(guidance.getAttributeNS(ANDROID, "layout_width"), guidance.getAttributeNS(ANDROID, "layout_height")));
        check.same("The shared bulb resource keeps its existing default for non-puzzle screens", "12dp",
                guidance.getAttributeNS(ANDROID, "padding"));
        check.same("Android draws the bulb inside a 20 dp visual inset", true,
                android.contains("lamp.setPadding(dp(14), dp(14), dp(14), dp(14))"));
        check.same("Compact Android score typography and vertical insets are reduced together", true,
                android.contains("points = text(12, TEXT)") && android.contains("streak = text(11, FLAME)")
                        && android.contains("setPadding(dp(10), dp(2), dp(10), dp(2))"));
        check.same("The score height includes its compact insets before remaining board space is measured", true,
                android.contains("fit.scoreContainerHeight(dp(30), getPaddingTop() + getPaddingBottom())"));
        check.same("Web keeps the same 48 px hit target", true,
                web.contains("min-block-size: var(--quest-action-size)") && web.contains("--quest-action-size: 48px"));
        check.same("Web shrinks only the bulb artwork, not its action target", true,
                web.contains("#quest-study-hint svg { inline-size: 1.25rem; block-size: 1.25rem; }"));
    }

    @Test void keepsTheNarrowWebScoreSingleRowAndAccessibleWhenItsVisualTextIsClipped(Checks check) throws Exception {
        String css = Files.readString(ProjectRoot.locate().resolve("web/src/main/web/study-quests.css"));
        String narrow = css.substring(css.indexOf("@media (max-width: 20rem)"));
        check.same("The narrow score reserves a flexible points column and a bounded streak column", true,
                narrow.contains("grid-template-columns: minmax(0, 1fr) minmax(0, 45%)"));
        check.same("The narrow score remains one row instead of stacking its contents", false,
                narrow.contains("grid-template-columns: minmax(0, 1fr);"));
        check.same("The points value keeps its complete DOM text while CSS only clips the visual overflow", true,
                css.matches("(?s).*#quest-study-score\\s*\\{\\s*min-inline-size: 0;\\s*overflow: hidden;\\s*text-overflow: ellipsis;\\s*white-space: nowrap;.*"));
    }
}
