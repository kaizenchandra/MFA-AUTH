package com.network.keycloak;

import org.junit.jupiter.api.*;
import org.keycloak.models.SingleUseObjectProvider;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PhoneChallengeTest {
    SingleUseObjectProvider store;
    Map<String,Map<String,String>> values;
    @BeforeEach void setup() {
        values=new ConcurrentHashMap<>();store=mock(SingleUseObjectProvider.class);
        when(store.putIfAbsent(anyString(),anyLong())).thenAnswer(i -> values.putIfAbsent(i.getArgument(0),Map.of())==null);
        doAnswer(i -> {values.put(i.getArgument(0),i.getArgument(2));return null;}).when(store).put(anyString(),anyLong(),anyMap());
        when(store.get(anyString())).thenAnswer(i -> values.get(i.getArgument(0)));
        when(store.remove(anyString())).thenAnswer(i -> values.remove(i.getArgument(0)));
    }
    @Test void validCodeAndReplay() {var c=new PhoneChallenge(store);var issued=c.issue("+14155550123","alice");assertTrue(c.verify(issued.id(),"alice","+14155550123",issued.code()));assertFalse(c.verify(issued.id(),"alice","+14155550123",issued.code()));}
    @Test void noPlaintextStored() {var i=new PhoneChallenge(store).issue("+14155550123","alice");assertFalse(values.get(i.id()).containsValue(i.code()));}
    @Test void ownershipBinding() {var c=new PhoneChallenge(store);var i=c.issue("+14155550123","alice");assertFalse(c.verify(i.id(),"bob","+14155550123",i.code()));assertFalse(c.verify(i.id(),"alice","+14155550124",i.code()));assertTrue(c.verify(i.id(),"alice","+14155550123",i.code()));}
    @Test void expiredCodeRejected() {var c=new PhoneChallenge(store);var i=c.issue("+14155550123","alice");values.remove(i.id());assertFalse(c.verify(i.id(),"alice","+14155550123",i.code()));}
    @Test void boundedAttempts() {var c=new PhoneChallenge(store);var i=c.issue("+14155550123","alice");String wrong=i.code().equals("000000")?"000001":"000000";for(int n=0;n<5;n++)assertFalse(c.verify(i.id(),"alice","+14155550123",wrong));assertFalse(c.verify(i.id(),"alice","+14155550123",i.code()));}
    @Test void resendLimited() {var c=new PhoneChallenge(store);c.issue("+14155550123","alice");assertThrows(IllegalStateException.class,()->c.issue("+14155550123","alice"));}
    @Test void invalidPhoneRejected(){assertThrows(IllegalArgumentException.class,()->new PhoneChallenge(store).issue("5551234","alice"));}
    @Test void concurrentConsumptionHasOneWinner() throws Exception {
        var c=new PhoneChallenge(store);var i=c.issue("+14155550123","alice");var winners=new AtomicInteger();
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {var jobs=new ArrayList<Future<?>>();for(int n=0;n<20;n++)jobs.add(pool.submit(()->{if(c.verify(i.id(),"alice","+14155550123",i.code()))winners.incrementAndGet();}));for(var f:jobs)f.get();}
        assertEquals(1,winners.get());
    }
}
