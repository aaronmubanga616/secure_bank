package dev.securebank.ledger;

import dev.securebank.IntegrationTestSupport;
import dev.securebank.accounts.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class LedgerIT extends IntegrationTestSupport {
    @Autowired LedgerService ledger;
    @Autowired AccountService accounts;
    @Autowired PlatformTransactionManager transactionManager;

    private UUID transaction(UUID owner,String type,UUID source,UUID destination,String amount) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO transactions(id,reference,user_id,type,source_account,destination_account,amount,status,description,idempotency_key,request_hash) VALUES (?,?,?,?,?,?,?,'PROCESSING','Ledger test',?,?)",
            id,"SB-"+id,owner,type,source,destination,new BigDecimal(amount),id.toString(),"0".repeat(64));
        return id;
    }
    private UUID post(UUID owner,String type,UUID source,UUID destination,String amount) {
        return new TransactionTemplate(transactionManager).execute(status->{
            UUID id=transaction(owner,type,source,destination,amount);
            ledger.post(id,source,destination,new BigDecimal(amount));
            jdbc.update("UPDATE transactions SET status='COMPLETED',completed_at=now() WHERE id=?",id);
            return id;
        });
    }

    @Test void customerBalancesComeFromBalancedImmutableJournalEntries() {
        var alice=customer(); var bob=customer();
        var source=accounts.create(alice.id(),AccountService.Type.CURRENT);
        var destination=accounts.create(bob.id(),AccountService.Type.SAVINGS);
        post(alice.id(),"DEPOSIT",null,source.id(),"1000.00");
        UUID transfer=post(alice.id(),"TRANSFER",source.id(),destination.id(),"250.25");
        assertThat(ledger.balance(source.id())).isEqualByComparingTo("749.75");
        assertThat(ledger.balance(destination.id())).isEqualByComparingTo("250.25");
        assertThat(accounts.get(alice.id(),source.id()).balance()).isEqualTo("749.75");
        BigDecimal difference=jdbc.queryForObject("SELECT sum(CASE WHEN direction='DEBIT' THEN amount ELSE -amount END) FROM ledger_entries WHERE transaction_id=?",BigDecimal.class,transfer);
        assertThat(difference).isEqualByComparingTo(BigDecimal.ZERO);
        assertThatThrownBy(()->jdbc.update("UPDATE ledger_entries SET amount=1 WHERE transaction_id=?",transfer)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM ledger_entries WHERE transaction_id=?",transfer)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE transactions SET description='altered' WHERE id=?",transfer)).isInstanceOf(RuntimeException.class);
        assertThat(ledger.balance(source.id())).isEqualByComparingTo("749.75");
    }

    @Test void databaseRejectsAnUnbalancedCompletedTransactionAtCommit() {
        var customer=customer(); var account=accounts.create(customer.id(),AccountService.Type.CURRENT);
        UUID id=UUID.randomUUID();
        assertThatThrownBy(()->new TransactionTemplate(transactionManager).executeWithoutResult(status->{
            jdbc.update("INSERT INTO transactions(id,reference,user_id,type,destination_account,amount,status,description,idempotency_key,request_hash,completed_at) VALUES (?,?,?,'DEPOSIT',?,100,'COMPLETED','Invalid journal',?,?,now())",
                id,"SB-"+id,customer.id(),account.id(),id.toString(),"0".repeat(64));
            jdbc.update("INSERT INTO ledger_entries(id,transaction_id,ledger_account_id,direction,amount) VALUES (?,?,?,'DEBIT',100)",UUID.randomUUID(),id,LedgerService.SIMULATED_CASH);
        })).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE id=?",Integer.class,id)).isZero();
        assertThat(ledger.balance(account.id())).isEqualByComparingTo("0.00");
    }

    @Test void databaseRejectsACompletedTransactionWithoutAnyJournal() {
        var customer=customer(); var account=accounts.create(customer.id(),AccountService.Type.CURRENT);
        assertThatThrownBy(()->new TransactionTemplate(transactionManager).executeWithoutResult(status->{
            UUID id=transaction(customer.id(),"DEPOSIT",null,account.id(),"100.00");
            jdbc.update("UPDATE transactions SET status='COMPLETED',completed_at=now() WHERE id=?",id);
        })).isInstanceOf(RuntimeException.class);
    }

    @Test void overdrawnJournalRollsBackBothSides() {
        var customer=customer(); var account=accounts.create(customer.id(),AccountService.Type.CURRENT);
        post(customer.id(),"DEPOSIT",null,account.id(),"100.00");
        assertThatThrownBy(()->post(customer.id(),"WITHDRAWAL",account.id(),null,"101.00")).isInstanceOf(RuntimeException.class);
        assertThat(ledger.balance(account.id())).isEqualByComparingTo("100.00");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE source_account=?",Integer.class,account.id())).isZero();
    }
}
