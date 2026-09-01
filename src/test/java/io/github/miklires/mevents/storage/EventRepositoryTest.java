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
    @Test void rewardsRemainPendingUntilConfirmed(){UUID run=UUID.randomUUID();repository.create(run,"supply",EventType.AIRDROP,"world",0,80,0).join();var tx=repository.journalReward(run,"supply",UUID.randomUUID(),"Alex","coins").join();assertEquals(1,repository.pendingRewards().join().size());assertTrue(repository.markDelivered(tx.transactionId()).join());assertTrue(repository.pendingRewards().join().isEmpty());}
    @Test void rewardJournalIsIdempotentPerPlayerAndRun(){UUID run=UUID.randomUUID(),player=UUID.randomUUID();repository.create(run,"supply",EventType.AIRDROP,"world",0,80,0).join();var first=repository.journalReward(run,"supply",player,"Alex","coins").join();var second=repository.journalReward(run,"supply",player,"Alex","diamonds").join();assertEquals(first.transactionId(),second.transactionId());assertEquals("coins",second.rewardId());assertEquals(1,repository.pendingRewards().join().size());}
    @Test void latestRunIsReturnedPerTemplate(){UUID first=UUID.randomUUID(),second=UUID.randomUUID();repository.create(first,"supply",EventType.AIRDROP,"world",0,80,0).join();repository.create(second,"boss",EventType.BOSS,"world",1,80,1).join();assertEquals(first,repository.latest("supply").join().orElseThrow().runId());assertEquals(second,repository.latest("boss").join().orElseThrow().runId());}
}
