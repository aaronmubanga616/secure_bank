package dev.securebank.transactions;

import dev.securebank.audit.AuditService;
import dev.securebank.auth.Crypto;
import dev.securebank.common.ApiException;
import dev.securebank.common.PageResult;
import dev.securebank.ledger.LedgerService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import static dev.securebank.transactions.TransactionDtos.*;
import dev.securebank.events.EventService;
import dev.securebank.fraud.RiskService;

@Service
public class TransactionService {
    private static final String SELECT="SELECT t.*,s.user_id AS source_owner,d.user_id AS destination_owner,s.account_number AS source_number,d.account_number AS destination_number FROM transactions t LEFT JOIN accounts s ON s.id=t.source_account LEFT JOIN accounts d ON d.id=t.destination_account";
    private static final String OWNED=" (t.user_id=? OR s.user_id=? OR d.user_id=?)";
    private final JdbcTemplate jdbc;
    private final LedgerService ledger;
    private final AuditService audit;
    private final LimitService limits;
    private final IdempotencyService idempotency;
    private final EventService events;
    private final RiskService risk;
    public TransactionService(JdbcTemplate jdbc,LedgerService ledger,AuditService audit,LimitService limits,IdempotencyService idempotency,EventService events,RiskService risk) {
        this.jdbc=jdbc; this.ledger=ledger; this.audit=audit; this.limits=limits; this.idempotency=idempotency; this.events=events; this.risk=risk;
    }

