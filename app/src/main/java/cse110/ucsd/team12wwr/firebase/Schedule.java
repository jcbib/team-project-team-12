package cse110.ucsd.team12wwr.firebase;

import java.util.Map;

public class Schedule {
    public String teamID;
    public String routeName;
    public String proposerUserID;
    public Map<String, Vote> userVoteMap;

    public enum Vote {
        ABSTAINED,
        ACCEPTED,
        DECLINED_CONFLICT,
        DECLINED_DIFFICULTY;
    }
}