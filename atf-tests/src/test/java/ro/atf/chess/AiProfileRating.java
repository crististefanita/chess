package ro.atf.chess;

import java.util.*;

/** Diagnostic performance against one declared anchor; unfinished games remain censored. */
final class AiProfileRating {
    record Game(String id,String pair,String family,String side,int scoreTwice) {
        Game {if(id==null||id.isBlank()||pair==null||pair.isBlank()||family==null||family.isBlank()
                ||!Set.of("WHITE","BLACK").contains(side)||scoreTwice< -1||scoreTwice>2)throw new IllegalArgumentException("Invalid rating game");}
    }
    record Summary(String status,int games,int pairs,int families,int wins,int draws,int losses,int unfinished,
                   Double resolvedPerformance,double conservativeLow95,double conservativeHigh95,
                   double unfinishedAllLosses,double unfinishedAllWins,String interpretation) { }
    static Summary summarize(List<Game> input,int anchor,int samples,long seed) {
        if(anchor<1||samples<1000)throw new IllegalArgumentException("Invalid declared anchor or bootstrap");
        var ids=new HashSet<String>();var pairs=new LinkedHashMap<String,List<Game>>();
        for(var game:input){if(!ids.add(game.id()))throw new IllegalArgumentException("Duplicate game");pairs.computeIfAbsent(game.pair(),unused->new ArrayList<>()).add(game);}
        var families=new TreeMap<String,List<PairedElo.Pair>>();int wins=0,draws=0,losses=0,unfinished=0;
        for(var pair:pairs.values()){
            if(pair.size()!=2||pair.get(0).side().equals(pair.get(1).side())||!pair.get(0).family().equals(pair.get(1).family()))throw new IllegalArgumentException("A pair needs both colors in the same family");
            int w=0,d=0,l=0,u=0;for(var game:pair)switch(game.scoreTwice()){case 2->w++;case 1->d++;case 0->l++;case -1->u++;default->throw new IllegalArgumentException("Invalid result");}
            families.computeIfAbsent(pair.get(0).family(),unused->new ArrayList<>()).add(new PairedElo.Pair(w,d,l,u));wins+=w;draws+=d;losses+=l;unfinished+=u;
        }
        if(families.size()<2)throw new IllegalArgumentException("Require at least two independent family clusters");
        int perFamily=families.values().iterator().next().size();if(families.values().stream().anyMatch(p->p.size()!=perFamily))throw new IllegalArgumentException("Family cluster sizes must match the frozen design");
        var interval=PairedElo.estimateRepeated(new ArrayList<>(families.values()),samples,seed);int resolved=wins+draws+losses;
        Double point=resolved==0?null:anchor+PairedElo.elo((wins+draws*.5)/resolved);
        return new Summary(resolved==0?"NOT_MEASURED":"DIAGNOSTIC_ANCHOR_RELATIVE",input.size(),pairs.size(),families.size(),wins,draws,losses,unfinished,point,
                anchor+interval.eloLow95(),anchor+interval.eloHigh95(),anchor+PairedElo.elo(interval.scoreLow()),anchor+PairedElo.elo(interval.scoreHigh()),
                "Resolved-game point; family-cluster 95% bootstrap with censored-game envelope; no unfinished game adjudicated drawn; not human/FIDE Elo");
    }
    private AiProfileRating() { }
}
