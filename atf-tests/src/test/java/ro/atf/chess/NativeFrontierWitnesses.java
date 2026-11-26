package ro.atf.chess;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Portable captured teacher evidence, independent of a running engine or external study directory. */
final class NativeFrontierWitnesses {
    private static final String PIN="91f6034d10ab8aa18c27394e397ab6b68829ae4df06c894507c414dfe0ecd923";
    static List<Witness> load()throws Exception {
        try(var in=NativeFrontierWitnesses.class.getResourceAsStream("/hard-lessons/checked-frontier-witnesses.yaml")){
            require(in!=null,"Missing captured frontier witnesses");var tree=LearningGameAudit.mapper().readTree(in.readAllBytes());
            require(PIN.equals(HardLeagueAudit.digest(HardLeagueAudit.canonical(tree).getBytes(StandardCharsets.UTF_8))),"Modified captured teacher evidence");
            require(tree.path("schema").asInt()==1&&tree.path("nativeExit").isIntegralNumber()&&tree.path("nativeExit").asInt()==0,"Failed teacher execution");
            var teacher=tree.path("teacher");require(teacher.path("sha256").asText().matches("[a-f0-9]{64}")&&teacher.path("threads").asInt()==1&&teacher.path("hashMb").asInt()==16&&!teacher.path("limitStrength").asBoolean(true),"Foreign teacher configuration");
            require(teacher.path("processExit").path("code").isIntegralNumber()&&teacher.path("processExit").path("code").asInt()==0&&teacher.path("processExit").path("signal").isNull(),"Teacher process did not exit cleanly");
            var header=tree.path("sessionHeader").toString();
            for(String marker:List.of("id name Stockfish 19","uciok","readyok","> setoption name Hash value 16","> setoption name Threads value 1","> setoption name UCI_LimitStrength value false","> setoption name UCI_ShowWDL value true"))require(header.contains(marker),"Missing actual teacher setup "+marker);
            var result=new ArrayList<Witness>();for(var item:tree.path("cases"))result.add(read(item));require(result.size()==7,"Incomplete witness families");return List.copyOf(result);
        }
    }
    static Witness read(JsonNode item) {
        require(item.path("history").isArray()&&item.path("nativeGuard").isBoolean(),"Missing explicit history or guard scope");
        var game=game(item);String origin=item.path("initialFen").asText();
        if(item.has("oracleInitialFen")){
            var normalized=origin.split(" ");require(normalized.length==6,"Invalid composed origin");normalized[4]="0";
            require(item.path("oracleInitialFen").asText().equals(String.join(" ",normalized)),"A composed-clock correction cannot substitute another board or rights");
            origin=item.path("oracleInitialFen").asText();
        }
        String fen=StudyFen.replay(origin,strings(item.path("history")).stream().map(ChessPosition.Move::fromUci).toList());
        var seen=new HashSet<String>();require(item.path("phases").size()==6,"Missing captured phase");
        for(var phase:item.path("phases")){
            int depth=phase.path("depth").asInt();String root=phase.path("move").asText(null);
            require(phase.path("depth").isIntegralNumber()&&(depth==17||depth==19)&&phase.path("caseId").equals(item.path("id"))&&seen.add(depth+"/"+root),"Foreign or repeated phase");
            require(phase.path("nativeFen").asText().equals(fen),"Foreign teacher board or clocks");
            String position="position fen "+origin+(item.path("history").isEmpty()?"":" moves "+String.join(" ",strings(item.path("history"))));
            String cap="";
            if(phase.has("nodeSafetyCap")){
                require(phase.path("nodeSafetyCap").isIntegralNumber()&&phase.path("nodeSafetyCap").asLong()>0&&phase.path("nodeSafetyCap").asLong()<=10_000_000,"Invalid declared teacher safety cap");
                cap=" nodes "+phase.path("nodeSafetyCap").asLong();
            }
            require(phase.path("positionCommand").asText().equals(position)&&strings(phase.path("commands")).equals(List.of(position,"go depth "+depth+cap+(root==null?"":" searchmoves "+root))),"Foreign position/search commands");
            List<String> exact=null;String best=null;for(String line:strings(phase.path("raw"))){
                var tokens=Arrays.asList(line.split(" "));if(line.startsWith("bestmove "))best=tokens.get(1);
                if(line.startsWith("info depth ")&&tokens.contains("score")&&tokens.contains("wdl")&&tokens.contains("pv")&&!tokens.contains("lowerbound")&&!tokens.contains("upperbound")&&Integer.parseInt(tokens.get(tokens.indexOf("depth")+1))==depth)exact=tokens;
            }
            require(exact!=null&&best!=null&&phase.path("bestmove").asText().equals(best),"Missing exact raw search or rewritten best move");
            int score=exact.indexOf("score"),wdl=exact.indexOf("wdl");require(phase.path("score").path("value").isIntegralNumber()&&exact.get(score+1).equals(phase.path("score").path("type").asText())&&Integer.parseInt(exact.get(score+2))==phase.path("score").path("value").asInt(),"Rewritten score");
            int total=0;require(phase.path("wdl").size()==3,"Missing WDL");for(int i=0;i<3;i++){int value=Integer.parseInt(exact.get(wdl+i+1));require(value>=0&&phase.path("wdl").get(i).isIntegralNumber()&&value==phase.path("wdl").get(i).asInt(),"Rewritten raw WDL");total+=value;}
            require(total==1000&&phase.path("expectedPerMille").isNumber()&&phase.path("expectedPerMille").asDouble()==phase.path("wdl").get(0).asInt()+phase.path("wdl").get(1).asInt()/2.0,"Foreign WDL scale");
            var pv=exact.subList(exact.indexOf("pv")+1,exact.size());require(!pv.isEmpty()&&pv.getFirst().equals(best)&&(root==null||root.equals(best)),"Foreign forced root or PV");
            var board=game.position();for(String move:pv)board=board.play(ChessPosition.Move.fromUci(move));
        }
        for(int depth:List.of(17,19))require(seen.contains(depth+"/null"),"Missing free-root comparison");
        require(game.position().legalMoves().contains(ChessPosition.Move.fromUci(item.path("expected").asText())),"Illegal selected reference");
        return new Witness(item);
    }
    record Witness(JsonNode input) {
        String id(){return input.path("id").asText();}
        boolean nativeGuard(){return input.path("nativeGuard").asBoolean();}
        ChessMatch game(){return NativeFrontierWitnesses.game(input);}
        String selected(){return input.path("expected").asText();}
        String rejected()throws Exception{return LearningGameAudit.mapper().treeToValue(input.path("rejected"),ChessPosition.Move.class).uci();}
        JsonNode phase(String root,int depth){for(var p:input.path("phases"))if(p.path("depth").asInt()==depth&&Objects.equals(root,p.path("move").asText(null)))return p;throw new IllegalArgumentException("Missing captured root");}
        double loss(String root,int depth){return Math.max(0,phase(null,depth).path("expectedPerMille").asDouble()-phase(root,depth).path("expectedPerMille").asDouble());}
    }
    private static ChessMatch game(JsonNode input){var p=ChessPosition.fromFen(input.path("initialFen").asText());var game=ChessMatch.fromPosition(p,new MatchSetup(ChessPosition.Side.WHITE,MatchSetup.Level.HARD,false));for(String uci:strings(input.path("history")))game=game.play(ChessPosition.Move.fromUci(uci));return game;}
    private static List<String> strings(JsonNode node){require(node.isArray(),"Missing raw array");var result=new ArrayList<String>();for(var value:node){require(value.isTextual(),"Non-text raw value");result.add(value.asText());}return result;}
    private static void require(boolean valid,String reason){if(!valid)throw new IllegalArgumentException(reason);}
}
