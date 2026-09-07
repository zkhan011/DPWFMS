package com.dpworld.fms.scheduling;
import com.dpworld.fms.domain.*; import java.time.*; import java.util.*;
public final class CandidateScorer {
 public record Weights(double distance,double eta,double energy,double workload,double health,double fairness,double priority,double deadline){}
 public record Candidate(Asset asset,double distanceMetres,double etaSeconds,int activeJobs,int completedJobs){}
 private final Weights weights; private final double reserveEnergyPercent;
 public CandidateScorer(Weights weights,double reserveEnergyPercent){this.weights=weights;this.reserveEnergyPercent=reserveEnergyPercent;}
 public Optional<Candidate> select(FleetJob job,Collection<Candidate> candidates,Instant now){return candidates.stream().filter(c->eligible(job,c.asset())).min(Comparator.comparingDouble(c->score(job,c,now)));}
 public boolean eligible(FleetJob job,Asset a){return a.canPerform(job.requiredCapabilities())&&a.energyPercent()>=reserveEnergyPercent&&a.status()!=AssetStatus.OFFLINE;}
 public double score(FleetJob j,Candidate c,Instant now){double urgency=j.deadline()==null?0:Math.max(0,3600-Duration.between(now,j.deadline()).toSeconds());double unhealthy=c.asset().maintenanceStatus()==MaintenanceStatus.SERVICEABLE?0:1;return c.distanceMetres()*weights.distance+c.etaSeconds()*weights.eta+(100-c.asset().energyPercent())*weights.energy+c.activeJobs()*weights.workload+unhealthy*weights.health+c.completedJobs()*weights.fairness-j.priority()*weights.priority-urgency*weights.deadline;}
}