    @Transactional
    public TransactionView create(UUID owner,CreateTransaction input,String key) {
        BigDecimal amount=validate(input,key);
        String destinationNumber=input.destinationAccountNumber()==null || input.destinationAccountNumber().isBlank()?null:input.destinationAccountNumber().trim().toUpperCase(Locale.ROOT);
        String description=input.description().trim();
        String hash=Crypto.hash(input.type()+"\0"+input.sourceAccount()+"\0"+destinationNumber+"\0"+amount.toPlainString()+"\0"+description);
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))","financial:"+owner+":"+key);
        var existing=jdbc.queryForList("SELECT id,request_hash FROM transactions WHERE user_id=? AND idempotency_key=?",owner,key);
        if (!existing.isEmpty()) {
            if (!hash.equals(existing.getFirst().get("request_hash"))) throw new ApiException(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","This key was already used for a different request.");
            return idempotency.response(owner,key,()->get(owner,(UUID)existing.getFirst().get("id")));
        }

        Map<String,Object> source=input.sourceAccount()==null?null:ownedAccount(owner,input.sourceAccount());
        Map<String,Object> destination=destinationNumber==null?null:destination(destinationNumber);
        if (input.type()==Type.DEPOSIT && !owner.equals(destination.get("user_id"))) throw ApiException.notFound();
        if (source!=null && destination!=null && source.get("id").equals(destination.get("id"))) throw ApiException.badRequest("SAME_ACCOUNT","Select different accounts.");
        UUID sourceId=source==null?null:(UUID)source.get("id");
        UUID destinationId=destination==null?null:(UUID)destination.get("id");
        lockParticipants(owner,sourceId,destinationId);
        source=sourceId==null?null:ownedAccount(owner,sourceId);
        destination=destinationId==null?null:jdbc.queryForList("SELECT a.*,u.status AS user_status FROM accounts a JOIN users u ON u.id=a.user_id WHERE a.id=?",destinationId).getFirst();
        ledger.lockAccounts(sourceId,destinationId);
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO transactions(id,reference,user_id,type,amount,source_account,destination_account,status,description,idempotency_key,request_hash) VALUES (?,?,?,?,?,?,?,'PROCESSING',?,?,?)",
            id,"SB-"+id,owner,input.type().name(),amount,sourceId,destinationId,description,key,hash);
        var data=new HashMap<String,Object>(); data.put("transactionId",id.toString()); data.put("amount",amount.toPlainString()); data.put("currency","ZMW"); data.put("type",input.type().name());
        if (destination!=null) data.put("destinationUser",destination.get("user_id").toString());
        events.record("TransactionCreated",owner,"transactions/"+id,data);
        String failure=checkRules(owner,input.type(),source,destination,amount);
        var decision=input.type()==Type.TRANSFER && failure==null?risk.assess(owner,destinationId,amount,id):new RiskService.Decision(0,false,List.of());
        jdbc.update("UPDATE transactions SET risk_score=? WHERE id=?",decision.score(),id);
        if (failure==null && decision.hold()) {
            jdbc.update("UPDATE transactions SET status='FLAGGED' WHERE id=?",id);
            risk.alert(owner,id,decision);
            audit.record(owner,"TRANSACTION_FLAGGED","REVIEW","transactions/"+id);
            events.record("TransactionFlagged",owner,"transactions/"+id,data);
        } else if (failure==null) {
            ledger.post(id,sourceId,destinationId,amount);
            jdbc.update("UPDATE transactions SET status='COMPLETED',completed_at=now() WHERE id=?",id);
            audit.record(owner,"TRANSACTION_COMPLETED","SUCCESS","transactions/"+id);
            events.record("TransactionCompleted",owner,"transactions/"+id,data);
            risk.alert(owner,id,decision);
        } else {
            jdbc.update("UPDATE transactions SET status='FAILED',failure_reason=? WHERE id=?",failure,id);
            audit.record(owner,"TRANSACTION_FAILED","FAILURE","transactions/"+id);
            events.record("TransactionFailed",owner,"transactions/"+id,data);
        }
        return idempotency.response(owner,key,()->get(owner,id));
    }

    @Transactional
    public void reviewHeld(UUID id,boolean approve) {
        var initial=jdbc.queryForList("SELECT * FROM transactions WHERE id=?",id);
        if(initial.isEmpty()) throw ApiException.notFound();
        var row=initial.getFirst(); UUID owner=(UUID)row.get("user_id");
        UUID source=(UUID)row.get("source_account"),destination=(UUID)row.get("destination_account");
        lockParticipants(owner,source,destination,false);
        String status=jdbc.queryForObject("SELECT status FROM transactions WHERE id=? FOR UPDATE",String.class,id);
        if(!"FLAGGED".equals(status)) return;
        ledger.lockAccounts(source,destination);
        jdbc.update("UPDATE transactions SET status='PROCESSING' WHERE id=?",id);
        var target=jdbc.queryForMap("SELECT a.*,u.status AS user_status FROM accounts a JOIN users u ON u.id=a.user_id WHERE a.id=?",destination);
        BigDecimal amount=(BigDecimal)row.get("amount");
        String failure=approve?checkRules(owner,Type.TRANSFER,ownedAccount(owner,source),target,amount):"FRAUD_CONFIRMED";
        var data=Map.<String,Object>of("transactionId",id.toString(),"amount",amount.toPlainString(),"currency","ZMW","type","TRANSFER","destinationUser",target.get("user_id").toString());
        if(failure==null) {
            ledger.post(id,source,destination,amount);
            jdbc.update("UPDATE transactions SET status='COMPLETED',completed_at=now() WHERE id=?",id);
            events.record("TransactionCompleted",owner,"transactions/"+id,data);
        } else {
            jdbc.update("UPDATE transactions SET status='FAILED',failure_reason=? WHERE id=?",failure,id);
            events.record("TransactionFailed",owner,"transactions/"+id,data);
        }
    }

    private BigDecimal validate(CreateTransaction input,String key) {
        if (key==null || !key.matches("[A-Za-z0-9._:-]{8,128}")) throw ApiException.badRequest("INVALID_IDEMPOTENCY_KEY","Supply an 8-128 character Idempotency-Key.");
        if (input.type()==null || input.amount()==null || input.description()==null || input.description().isBlank() || input.description().trim().length()>160
            || input.description().chars().anyMatch(c->Character.isISOControl(c))) throw ApiException.badRequest("INVALID_REQUEST","Check the transaction details.");
        if ((input.type()==Type.DEPOSIT)!=(input.sourceAccount()==null) || (input.type()==Type.WITHDRAWAL)!=(input.destinationAccountNumber()==null || input.destinationAccountNumber().isBlank()))
            throw ApiException.badRequest("INVALID_ACCOUNTS","Choose the required source and destination accounts.");
        try {
            BigDecimal amount=input.amount().setScale(2,RoundingMode.UNNECESSARY);
            if (amount.signum()<=0 || amount.precision()>18) throw new ArithmeticException();
            return amount;
        } catch(ArithmeticException error) { throw ApiException.badRequest("INVALID_AMOUNT","Use a positive amount with at most two decimal places."); }
    }

    private Map<String,Object> ownedAccount(UUID owner,UUID id) {
        var rows=jdbc.queryForList("SELECT a.*,u.status AS user_status FROM accounts a JOIN users u ON u.id=a.user_id WHERE a.id=? AND a.user_id=?",id,owner);
        if (rows.isEmpty()) throw ApiException.notFound(); return rows.getFirst();
    }
    private Map<String,Object> destination(String number) {
        var rows=jdbc.queryForList("SELECT a.*,u.status AS user_status FROM accounts a JOIN users u ON u.id=a.user_id WHERE a.account_number=?",number);
        if (rows.isEmpty()) throw ApiException.notFound(); return rows.getFirst();
    }
    private void lockParticipants(UUID owner,UUID source,UUID destination) {
        lockParticipants(owner,source,destination,true);
    }
    private void lockParticipants(UUID owner,UUID source,UUID destination,boolean requireActive) {
        var users=jdbc.queryForList("SELECT id,status,role,email_verified FROM users WHERE id=? OR id IN (SELECT user_id FROM accounts WHERE id IN (?,?)) ORDER BY id FOR UPDATE",owner,source,destination);
        var current=users.stream().filter(u->owner.equals(u.get("id"))).findFirst().orElseThrow(ApiException::notFound);
        if (requireActive && (!current.get("status").equals("ACTIVE") || !current.get("role").equals("CUSTOMER") || !Boolean.TRUE.equals(current.get("email_verified"))))
            throw new ApiException(HttpStatus.FORBIDDEN,"ACCOUNT_INELIGIBLE","The customer cannot transact.");
        jdbc.queryForList("SELECT id FROM accounts WHERE id IN (?,?) ORDER BY id FOR UPDATE",source,destination);
    }
    private String checkRules(UUID owner,Type type,Map<String,Object> source,Map<String,Object> destination,BigDecimal amount) {
        if ((source!=null && (!source.get("status").equals("ACTIVE") || !source.get("user_status").equals("ACTIVE")))
            || (destination!=null && (!destination.get("status").equals("ACTIVE") || !destination.get("user_status").equals("ACTIVE")))) return "ACCOUNT_UNAVAILABLE";
        var policy=limits.current();
        if (amount.compareTo(policy.perTransaction())>0) return "TRANSACTION_LIMIT";
        BigDecimal dailyLimit=policy.daily(type);
        Instant start=LocalDate.now(ZoneId.of("Africa/Lusaka")).atStartOfDay(ZoneId.of("Africa/Lusaka")).toInstant();
        BigDecimal spent=jdbc.queryForObject("SELECT coalesce(sum(amount),0) FROM transactions WHERE user_id=? AND type=? AND status IN ('COMPLETED','FLAGGED') AND created_at>=?",BigDecimal.class,owner,type.name(),Timestamp.from(start));
        if (spent.add(amount).compareTo(dailyLimit)>0) return "DAILY_LIMIT";
        if (source!=null && ledger.balance((UUID)source.get("id")).compareTo(amount)<0) return "INSUFFICIENT_FUNDS";
        return null;
    }

    public TransactionView get(UUID owner,UUID id) {
        var rows=jdbc.query(SELECT+" WHERE t.id=? AND"+OWNED,(r,n)->view(r,owner),id,owner,owner,owner);
        if (rows.isEmpty()) throw ApiException.notFound(); return rows.getFirst();
    }

    public PageResult<TransactionView> list(UUID owner,int page,int size,String query,Status status,Instant from,Instant to,BigDecimal minimum,BigDecimal maximum) {
        return listScoped(owner,null,page,size,query,status,from,to,minimum,maximum);
    }
    public PageResult<TransactionView> listAll(int page,int size,String query,Status status,Instant from,Instant to,BigDecimal minimum,BigDecimal maximum) {
        return listScoped(null,null,page,size,query,status,from,to,minimum,maximum);
    }
    public PageResult<TransactionView> forAccount(UUID owner,UUID account,int page,int size) {
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM accounts WHERE id=? AND user_id=?)",Boolean.class,account,owner)))throw ApiException.notFound();
        return listScoped(owner,account,page,size,null,null,null,null,null,null);
    }
    public TransactionView forStaff(UUID id) {
        var rows=jdbc.query(SELECT+" WHERE t.id=?",(r,n)->view(r,r.getObject("user_id",UUID.class)),id);
        if(rows.isEmpty())throw ApiException.notFound();return rows.getFirst();
    }
    private PageResult<TransactionView> listScoped(UUID owner,UUID account,int page,int size,String query,Status status,Instant from,Instant to,BigDecimal minimum,BigDecimal maximum) {
        if (page<0 || page>100000 || size<1 || size>100 || (from!=null && to!=null && from.isAfter(to))) throw ApiException.badRequest("INVALID_PAGE","Check the page or date range.");
        var params=owner==null?new ArrayList<Object>():new ArrayList<Object>(List.of(owner,owner,owner)); var where=new StringBuilder(owner==null?" WHERE true":" WHERE"+OWNED);
        if(account!=null){where.append(" AND (t.source_account=? OR t.destination_account=?)");params.add(account);params.add(account);}
        if(minimum!=null&&minimum.signum()<0||maximum!=null&&maximum.signum()<0||minimum!=null&&maximum!=null&&minimum.compareTo(maximum)>0)throw ApiException.badRequest("INVALID_FILTER","Check the amount range.");
        if (query!=null && !query.isBlank()) { where.append(" AND (t.description ILIKE ? OR t.reference ILIKE ?)"); params.add("%"+query.trim()+"%"); params.add("%"+query.trim()+"%"); }
        if (status!=null) { where.append(" AND t.status=?"); params.add(status.name()); }
        if (from!=null) { where.append(" AND t.created_at>=?"); params.add(Timestamp.from(from)); }
        if (to!=null) { where.append(" AND t.created_at<?"); params.add(Timestamp.from(to)); }
        if (minimum!=null) { where.append(" AND t.amount>=?"); params.add(minimum); }
        if (maximum!=null) { where.append(" AND t.amount<=?"); params.add(maximum); }
        Long total=jdbc.queryForObject("SELECT count(*) FROM ("+SELECT+where+") filtered",Long.class,params.toArray());
        params.add(size); params.add(page*size);
        var content=jdbc.query(SELECT+where+" ORDER BY t.created_at DESC,t.id DESC LIMIT ? OFFSET ?",(r,n)->view(r,owner==null?r.getObject("user_id",UUID.class):owner),params.toArray());
        return new PageResult<>(content,page,size,total==null?0:total);
    }

    private TransactionView view(ResultSet row,UUID owner) throws SQLException {
        UUID sourceOwner=row.getObject("source_owner",UUID.class), destinationOwner=row.getObject("destination_owner",UUID.class);
        String direction=owner.equals(sourceOwner)?(owner.equals(destinationOwner)?"INTERNAL":"OUT"):"IN";
        Timestamp completed=row.getTimestamp("completed_at");
        return new TransactionView(row.getObject("id",UUID.class),row.getString("reference"),row.getString("type"),row.getBigDecimal("amount").setScale(2).toPlainString(),row.getString("currency"),
            row.getObject("source_account",UUID.class),row.getObject("destination_account",UUID.class),mask(row.getString("source_number")),mask(row.getString("destination_number")),
            row.getString("status"),row.getString("description"),direction,row.getInt("risk_score"),row.getString("failure_reason"),row.getTimestamp("created_at").toInstant(),completed==null?null:completed.toInstant());
    }
    private String mask(String value) { return value==null?null:"**** "+value.substring(Math.max(0,value.length()-4)); }
}
