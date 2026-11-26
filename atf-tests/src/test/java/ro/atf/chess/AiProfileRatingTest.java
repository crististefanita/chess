package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class AiProfileRatingTest {
    private static List<AiProfileRating.Game> games(int...scores){var result=new ArrayList<AiProfileRating.Game>();for(int i=0;i<scores.length;i++)result.add(new AiProfileRating.Game("g"+i,"pair"+(i/2),"family"+(i/2),i%2==0?"WHITE":"BLACK",scores[i]));return result;}
    @Test void knownResolvedResultsAndCensorBoundsKeepTheirMeaning(Checks check){
        var all=AiProfileRating.summarize(games(2,0,2,0),1500,10000,41273);
        check.same("Equal resolved scores reproduce the declared anchor",1500.0,all.resolvedPerformance());
        var fit=AiProfileRating.summarize(games(2,-1,0,1),1500,10000,41273);
        check.same("Unfinished games remain separate from actual draws",List.of(1,1,1,1),List.of(fit.wins(),fit.draws(),fit.losses(),fit.unfinished()));
        check.same("The point uses only resolved results",1500.0,fit.resolvedPerformance());
        check.same("All unfinished losses use 1.5 of four possible points",1500+400*Math.log10(.375/.625),fit.unfinishedAllLosses());
        check.same("All unfinished wins use 2.5 of four possible points",1500+400*Math.log10(.625/.375),fit.unfinishedAllWins());
        check.same("The frozen paired-family bootstrap is reproducible",fit,AiProfileRating.summarize(games(2,-1,0,1),1500,10000,41273));
    }
    @Test void noResolvedGamesNeverProduceAnInvented1500Rating(Checks check){
        var fit=AiProfileRating.summarize(games(-1,-1,-1,-1),1500,10000,41273);
        check.same("Zero resolved games are explicitly unmeasured","NOT_MEASURED",fit.status());
        check.same("There is no midpoint silently labelled measured",null,fit.resolvedPerformance());
        check.same("Censored data retains its unbounded uncertainty",List.of(Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY),List.of(fit.conservativeLow95(),fit.conservativeHigh95()));
        check.same("A boundary win rate is not clipped to a plausible-looking Elo",Double.POSITIVE_INFINITY,AiProfileRating.summarize(games(2,2,2,2),1500,10000,41273).resolvedPerformance());
    }
    @Test void duplicateMissingColorsAndUnequalFamilySamplesAreRejected(Checks check){
        var valid=games(2,0,2,0);var duplicate=new ArrayList<>(valid);duplicate.add(valid.getFirst());
        check.rejects("Repeated receipts cannot increase the sample",IllegalArgumentException.class,()->AiProfileRating.summarize(duplicate,1500,10000,1));
        check.rejects("Half a color pair cannot become an independent game",IllegalArgumentException.class,()->AiProfileRating.summarize(valid.subList(0,3),1500,10000,1));
        var sameSide=new ArrayList<>(valid);sameSide.set(1,new AiProfileRating.Game("other","pair0","family0","WHITE",0));
        check.rejects("Color reversal is required",IllegalArgumentException.class,()->AiProfileRating.summarize(sameSide,1500,10000,1));
        var uneven=new ArrayList<>(valid);uneven.add(new AiProfileRating.Game("extraW","extra","family0","WHITE",2));uneven.add(new AiProfileRating.Game("extraB","extra","family0","BLACK",2));
        check.rejects("A convenient family cannot be oversampled silently",IllegalArgumentException.class,()->AiProfileRating.summarize(uneven,1500,10000,1));
    }
}
