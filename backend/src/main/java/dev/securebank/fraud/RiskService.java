package dev.securebank.fraud;

import dev.securebank.events.EventService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class RiskService {
    public record Decision(int score,boolean hold,List<String> reasons) {}
    private final JdbcTemplate jdbc;
    private final EventService events;
    public RiskService(JdbcTemplate jdbc,EventService events) { this.jdbc=jdbc; this.events=events; }
    public Decision assess(UUID owner,UUID destination,BigDecimal amount) {
        var rules=jdbc.queryForMap("SELECT * FROM fraud_rules WHERE id=1");
        int score=0; var reasons=new ArrayList<String>();
        if(amount.compareTo((BigDecimal)rules.get("large_amount"))>=0) { score+=45; reasons.add("LARGE_AMOUNT"); }
        if(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE user_id=? AND type='TRANSFER' AND created_at>now()-interval '5 minutes'",Integer.class,owner)>=(Integer)rules.get("velocity_count")) { score+=40; reasons.add("TRANSFER_VELOCITY"); }
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM beneficiaries WHERE user_id=? AND destination_account=? AND status='ACTIVE' AND created_at<now()-interval '24 hours')",Boolean.class,owner,destination))) { score+=30; reasons.add("NEW_RECIPIENT"); }
        if(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE user_id=? AND action IN ('LOGIN','MFA_VERIFY') AND result='FAILURE' AND created_at>now()-interval '15 minutes'",Integer.class,owner)>=5) { score+=25; reasons.add("FAILED_SIGN_INS"); }
        if(jdbc.queryForObject("SELECT count(*) FROM user_devices WHERE user_id=? AND first_seen_at>now()-interval '15 minutes'",Integer.class,owner)>0 && jdbc.queryForObject("SELECT count(*) FROM user_devices WHERE user_id=?",Integer.class,owner)>1) { score+=20; reasons.add("NEW_DEVICE"); }
        int hour=ZonedDateTime.now(ZoneId.of("Africa/Lusaka")).getHour();
        if(hour<5) { score+=10; reasons.add("UNUSUAL_HOUR"); }
        score=Math.min(score,100); return new Decision(score,score>=(Integer)rules.get("hold_score"),List.copyOf(reasons));
    }
    public void alert(UUID owner,UUID transaction,Decision decision) {
        if(decision.score()<=30) return;
        UUID id=UUID.randomUUID();
        int inserted=jdbc.update("INSERT INTO fraud_alerts(id,user_id,transaction_id,score,reasons) VALUES (?,?,?,?,?) ON CONFLICT(transaction_id) DO NOTHING",id,owner,transaction,decision.score(),String.join(",",decision.reasons()));
        if(inserted==1) events.record("FraudAlertCreated",owner,"fraud/"+id,Map.of("transactionId",transaction.toString(),"score",decision.score()));
    }
}
