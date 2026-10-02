package app.rpphone.companion;
final class ReconnectPolicy {
 int attempt=0;long next=0;boolean login=false;
 void ready(){attempt=0;next=0;login=false;}
 long delay(){return Math.min(60000L,5000L*(1L<<Math.min(attempt++,4)));}
 boolean allowed(long now,boolean network,boolean call,boolean auth){return network && !call && !auth && !login && now>=next;}
}
