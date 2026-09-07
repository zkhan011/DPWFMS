package com.dpworld.fms.domain;
import org.junit.jupiter.api.Test; import java.time.Instant; import java.util.*; import static org.junit.jupiter.api.Assertions.*;
class FleetJobTest { @Test void enforcesTransitionsAndKeepsHistory(){ var j=new FleetJob(UUID.randomUUID(),"J-1",JobType.INSPECTION,1,"A","B",Set.of(),Instant.now(),null,"test"); assertThrows(IllegalStateException.class,()->j.transition(JobStatus.COMPLETED,"x","bad")); j.transition(JobStatus.VALIDATED,"x","ok"); assertEquals(2,j.events().size()); assertThrows(UnsupportedOperationException.class,()->j.events().clear()); } }
