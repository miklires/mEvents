package io.github.miklires.mevents.storage;

import io.github.miklires.mevents.api.*;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class EventRepositoryTest {
    private EventRepository repository;
    @BeforeEach void open(){repository=new EventRepository("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1");repository.initialize().join();}
    @AfterEach void close(){repository.close();}
    @Test void transitionUsesCompareAndSet(){UUID id=UUID.randomUUID();repository.create(id,"supply",EventType.AIRDROP,"world",0,80,0).join();assertTrue(repository.transition(id,EventState.SCHEDULED,EventState.PREPARING,null,null,"prepare").join());assertFalse(repository.transition(id,EventState.SCHEDULED,EventState.CANCELLED,null,null,"late").join());}
    @Test void contributionIsAggregated(){UUID run=UUID.randomUUID(),player=UUID.randomUUID();repository.addDamage(run,player,"Alex",2.5).join();repository.addDamage(run,player,"Alex",3.5).join();assertEquals(6.0,repository.contributions(run).join().getFirst().damage());}
    @Test void rewardsRemainPendingUntilConfirmed(){var tx=repository.journalReward(UUID.randomUUID(),UUID.randomUUID(),"Alex","coins").join();assertEquals(1,repository.pendingRewards().join().size());assertTrue(repository.markDelivered(tx.transactionId()).join());assertTrue(repository.pendingRewards().join().isEmpty());}
}
